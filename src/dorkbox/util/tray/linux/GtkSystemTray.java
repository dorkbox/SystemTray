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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import com.sun.jna.Pointer;

import dorkbox.util.jna.linux.Gobject;
import dorkbox.util.jna.linux.Gtk;
import dorkbox.util.jna.linux.Gtk.GdkEventButton;
import dorkbox.util.jna.linux.GtkSupport;
import dorkbox.util.tray.SystemTray;
import dorkbox.util.tray.SystemTrayMenuAction;
import dorkbox.util.tray.SystemTrayMenuPopup;


/**
 * Class for handling all system tray interactions via GTK.
 *
 * This is the "old" way to do it, and does not work with some desktop environments.
 */
public class GtkSystemTray extends SystemTray {

    private static final Gobject libgobject = Gobject.INSTANCE;
    private static final Gtk libgtk = Gtk.INSTANCE;

    private final CountDownLatch blockUntilStarted = new CountDownLatch(1);
    private final Map<String, JMenuItem> menuEntries = new HashMap<String, JMenuItem>(2);

    private volatile SystemTrayMenuPopup jmenu;
    private volatile JMenuItem connectionStatusItem;

    private volatile Pointer trayIcon;

    // need to hang on to these to prevent gc
    private final List<Pointer> widgets = new ArrayList<Pointer>(4);

    public GtkSystemTray() {
    }

    @Override
    public void createTray(String iconName) {
        this.trayIcon = libgtk.gtk_status_icon_new();
        libgtk.gtk_status_icon_set_from_file(this.trayIcon, iconPath(iconName));
        libgtk.gtk_status_icon_set_tooltip(this.trayIcon, this.appName);
        libgtk.gtk_status_icon_set_visible(this.trayIcon, true);

        Gobject.GEventCallback gtkCallback = new Gobject.GEventCallback() {
            @Override
            public void callback(Pointer instance, final GdkEventButton event) {
                // BUTTON_PRESS only
                if (event.type == 4) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (GtkSystemTray.this.jmenu.isVisible()) {
                                GtkSystemTray.this.jmenu.setVisible(false);
                            } else {
                                int iconX = (int) (event.x_root - event.x);
                                int iconY = (int) (event.y_root - event.y);
                                // System.err.println("x: " + iconX + "  y: " + iconY);
                                // System.err.println("x1: " + event.x_root + "  y1: " + event.y_root); // relative to SCREEN
                                // System.err.println("x2: " + event.x + "  y2: " + event.y); // relative to WINDOW

                                Dimension size = GtkSystemTray.this.jmenu.getPreferredSize();

                                // do we open at top-right or top-left?
                                // we ASSUME monitor size is greater than 640x480 AND that our tray icon is IN THE CORNER SOMEWHERE

                                // always put the menu in the middle
                                iconX -= size.width / 2;

                                // y = 2 -> top
                                // y = 1068 -> bottom
                                if (iconY > 240) {
                                    iconY -= size.height;
                                } else {
                                    // have to account for the icon
                                    iconY += ICON_SIZE;
                                }

                                GtkSystemTray.this.jmenu.setInvoker(GtkSystemTray.this.jmenu);
                                GtkSystemTray.this.jmenu.setLocation(iconX, iconY);
                                GtkSystemTray.this.jmenu.setVisible(true);
                            }
                        }
                    });
                }
            }
        };
        // all the clicks. This is because native menu popups are a pain to figure out, so we cheat and use some java bits to do the popup
        libgobject.g_signal_connect_data(this.trayIcon, "button_press_event", gtkCallback, null, null, 0);

        if (!GtkSupport.usesSwtMainLoop) {
            Thread gtkUpdateThread = new Thread() {
                @Override
                public void run() {
                    // notify our main thread to continue
                    GtkSystemTray.this.blockUntilStarted.countDown();

                    try {
                        libgtk.gtk_main();
                    } catch (Throwable t) {
                        logger.warn("Unable to run main loop", t);
                    }
                }
            };
            gtkUpdateThread.setName("GTK event loop");
            gtkUpdateThread.setDaemon(true);
            gtkUpdateThread.start();
        }

        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    GtkSystemTray.this.jmenu = new SystemTrayMenuPopup();
                }
            });
        } catch (InvocationTargetException e) {
            logger.error("Error creating tray menu", e);
        } catch (InterruptedException e) {
            logger.error("Error creating tray menu", e);
        }

        // we CANNOT continue until the GTK thread has started! (ignored if SWT is used)
        try {
            this.blockUntilStarted.await();
            this.active = true;
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void removeTray() {
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

        super.removeTray();
    }

    @Override
    public void setStatus(final String infoString, String iconName) {
        Runnable doRun = new Runnable() {
            @Override
            public void run() {
                if (GtkSystemTray.this.connectionStatusItem == null) {
                    GtkSystemTray.this.connectionStatusItem = new JMenuItem(infoString);
                    GtkSystemTray.this.connectionStatusItem.setEnabled(false);
                    GtkSystemTray.this.jmenu.add(GtkSystemTray.this.connectionStatusItem);
                } else {
                    GtkSystemTray.this.connectionStatusItem.setText(infoString);
                }
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            doRun.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(doRun);
            } catch (InvocationTargetException e) {
                logger.error("Error updating tray menu", e);
            } catch (InterruptedException e) {
                logger.error("Error updating tray menu", e);
            }
        }

        libgtk.gtk_status_icon_set_from_file(GtkSystemTray.this.trayIcon, iconPath(iconName));
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    @Override
    public void addMenuEntry(final String menuText, final SystemTrayMenuAction callback) {
        Runnable doRun = new Runnable() {
            @Override
            public void run() {
                Map<String, JMenuItem> menuEntries2 = GtkSystemTray.this.menuEntries;

                synchronized (menuEntries2) {
                    JMenuItem menuEntry = menuEntries2.get(menuText);

                    if (menuEntry == null) {
                        SystemTrayMenuPopup menu = GtkSystemTray.this.jmenu;

                        menuEntry = new JMenuItem(menuText);
                        menuEntry.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                GtkSystemTray.this.callbackExecutor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onClick(GtkSystemTray.this);
                                    }
                                });
                            }
                        });
                        menu.add(menuEntry);

                        menuEntries2.put(menuText, menuEntry);
                    } else {
                        updateMenuEntry(menuText, menuText, callback);
                    }
                }
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            doRun.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(doRun);
            } catch (InvocationTargetException e) {
                logger.error("Error updating tray menu", e);
            } catch (InterruptedException e) {
                logger.error("Error updating tray menu", e);
            }
        }
    }

    /**
     * Will update an already existing menu entry (or add a new one, if it doesn't exist)
     */
    @Override
    public void updateMenuEntry(final String origMenuText, final String newMenuText, final SystemTrayMenuAction newCallback) {
        Runnable doRun = new Runnable() {
            @Override
            public void run() {
                Map<String, JMenuItem> menuEntries2 = GtkSystemTray.this.menuEntries;

                synchronized (menuEntries2) {
                    JMenuItem menuEntry = menuEntries2.get(origMenuText);

                    if (menuEntry != null) {
                        ActionListener[] actionListeners = menuEntry.getActionListeners();
                        for (ActionListener l : actionListeners) {
                            menuEntry.removeActionListener(l);
                        }

                        menuEntry.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                GtkSystemTray.this.callbackExecutor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        newCallback.onClick(GtkSystemTray.this);
                                    }
                                });
                            }
                        });
                        menuEntry.setText(newMenuText);
                        menuEntry.revalidate();
                    } else {
                        addMenuEntry(origMenuText, newCallback);
                    }
                }
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            doRun.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(doRun);
            } catch (InvocationTargetException e) {
                logger.error("Error updating tray menu", e);
            } catch (InterruptedException e) {
                logger.error("Error updating tray menu", e);
            }
        }
    }
}
