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

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.jna.linux.GEventCallback;
import dorkbox.systemTray.jna.linux.GdkEventButton;
import dorkbox.systemTray.jna.linux.Gobject;
import dorkbox.systemTray.jna.linux.Gtk;

/**
 * Class for handling all system tray interactions via GTK.
 * <p/>
 * This is the "old" way to do it, and does not work with some desktop environments. This is a hybrid class, because we want to show the
 * swing menu popup INSTEAD of GTK menu popups. The "golden standard" is our swing menu popup, since we have 100% control over it.
 */
@SuppressWarnings("Duplicates")
public
class _GtkStatusIconTray extends SwingMenu {
    private volatile Pointer trayIcon;

    // http://code.metager.de/source/xref/gnome/Platform/gtk%2B/gtk/deprecated/gtkstatusicon.c
    // https://github.com/djdeath/glib/blob/master/gobject/gobject.c

    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    private final List<Object> gtkCallbacks = new ArrayList<Object>();

    // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    private volatile boolean isActive = false;

    // is the system tray visible or not.
    private volatile boolean visible = true;

    // called on the EDT
    public
    _GtkStatusIconTray(final SystemTray systemTray) {
        super(systemTray, null, new TrayPopup());

        JPopupMenu popupMenu = (JPopupMenu) _native;
        popupMenu.pack();
        popupMenu.setFocusable(true);

        final Runnable popupRunnable = new Runnable() {
            @Override
            public
            void run() {
                Point point = MouseInfo.getPointerInfo()
                                       .getLocation();

                TrayPopup popupMenu = (TrayPopup) _native;
                popupMenu.doShow(point, 0);
            }
        };

        // appindicators DO NOT support anything other than PLAIN gtk-menus (which we hack to support swing menus)
        //   they ALSO do not support tooltips, so we cater to the lowest common denominator
        // trayIcon.setToolTip("app name");

        Gtk.startGui();

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                final Pointer trayIcon_ = Gtk.gtk_status_icon_new();
                trayIcon = trayIcon_;

                final GEventCallback gtkCallback = new GEventCallback() {
                    @Override
                    public
                    void callback(Pointer notUsed, final GdkEventButton event) {
                        // show the swing menu on the EDT
                        // BUTTON_PRESS only (any mouse click)
                        if (event.type == 4) {
                            // show the swing menu on the EDT
                            dispatch(popupRunnable);
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

                // ... so, bizzaro things going on here. These errors DO NOT happen if JavaFX is dispatching the events.
                //     BUT   this is REQUIRED when running JavaFX. For unknown reasons, the title isn't pushed to GTK, so our
                //           gnome-shell extension cannot see our tray icon -- so naturally, it won't move it to the "top" area and
                //           we appear broken.
                if (SystemTray.isJavaFxLoaded) {
                    Gtk.gtk_status_icon_set_name(trayIcon, "SystemTray");
                }
            }
        });
    }


    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    public
    void shutdown() {
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

            Gtk.shutdownGui();

            // uses EDT
            removeAll();
            remove();  // remove ourselves from our parent
        }
    }

    public
    void setImage_(final File iconFile) {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_status_icon_set_from_file(trayIcon, iconFile.getAbsolutePath());

                if (!isActive) {
                    isActive = true;
                    Gtk.gtk_status_icon_set_visible(trayIcon, true);
                }
            }
        });

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                ((TrayPopup) _native).setTitleBarImage(iconFile);
            }
        });
    }

    public
    void setEnabled(final boolean setEnabled) {
        visible = !setEnabled;

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (visible && !setEnabled) {
                    Gtk.gtk_status_icon_set_visible(trayIcon, setEnabled);
                } else if (!visible && setEnabled) {
                    Gtk.gtk_status_icon_set_visible(trayIcon, setEnabled);
                }
            }
        });
    }
}
