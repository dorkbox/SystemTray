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

import com.sun.jna.Function;
import com.sun.jna.Pointer;

import dorkbox.systemTray.SystemTray;

/**
 * bindings for gtk 2 or 3
 *
 * note: gtk2/3 loading is SENSITIVE, and which AppIndicator symbols are loaded depends on this being loaded first
 *
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
@SuppressWarnings("Duplicates")
public
class Gtk {
    // For funsies to look at, SyncThing did a LOT of work on compatibility in python (unfortunate for us, but interesting).
    // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

    // NOTE: AppIndicator uses this info to figure out WHAT VERSION OF appindicator to use: GTK2 -> appindiactor1, GTK3 -> appindicator3
    public static volatile boolean isGtk2 = false;


    public static Function gtk_status_icon_position_menu = null;

    private static boolean alreadyRunningGTK = false;
    private static boolean isLoaded = false;


    /**
     * We can have GTK v3 or v2.
     *
     * Observations:
     * JavaFX uses GTK2, and we can't load GTK3 if GTK2 symbols are loaded
     * SWT uses GTK2 or GTK3. We do not work with the GTK3 version of SWT.
     */

    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-x11-2.0.so.0 | grep gtk
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk
    static {
        boolean shouldUseGtk2 = SystemTray.FORCE_GTK2 || SystemTray.COMPATIBILITY_MODE;

        // JavaFX Java7,8 is GTK2 only. Java9 can have it be GTK3 if -Djdk.gtk.version=3 is specified
        // see http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html

        if (SystemTray.COMPATIBILITY_MODE && System.getProperty("jdk.gtk.version", "2").equals("3") && !SystemTray.FORCE_GTK2) {
            // the user specified to use GTK3, so we should honor that
            shouldUseGtk2 = false;
        }

        // for more info on JavaFX: https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
        // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.


        // in some cases, we ALWAYS want to try GTK2 first
        if (shouldUseGtk2) {
            try {
                JnaHelper.register("gtk-x11-2.0", Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction("gtk-x11-2.0", "gtk_status_icon_position_menu");
                isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                alreadyRunningGTK = gtk_main_level() != 0;
                isLoaded = true;

                if (SystemTray.DEBUG) {
                    logger.info("GTK: gtk-x11-2.0");
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
                JnaHelper.register("libgtk-3.so.0", Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction("libgtk-3.so.0", "gtk_status_icon_position_menu");
                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                alreadyRunningGTK = gtk_main_level() != 0;
                isLoaded = true;

                if (SystemTray.DEBUG) {
                    logger.info("GTK: libgtk-3.so.0");
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
                JnaHelper.register("gtk-x11-2.0", Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction("gtk-x11-2.0", "gtk_status_icon_position_menu");
                isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has stared GTK -- so we DO NOT NEED TO.
                alreadyRunningGTK = gtk_main_level() != 0;
                isLoaded = true;

                if (SystemTray.DEBUG) {
                    logger.info("GTK: gtk-x11-2.0");
                }
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.error("Error loading library", e);
                }
            }
        }

        if (SystemTray.DEBUG) {
            logger.info("Is the system already running GTK? {}", alreadyRunningGTK);
        }

        if (!isLoaded) {
            throw new RuntimeException("We apologize for this, but we are unable to determine the GTK library is in use, " +
                                       "or even if it is in use... Please create an issue for this and include your OS type and configuration.");
        }
    }

    private static volatile boolean started = false;

    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    private static final LinkedList<Object> gtkCallbacks = new LinkedList<Object>();

    private static Thread gtkUpdateThread = null;

    public static final int FALSE = 0;
    public static final int TRUE = 1;


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
                        // prep for the event loop.
                        gdk_threads_init();
                        gdk_threads_enter();

                        GThread.g_thread_init(null);

                        if (!SystemTray.COMPATIBILITY_MODE) {
                            gtk_init_check(0);
                        }

                        // notify our main thread to continue
                        blockUntilStarted.countDown();

                        if (!SystemTray.COMPATIBILITY_MODE) {
                            // blocks unit quit
                            gtk_main();
                        }

                        gdk_threads_leave();
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
            final FuncCallback callback = new FuncCallback() {
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
            gdk_threads_add_idle(callback, null);
        }
    }

    public static
    void shutdownGui() {
        // If JavaFX/SWT is used, this is UNNECESSARY (and will break SWT/JavaFX shutdown)
        if (!(alreadyRunningGTK || SystemTray.COMPATIBILITY_MODE)) {
            gtk_main_quit();
        }

        started = false;
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
     * using g_idle_add() instead would require thread protection in the callback
     *
     * @return TRUE to run this callback again, FALSE to remove from the list of event sources (and not call it again)
     */
    private static native int gdk_threads_add_idle(FuncCallback callback, Pointer data);

    /**
     * aks for the current nesting level of the main loop. Useful to determine (at startup) if GTK is already running
     */
    private static native int gtk_main_level();

    /**
     * Makes the innermost invocation of the main loop return when it regains control. ONLY CALL FROM THE GtkSupport class, UNLESS you know
     * what you're doing!
     */
    private static native void gtk_main_quit();

    private static native void gdk_threads_init();

    // tricky business. This should only be in the dispatch thread
    private static native void gdk_threads_enter();
    private static native void gdk_threads_leave();





    public static native Pointer gtk_menu_new();

    public static native Pointer gtk_menu_item_new();

    public static native Pointer gtk_menu_item_new_with_label(String label);

    // to create a menu entry WITH an icon.
    public static native Pointer gtk_image_new_from_file(String iconPath);


    public static native Pointer gtk_image_menu_item_new_with_label(String label);

    public static native void gtk_image_menu_item_set_image(Pointer image_menu_item, Pointer image);

    public static native void gtk_image_menu_item_set_always_show_image(Pointer menu_item, int forceShow);

    public static native Pointer gtk_bin_get_child(Pointer parent);

    public static native void gtk_label_set_text(Pointer label, String text);

    public static native void gtk_label_set_markup(Pointer label, Pointer markup);

    public static native void gtk_label_set_use_markup(Pointer label, int gboolean);

    public static native Pointer gtk_status_icon_new();

    public static native void gtk_status_icon_set_from_file(Pointer widget, String lablel);

    public static native void gtk_status_icon_set_visible(Pointer widget, boolean visible);

    // app indicators don't support this, and we cater to the lowest common denominator
//  public static native void gtk_status_icon_set_tooltip(Pointer widget, String tooltipText);

    public static native void gtk_status_icon_set_title(Pointer widget, String titleText);

    public static native void gtk_status_icon_set_name(Pointer widget, String name);

    public static native void gtk_menu_popup(Pointer menu, Pointer widget, Pointer bla, Function func, Pointer data, int button, int time);

    public static native void gtk_menu_item_set_label(Pointer menu_item, String label);

    public static native void gtk_menu_shell_append(Pointer menu_shell, Pointer child);

    public static native void gtk_menu_shell_deactivate(Pointer menu_shell, Pointer child);

    public static native void gtk_widget_set_sensitive(Pointer widget, int sensitive);

    public static native void gtk_container_remove(Pointer menu, Pointer subItem);

    public static native void gtk_widget_show(Pointer widget);

    public static native void gtk_widget_show_all(Pointer widget);

    public static native void gtk_widget_destroy(Pointer widget);
}

