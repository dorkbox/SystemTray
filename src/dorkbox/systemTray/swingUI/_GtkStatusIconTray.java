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
package dorkbox.systemTray.swingUI;

import java.awt.MouseInfo;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JPopupMenu;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.jna.linux.GEventCallback;
import dorkbox.systemTray.jna.linux.GdkEventButton;
import dorkbox.systemTray.jna.linux.Gobject;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.util.SwingUtil;

/**
 * Class for handling all system tray interactions via GTK.
 * <p/>
 * This is the "old" way to do it, and does not work with some desktop environments. This is a hybrid class, because we want to show the
 * swing menu popup INSTEAD of GTK menu popups. The "golden standard" is our swing menu popup, since we have 100% control over it.
 *
 * http://code.metager.de/source/xref/gnome/Platform/gtk%2B/gtk/deprecated/gtkstatusicon.c
 * https://github.com/djdeath/glib/blob/master/gobject/gobject.c
 */
@SuppressWarnings("Duplicates")
public
class _GtkStatusIconTray extends Tray implements SwingUI {
    private volatile Pointer trayIcon;

    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    private final List<Object> gtkCallbacks = new ArrayList<Object>();

    private AtomicBoolean shuttingDown = new AtomicBoolean();

    private volatile boolean isActive = false;

    // is the system tray visible or not.
    private volatile boolean visible = true;
    private volatile File imageFile;
    private volatile Runnable popupRunnable;

    // called on the EDT
    public
    _GtkStatusIconTray(final SystemTray systemTray) {
        super();

        Gtk.startGui();

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                trayIcon = Gtk.gtk_status_icon_new();

                final GEventCallback gtkCallback = new GEventCallback() {
                    @Override
                    public
                    void callback(Pointer notUsed, final GdkEventButton event) {
                        // show the swing menu on the EDT
                        // BUTTON_PRESS only (any mouse click)
                        if (event.type == 4) {
                            // show the swing menu on the EDT
                            SwingUtil.invokeLater(popupRunnable);
                        }
                    }
                };
                final NativeLong button_press_event = Gobject.g_signal_connect_object(trayIcon, "button_press_event",
                                                                                      gtkCallback, null, 0);

                // have to do this to prevent GC on these objects
                gtkCallbacks.add(gtkCallback);
                gtkCallbacks.add(button_press_event);
            }
        });

        Gtk.waitForStartup();

        // we have to be able to set our title, otherwise the gnome-shell extension WILL NOT work
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                // by default, the title/name of the tray icon is "java". We are the only java-based tray icon, so we just use that.
                // If you change "SystemTray" to something else, make sure to change it in extension.js as well

                // necessary for gnome icon detection/placement because we move tray icons around by title. This is hardcoded
                //  in extension.js, so don't change it
                Gtk.gtk_status_icon_set_title(trayIcon, "SystemTray");

                // can cause
                // Gdk-CRITICAL **: gdk_window_thaw_toplevel_updates: assertion 'window->update_and_descendants_freeze_count > 0' failed
                // Gdk-CRITICAL **: IA__gdk_window_thaw_toplevel_updates_libgtk_only: assertion 'private->update_and_descendants_freeze_count > 0' failed

                // ... so, bizzaro things going on here. These errors DO NOT happen if JavaFX or Gnome is dispatching the events.
                //     BUT   this is REQUIRED when running JavaFX or Gnome For unknown reasons, the title isn't pushed to GTK, so our
                //           gnome-shell extension cannot see our tray icon -- so naturally, it won't move it to the "top" area and
                //           we appear broken.
                if (System.getProperty("SystemTray_SET_NAME", "false").equals("true")) {
                    Gtk.gtk_status_icon_set_name(trayIcon, "SystemTray");
                }
            }
        });

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                final SwingMenu swingMenu = new SwingMenu(null) {
                    @Override
                    public
                    void setEnabled(final MenuItem menuItem) {
                        Gtk.dispatch(new Runnable() {
                            @Override
                            public
                            void run() {
                                boolean enabled = menuItem.getEnabled();

                                if (visible && !enabled) {
                                    Gtk.gtk_status_icon_set_visible(trayIcon, enabled);
                                    visible = false;
                                }
                                else if (!visible && enabled) {
                                    Gtk.gtk_status_icon_set_visible(trayIcon, enabled);
                                    visible = true;
                                }
                            }
                        });
                    }

                    @Override
                    public
                    void setImage(final MenuItem menuItem) {
                        imageFile = menuItem.getImage();
                        if (imageFile == null) {
                            return;
                        }

                        Gtk.dispatch(new Runnable() {
                            @Override
                            public
                            void run() {
                                Gtk.gtk_status_icon_set_from_file(trayIcon, imageFile.getAbsolutePath());

                                if (!isActive) {
                                    isActive = true;
                                    Gtk.gtk_status_icon_set_visible(trayIcon, true);
                                }
                            }
                        });

                        // needs to be on EDT
                        SwingUtil.invokeLater(new Runnable() {
                            @Override
                            public
                            void run() {
                                ((TrayPopup) _native).setTitleBarImage(imageFile);
                            }
                        });
                    }

                    @Override
                    public
                    void setText(final MenuItem menuItem) {
                        // no op
                    }

                    @Override
                    public
                    void setShortcut(final MenuItem menuItem) {
                        // no op
                    }

                    @Override
                    public
                    void remove() {
                        // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
                        if (!shuttingDown.getAndSet(true)) {
                            Gtk.dispatch(new Runnable() {
                                @Override
                                public
                                void run() {
                                    // this hides the indicator
                                    Gtk.gtk_status_icon_set_visible(trayIcon, false);
                                    Gobject.g_object_unref(trayIcon);

                                    // mark for GC
                                    trayIcon = null;
                                    gtkCallbacks.clear();
                                }
                            });

                            // does not need to be called on the dispatch (it does that)
                            Gtk.shutdownGui();

                            super.remove();
                        }
                    }
                };


                JPopupMenu popupMenu = (JPopupMenu) swingMenu._native;
                popupMenu.pack();
                popupMenu.setFocusable(true);

                popupRunnable = new Runnable() {
                    @Override
                    public
                    void run() {
                        Point point = MouseInfo.getPointerInfo()
                                               .getLocation();

                        TrayPopup popupMenu = (TrayPopup) swingMenu._native;
                        popupMenu.doShow(point, 0);
                    }
                };


                bind(swingMenu, null, systemTray);
            }
        });
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
