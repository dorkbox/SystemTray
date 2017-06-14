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
package dorkbox.systemTray.nativeUI;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Pointer;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.gnomeShell.Extension;
import dorkbox.systemTray.jna.linux.GEventCallback;
import dorkbox.systemTray.jna.linux.GdkEventButton;
import dorkbox.systemTray.jna.linux.Gobject;
import dorkbox.systemTray.jna.linux.Gtk;

/**
 * Class for handling all system tray interactions via GTK.
 * <p/>
 * This is the "old" way to do it, and does not work with some newer desktop environments.
 */
@SuppressWarnings("Duplicates")
public final
class _GtkStatusIconNativeTray extends Tray implements NativeUI {
    private volatile Pointer trayIcon;

    // http://code.metager.de/source/xref/gnome/Platform/gtk%2B/gtk/deprecated/gtkstatusicon.c
    // https://github.com/djdeath/glib/blob/master/gobject/gobject.c

    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    // see: https://github.com/java-native-access/jna/blob/master/www/CallbacksAndClosures.md
    private GEventCallback gtkCallback = null;

    // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    private volatile boolean isActive = false;

    // is the system tray visible or not.
    private volatile boolean visible = true;
    private volatile File imageFile;

    private final GtkMenu gtkMenu;

    // called on the EDT
    public
    _GtkStatusIconNativeTray(final SystemTray systemTray) {
        super();

        Gtk.startGui();

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        gtkMenu = new GtkMenu() {
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
                            gtkCallback = null;
                        }
                    });

                    super.remove();

                    // does not need to be called on the dispatch (it does that)
                    Gtk.shutdownGui();
                }
            }
        };

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                trayIcon = Gtk.gtk_status_icon_new();

                gtkCallback = new GEventCallback() {
                    @Override
                    public
                    void callback(Pointer notUsed, final GdkEventButton event) {
                        // show the swing menu on the EDT
                        // BUTTON_PRESS only (any mouse click)
                        if (event.type == 4) {
                            Gtk.gtk_menu_popup(gtkMenu._nativeMenu, null, null, Gtk.gtk_status_icon_position_menu,
                                               trayIcon, 0, event.time);
                        }
                    }
                };
                Gobject.g_signal_connect_object(trayIcon, "button_press_event", gtkCallback, null, 0);
            }
        });

        Gtk.waitForStartup();

        // we have to be able to set our title, otherwise the gnome-shell extension WILL NOT work
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                // in GNOME by default, the title/name of the tray icon is "java". We are the only java-based tray icon, so we just use that.
                // If you change "SystemTray" to something else, make sure to change it in extension.js as well

                // necessary for gnome icon detection/placement because we move tray icons around by title. This is hardcoded
                //  in extension.js, so don't change it
                Gtk.gtk_status_icon_set_title(trayIcon, Extension.DEFAULT_NAME);

                // can cause
                // Gdk-CRITICAL **: gdk_window_thaw_toplevel_updates: assertion 'window->update_and_descendants_freeze_count > 0' failed
                // Gdk-CRITICAL **: IA__gdk_window_thaw_toplevel_updates_libgtk_only: assertion 'private->update_and_descendants_freeze_count > 0' failed

                // ... so, bizzaro things going on here. These errors DO NOT happen if JavaFX or Gnome is dispatching the events.
                //     BUT   this is REQUIRED when running JavaFX or Gnome For unknown reasons, the title isn't pushed to GTK, so our
                //           gnome-shell extension cannot see our tray icon -- so naturally, it won't move it to the "top" area and
                //           we appear broken.
                if (SystemTray.isJavaFxLoaded || Tray.usingGnome) {
                    Gtk.gtk_status_icon_set_name(trayIcon, Extension.DEFAULT_NAME);
                }
            }
        });

        bind(gtkMenu, null, systemTray);

        // do we need to install the GNOME extension??
        Tray.installExtension();
    }

    @Override
    protected
    void setTooltip_(final String tooltipText) {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_status_icon_set_tooltip_text(trayIcon, tooltipText);
            }
        });
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
