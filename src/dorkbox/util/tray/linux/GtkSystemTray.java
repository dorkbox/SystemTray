/*
 * Copyright 2014 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.util.tray.linux;

import com.sun.jna.Pointer;
import dorkbox.util.ScreenUtil;
import dorkbox.util.SwingUtil;
import dorkbox.util.jna.linux.Gobject;
import dorkbox.util.jna.linux.Gtk;
import dorkbox.util.jna.linux.Gtk.GdkEventButton;
import dorkbox.util.jna.linux.GtkSupport;
import dorkbox.util.tray.SystemTray;
import dorkbox.util.tray.SystemTrayMenuAction;
import dorkbox.util.tray.SystemTrayMenuPopup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for handling all system tray interactions via GTK.
 * <p/>
 * This is the "old" way to do it, and does not work with some desktop environments.
 */
public
class GtkSystemTray extends SystemTray {
    private static final Gobject libgobject = Gobject.INSTANCE;
    private static final Gtk libgtk = Gtk.INSTANCE;

    final Map<String, JMenuItem> menuEntries = new HashMap<String, JMenuItem>(2);

    volatile SystemTrayMenuPopup jmenu;
    volatile JMenuItem connectionStatusItem;

    private volatile Pointer trayIcon;

    // need to hang on to these to prevent gc
    private final List<Pointer> widgets = new ArrayList<Pointer>(4);

    // have to make this a field, to prevent GC on this object
    @SuppressWarnings("FieldCanBeLocal")
    private Gobject.GEventCallback gtkCallback;

    public
    GtkSystemTray() {
    }

    @Override
    public
    void createTray(String iconName) {
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                GtkSystemTray.this.jmenu = new SystemTrayMenuPopup();
            }
        });

        libgtk.gdk_threads_enter();

        final Pointer trayIcon = libgtk.gtk_status_icon_new();
        this.trayIcon = trayIcon;
        libgtk.gtk_status_icon_set_from_file(trayIcon, iconPath(iconName));
        libgtk.gtk_status_icon_set_tooltip(trayIcon, this.appName);
        libgtk.gtk_status_icon_set_visible(trayIcon, true);

        // have to make this a field, to prevent GC on this object
        this.gtkCallback = new Gobject.GEventCallback() {
            @Override
            public
            void callback(Pointer system_tray, final GdkEventButton event) {
                // BUTTON_PRESS only (any mouse click)
                if (event.type == 4) {
                    SwingUtil.invokeLater(new Runnable() {
                        @Override
                        public
                        void run() {
                            // test this using cinnamon (which still uses status icon)

                            final SystemTrayMenuPopup jmenu = GtkSystemTray.this.jmenu;
                            if (jmenu.isVisible()) {
                                jmenu.setVisible(false);
                            }
                            else {
                                Dimension size = jmenu.getPreferredSize();

                                int x = (int) event.x_root;
                                int y = (int) event.y_root;

                                Point point = new Point(x, y);
                                Rectangle bounds = ScreenUtil.getScreenBoundsAt(point);

                                if (y < bounds.y) {
                                    y = bounds.y;
                                }
                                else if (y + size.height > bounds.y + bounds.height) {
                                    // our menu cannot have the top-edge snap to the mouse
                                    // so we make the bottom-edge snap to the mouse
                                    y -= size.height; // snap to edge of mouse
                                }

                                if (x < bounds.x) {
                                    x = bounds.x;
                                }
                                else if (x + size.width > bounds.x + bounds.width) {
                                    // our menu cannot have the left-edge snap to the mouse
                                    // so we make the right-edge snap to the mouse
                                    x -= size.width; // snap to edge of mouse
                                }

                                // SMALL problem, is that on linux, the popup is BEHIND the tray bar!
                                // to solve the problem, we anchor the popup above (or below) the tray bar
                                int distanceToEdgeOfTray = (int) event.y;
                                // System.err.println("  distance: " + distanceToEdgeOfTray);
                                // we are at the top of the screen
                                if (y < 100) {
                                    y += distanceToEdgeOfTray + 4;
                                }
                                else {
                                    y -= distanceToEdgeOfTray + 4;
                                }

                                jmenu.setInvoker(jmenu);
                                jmenu.setLocation(x, y);
                                jmenu.setVisible(true);
                                jmenu.requestFocus();
                            }
                        }
                    });
                }
            }
        };
        // all the clicks. This is because native menu popups are a pain to figure out, so we cheat and use some java bits to do the popup
        libgobject.g_signal_connect_data(trayIcon, "button_press_event", this.gtkCallback, null, null, 0);
        libgtk.gdk_threads_leave();

        this.active = true;
    }

    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    @Override
    public
    void removeTray() {
        libgtk.gdk_threads_enter();
        for (Pointer widget : this.widgets) {
            libgtk.gtk_widget_destroy(widget);
        }

        // this hides the indicator
        libgtk.gtk_status_icon_set_visible(this.trayIcon, false);
        libgobject.g_object_unref(this.trayIcon);

        this.active = false;

        // GC it
        this.trayIcon = null;
        this.widgets.clear();

        synchronized (this.menuEntries) {
            this.menuEntries.clear();
        }

        this.jmenu.setVisible(false);
        this.jmenu.setEnabled(false);

        this.jmenu = null;
        this.connectionStatusItem = null;

        GtkSupport.shutdownGTK();
        libgtk.gdk_threads_leave();

        super.removeTray();
    }

    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    @Override
    public
    void setStatus(final String infoString, String iconName) {
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                if (GtkSystemTray.this.connectionStatusItem == null) {
                    final JMenuItem connectionStatusItem = new JMenuItem(infoString);
                    GtkSystemTray.this.connectionStatusItem = connectionStatusItem;
                    connectionStatusItem.setEnabled(false);
                    GtkSystemTray.this.jmenu.add(connectionStatusItem);
                }
                else {
                    GtkSystemTray.this.connectionStatusItem.setText(infoString);
                }
            }
        });

        libgtk.gdk_threads_enter();
        libgtk.gtk_status_icon_set_from_file(GtkSystemTray.this.trayIcon, iconPath(iconName));
        libgtk.gdk_threads_leave();
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    @Override
    public
    void addMenuEntry(final String menuText, final SystemTrayMenuAction callback) {
        SwingUtil.invokeAndWait(new Runnable() {
            @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
            @Override
            public
            void run() {
                final Map<String, JMenuItem> menuEntries2 = GtkSystemTray.this.menuEntries;

                synchronized (menuEntries2) {
                    JMenuItem menuEntry = menuEntries2.get(menuText);

                    if (menuEntry == null) {
                        SystemTrayMenuPopup menu = GtkSystemTray.this.jmenu;

                        menuEntry = new JMenuItem(menuText);
                        menuEntry.addActionListener(new ActionListener() {
                            @Override
                            public
                            void actionPerformed(ActionEvent e) {
//                                SystemTrayMenuPopup source = (SystemTrayMenuPopup) ((JMenuItem)e.getSource()).getParent();

                                GtkSystemTray.this.callbackExecutor.execute(new Runnable() {
                                    @Override
                                    public
                                    void run() {
                                        callback.onClick(GtkSystemTray.this);
                                    }
                                });
                            }
                        });
                        menu.add(menuEntry);

                        menuEntries2.put(menuText, menuEntry);
                    }
                    else {
                        updateMenuEntry(menuText, menuText, callback);
                    }
                }
            }
        });
    }

    /**
     * Will update an already existing menu entry (or add a new one, if it doesn't exist)
     */
    @Override
    public
    void updateMenuEntry(final String origMenuText, final String newMenuText, final SystemTrayMenuAction newCallback) {
        SwingUtil.invokeAndWait(new Runnable() {
            @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
            @Override
            public
            void run() {
                final Map<String, JMenuItem> menuEntries2 = GtkSystemTray.this.menuEntries;

                synchronized (menuEntries2) {
                    JMenuItem menuEntry = menuEntries2.get(origMenuText);

                    if (menuEntry != null) {
                        ActionListener[] actionListeners = menuEntry.getActionListeners();
                        for (ActionListener l : actionListeners) {
                            menuEntry.removeActionListener(l);
                        }

                        menuEntry.addActionListener(new ActionListener() {
                            @Override
                            public
                            void actionPerformed(ActionEvent e) {
                                GtkSystemTray.this.callbackExecutor.execute(new Runnable() {
                                    @Override
                                    public
                                    void run() {
                                        newCallback.onClick(GtkSystemTray.this);
                                    }
                                });
                            }
                        });
                        menuEntry.setText(newMenuText);
                        menuEntry.revalidate();
                    }
                    else {
                        addMenuEntry(origMenuText, newCallback);
                    }
                }
            }
        });
    }
}
