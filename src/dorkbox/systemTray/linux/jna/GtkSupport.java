/*
 * Copyright 2015 dorkbox, llc
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
package dorkbox.systemTray.linux.jna;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import dorkbox.systemTray.SystemTray;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;

public
class GtkSupport {
    // For funsies, SyncThing did a LOT of work on compatibility (unfortunate for us) in python.
    // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

    private static volatile boolean started = false;

    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    private static final LinkedList<Object> gtkCallbacks = new LinkedList<Object>();

    /**
     * must call get() before accessing this! Only "Gtk" interface should access this!
     */
    static volatile Function gtk_status_icon_position_menu = null;

    public static volatile boolean isGtk2 = false;

    private static volatile boolean alreadyRunningGTK = false;
    private static Thread gtkUpdateThread = null;

    /**
     * Helper for GTK, because we could have v3 or v2.
     *
     * Observations: JavaFX uses GTK2, and we can't load GTK3 if GTK2 symbols are loaded
     * SWT uses GTK2 or GTK3. We do not work with the GTK3 version of SWT.
     */
    @SuppressWarnings("Duplicates")
    public static
    Gtk get() {
        Gtk library;

        boolean shouldUseGtk2 = SystemTray.FORCE_GTK2 || SystemTray.COMPATIBILITY_MODE;

        // for more info on JavaFX: https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
        // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.

        // in some cases, we ALWAYS want to try GTK2 first
        if (shouldUseGtk2) {
            try {
                gtk_status_icon_position_menu = Function.getFunction("gtk-x11-2.0", "gtk_status_icon_position_menu");
                library = (Gtk) Native.loadLibrary("gtk-x11-2.0", Gtk.class);
                if (library != null) {
                    isGtk2 = true;

                    // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                    // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                    alreadyRunningGTK = library.gtk_main_level() != 0;
                    return library;
                }
            } catch (Throwable ignored) {
            }
        }

        if (AppIndicatorQuery.isLoaded) {
            if (AppIndicatorQuery.isVersion3) {
                // appindicator3 requires GTK3
                try {
                    gtk_status_icon_position_menu = Function.getFunction("libgtk-3.so.0", "gtk_status_icon_position_menu");
                    library = (Gtk) Native.loadLibrary("libgtk-3.so.0", Gtk.class);
                    if (library != null) {
                        // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                        // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                        alreadyRunningGTK = library.gtk_main_level() != 0;
                        return library;
                    }
                } catch (Throwable ignored) {
                }
            } else {
                // appindicator1 requires GTK2
                try {
                    gtk_status_icon_position_menu = Function.getFunction("gtk-x11-2.0", "gtk_status_icon_position_menu");
                    library = (Gtk) Native.loadLibrary("gtk-x11-2.0", Gtk.class);
                    if (library != null) {
                        isGtk2 = true;

                        // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                        // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                        alreadyRunningGTK = library.gtk_main_level() != 0;
                        return library;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // now for the defaults...

        // start with version 3
        try {
            gtk_status_icon_position_menu = Function.getFunction("libgtk-3.so.0", "gtk_status_icon_position_menu");
            library = (Gtk) Native.loadLibrary("libgtk-3.so.0", Gtk.class);
            if (library != null) {
                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                alreadyRunningGTK = library.gtk_main_level() != 0;
                return library;
            }
        } catch (Throwable ignored) {
        }

        // now version 2
        try {
            gtk_status_icon_position_menu = Function.getFunction("gtk-x11-2.0", "gtk_status_icon_position_menu");
            library = (Gtk) Native.loadLibrary("gtk-x11-2.0", Gtk.class);
            if (library != null) {
                isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                alreadyRunningGTK = library.gtk_main_level() != 0;
                return library;
            }
        } catch (Throwable ignored) {
        }

        throw new RuntimeException("We apologize for this, but we are unable to determine the GTK library is in use, " +
                                   "or even if it is in use... Please create an issue for this and include your OS type and configuration.");
    }

    public static
    void startGui() {
        // only permit one startup per JVM instance
        if (!started) {
            started = true;

            // startup the GTK GUI event loop. There can be multiple/nested loops.

            // If JavaFX/SWT is used, this is UNNECESSARY
            if (!alreadyRunningGTK) {
                // only necessary if we are the only GTK instance running...
                final CountDownLatch blockUntilStarted = new CountDownLatch(1);

                gtkUpdateThread = new Thread() {
                    @Override
                    public
                    void run() {
                        Gtk gtk = Gtk.INSTANCE;

                        // prep for the event loop.
                        gtk.gdk_threads_init();
                        gtk.gdk_threads_enter();

                        GThread.INSTANCE.g_thread_init(null);

                        if (!SystemTray.COMPATIBILITY_MODE) {
                            gtk.gtk_init_check(0, null);
                        }

                        // notify our main thread to continue
                        blockUntilStarted.countDown();

                        if (!SystemTray.COMPATIBILITY_MODE) {
                            // blocks unit quit
                            gtk.gtk_main();
                        }

                        gtk.gdk_threads_leave();
                    }
                };
                gtkUpdateThread.setName("GTK Native Event Loop");
                gtkUpdateThread.start();

                try {
                    // we CANNOT continue until the GTK thread has started!
                    blockUntilStarted.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Best practices for GTK, is to call EVERYTHING for it on the GTK THREAD. This accomplishes that.
     */
    public static
    void dispatch(final Runnable runnable) {
        if (gtkUpdateThread == Thread.currentThread()) {
            // if we are ALREADY inside the native event
            runnable.run();
        } else {
            final Gobject.FuncCallback callback = new Gobject.FuncCallback() {
                @Override
                public
                int callback(final Pointer data) {
                    synchronized (gtkCallbacks) {
                        gtkCallbacks.removeFirst(); // now that we've 'handled' it, we can remove it from our callback list
                    }
                    runnable.run();

                    return Gtk.FALSE; // don't want to call this again
                }
            };

            synchronized (gtkCallbacks) {
                gtkCallbacks.offer(callback); // prevent GC from collecting this object before it can be called
            }
            Gtk.INSTANCE.gdk_threads_add_idle(callback, null);
        }
    }

    public static
    void shutdownGui() {
        // If JavaFX/SWT is used, this is UNNECESSARY (and will break SWT/JavaFX shutdown)
        if (!(alreadyRunningGTK || SystemTray.COMPATIBILITY_MODE)) {
            Gtk.INSTANCE.gtk_main_quit();
        }

        started = false;
    }
}
