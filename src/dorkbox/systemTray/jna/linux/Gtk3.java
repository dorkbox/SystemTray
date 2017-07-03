/*
 * Copyright 2017 dorkbox, llc
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

import com.sun.jna.Pointer;

/**
 * bindings for GTK+ 3.
 *
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
public
class Gtk3 {
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk
    // objdump -T /usr/local/lib/libgtk-3.so.0 | grep gtk

    public static native int gtk_get_major_version();
    public static native int gtk_get_minor_version();
    public static native int gtk_get_micro_version();


    /**
     * Loads a theme from the usual theme paths
     *
     * @param name A theme name
     * @param variant variant to load, for example, "dark", or NULL for the default.
     *
     * @return a GtkCssProvider with the theme loaded. This memory is owned by GTK+, and you must not free it.
     * @since 3.0
     */
    public static native Pointer gtk_css_provider_get_named(String name, String variant);

    /**
     * Returns the provider containing the style settings used as a fallback for all widgets.
     *
     * @return a GtkCssProvider with the theme loaded. This memory is owned by GTK+, and you must not free it.
     * @since 3.0
     */
    public static native Pointer gtk_css_provider_get_default();

    /**
     * Converts the provider into a string representation in CSS format.
     *
     * Using gtk_css_provider_load_from_data() with the return value from this function on a new provider created with
     * gtk_css_provider_new() will basically create a duplicate of this provider .
     *
     * @since 3.2 (released in 2011)
     */
    public static native String gtk_css_provider_to_string(Pointer provider);

    /**
     * Gets the foreground color for a given state.
     *
     * @since 3.0
     */
    public static native void gtk_style_context_get_color(Pointer context, int stateFlags, Pointer color);

    /**
     * Returns the state used for style matching.
     *
     * @since 3.0
     */
    public static native int gtk_style_context_get_state(Pointer context);

    /**
     * Looks up and resolves a color name in the context color map.
     *
     * @since 3.0 (but not in the documentation...)
     */
    public static native boolean gtk_style_context_lookup_color(Pointer widget, String name, Pointer color);

    /**
     * Returns the style context associated to widget . The returned object is guaranteed to be the same for the lifetime of widget .
     *
     * @since 3.0 (but not in the documentation...)
     */
    public static native Pointer gtk_widget_get_style_context(Pointer widget);


    /**
     * Saves the context state, so temporary modifications done through gtk_style_context_add_class(), gtk_style_context_remove_class(),
     * gtk_style_context_set_state(), etc. can quickly be reverted in one go through gtk_style_context_restore().
     *
     * The matching call to gtk_style_context_restore() must be done before GTK returns to the main loop.
     *
     * @since 3.0
     */
    public static native void gtk_style_context_save(Pointer context);


    /**
     * Restores context state to a previous stage. See gtk_style_context_save().
     *
     * @since 3.0
     */
    public static native void gtk_style_context_restore(Pointer context);

    /**
     * Adds a style class to context , so posterior calls to gtk_style_context_get() or any of the gtk_render_*() functions will make
     * use of this new class for styling.
     *
     * @since 3.0
     */
    public static native void gtk_style_context_add_class(Pointer context, String className);

    /**
     * Gets the padding for a given state as a GtkBorder. See gtk_style_context_get() and GTK_STYLE_PROPERTY_PADDING for details.
     *
     * @since 3.0
     */
    public static native void gtk_style_context_get_padding(Pointer context, int state, Pointer border);

    /**
     * Gets the border for a given state as a GtkBorder.
     *
     * See gtk_style_context_get_property() and GTK_STYLE_PROPERTY_BORDER_WIDTH for details.
     *
     * @since 3.0
     */
    public static native void gtk_style_context_get_border(Pointer context, int state, Pointer border);

    /**
     * Returns the internal scale factor that maps from window coordinates to the actual device pixels. On traditional systems this is 1,
     * but on very high density outputs this can be a higher value (often 2).
     *
     * A higher value means that drawing is automatically scaled up to a higher resolution, so any code doing drawing will automatically
     * look nicer. However, if you are supplying pixel-based data the scale value can be used to determine whether to use a pixel
     * resource with higher resolution data.
     *
     * The scale of a window may change during runtime, if this happens a configure event will be sent to the toplevel window.
     *
     * @return the scale factor
     *
     * @since 3.10
     */
    public static native
    int gdk_window_get_scale_factor (Pointer window);
}
