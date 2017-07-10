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

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.jna.linux.structs.GtkStyle;
import dorkbox.util.OS;
import dorkbox.util.jna.JnaHelper;

/**
 * Bindings for GTK+ 2. Bindings that are exclusively for GTK+ 3 are in that respective class
 * <p>
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
@SuppressWarnings({"Duplicates", "SameParameterValue", "DeprecatedIsStillUsed", "WeakerAccess"})
public
class Gtk {
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-x11-2.0.so.0 | grep gtk
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk
    // objdump -T /usr/local/lib/libgtk-3.so.0 | grep gtk

    // For funsies to look at, SyncThing did a LOT of work on compatibility in python (unfortunate for us, but interesting).
    // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

    @SuppressWarnings({"unused", "PointlessBitwiseExpression"})
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

    public static class IconSize {
        public static final int INVALID = 0; // Invalid size.
        public static final int MENU = 1; // Size appropriate for menus (16px).
        public static final int SMALL_TOOLBAR = 2; // Size appropriate for small toolbars (16px).
        public static final int LARGE_TOOLBAR = 3; // Size appropriate for large toolbars (24px)
        public static final int BUTTON = 4; // Size appropriate for buttons (16px)
        public static final int DND = 5; // Size appropriate for drag and drop (32px)
        public static final int DIALOG = 6; // Size appropriate for dialogs (48px)
    }


    // NOTE: AppIndicator uses this info to figure out WHAT VERSION OF appindicator to use: GTK2 -> appindicator1, GTK3 -> appindicator3
    public static final boolean isGtk2;
    public static final boolean isGtk3;
    public static final boolean isLoaded;


    public static final int FALSE = 0;
    public static final int TRUE = 1;
    static final boolean alreadyRunningGTK;

    public static Function gtk_status_icon_position_menu = null;

    public static final int MAJOR;
    public static final int MINOR;
    public static final int MICRO;

    /*
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
        int major = 0;
        int minor = 0;
        int micro = 0;

        boolean shouldLoadGtk = !(OS.isWindows() || OS.isMacOsX());
        if (!shouldLoadGtk) {
            _isLoaded = true;
        }

        // we can force the system to use the swing indicator, which WORKS, but doesn't support transparency in the icon. However, there
        // are certain GTK functions we might want to use (even if we are Swing or AWT), so we load GTK anyways...

        // in some cases, we ALWAYS want to try GTK2 first
        String gtk2LibName = "gtk-x11-2.0";
        String gtk3LibName = "libgtk-3.so.0";


        if (!_isLoaded && shouldUseGtk2) {
            try {
                NativeLibrary library = JnaHelper.register(gtk2LibName, Gtk2.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                _isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = Gtk2.gtk_main_level() != 0;
                _isLoaded = true;

                major = library.getGlobalVariableAddress("gtk_major_version").getInt(0);
                minor = library.getGlobalVariableAddress("gtk_minor_version").getInt(0);
                micro = library.getGlobalVariableAddress("gtk_micro_version").getInt(0);

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
                // ALSO map Gtk2.java to GTK3 library. Cannot have Gtk3 extend Gtk2, it won't work.
                JnaHelper.register(gtk3LibName, Gtk2.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk3LibName, "gtk_status_icon_position_menu");

                // ALSO have to load the SPECIFIC Gtk+ 3 methods. We cannot subclass because JNA doesn't like it.
                // This is BY FAR the best way to accomplish this, however because of the way static methods work, we are
                // stuck "loading it twice"
                JnaHelper.register(gtk3LibName, Gtk3.class);

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = Gtk2.gtk_main_level() != 0;
                _isLoaded = true;

                major = Gtk3.gtk_get_major_version();
                minor = Gtk3.gtk_get_minor_version();
                micro = Gtk3.gtk_get_micro_version();

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
                NativeLibrary library = JnaHelper.register(gtk2LibName, Gtk2.class);
                gtk_status_icon_position_menu = Function.getFunction(gtk2LibName, "gtk_status_icon_position_menu");
                _isGtk2 = true;

                // when running inside of JavaFX, this will be '1'. All other times this should be '0'
                // when it's '1', it means that someone else has started GTK -- so we DO NOT NEED TO.
                _alreadyRunningGTK = Gtk2.gtk_main_level() != 0;
                _isLoaded = true;

                major = library.getGlobalVariableAddress("gtk_major_version").getInt(0);
                minor = library.getGlobalVariableAddress("gtk_minor_version").getInt(0);
                micro = library.getGlobalVariableAddress("gtk_micro_version").getInt(0);

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

            MAJOR = major;
            MINOR = minor;
            MICRO = micro;
        }
        else {
            isLoaded = false;

            alreadyRunningGTK = false;
            isGtk2 = false;
            isGtk3 = false;

            MAJOR = 0;
            MINOR = 0;
            MICRO = 0;
        }

        if (shouldLoadGtk) {
            // now we output what version of GTK we have loaded.
            if (SystemTray.DEBUG) {
                SystemTray.logger.debug("GTK Version: " + MAJOR + "." + MINOR + "." + MICRO);
            }

            if (!_isLoaded) {
                throw new RuntimeException("We apologize for this, but we are unable to determine the GTK library is in use, " +
                                           "or even if it is in use... Please create an issue for this and include your OS type and configuration.");
            }
        }
    }

    /**
     * Creates a new GtkMenu
     */
    public static
    Pointer gtk_menu_new() {
        return Gtk2.gtk_menu_new();
    }

    /**
     * Sets or replaces the menu item’s submenu, or removes it when a NULL submenu is passed.
     */
    public static
    void gtk_menu_item_set_submenu(Pointer menuEntry, Pointer menu) {
        Gtk2.gtk_menu_item_set_submenu(menuEntry, menu);
    }

    /**
     * Creates a new GtkSeparatorMenuItem.
     */
    public static
    Pointer gtk_separator_menu_item_new() {
        return Gtk2.gtk_separator_menu_item_new();
    }

    /**
     * Creates a new GtkImage displaying the file filename . If the file isn’t found or can’t be loaded, the resulting GtkImage will
     * display a “broken image” icon. This function never returns NULL, it always returns a valid GtkImage widget.
     * <p>
     * If the file contains an animation, the image will contain an animation.
     */
    public static
    Pointer gtk_image_new_from_file(String iconPath) {
        return Gtk2.gtk_image_new_from_file(iconPath);
    }

    /**
     * Sets the active state of the menu item’s check box.
     */
    public static
    void gtk_check_menu_item_set_active(Pointer check_menu_item, boolean isChecked) {
        Gtk2.gtk_check_menu_item_set_active(check_menu_item, isChecked);
    }

    /**
     * Creates a new GtkImageMenuItem containing a label. The label will be created using gtk_label_new_with_mnemonic(), so underscores
     * in label indicate the mnemonic for the menu item.
     * <p>
     * uses '_' to define which key is the mnemonic
     * <p>
     * gtk_image_menu_item_new_with_mnemonic has been deprecated since version 3.10 and should not be used in newly-written code.
     * NOTE: Use gtk_menu_item_new_with_mnemonic() instead.
     */
    public static
    Pointer gtk_image_menu_item_new_with_mnemonic(String label) {
        return Gtk2.gtk_image_menu_item_new_with_mnemonic(label);
    }

    public static
    Pointer gtk_check_menu_item_new_with_mnemonic(String label) {
        return Gtk2.gtk_check_menu_item_new_with_mnemonic(label);
    }

    /**
     * Sets the image of image_menu_item to the given widget. Note that it depends on the show-menu-images setting whether the image
     * will be displayed or not.
     * <p>
     * gtk_image_menu_item_set_image has been deprecated since version 3.10 and should not be used in newly-written code.
     */
    public static
    void gtk_image_menu_item_set_image(Pointer image_menu_item, Pointer image) {
        Gtk2.gtk_image_menu_item_set_image(image_menu_item, image);
    }

    /**
     * If TRUE, the menu item will ignore the “gtk-menu-images” setting and always show the image, if available.
     * Use this property if the menuitem would be useless or hard to use without the image
     * <p>
     * gtk_image_menu_item_set_always_show_image has been deprecated since version 3.10 and should not be used in newly-written code.
     */
    public static
    void gtk_image_menu_item_set_always_show_image(Pointer menu_item, boolean forceShow) {
        Gtk2.gtk_image_menu_item_set_always_show_image(menu_item, forceShow);
    }

    /**
     * Creates an empty status icon object.
     * <p>
     * gtk_status_icon_new has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    public static
    Pointer gtk_status_icon_new() {
        return Gtk2.gtk_status_icon_new();
    }

    /**
     * Obtains the root window (parent all other windows are inside) for the default display and screen.
     *
     * @return the default root window
     */
    public static
    Pointer gdk_get_default_root_window() {
        return Gtk2.gdk_get_default_root_window();
    }

    /**
     * Gets the default screen for the default display. (See gdk_display_get_default()).
     *
     * @return a GdkScreen, or NULL if there is no default display.
     *
     * @since 2.2
     */
    public static
    Pointer gdk_screen_get_default() {
        return Gtk2.gdk_screen_get_default();
    }

    /**
     * Gets the resolution for font handling on the screen; see gdk_screen_set_resolution() for full details.
     *
     * IE:
     *
     * The resolution for font handling on the screen. This is a scale factor between points specified in a PangoFontDescription and
     * cairo units. The default value is 96, meaning that a 10 point font will be 13 units high. (10 * 96. / 72. = 13.3).
     *
     * @return the current resolution, or -1 if no resolution has been set.
     *
     * @since Since: 2.10
     */
    public static
    double gdk_screen_get_resolution(Pointer screen) {
        return Gtk2.gdk_screen_get_resolution(screen);
    }

    /**
     * Makes status_icon display the file filename . See gtk_status_icon_new_from_file() for details.
     * <p>
     * gtk_status_icon_set_from_file has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    public static
    void gtk_status_icon_set_from_file(Pointer widget, String label) {
        Gtk2.gtk_status_icon_set_from_file(widget, label);
    }

    /**
     * Shows or hides a status icon.
     * <p>
     * gtk_status_icon_set_visible has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    public static
    void gtk_status_icon_set_visible(Pointer widget, boolean visible) {
        Gtk2.gtk_status_icon_set_visible(widget, visible);
    }


    /**
     * Sets text as the contents of the tooltip.
     * This function will take care of setting “has-tooltip” to TRUE and of the default handler for the “query-tooltip” signal.
     *
     * app indicators don't support this
     *
     * gtk_status_icon_set_tooltip_text has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    public static
    void gtk_status_icon_set_tooltip_text(Pointer widget, String tooltipText) {
        Gtk2.gtk_status_icon_set_tooltip_text(widget, tooltipText);
    }

    /**
     * Sets the title of this tray icon. This should be a short, human-readable, localized string describing the tray icon. It may be used
     * by tools like screen readers to render the tray icon.
     * <p>
     * gtk_status_icon_set_title has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    public static
    void gtk_status_icon_set_title(Pointer widget, String titleText) {
        Gtk2.gtk_status_icon_set_title(widget, titleText);
    }

    /**
     * Sets the name of this tray icon. This should be a string identifying this icon. It is may be used for sorting the icons in the
     * tray and will not be shown to the user.
     * <p>
     * gtk_status_icon_set_name has been deprecated since version 3.14 and should not be used in newly-written code.
     * Use notifications
     */
    public static
    void gtk_status_icon_set_name(Pointer widget, String name) {
        Gtk2.gtk_status_icon_set_name(widget, name);
    }

    /**
     * Displays a menu and makes it available for selection.
     * <p>
     * gtk_menu_popup has been deprecated since version 3.22 and should not be used in newly-written code.
     * NOTE: Please use gtk_menu_popup_at_widget(), gtk_menu_popup_at_pointer(). or gtk_menu_popup_at_rect() instead
     */
    public static
    void gtk_menu_popup(Pointer menu, Pointer widget, Pointer bla, Function func, Pointer data, int button, int time) {
        Gtk2.gtk_menu_popup(menu, widget, bla, func, data, button, time);
    }

    /**
     * Sets text on the menu_item label
     */
    public static
    void gtk_menu_item_set_label(Pointer menu_item, String label) {
        Gtk2.gtk_menu_item_set_label(menu_item, label);
    }

    /**
     * Adds a new GtkMenuItem to the end of the menu shell's item list.
     */
    public static
    void gtk_menu_shell_append(Pointer menu_shell, Pointer child) {
        Gtk2.gtk_menu_shell_append(menu_shell, child);
    }

    /**
     * Sets the sensitivity of a widget. A widget is sensitive if the user can interact with it. Insensitive widgets are “grayed out”
     * and the user can’t interact with them. Insensitive widgets are known as “inactive”, “disabled”, or “ghosted” in some other toolkits.
     */
    public static
    void gtk_widget_set_sensitive(Pointer widget, boolean sensitive) {
        Gtk2.gtk_widget_set_sensitive(widget, sensitive);
    }

    /**
     * Recursively shows a widget, and any child widgets (if the widget is a container)
     */
    public static
    void gtk_widget_show_all(Pointer widget) {
        Gtk2.gtk_widget_show_all(widget);
    }

    /**
     * Removes widget from container . widget must be inside container . Note that container will own a reference to widget , and that
     * this may be the last reference held; so removing a widget from its container can destroy that widget.
     * <p>
     * If you want to use widget again, you need to add a reference to it before removing it from a container, using g_object_ref().
     * If you don’t want to use widget again it’s usually more efficient to simply destroy it directly using gtk_widget_destroy()
     * since this will remove it from the container and help break any circular reference count cycles.
     */
    public static
    void gtk_container_remove(Pointer parentWidget, Pointer widget) {
        Gtk2.gtk_container_remove(parentWidget, widget);
    }

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
    public static
    void gtk_widget_destroy(Pointer widget) {
        Gtk2.gtk_widget_destroy(widget);
    }

    /**
     * Gets the GtkSettings object for screen , creating it if necessary.
     *
     * @since 2.2
     */
    public static
    Pointer gtk_settings_get_for_screen(Pointer screen) {
        return Gtk2.gtk_settings_get_for_screen(screen);
    }

    /**
     * Simply an accessor function that returns @widget->style.
     */
    public static
    GtkStyle.ByReference gtk_widget_get_style(Pointer widget) {
        return Gtk2.gtk_widget_get_style(widget);
    }

    /**
     * Finds all matching RC styles for a given widget, composites them together, and then creates a GtkStyle representing the composite
     * appearance. (GTK+ actually keeps a cache of previously created styles, so a new style may not be created.)
     */
    public static
    Pointer gtk_rc_get_style(Pointer widget)  {
        return Gtk2.gtk_rc_get_style(widget);
    }

    /**
     * Looks up color_name in the style’s logical color mappings, filling in color and returning TRUE if found, otherwise returning
     * FALSE. Do not cache the found mapping, because it depends on the GtkStyle and might change when a theme switch occurs.
     *
     * @since 2.10
     */
    public static
    boolean gtk_style_lookup_color(Pointer widgetStyle, String color_name, Pointer color)  {
        return Gtk2.gtk_style_lookup_color(widgetStyle, color_name, color);
    }

    /**
     * Adds widget to container . Typically used for simple containers such as GtkWindow, GtkFrame, or GtkButton; for more complicated
     * layout containers such as GtkBox or GtkTable, this function will pick default packing parameters that may not be correct. So
     * consider functions such as gtk_box_pack_start() and gtk_table_attach() as an alternative to gtk_container_add() in those cases.
     * A widget may be added to only one container at a time; you can't place the same widget inside two different containers.
     */
    public static
    void gtk_container_add(Pointer offscreen, Pointer widget)  {
        Gtk2.gtk_container_add(offscreen, widget);
    }

    /**
     * Get's the child from a GTK Bin object
     */
    public static
    Pointer gtk_bin_get_child(Pointer bin)  {
        return Gtk2.gtk_bin_get_child(bin);
    }

    /**
     * Gets the PangoLayout used to display the label. The layout is useful to e.g. convert text positions to pixel positions, in
     * combination with gtk_label_get_layout_offsets(). The returned layout is owned by the label so need not be freed by the caller.
     *
     * The label is free to recreate its layout at any time, so it should be considered read-only.
     */
    public static
    Pointer gtk_label_get_layout(Pointer label)  {
        return Gtk2.gtk_label_get_layout(label);
    }

    /**
     * Computes the logical and ink extents of layout in device units. This function just calls pango_layout_get_extents() followed
     * by two pango_extents_to_pixels() calls, rounding ink_rect and logical_rect such that the rounded rectangles fully contain the
     * unrounded one (that is, passes them as first argument to pango_extents_to_pixels()).
     *
     * @param layout a PangoLayout
     * @param ink_rect rectangle used to store the extents of the layout as drawn or NULL to indicate that the result is not needed.
     * @param logical_rect rectangle used to store the logical extents of the layout or NULL to indicate that the result is not needed.
     */
    public static
    void pango_layout_get_pixel_extents(Pointer layout, Pointer ink_rect, Pointer logical_rect)  {
        Gtk2.pango_layout_get_pixel_extents(layout, ink_rect, logical_rect);
    }

    /**
     * Creates a toplevel container widget that is used to retrieve snapshots of widgets without showing them on the screen.
     *
     * @since 2.20
     */
    public static
    Pointer	gtk_offscreen_window_new()  {
        return Gtk2.gtk_offscreen_window_new();
    }

    /**
     * This function is typically used when implementing a GtkContainer subclass. Obtains the preferred size of a widget. The
     * container uses this information to arrange its child widgets and decide what size allocations to give them with
     * gtk_widget_size_allocate().
     *
     * You can also call this function from an application, with some caveats. Most notably, getting a size request requires the
     * widget to be associated with a screen, because font information may be needed. Multihead-aware applications should keep this in mind.
     *
     * Also remember that the size request is not necessarily the size a widget will actually be allocated.
     */
    public static
    void gtk_widget_size_request(final Pointer widget, final Pointer requisition) {
        if (isGtk2) {
            Gtk2.gtk_widget_size_request(widget, requisition);
        }
        else {
            Gtk3.gtk_widget_get_preferred_size(widget, requisition, null);
        }
    }

    /**
     * Creates a new GtkImageMenuItem containing the image and text from a stock item. Some stock ids have preprocessor macros
     * like GTK_STOCK_OK and GTK_STOCK_APPLY.
     *
     * @param stock_id the name of the stock item.
     * @param accel_group the GtkAccelGroup to add the menu items accelerator to, or NULL.
     *
     * @return a new GtkImageMenuItem.
     */
    public static
    Pointer gtk_image_menu_item_new_from_stock(String stock_id, Pointer accel_group) {
        return Gtk2.gtk_image_menu_item_new_from_stock(stock_id, accel_group);
    }
}

