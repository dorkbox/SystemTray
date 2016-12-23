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
package dorkbox.systemTray.jna.linux;

import static dorkbox.systemTray.SystemTray.logger;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Function;
import com.sun.jna.Pointer;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.jna.JnaHelper;
import dorkbox.systemTray.util.JavaFX;
import dorkbox.systemTray.util.Swt;

/**
 * bindings for gtk 2 or 3
 *
 * note: gtk2/3 loading is SENSITIVE, and which AppIndicator symbols are loaded depends on this being loaded first
 *       Additionally, gtk3 has deprecated some methods -- which, fortunately for us, means it will be another 25 years before they are
 *       removed; forcing us to have separate gtk2/3 bindings (we can then only have gtk3 bindings)
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

    // This is required because the EDT needs to have it's own value for this boolean, that is a different value than the main thread
    private static ThreadLocal<Boolean> isDispatch = new ThreadLocal<Boolean>() {
        @Override
        protected
        Boolean initialValue() {
            return false;
        }
    };

    private static final int TIMEOUT = 2;

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
        if (SystemTray.FORCE_TRAY_TYPE == SystemTray.TrayType.Swing) {
            isLoaded = true;
        }

        if (!isLoaded && shouldUseGtk2) {
            try {
                JnaHelper.register(gtk2LibName, Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
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
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
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
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
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

    @SuppressWarnings("WeakerAccess")
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
                    logger.debug("Running GTK Native Event Loop");
                }

                gtkUpdateThread = new Thread() {
                    @Override
                    public
                    void run() {
                        // prep for the event loop.
                        // GThread.g_thread_init(null);  would be needed for g_idle_add()

                        if (!gtk_init_check(0)) {
                            if (SystemTray.DEBUG) {
                                logger.error("Error starting GTK");
                            }
                            return;
                        }

                        // gdk_threads_enter();  would be needed for g_idle_add()

                        // blocks unit quit
                        gtk_main();

                        // clean up threads
                        // gdk_threads_leave();  would be needed for g_idle_add()
                    }
                };
                gtkUpdateThread.setDaemon(false); // explicitly NOT daemon so that this will hold the JVM open as necessary
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

                    if (!blockUntilStarted.await(10, TimeUnit.SECONDS)) {
                        if (SystemTray.DEBUG) {
                            SystemTray.logger.error("Something is very wrong. The waitForStartup took longer than expected.",
                                                    new Exception(""));
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else if (SystemTray.isSwtLoaded) {
            if (!Swt.isEventThread()) {
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

                    if (!blockUntilStarted.await(10, TimeUnit.SECONDS)) {
                        if (SystemTray.DEBUG) {
                            SystemTray.logger.error("Something is very wrong. The waitForStartup took longer than expected.",
                                                    new Exception(""));
                        }
                    }
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

                if (!blockUntilStarted.await(10, TimeUnit.SECONDS)) {
                    if (SystemTray.DEBUG) {
                        SystemTray.logger.error("Something is very wrong. The waitForStartup took longer than expected.",
                                                new Exception(""));
                    }
                }
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
        if (alreadyRunningGTK) {
            if (SystemTray.isJavaFxLoaded) {
                // JavaFX only
                if (JavaFX.isEventThread()) {
                    // Run directly on the JavaFX event thread
                    runnable.run();
                }
                else {
                    JavaFX.dispatch(runnable);
                }

                return;
            }

            if (SystemTray.isSwtLoaded) {
                if (Swt.isEventThread()) {
                    // Run directly on the SWT event thread. If it's not on the dispatch thread, we can use raw GTK to put it there
                    runnable.run();

                    return;
                }
            }
        }

        // not javafx
        // gtk/swt are **mostly** the same in how events are dispatched, so we can use "raw" gtk methods for SWT
        if (isDispatch.get()) {
            // Run directly on the dispatch thread
            runnable.run();
        } else {
            final FuncCallback callback = new FuncCallback() {
                @Override
                public
                int callback(final Pointer data) {
                    synchronized (gtkCallbacks) {
                        gtkCallbacks.removeFirst(); // now that we've 'handled' it, we can remove it from our callback list
                    }

                    isDispatch.set(true);

                    try {
                        runnable.run();
                    } finally {
                        isDispatch.set(false);
                    }

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

    public static
    void dispatchAndWait(final Runnable runnable) {
        if (isDispatch.get()) {
            // Run directly on the dispatch thread (should not "redispatch" this again)
            runnable.run();
        } else {
            final CountDownLatch countDownLatch = new CountDownLatch(1);

            Gtk.dispatch(new Runnable() {
                @Override
                public
                void run() {
                    try {
                        runnable.run();
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });

            // this is slightly different than how swing does it. We have a timeout here so that we can make sure that updates on the GUI
            // thread occur in REASONABLE time-frames, and alert the user if not.
            try {
                if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                    if (SystemTray.DEBUG) {
                        SystemTray.logger.error("Something is very wrong. The Event Dispatch Queue took longer than " + TIMEOUT + " seconds " +
                                                "to complete.",
                                                new Exception(""));
                    } else {
                        throw new RuntimeException("Something is very wrong. The Event Dispatch Queue took longer than " + TIMEOUT + " seconds " +
                                                   "to complete.");
                    }
                }
            } catch (InterruptedException e) {
                SystemTray.logger.error("Error waiting for dispatch to complete.", new Exception(""));
            }
        }
    }

    public static
    void shutdownGui() {
        dispatchAndWait(new Runnable() {
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
     * required to properly setup the dispatch flag when using native menus
     *
     * @param callback will never be null.
     */
    public static
    void proxyClick(final Entry menuEntry, final ActionListener callback) {
        Gtk.isDispatch.set(true);

        try {
            if (menuEntry != null) {
                callback.actionPerformed(new ActionEvent(menuEntry, ActionEvent.ACTION_PERFORMED, ""));
            } else {
                // checkbox entries will not pass the menuEntry in, because they redispatch the click event so that the checkbox state is
                // toggled
                callback.actionPerformed(null);
            }
        } finally {
            Gtk.isDispatch.set(false);
        }
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
    public static native Pointer gtk_menu_item_set_submenu(Pointer menuEntry, Pointer menu);



    public static native Pointer gtk_separator_menu_item_new();

    // to create a menu entry WITH an icon.
    public static native Pointer gtk_image_new_from_file(String iconPath);

    // uses '_' to define which key is the mnemonic
    public static native Pointer gtk_image_menu_item_new_with_mnemonic(String label);
    public static native Pointer gtk_check_menu_item_new_with_mnemonic (String label);

    public static native void gtk_check_menu_item_set_active (Pointer check_menu_item, boolean isChecked);

    public static native void gtk_image_menu_item_set_image(Pointer image_menu_item, Pointer image);

    public static native void gtk_image_menu_item_set_always_show_image(Pointer menu_item, boolean forceShow);

    public static native Pointer gtk_status_icon_new();

    public static native void gtk_status_icon_set_from_file(Pointer widget, String label);

    public static native void gtk_status_icon_set_visible(Pointer widget, boolean visible);

    // app indicators don't support this, and we cater to the lowest common denominator
//  public static native void gtk_status_icon_set_tooltip(Pointer widget, String tooltipText);

    public static native void gtk_status_icon_set_title(Pointer widget, String titleText);

    public static native void gtk_status_icon_set_name(Pointer widget, String name);

    public static native void gtk_menu_popup(Pointer menu, Pointer widget, Pointer bla, Function func, Pointer data, int button, int time);

    public static native void gtk_menu_item_set_label(Pointer menu_item, String label);

    public static native void gtk_menu_shell_append(Pointer menu_shell, Pointer child);

    // Typically this results in the menu shell being erased from the screen
    public static native void gtk_menu_shell_deactivate(Pointer menuShell);

    public static native void gtk_widget_set_sensitive(Pointer widget, boolean sensitive);

    public static native void gtk_widget_show_all(Pointer widget);

    // will automatically get destroyed if no other references to it
    public static native void gtk_container_remove(Pointer parentWidget, Pointer widget);

    // from: https://developer.gnome.org/gtk3/stable/GtkWidget.html#gtk-widget-destroy
    // You should typically call this function on top level widgets, and rarely on child widgets.
    public static native void gtk_widget_destroy(Pointer widget);


    public static native int gdk_threads_add_idle_full(int priority, FuncCallback function, Pointer data, Pointer notify);
}

