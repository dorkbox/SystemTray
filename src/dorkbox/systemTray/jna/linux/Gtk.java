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
import dorkbox.systemTray.util.JavaFX;
import dorkbox.systemTray.util.Swt;
import dorkbox.util.OS;
import dorkbox.util.jna.JnaHelper;

/**
 * bindings for GTK+ 2. Bindings that are exclusively for GTK+ 3 are in that respective class
 * <p>
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
@SuppressWarnings({"Duplicates", "SameParameterValue", "DanglingJavadoc", "DeprecatedIsStillUsed"})
public
class Gtk {
    // For funsies to look at, SyncThing did a LOT of work on compatibility in python (unfortunate for us, but interesting).
    // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py


    @SuppressWarnings("unused")
    public static class State {
        public static final int NORMAL = 0x0; // normal state.
        public static final int ACTIVE = 0x1; // pressed-in or activated; e.g. buttons while the mouse button is held down.
        public static final int PRELIGHT = 0x2; // color when the mouse is over an activatable widget.
        public static final int SELECTED = 0x3; // color when something is selected, e.g. when selecting some text to cut/copy.
        public static final int INSENSITIVE = 0x4; // color when the mouse is over an activatable widget.

        public static final int FLAG_NORMAL = 0;
        public static final int FLAG_ACTIVE = 1 << 0;
        public static final int FLAG_PRELIGHT = 1 << 1;
        public static final int FLAG_SELECTED = 1 << 2;
        public static final int FLAG_INSENSITIVE = 1 << 3;
        public static final int FLAG_INCONSISTENT = 1 << 4;
        public static final int FLAG_FOCUSED = 1 << 5;
        public static final int FLAG_BACKDROP = 1 << 6;
    }



    // NOTE: AppIndicator uses this info to figure out WHAT VERSION OF appindicator to use: GTK2 -> appindicator1, GTK3 -> appindicator3
    public static final boolean isGtk2;
    public static final boolean isGtk3;
    public static final boolean isLoaded;


    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-x11-2.0.so.0 | grep gtk
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk

    // objdump -T /usr/local/lib/libgtk-3.so.0 | grep gtk

    @SuppressWarnings("WeakerAccess")
    public static final int FALSE = 0;
    public static final int TRUE = 1;
    private static final boolean alreadyRunningGTK;
    // when debugging the EDT, we need a longer timeout.
    private static final boolean debugEDT = true;
    // timeout is in seconds
    private static final int TIMEOUT = debugEDT ? 10000000 : 2;
    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final LinkedList<Object> gtkCallbacks = new LinkedList<Object>();
    public static Function gtk_status_icon_position_menu = null;
    // This is required because the EDT needs to have it's own value for this boolean, that is a different value than the main thread
    private static ThreadLocal<Boolean> isDispatch = new ThreadLocal<Boolean>() {
        @Override
        protected
        Boolean initialValue() {
            return false;
        }
    };
    private static volatile boolean started = false;
    @SuppressWarnings("FieldCanBeLocal")
    private static Thread gtkUpdateThread = null;

    /**
     * We can have GTK v3 or v2.
     *
     * Observations:
     * JavaFX uses GTK2, and we can't load GTK3 if GTK2 symbols are loaded
     * SWT uses GTK2 or GTK3. We do not work with the GTK3 version of SWT.
     */
    static {
        boolean shouldUseGtk2 = SystemTray.FORCE_GTK2;
        boolean _isGtk2 = false;
        boolean _isLoaded = false;
        boolean _alreadyRunningGTK = false;

        boolean shouldLoadGtk = !(OS.isWindows() || OS.isMacOsX());
        if (!shouldLoadGtk) {
            _isLoaded = true;
        }

        // we can force the system to use the swing indicator, which WORKS, but doesn't support transparency in the icon.
        if (!_isLoaded &&
            (SystemTray.FORCE_TRAY_TYPE == SystemTray.TrayType.Swing || SystemTray.FORCE_TRAY_TYPE == SystemTray.TrayType.AWT)) {
            if (SystemTray.DEBUG) {
                logger.debug("Not loading GTK for Swing or AWT");
            }
            _isLoaded = true;
        }

        // in some cases, we ALWAYS want to try GTK2 first
        String gtk2LibName = "gtk-x11-2.0";
        String gtk3LibName = "libgtk-3.so.0";


        if (!_isLoaded && shouldUseGtk2) {
            try {
                JnaHelper.register(gtk2LibName, Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                _isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = gtk_main_level() != 0;
                _isLoaded = true;

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
        if (!_isLoaded) {
            try {
                JnaHelper.register(gtk3LibName, Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk3LibName, "gtk_status_icon_position_menu");

                // ALSO have to load the SPECIFIC Gtk+ 3 methods. We cannot subclass because JNA doesn't like it.
                // This is BY FAR the best way to accomplish this, however because of the way static methods work, we are
                // stuck "loading it twice"
                JnaHelper.register(gtk3LibName, Gtk3.class);

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = gtk_main_level() != 0;
                _isLoaded = true;

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
        if (!_isLoaded) {
            try {
                JnaHelper.register(gtk2LibName, Gtk.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                _isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = gtk_main_level() != 0;
                _isLoaded = true;

                if (SystemTray.DEBUG) {
                    logger.debug("GTK: {}", gtk2LibName);
                }
            } catch (Throwable e) {
                if (SystemTray.DEBUG) {
                    logger.error("Error loading library", e);
                }
            }
        }

        if (shouldLoadGtk && _isLoaded) {
            isLoaded = true;

            // depending on how the system is initialized, SWT may, or may not, have the gtk_main loop running. It will EVENTUALLY run, so we
            // do not want to run our own GTK event loop.
            _alreadyRunningGTK |= SystemTray.isSwtLoaded;

            if (SystemTray.DEBUG) {
                logger.debug("Is the system already running GTK? {}", _alreadyRunningGTK);
            }

            alreadyRunningGTK = _alreadyRunningGTK;
            isGtk2 = _isGtk2;
            isGtk3 = !_isGtk2;
        }
        else {
            isLoaded = false;

            alreadyRunningGTK = false;
            isGtk2 = false;
            isGtk3 = false;
        }

        if (shouldLoadGtk && !_isLoaded) {
            throw new RuntimeException("We apologize for this, but we are unable to determine the GTK library is in use, " +
                                       "or even if it is in use... Please create an issue for this and include your OS type and configuration.");
        }
    }

    public static
    void startGui() {
        // only permit one startup per JVM instance
        if (!started) {
            started = true;

            // startup the GTK GUI event loop. There can be multiple/nested loops.


            if (!alreadyRunningGTK) {
                // If JavaFX/SWT is used, this is UNNECESSARY (we can detect if the GTK main_loop is running)

                gtkUpdateThread = new Thread() {
                    @Override
                    public
                    void run() {
                        Glib.GLogFunc orig = null;
                        if (SystemTray.DEBUG) {
                            logger.debug("Running GTK Native Event Loop");
                        } else {
                            // NOTE: This can output warnings, so we suppress them
                            orig = Glib.g_log_set_default_handler(Glib.nullLogFunc, null);
                        }


                        // prep for the event loop.
                        // GThread.g_thread_init(null);  would be needed for g_idle_add()

                        if (!gtk_init_check(0)) {
                            if (SystemTray.DEBUG) {
                                logger.error("Error starting GTK");
                            }
                            return;
                        }

                        // gdk_threads_enter();  would be needed for g_idle_add()

                        if (orig != null) {
                            Glib.g_log_set_default_handler(orig, null);
                        }

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
     * This would NORMALLY have a 2nd argument that is a String[] -- however JNA direct-mapping DOES NOT support this. We are lucky
     * enough that we just pass 'null' as the second argument, therefore, we don't have to define that parameter here.
     */
    private static native
    boolean gtk_init_check(int argc);

    /**
     * Runs the main loop until gtk_main_quit() is called. You can nest calls to gtk_main(). In that case gtk_main_quit() will make the
     * innermost invocation of the main loop return.
     */
    private static native
    void gtk_main();

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
                    if (!blockUntilStarted.await(10, TimeUnit.SECONDS)) {
                        if (SystemTray.DEBUG) {
                            SystemTray.logger.error("Something is very wrong. The waitForStartup took longer than expected.",
                                                    new Exception(""));
                        }
                    }

                    // we have to WAIT until all events are done processing, OTHERWISE we have initialization issues
                    while (true) {
                        Thread.sleep(100);

                        synchronized (gtkCallbacks) {
                            if (gtkCallbacks.isEmpty()) {
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (SystemTray.isSwtLoaded) {
            if (!Swt.isEventThread()) {
                // we have to WAIT until all events are done processing, OTHERWISE we have initialization issues
                try {
                    if (!blockUntilStarted.await(10, TimeUnit.SECONDS)) {
                        if (SystemTray.DEBUG) {
                            SystemTray.logger.error("Something is very wrong. The waitForStartup took longer than expected.",
                                                    new Exception(""));
                        }
                    }

                    while (true) {
                        Thread.sleep(100);

                        synchronized (gtkCallbacks) {
                            if (gtkCallbacks.isEmpty()) {
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        else {
            try {
                if (!blockUntilStarted.await(10, TimeUnit.SECONDS)) {
                    if (SystemTray.DEBUG) {
                        SystemTray.logger.error("Something is very wrong. The waitForStartup took longer than expected.",
                                                new Exception(""));
                    }
                }

                // we have to WAIT until all events are done processing, OTHERWISE we have initialization issues
                while (true) {
                    Thread.sleep(100);

                    synchronized (gtkCallbacks) {
                        if (gtkCallbacks.isEmpty()) {
                            break;
                        }
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
        }
        else {
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

    /**
     * Adds a function to be called whenever there are no higher priority events pending. If the function returns FALSE it is automatically
     * removed from the list of event sources and will not be called again.
     * <p>
     * This variant of g_idle_add_full() calls function with the GDK lock held. It can be thought of a MT-safe version for GTK+ widgets
     * for the following use case, where you have to worry about idle_callback() running in thread A and accessing self after it has
     * been finalized in thread B.
     */
    public static native
    int gdk_threads_add_idle_full(int priority, FuncCallback function, Pointer data, Pointer notify);

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

    public static
    void dispatchAndWait(final Runnable runnable) {
        if (isDispatch.get()) {
            // Run directly on the dispatch thread (should not "redispatch" this again)
            runnable.run();
        }
        else {
            final CountDownLatch countDownLatch = new CountDownLatch(1);

            Gtk.dispatch(new Runnable() {
                @Override
                public
                void run() {
                    try {
                        runnable.run();
                    } catch (Exception e) {
                        SystemTray.logger.error("Error during GTK run loop: ", e);
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
                        SystemTray.logger.error(
                                        "Something is very wrong. The Event Dispatch Queue took longer than " + TIMEOUT + " seconds " +
                                        "to complete.", new Exception(""));
                    }
                    else {
                        throw new RuntimeException("Something is very wrong. The Event Dispatch Queue took longer than " + TIMEOUT +
                                                   " seconds " + "to complete.");
                    }
                }
            } catch (InterruptedException e) {
                SystemTray.logger.error("Error waiting for dispatch to complete.", new Exception(""));
            }
        }
    }

    /**
     * Makes the innermost invocation of the main loop return when it regains control. ONLY CALL FROM THE GtkSupport class, UNLESS you know
     * what you're doing!
     */
    private static native
    void gtk_main_quit();

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
            }
            else {
                // checkbox entries will not pass the menuEntry in, because they redispatch the click event so that the checkbox state is
                // toggled
                callback.actionPerformed(null);
            }
        } finally {
            Gtk.isDispatch.set(false);
        }
    }

    /**
     * aks for the current nesting level of the main loop. Useful to determine (at startup) if GTK is already running
     */
    private static native
    int gtk_main_level();

    /**
     * Creates a new GtkMenu
     */
    public static native
    Pointer gtk_menu_new();


    /**
     * Sets or replaces the menu item’s submenu, or removes it when a NULL submenu is passed.
     */
    public static native
    Pointer gtk_menu_item_set_submenu(Pointer menuEntry, Pointer menu);

    /**
     * Creates a new GtkSeparatorMenuItem.
     */
    public static native
    Pointer gtk_separator_menu_item_new();

    /**
     * Creates a new GtkImage displaying the file filename . If the file isn’t found or can’t be loaded, the resulting GtkImage will
     * display a “broken image” icon. This function never returns NULL, it always returns a valid GtkImage widget.
     * <p>
     * If the file contains an animation, the image will contain an animation.
     */
    public static native
    Pointer gtk_image_new_from_file(String iconPath);


    /**
     * Sets the active state of the menu item’s check box.
     */
    public static native
    void gtk_check_menu_item_set_active(Pointer check_menu_item, boolean isChecked);



    /**
     * Creates a new GtkImageMenuItem containing a label. The label will be created using gtk_label_new_with_mnemonic(), so underscores
     * in label indicate the mnemonic for the menu item.
     * <p>
     * uses '_' to define which key is the mnemonic
     * <p>
     * gtk_image_menu_item_new_with_mnemonic has been deprecated since version 3.10 and should not be used in newly-written code.
     * NOTE: Use gtk_menu_item_new_with_mnemonic() instead.
     */
    @Deprecated
    public static native
    Pointer gtk_image_menu_item_new_with_mnemonic(String label);

    public static native
    Pointer gtk_check_menu_item_new_with_mnemonic(String label);

    /**
     * Sets the image of image_menu_item to the given widget. Note that it depends on the show-menu-images setting whether the image
     * will be displayed or not.
     * <p>
     * gtk_image_menu_item_set_image has been deprecated since version 3.10 and should not be used in newly-written code.
     */
    @Deprecated
    public static native
    void gtk_image_menu_item_set_image(Pointer image_menu_item, Pointer image);

    /**
     * If TRUE, the menu item will ignore the “gtk-menu-images” setting and always show the image, if available.
     * Use this property if the menuitem would be useless or hard to use without the image
     * <p>
     * gtk_image_menu_item_set_always_show_image has been deprecated since version 3.10 and should not be used in newly-written code.
     */
    @Deprecated
    public static native
    void gtk_image_menu_item_set_always_show_image(Pointer menu_item, boolean forceShow);

    /**
     * Creates an empty status icon object.
     * <p>
     * gtk_status_icon_new has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native
    Pointer gtk_status_icon_new();

    /**
     * Gets the size in pixels that is available for the image. Stock icons and named icons adapt their size automatically if the size of
     * the notification area changes. For other storage types, the size-changed signal can be used to react to size changes.
     * Note that the returned size is only meaningful while the status icon is embedded (see gtk_status_icon_is_embedded()).
     * <p>
     * gtk_status_icon_get_size has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native
    int gtk_status_icon_get_size(Pointer status_icon);

    /**
     * Makes status_icon display the file filename . See gtk_status_icon_new_from_file() for details.
     * <p>
     * gtk_status_icon_set_from_file has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native
    void gtk_status_icon_set_from_file(Pointer widget, String label);

    /**
     * Shows or hides a status icon.
     * <p>
     * gtk_status_icon_set_visible has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native
    void gtk_status_icon_set_visible(Pointer widget, boolean visible);


    @Deprecated
    /**
     * Sets text as the contents of the tooltip.
     * This function will take care of setting “has-tooltip” to TRUE and of the default handler for the “query-tooltip” signal.
     *
     * app indicators don't support this
     *
     * gtk_status_icon_set_tooltip_text has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */ public static native
    void gtk_status_icon_set_tooltip_text(Pointer widget, String tooltipText);

    /**
     * Sets the title of this tray icon. This should be a short, human-readable, localized string describing the tray icon. It may be used
     * by tools like screen readers to render the tray icon.
     * <p>
     * gtk_status_icon_set_title has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native
    void gtk_status_icon_set_title(Pointer widget, String titleText);

    /**
     * Sets the name of this tray icon. This should be a string identifying this icon. It is may be used for sorting the icons in the
     * tray and will not be shown to the user.
     * <p>
     * gtk_status_icon_set_name has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native
    void gtk_status_icon_set_name(Pointer widget, String name);

    /**
     * Displays a menu and makes it available for selection.
     * <p>
     * gtk_menu_popup has been deprecated since version 3.22 and should not be used in newly-written code.
     * NOTE: Please use gtk_menu_popup_at_widget(), gtk_menu_popup_at_pointer(). or gtk_menu_popup_at_rect() instead
     */
    @Deprecated
    public static native
    void gtk_menu_popup(Pointer menu, Pointer widget, Pointer bla, Function func, Pointer data, int button, int time);

    /**
     * Sets text on the menu_item label
     */
    public static native
    void gtk_menu_item_set_label(Pointer menu_item, String label);

    /**
     * Adds a new GtkMenuItem to the end of the menu shell's item list.
     */
    public static native
    void gtk_menu_shell_append(Pointer menu_shell, Pointer child);

    /**
     * Sets the sensitivity of a widget. A widget is sensitive if the user can interact with it. Insensitive widgets are “grayed out”
     * and the user can’t interact with them. Insensitive widgets are known as “inactive”, “disabled”, or “ghosted” in some other toolkits.
     */
    public static native
    void gtk_widget_set_sensitive(Pointer widget, boolean sensitive);

    /**
     * Recursively shows a widget, and any child widgets (if the widget is a container)
     */
    public static native
    void gtk_widget_show_all(Pointer widget);

    /**
     * Removes widget from container . widget must be inside container . Note that container will own a reference to widget , and that
     * this may be the last reference held; so removing a widget from its container can destroy that widget.
     * <p>
     * If you want to use widget again, you need to add a reference to it before removing it from a container, using g_object_ref().
     * If you don’t want to use widget again it’s usually more efficient to simply destroy it directly using gtk_widget_destroy()
     * since this will remove it from the container and help break any circular reference count cycles.
     */
    public static native
    void gtk_container_remove(Pointer parentWidget, Pointer widget);

    /**
     * Destroys a widget.
     * When a widget is destroyed all references it holds on other objects will be released:
     * - if the widget is inside a container, it will be removed from its parent
     * - if the widget is a container, all its children will be destroyed, recursively
     * - if the widget is a top level, it will be removed from the list of top level widgets that GTK+ maintains internally
     * <p>
     * It's expected that all references held on the widget will also be released; you should connect to the “destroy” signal if you
     * hold a reference to widget and you wish to remove it when this function is called. It is not necessary to do so if you are
     * implementing a GtkContainer, as you'll be able to use the GtkContainerClass.remove() virtual function for that.
     * <p>
     * It's important to notice that gtk_widget_destroy() will only cause the widget to be finalized if no additional references,
     * acquired using g_object_ref(), are held on it. In case additional references are in place, the widget will be in an "inert" state
     * after calling this function; widget will still point to valid memory, allowing you to release the references you hold, but you
     * may not query the widget's own state.
     * <p>
     * NOTE You should typically call this function on top level widgets, and rarely on child widgets.
     */
    public static native
    void gtk_widget_destroy(Pointer widget);

    /**
     * Gets the GtkSettings object for the default GDK screen, creating it if necessary. See gtk_settings_get_for_screen().
     * <p>
     * If there is no default screen, then returns NULL.
     */
    public static native
    Pointer gtk_settings_get_default();


    /**
     * Simply an accessor function that returns @widget->style.
     */
    public static native
    GtkStyle.ByReference gtk_widget_get_style(Pointer widget);

    /**
     * Finds all matching RC styles for a given widget, composites them together, and then creates a GtkStyle representing the composite
     * appearance. (GTK+ actually keeps a cache of previously created styles, so a new style may not be created.)
     */
    public static native
    Pointer gtk_rc_get_style(Pointer widget);

    /**
     * Creates a toplevel container widget that is used to retrieve snapshots of widgets without showing them on the screen.
     *
     * @since 2.20
     */
    public static native
    Pointer gtk_offscreen_window_new();

    /**
     * Looks up color_name in the style’s logical color mappings, filling in color and returning TRUE if found, otherwise returning
     * FALSE. Do not cache the found mapping, because it depends on the GtkStyle and might change when a theme switch occurs.
     *
     * @since 2.10
     */
    public static native
    boolean gtk_style_lookup_color(Pointer widgetStyle, String color_name, Pointer color);

    /**
     * Adds widget to container . Typically used for simple containers such as GtkWindow, GtkFrame, or GtkButton; for more complicated
     * layout containers such as GtkBox or GtkTable, this function will pick default packing parameters that may not be correct. So
     * consider functions such as gtk_box_pack_start() and gtk_table_attach() as an alternative to gtk_container_add() in those cases.
     * A widget may be added to only one container at a time; you can't place the same widget inside two different containers.
     */
    public static native
    void gtk_container_add(Pointer offscreen, Pointer widget);
}

