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

import static dorkbox.systemTray.SystemTray.logger;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Function;
import com.sun.jna.Pointer;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.util.JavaFX;
import dorkbox.systemTray.util.Swt;

/**
 * bindings for gtk 2 or 3
 *
 * note: gtk2/3 loading is SENSITIVE, and which AppIndicator symbols are loaded depends on this being loaded first
 *
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
@SuppressWarnings({"Duplicates", "SameParameterValue", "DanglingJavadoc"})
public
class Gtk {
    // For funsies to look at, SyncThing did a LOT of work on compatibility in python (unfortunate for us, but interesting).
    // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

    // NOTE: AppIndicator uses this info to figure out WHAT VERSION OF appindicator to use: GTK2 -> appindicator1, GTK3 -> appindicator3
    public static volatile boolean isGtk2 = false;


    public static Function gtk_status_icon_position_menu = null;

    private static boolean alreadyRunningGTK = false;
    private static boolean isLoaded = false;

    // there is ONLY a single thread EVER setting this value!!
    private static volatile boolean isDispatch = false;
    public static boolean isKDE = false;

    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-x11-2.0.so.0 | grep gtk
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk

    /**
     * We can have GTK v3 or v2.
     *
     * Observations:
     * JavaFX uses GTK2, and we can't load GTK3 if GTK2 symbols are loaded
     * SWT uses GTK2 or GTK3. We do not work with the GTK3 version of SWT.
     */
    static {
        boolean shouldUseGtk2 = SystemTray.FORCE_GTK2;

        // in some cases, we ALWAYS want to try GTK2 first
        String gtk2LibName = "gtk-x11-2.0";
        String gtk3LibName = "libgtk-3.so.0";

        // we can force the system to use the swing indicator, which WORKS, but doesn't support transparency in the icon.
        if (SystemTray.FORCE_TRAY_TYPE == SystemTray.TYPE_SWING) {
            isLoaded = true;
        }

        if (!isLoaded && shouldUseGtk2) {
            try {
                JnaHelper.register(gtk2LibName, Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                alreadyRunningGTK = gtk_main_level() != 0;
                isLoaded = true;

                if (SystemTray.DEBUG) {
                    logger.debug("GTK: {}", gtk2LibName);
                }
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.error("Error loading library", e);
                }
            }
        }

        // now for the defaults...

        // start with version 3
        if (!isLoaded) {
            try {
                JnaHelper.register(gtk3LibName, Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk3LibName, "gtk_status_icon_position_menu");
                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                alreadyRunningGTK = gtk_main_level() != 0;
                isLoaded = true;

                if (SystemTray.DEBUG) {
                    logger.debug("GTK: {}", gtk3LibName);
                }
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.error("Error loading library", e);
                }
            }
        }

        // now version 2
        if (!isLoaded) {
            try {
                JnaHelper.register(gtk2LibName, Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                alreadyRunningGTK = gtk_main_level() != 0;
                isLoaded = true;

                if (SystemTray.DEBUG) {
                    logger.debug("GTK: {}", gtk2LibName);
                }
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.error("Error loading library", e);
                }
            }
        }

        // depending on how the system is initialized, SWT may, or may not, have the gtk_main loop running. It will EVENTUALLY run, so we
        // do not want to run our own GTK event loop.
        alreadyRunningGTK |= SystemTray.isSwtLoaded;

        if (SystemTray.DEBUG) {
            logger.debug("Is the system already running GTK? {}", alreadyRunningGTK);
        }

        if (!isLoaded) {
            throw new RuntimeException("We apologize for this, but we are unable to determine the GTK library is in use, " +
                                       "or even if it is in use... Please create an issue for this and include your OS type and configuration.");
        }
    }

    private static volatile boolean started = false;

    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final LinkedList<Object> gtkCallbacks = new LinkedList<Object>();

    @SuppressWarnings("FieldCanBeLocal")
    private static Thread gtkUpdateThread = null;

    public static final int FALSE = 0;
    public static final int TRUE = 1;


    public static
    void startGui() {
        // only permit one startup per JVM instance
        if (!started) {
            started = true;

            // startup the GTK GUI event loop. There can be multiple/nested loops.


            if (!alreadyRunningGTK ) {
                // If JavaFX/SWT is used, this is UNNECESSARY (we can detect if the GTK main_loop is running)
                if (SystemTray.DEBUG) {
                    logger.error("Running GTK Native Event Loop");
                }

                gtkUpdateThread = new Thread() {
                    @Override
                    public
                    void run() {
                        // prep for the event loop.
                        GThread.g_thread_init(null);

                        if (!gtk_init_check(0)) {
                            if (SystemTray.DEBUG) {
                                logger.error("Error starting GTK");
                            }
                            return;
                        }

                        gdk_threads_enter();

                        // blocks unit quit
                        gtk_main();

                        // clean up threads
                        gdk_threads_leave();
                    }
                };
                gtkUpdateThread.setName("GTK Native Event Loop");
                gtkUpdateThread.start();
            }
        }
    }

    /**
     * Waits for the GUI to finish loading
     */
    public static
    void waitForStartup() {
        final CountDownLatch blockUntilStarted = new CountDownLatch(1);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                blockUntilStarted.countDown();
            }
        });


        if (SystemTray.isJavaFxLoaded) {
            if (!JavaFX.isEventThread()) {
                try {
                    // we have to WAIT until all events are done processing, OTHERWISE we have initialization issues
                    while (true) {
                        Thread.sleep(100);

                        synchronized (gtkCallbacks) {
                            if (gtkCallbacks.isEmpty()) {
                                break;
                            }
                        }
                    }

                    blockUntilStarted.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else if (SystemTray.isSwtLoaded) {
            if (SystemTray.FORCE_TRAY_TYPE != SystemTray.TYPE_GTK_STATUSICON) {
                // GTK system tray has threading issues if we block here (because it is likely in the event thread)
                // AppIndicator version doesn't have this problem

                // we have to WAIT until all events are done processing, OTHERWISE we have initialization issues
                try {
                    while (true) {
                        Thread.sleep(100);

                        synchronized (gtkCallbacks) {
                            if (gtkCallbacks.isEmpty()) {
                                break;
                            }
                        }
                    }

                    blockUntilStarted.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {

            try {
                // we have to WAIT until all events are done processing, OTHERWISE we have initialization issues
                while (true) {
                    Thread.sleep(100);

                    synchronized (gtkCallbacks) {
                        if (gtkCallbacks.isEmpty()) {
                            break;
                        }
                    }
                }

                blockUntilStarted.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Best practices for GTK, is to call EVERYTHING for it on the GTK THREAD. This accomplishes that.
     */
    public static
    void dispatch(final Runnable runnable) {
        // FIXME: on mac, check -XstartOnFirstThread.. there are issues with javaFX (possibly SWT as well)

        if (alreadyRunningGTK) {
            // SWT/JavaFX
            if (SystemTray.isJavaFxLoaded) {
                if (JavaFX.isEventThread()) {
                    // Run directly on the JavaFX event thread
                    runnable.run();
                }
                else {
                    JavaFX.dispatch(runnable);
                }
            }
            else if (SystemTray.isSwtLoaded) {
                if (isDispatch) {
                    // Run directly on the dispatch thread
                    runnable.run();
                } else {
                    Swt.dispatch(new Runnable() {
                        @Override
                        public
                        void run() {
                            isDispatch = true;

                            runnable.run();

                            isDispatch = false;
                        }
                    });
                }
            }
        }
        else {
            // non-swt/javafx
            if (isDispatch) {
                // Run directly on the dispatch thread
                runnable.run();
            } else {
                final FuncCallback callback = new FuncCallback() {
                    @Override
                    public
                    int callback(final Pointer data) {
                        synchronized (gtkCallbacks) {
                            gtkCallbacks.removeFirst();// now that we've 'handled' it, we can remove it from our callback list
                        }

                        isDispatch = true;

                        runnable.run();

                        isDispatch = false;
                        return Gtk.FALSE; // don't want to call this again
                    }
                };

                synchronized (gtkCallbacks) {
                    gtkCallbacks.offer(callback); // prevent GC from collecting this object before it can be called
                }

                // the correct way to do it. Add with a slightly higher value
                gdk_threads_add_idle_full(100, callback, null, null);
            }
        }
    }

    public static
    void shutdownGui() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                // If JavaFX/SWT is used, this is UNNECESSARY (and will break SWT/JavaFX shutdown)
                if (!alreadyRunningGTK) {
                    gtk_main_quit();
                }

                started = false;
            }
        });
    }

    /**
     * This would NORMALLY have a 2nd argument that is a String[] -- however JNA direct-mapping DOES NOT support this. We are lucky
     * enough that we just pass 'null' as the second argument, therefore, we don't have to define that parameter here.
     */
    private static native boolean gtk_init_check(int argc);

    /**
     * Runs the main loop until gtk_main_quit() is called. You can nest calls to gtk_main(). In that case gtk_main_quit() will make the
     * innermost invocation of the main loop return.
     */
    private static native void gtk_main();

    /**
     * aks for the current nesting level of the main loop. Useful to determine (at startup) if GTK is already running
     */
    private static native int gtk_main_level();

    /**
     * Makes the innermost invocation of the main loop return when it regains control. ONLY CALL FROM THE GtkSupport class, UNLESS you know
     * what you're doing!
     */
    private static native void gtk_main_quit();


    public static native Pointer gtk_menu_new();

    // uses '_' to define which key is the mnemonic
    public static native Pointer gtk_image_menu_item_new_with_mnemonic(String label);

    public static native Pointer gtk_status_icon_new();

    public static native void gtk_status_icon_set_from_file(Pointer widget, String label);

    public static native void gtk_status_icon_set_visible(Pointer widget, boolean visible);

    // app indicators don't support this, and we cater to the lowest common denominator
//  public static native void gtk_status_icon_set_tooltip(Pointer widget, String tooltipText);

    public static native void gtk_status_icon_set_title(Pointer widget, String titleText);

    public static native void gtk_status_icon_set_name(Pointer widget, String name);

    public static native void gtk_menu_shell_append(Pointer menu_shell, Pointer child);

    public static native void gtk_widget_show_all(Pointer widget);

    public static native void gtk_widget_destroy(Pointer widget);


    public static native void gdk_threads_enter();
    public static native void gdk_threads_leave();
    public static native int gdk_threads_add_idle_full(int priority, FuncCallback function, Pointer data, Pointer notify);
}

