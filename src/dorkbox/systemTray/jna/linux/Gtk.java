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

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.jna.Function;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.nativeUI._AppIndicatorNativeTray;
import dorkbox.systemTray.nativeUI._GtkStatusIconNativeTray;
import dorkbox.systemTray.util.JavaFX;
import dorkbox.systemTray.util.Swt;
import dorkbox.util.OS;
import dorkbox.util.jna.JnaHelper;

/**
 * bindings for GTK+ 2. Bindings that are exclusively for GTK+ 3 are in that respective class
 *
 * note: gtk2/3 loading is SENSITIVE, and which AppIndicator symbols are loaded depends on this being loaded first
 *       Additionally, gtk3 has deprecated some methods -- which, fortunately for us, means it will be another 25 years before they are
 *       removed; forcing us to have completely separate gtk2/3 bindings.
 *
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
@SuppressWarnings({"Duplicates", "SameParameterValue", "DanglingJavadoc", "DeprecatedIsStillUsed"})
public
class Gtk {
    // For funsies to look at, SyncThing did a LOT of work on compatibility in python (unfortunate for us, but interesting).
    // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

    // NOTE: AppIndicator uses this info to figure out WHAT VERSION OF appindicator to use: GTK2 -> appindicator1, GTK3 -> appindicator3
    public static final boolean isGtk2;
    public static final boolean isGtk3;
    public static final boolean isLoaded;

    public static Function gtk_status_icon_position_menu = null;

    private static final boolean alreadyRunningGTK;

    // when debugging the EDT, we need a longer timeout.
    private static final boolean debugEDT = true;

    // This is required because the EDT needs to have it's own value for this boolean, that is a different value than the main thread
    private static ThreadLocal<Boolean> isDispatch = new ThreadLocal<Boolean>() {
        @Override
        protected
        Boolean initialValue() {
            return false;
        }
    };

    // timeout is in seconds
    private static final int TIMEOUT = debugEDT ? 10000000 : 2;

    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-x11-2.0.so.0 | grep gtk
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk

    // objdump -T /usr/local/lib/libgtk-3.so.0 | grep gtk

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


            if (!alreadyRunningGTK) {
                // If JavaFX/SWT is used, this is UNNECESSARY (we can detect if the GTK main_loop is running)

                gtkUpdateThread = new Thread() {
                    @Override
                    public
                    void run() {
                        if (SystemTray.DEBUG) {
                            logger.debug("Running GTK Native Event Loop");
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
     * aks for the current nesting level of the main loop. Useful to determine (at startup) if GTK is already running
     */
    private static native
    int gtk_main_level();

    /**
     * Makes the innermost invocation of the main loop return when it regains control. ONLY CALL FROM THE GtkSupport class, UNLESS you know
     * what you're doing!
     */
    private static native
    void gtk_main_quit();

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
     *
     * uses '_' to define which key is the mnemonic
     *
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
     *
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
     *
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
     *
     * gtk_status_icon_get_size has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native int gtk_status_icon_get_size(Pointer status_icon);

    /**
     * Makes status_icon display the file filename . See gtk_status_icon_new_from_file() for details.
     *
     * gtk_status_icon_set_from_file has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native
    void gtk_status_icon_set_from_file(Pointer widget, String label);

    /**
     * Shows or hides a status icon.
     *
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
     */
    public static native
    void gtk_status_icon_set_tooltip_text(Pointer widget, String tooltipText);

    /**
     * Sets the title of this tray icon. This should be a short, human-readable, localized string describing the tray icon. It may be used
     * by tools like screen readers to render the tray icon.
     *
     * gtk_status_icon_set_title has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native
    void gtk_status_icon_set_title(Pointer widget, String titleText);

    /**
     * Sets the name of this tray icon. This should be a string identifying this icon. It is may be used for sorting the icons in the
     * tray and will not be shown to the user.
     *
     * gtk_status_icon_set_name has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    @Deprecated
    public static native
    void gtk_status_icon_set_name(Pointer widget, String name);

    /**
     * Displays a menu and makes it available for selection.
     *
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
     * Finds all matching RC styles for a given widget, composites them together, and then creates a GtkStyle representing the composite
     * appearance. (GTK+ actually keeps a cache of previously created styles, so a new style may not be created.)
     */
    public static native Pointer gtk_rc_get_style(Pointer widget);

    /**
     * Creates a toplevel container widget that is used to retrieve snapshots of widgets without showing them on the screen.
     *
     * @since 2.20
     */
    public static native Pointer gtk_offscreen_window_new();

    /**
     * Looks up color_name in the style’s logical color mappings, filling in color and returning TRUE if found, otherwise returning
     * FALSE. Do not cache the found mapping, because it depends on the GtkStyle and might change when a theme switch occurs.
     *
     * @since 2.10
     */
    public static native  boolean gtk_style_lookup_color(Pointer widgetStyle, String color_name, Pointer color);


    /**
     * Adds widget to container . Typically used for simple containers such as GtkWindow, GtkFrame, or GtkButton; for more complicated
     * layout containers such as GtkBox or GtkTable, this function will pick default packing parameters that may not be correct. So
     * consider functions such as gtk_box_pack_start() and gtk_table_attach() as an alternative to gtk_container_add() in those cases.
     * A widget may be added to only one container at a time; you can't place the same widget inside two different containers.
     */
    public static native void gtk_container_add(Pointer offscreen, Pointer widget);


    /**
     * @return the widget color of text for the current theme, or black. It is important that this is called AFTER GTK has been initialized.
     */
    public static
    Color getCurrentThemeTextColor() {
        /*
         * There are several 'directives' to change the attributes of a widget.
         *  fg - Sets the foreground color of a widget.
         *  bg - Sets the background color of a widget.
         *  text - Sets the foreground color for widgets that have editable text.
         *  base - Sets the background color for widgets that have editable text.
         *  bg_pixmap - Sets the background of a widget to a tiled pixmap.
         *  font_name - Sets the font to be used with the given widget.
         *  xthickness - Sets the left and right border width. This is not what you might think; it sets the borders of children(?)
         *  ythickness - similar to above but for the top and bottom.
         *
         * There are several states a widget can be in, and you can set different colors, pixmaps and fonts for each state. These states are:
         *  NORMAL - The normal state of a widget. Ie the mouse is not over it, and it is not being pressed, etc.
         *  PRELIGHT - When the mouse is over top of the widget, colors defined using this state will be in effect.
         *  ACTIVE - When the widget is pressed or clicked it will be active, and the attributes assigned by this tag will be in effect.
         *  INSENSITIVE - This is the state when a widget is 'greyed out'. It is not active, and cannot be clicked on.
         *  SELECTED - When an object is selected, it takes these attributes.
         */

        // NOTE: when getting CSS, we redirect STDERR to null, so that we won't spam the console if there are parse errors.
        //   this is a horrid hack, but the only way to work around the errors we have no control over. The parse errors, if bad enough
        //   just mean that we are unable to get the CSS as we want.

        // these methods are from most accurate (but limited in support) to compatible across Linux OSes.. Strangely enough, GTK makes
        // this information insanely difficult to get.
        final AtomicReference<Color> color = new AtomicReference<Color>(null);
        Gtk.dispatchAndWait(new Runnable() {

            @Override
            public
            void run() {
                // see if we can get the info via CSS properties (> GTK+ 3.2)
                if (Gtk.isGtk3) {
                    Color c = getFromCss();
                    if (c != null) {
                        System.err.println("Got from CSS");
                        color.set(c);
                        return;
                    }
                }

                // we got here because it's not possible to get the info via raw-CSS...


                // try to get via the color scheme
                Color c = getFromColorScheme();
                if (c != null) {
                    System.err.println("Got from color scheme");
                    color.set(c);
                    return;
                }


                // the following methods all require an offscreen widget to get the style information from


                // create an off-screen widget (don't forget to destroy everything!)
                Pointer offscreen = Gtk.gtk_offscreen_window_new();
                final Pointer item = Gtk.gtk_image_menu_item_new_with_mnemonic("asd");
                Gtk.gtk_container_add (offscreen, item);
                Gtk.gtk_widget_show_all(item);



                // Try to get via RC style... Sometimes this works (sometimes it does not...)
                {
                    Pointer style = Gtk.gtk_rc_get_style(item);

                    GdkColor gdkColor = new GdkColor();
                    boolean success = Gtk.gtk_style_lookup_color(style, "menu_fg_color", gdkColor.getPointer());
                    if (!success) {
                        success = Gtk.gtk_style_lookup_color(style, "text_color", gdkColor.getPointer());
                    }
                    if (!success) {
                        success = Gtk.gtk_style_lookup_color(style, "theme_text_color", gdkColor.getPointer());
                    }
                    if (success) {
                        // Have to convert to positive int (value between 0 and 65535, these are 16 bits per pixel) that is from 0-255
                        int red = gdkColor.red & 0x0000FFFF;
                        int green = gdkColor.green & 0x0000FFFF;
                        int blue = gdkColor.blue & 0x0000FFFF;

                        red = (red >> 8) & 0xFF;
                        green = (green >> 8) & 0xFF;
                        blue = (blue >> 8) & 0xFF;

                        color.set(new Color(red, green, blue));

                        Gtk.gtk_widget_destroy(item);
                        return;
                    }
                }

                if (Gtk.isGtk3) {
                    Pointer context = Gtk3.gtk_widget_get_style_context(item);
                    int state = Gtk3.gtk_style_context_get_state(context);

                    GdkRGBAColor gdkColor = new GdkRGBAColor();
                    boolean success = Gtk3.gtk_style_context_lookup_color(context, "fg_color", gdkColor.getPointer());
                    if (!success) {
                        success = Gtk3.gtk_style_context_lookup_color(context, "text_color", gdkColor.getPointer());
                    }
                    if (!success) {
                        success = Gtk3.gtk_style_context_lookup_color(context, "menu_fg_color", gdkColor.getPointer());
                    }

                    if (!success) {
                        success = Gtk3.gtk_style_context_lookup_color(context, "color", gdkColor.getPointer());
                    }

                    if (success) {
                        color.set(new Color((float) gdkColor.red, (float) gdkColor.green, (float) gdkColor.blue, (float) gdkColor.alpha));
                    } else {
                        // fall back in case nothing else works
                        Gtk3.gtk_style_context_get_color(context, state, gdkColor.getPointer());
                        if ((gdkColor.red == 0.0 && gdkColor.green == 0.0 && gdkColor.blue == 0.0) || gdkColor.alpha == 0.0) {
                            // have nothing here, check something else?

                        } else {
                            // if we have something that is not all 0's
                            color.set(new Color((float) gdkColor.red, (float) gdkColor.green, (float) gdkColor.blue, (float) gdkColor.alpha));
                        }
                    }
                }

                Gtk.gtk_widget_destroy(item);
            }
        });


        Color color1 = color.get();
        if (color1 != null) {
            System.err.println("COLOR: " + color1);
            return color1;
        }

        SystemTray.logger.error("Unable to determine the text color in use by your system. Please create an issue and include your " +
                                "full OS configuration and desktop environment (including theme and color variant) details.");

        // who knows WHAT the color is supposed to be. This is just a "best guess" default value.
        return Color.BLACK;
    }

    /**
     * get the color we are interested in via raw CSS parsing. This is specifically to get the color of the text of the
     * appindicator/gtk-status-icon menu.
     *
     * @return the color string, parsed from CSS/
     */
    private static Color getFromCss() {
        String css = getGtkThemeCss();
        if (css != null) {
//            System.err.println("css: " + css);

            String[] nodes;
            Tray tray = (Tray) SystemTray.get()
                                         .getMenu();


            // we care about the following CSS head nodes, and account for multiple versions, in order of preference.
            if (tray instanceof _GtkStatusIconNativeTray) {
                nodes = new String[] {"GtkPopover", "gnome-panel-menu-bar", "unity-panel", "PanelMenuBar", ".check"};
            }
            else if (tray instanceof _AppIndicatorNativeTray) {
                nodes = new String[] {"GtkPopover", "unity-panel", "gnome-panel-menu-bar", "PanelMenuBar", ".check"};
            }
            else {
                // not supported for other types
                return null;
            }

            // collect a list of all of the sections that have what we are interested in
            List<String> sections = new ArrayList<String>();

            String colorString = null;

            // now check the css nodes to see if they contain a combination of what we are looking for.
            colorCheck:
            for (String node : nodes) {
                int i = 0;
                while (i != -1) {
                    i = css.indexOf(node, i);
                    if (i > -1) {
                        int endOfNodeLabels = css.indexOf("{", i);
                        int endOfSection = css.indexOf("}", endOfNodeLabels + 1) + 1;
                        int endOfSectionTest = css.indexOf("}", i) + 1;

                        // this makes sure that weird parsing errors don't happen as a result of node keywords appearing in node sections
                        if (endOfSection != endOfSectionTest) {
                            // advance the index
                            i = endOfSection;
                            continue;
                        }

                        String nodeLabel = css.substring(i, endOfNodeLabels);
                        String nodeSection = css.substring(endOfNodeLabels, endOfSection);

//                        if (nodeSection.contains("menu_fg_color")) {
//                            System.err.println(nodeSection);
//                        }

                        int j = nodeSection.indexOf(" color");
                        if (j > -1) {
                            sections.add(nodeLabel + " " + nodeSection);
                        }

                        // advance the index
                        i = endOfSection;
                    }
                }
            }

//            for (String section : sections) {
//                System.err.println("--------------");
//                System.err.println(section);
//                System.err.println("--------------");
//            }

           if (!sections.isEmpty()) {
               String section = sections.get(0);
               int start = section.indexOf("{");
               int colorIndex = section.indexOf(" color", start);

               int startOfColorDef = section.indexOf(":", colorIndex) + 1;
               int endOfColorDef = section.indexOf(";", startOfColorDef);
               colorString = section.substring(startOfColorDef, endOfColorDef)
                                        .trim();
           }

            // hopefully we found it.
            if (colorString != null) {
                if (colorString.startsWith("@")) {
                    // it's a color definition
                    colorString = colorString.substring(1);

                    // have to setup the "define color" section
                    String colorDefine = "@define-color";
                    int start = css.indexOf(colorDefine);
                    int end = css.lastIndexOf(colorDefine);
                    end = css.lastIndexOf(";", end) + 1; // include the ;
                    String colorDefines = css.substring(start, end);

//                    System.err.println("+++++++++++++++++++++++");
//                    System.err.println(colorDefines);
//                    System.err.println("+++++++++++++++++++++++");

                    // since it's a color definition, it will start a very specific way.
                    String newColorString = colorDefine + " " + colorString;

                    int i = 0;
                    while (i != -1) {
                        i = colorDefines.indexOf(newColorString);

                        if (i >= 0) {
                            try {
                                int startIndex = i + newColorString.length();
                                int endIndex = colorDefines.indexOf(";", i);

                                String colorSubString = colorDefines.substring(startIndex, endIndex)
                                                                    .trim();

                                if (colorSubString.startsWith("@")) {
                                    // have to recursively get the defined color
                                    newColorString = colorDefine + " " + colorSubString.substring(1);
                                    i = 0;
                                    continue;
                                }

                                return parseColor(colorSubString);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } else {
                    return parseColor(colorString);
                }
            }
        }

        return null;
    }

    // this works for GtkStatusIcon menus.
    private static Color getFromColorScheme() {
        Pointer settings = Gtk.gtk_settings_get_default();
        if (settings != null) {
            // see if we can get the info we want the EASY way (likely only when GTK+ 2 is used, but can be < GTK+ 3.2)...

            //  been deprecated since version 3.8
            PointerByReference pointer = new PointerByReference();
            Gobject.g_object_get(settings, "gtk-color-scheme", pointer, null);

            // A palette of named colors for use in themes. The format of the string is
            //      name1: color1
            //      name2: color2
            //
            //  Color names must be acceptable as identifiers in the gtkrc syntax, and color specifications must be in the format
            //      accepted by gdk_color_parse().
            //
            // Note that due to the way the color tables from different sources are merged, color specifications will be converted
            // to hexadecimal form when getting this property.
            //
            //  Starting with GTK+ 2.12, the entries can alternatively be separated by ';' instead of newlines:
            //      name1: color1; name2: color2; ...
            //
            // GtkSettings:gtk-color-scheme has been deprecated since version 3.8 and should not be used in newly-written code.
            //  Color scheme support was dropped and is no longer supported. You can still set this property, but it will be ignored.


            Pointer value = pointer.getValue();
            if (value != null) {
                String s = value.getString(0);
                if (!s.isEmpty()) {
                    // System.out.println("\t string: " + s);

                    // Note: these are the values on my system when forcing GTK+ 2 (XUbuntu 16.04) with GtkStatusIcon
                    // bg_color_dark: #686868686868
                    // fg_color: #3c3c3c3c3c3c
                    // fm_color: #f7f7f7f7f7f7
                    // selected_fg_color: #ffffffffffff
                    // panel_bg: #686868686868
                    // text_color: #212121212121
                    // text_color_dark: #ffffffffffff
                    // tooltip_bg_color: #000000000000
                    // link_color: #2d2d7171b8b8
                    // tooltip_fg_color: #e1e1e1e1e1e1
                    // base_color: #fcfcfcfcfcfc
                    // bg_color: #cececececece
                    // selected_bg_color: #39398e8ee7e7


                    String textColor = "text_color";  // also theme_text_color
                    int i = s.indexOf(textColor);
                    if (i >= 0) {
                        try {
                            // the color will ALWAYS be in hex notation

                            // it is also possible to be separated by ; instead of newline
                            int endIndex = s.indexOf(';', i);
                            if (endIndex == -1) {
                                endIndex = s.indexOf('\n', i);
                            }

                            int startIndex = s.indexOf('#', i);
                            String colorString = s.substring(startIndex, endIndex).trim();

                            return parseColor(colorString);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        return null;
    }


    /**
     * Parses out the color from a color:
     *
     *    - the word "transparent"
     *    - hex 12 digit  #ffffaaaaffff
     *    - hex 6 digit   #ffaaff
     *    - hex 3 digit   #faf
     *    - rgb(r, g, b)  rgb(33, 33, 33)
     *    - rgb(r, g, b)  rgb(.6, .3, .3)
     *    - rgb(r%, g%, b%)  rgb(10%, 20%, 30%)
     *    - rgba(r, g, b, a)  rgb(33, 33, 33, 0.53)
     *    - rgba(r, g, b, a)  rgb(.33, .33, .33, 0.53)
     *    - rgba(r, g, b, a)  rgb(10%, 20%, 30%, 0.53)
     *
     *  Notes:
     *   - rgb(), when an int, is between 0-255
     *   - rgb(), when a float, is between 0.0-1.0
     *   - rgb(), when a percent, is between 0-100
     *   - alpha is always a float
     *
     * @return the parsed color
     */
    private static
    Color parseColor(String colorString) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int alpha = 255;

        if (colorString.startsWith("#")) {
            colorString = colorString.substring(1);

            if (colorString.length() > 11) {
                red = Integer.parseInt(colorString.substring(0, 4), 16);
                green = Integer.parseInt(colorString.substring(4, 8), 16);
                blue = Integer.parseInt(colorString.substring(8), 16);

                // Have to convert to positive int (value between 0 and 65535, these are 16 bits per pixel) that is from 0-255
                red = red & 0x0000FFFF;
                green = green & 0x0000FFFF;
                blue = blue & 0x0000FFFF;

                red = (red >> 8) & 0xFF;
                green = (green >> 8) & 0xFF;
                blue = (blue >> 8) & 0xFF;
            }
            else if (colorString.length() > 5) {
                red = Integer.parseInt(colorString.substring(0, 2), 16);
                green = Integer.parseInt(colorString.substring(2, 4), 16);
                blue = Integer.parseInt(colorString.substring(4), 16);
            }
            else {
                red = Integer.parseInt(colorString.substring(0, 1), 16);
                green = Integer.parseInt(colorString.substring(1, 2), 16);
                blue = Integer.parseInt(colorString.substring(2), 16);
            }
        }
        else if (colorString.startsWith("rgba")) {
            colorString = colorString.substring(colorString.indexOf('(') + 1, colorString.indexOf(')'));
            String[] split = colorString.split(",");

            String trim1 = split[0].trim();
            String trim2 = split[1].trim();
            String trim3 = split[2].trim();
            String trim4 = split[3].trim();

            if (colorString.contains("%")) {
                trim1 = trim1.replace("%", "");
                trim2 = trim2.replace("%", "");
                trim3 = trim3.replace("%", "");

                red = Integer.parseInt(trim1) * 255;
                green = Integer.parseInt(trim2) * 255;
                blue = Integer.parseInt(trim3) * 255;
            } else if (colorString.contains(".")) {
                red = (int) (Float.parseFloat(trim1) * 255);
                green = (int) (Float.parseFloat(trim2) * 255);
                blue = (int) (Float.parseFloat(trim3) * 255);
            } else {
                red = Integer.parseInt(trim1);
                green = Integer.parseInt(trim2);
                blue = Integer.parseInt(trim3);
            }

            float alphaF = Float.parseFloat(trim4);
            alpha = (int) (alphaF * 255);
        }
        else if (colorString.startsWith("rgb")) {
            colorString = colorString.substring(colorString.indexOf('(') + 1, colorString.indexOf(')'));
            String[] split = colorString.split(",");

            String trim1 = split[0].trim();
            String trim2 = split[1].trim();
            String trim3 = split[2].trim();

            if (colorString.contains("%")) {
                trim1 = trim1.replace("%", "");
                trim2 = trim2.replace("%", "");
                trim3 = trim3.replace("%", "");

                red = Integer.parseInt(trim1) * 255;
                green = Integer.parseInt(trim2) * 255;
                blue = Integer.parseInt(trim3) * 255;
            } else if (colorString.contains(".")) {
                red = (int) (Float.parseFloat(trim1) * 255);
                green = (int) (Float.parseFloat(trim2) * 255);
                blue = (int) (Float.parseFloat(trim3) * 255);
            } else {
                red = Integer.parseInt(trim1);
                green = Integer.parseInt(trim2);
                blue = Integer.parseInt(trim3);
            }
        }
        else if (colorString.contains("transparent")) {
            alpha = 0;
        }
        else {
            int index = colorString.indexOf(";");
            if (index > 0) {
                colorString = colorString.substring(0, index);
            }
            colorString = colorString.replaceAll("\"", "");
            colorString = colorString.replaceAll("'", "");

            // maybe it's just a "color" description, such as "red"?
            try {
                return Color.decode(colorString);
            } catch (Exception e) {
                return null;
            }
        }

        return new Color(red, green, blue, alpha);
    }

    /**
     * @return the CSS for the current theme or null. It is important that this is called AFTER GTK has been initialized.
     */
    public static
    String getGtkThemeCss() {
        final AtomicReference<String> css = new AtomicReference<String>(null);

        Gtk.dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                Pointer settings = Gtk.gtk_settings_get_default();
                if (settings != null) {
                    PointerByReference pointer = new PointerByReference();
                    Gobject.g_object_get(settings, "gtk-theme-name", pointer, null);

                    // https://wiki.archlinux.org/index.php/GTK%2B
                    //
                    // gets the name of the currently loaded theme (can be used to get colors?)
                    //    GTK+ 2:
                    //    ~/.gtkrc-2.0
                    //    gtk-icon-theme-name = "Adwaita"
                    //    gtk-theme-name = "Adwaita"
                    //    gtk-font-name = "DejaVu Sans 11"
                    //
                    //    GTK+ 3:
                    //    $XDG_CONFIG_HOME/gtk-3.0/settings.ini
                    //    [Settings]
                    //    gtk-icon-theme-name = Adwaita
                    //    gtk-theme-name = Adwaita
                    //    gtk-font-name = DejaVu Sans 11
                    // Note: The icon theme name is the name defined in the theme's index file, not the name of its directory.

                    String themeName = null;
                    Pointer value = pointer.getValue();
                    if (value != null) {
                        themeName = value.getString(0);
                    }

                    if (themeName != null) {
                        value = Gtk3.gtk_css_provider_get_named(themeName, null);
                        if (value != null) {
                            // we have the css provider!
                            // NOTE: This can output warnings if the theme doesn't parse correctly by GTK, so we suppress them
                            Glib.GLogFunc orig = Glib.g_log_set_default_handler(Glib.nullLogFunc, null);

                            css.set(Gtk3.gtk_css_provider_to_string(value));

                            Glib.g_log_set_default_handler(orig, null);
                        }
                    }
                }
                else {
                    Pointer value = Gtk3.gtk_css_provider_get_default();
                    if (value != null) {
                        // we have the css provider!
                        // NOTE: This can output warnings if the theme doesn't parse correctly by GTK, so we suppress them
                        Glib.GLogFunc orig = Glib.g_log_set_default_handler(Glib.nullLogFunc, null);

                        css.set(Gtk3.gtk_css_provider_to_string(value));

                        Glib.g_log_set_default_handler(orig, null);
                    }
                }
            }
        });

        // will be either the string, or null.
        return css.get();
    }
}

