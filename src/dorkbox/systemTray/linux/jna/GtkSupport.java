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
import dorkbox.systemTray.SystemTray;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;

public
class GtkSupport {
    private static volatile boolean started = false;
    private static final ArrayBlockingQueue<Runnable> dispatchEvents = new ArrayBlockingQueue<Runnable>(256);
    private static volatile Thread gtkDispatchThread;

    /**
     * must call get() before accessing this! Only "Gtk" interface should access this!
     */
    static volatile Function gtk_status_icon_position_menu = null;
    public static volatile boolean isGtk2 = false;
    private static volatile boolean alreadyRunningGTK = false;

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
        alreadyRunningGTK = SystemTray.COMPATIBILITY_MODE;


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
                    alreadyRunningGTK |= library.gtk_main_level() != 0;
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
                        alreadyRunningGTK |= library.gtk_main_level() != 0;
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
                        alreadyRunningGTK |= library.gtk_main_level() != 0;
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
                alreadyRunningGTK |= library.gtk_main_level() != 0;
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
                alreadyRunningGTK |= library.gtk_main_level() != 0;
                return library;
            }
        } catch (Throwable ignored) {
        }

        throw new RuntimeException("We apologize for this, but we are unable to determine the GTK library is in use, if " +
                                   "or even if it is in use... Please create an issue for this and include your OS type and configuration.");
    }

    public static
    void startGui() {
        // only permit one startup per JVM instance
        if (!started) {
            started = true;

            // GTK specifies that we ONLY run from a single thread. This guarantees that.
            gtkDispatchThread = new Thread() {
                @Override
                public
                void run() {
                    final Gtk gtk = Gtk.INSTANCE;
                    while (started) {
                        try {
                            final Runnable take = dispatchEvents.take();

                            gtk.gdk_threads_enter();
                            take.run();
                            gtk.gdk_threads_leave();

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            gtkDispatchThread.setName("GTK Event Loop");
            gtkDispatchThread.start();


            // startup the GTK GUI event loop. There can be multiple/nested loops.

            // If JavaFX/SWT is used, this is UNNECESSARY
            if (!alreadyRunningGTK) {
                // only necessary if we are the only GTK instance running...
                final CountDownLatch blockUntilStarted = new CountDownLatch(1);
                Thread gtkUpdateThread = new Thread() {
                    @Override
                    public
                    void run() {
                        Gtk gtk = Gtk.INSTANCE;

                        // prep for the event loop.
                        gtk.gdk_threads_init();
                        gtk.gdk_threads_enter();

                        GThread.INSTANCE.g_thread_init(null);
                        gtk.gtk_init_check(0, null);

                        // notify our main thread to continue
                        blockUntilStarted.countDown();

                        // blocks unit quit
                        gtk.gtk_main();

                        gtk.gdk_threads_leave();
                    }
                };
                gtkUpdateThread.setName("GTK Event Loop (Native)");
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
     * Best practices for GTK, is to call EVERYTHING for it on a SINGLE THREAD. This accomplishes that.
     */
    public static
    void dispatch(Runnable runnable) {
        try {
            dispatchEvents.put(runnable);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static
    void shutdownGui() {
        // If JavaFX/SWT is used, this is UNNECESSARY (an will break SWT/JavaFX shutdown)
        if (!alreadyRunningGTK) {
            Gtk.INSTANCE.gtk_main_quit();
        }

        started = false;

        // put it in a NEW dispatch event (so that we cleanup AFTER this one is finished)
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                new Thread(new Runnable() {
                    @Override
                    public
                    void run() {
                        // this should happen in a new thread
                        gtkDispatchThread.interrupt();
                    }
                }).run();
            }
        });
    }
}
