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
 * <p>
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
public
class Gtk3 {
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk
    // objdump -T /usr/local/lib/libgtk-3.so.0 | grep gtk

    public static native
    int gtk_get_major_version();

    public static native
    int gtk_get_minor_version();

    public static native
    int gtk_get_micro_version();

    /**
     * Returns the internal scale factor that maps from window coordinates to the actual device pixels. On traditional systems this is 1,
     * but on very high density outputs this can be a higher value (often 2).
     * <p>
     * A higher value means that drawing is automatically scaled up to a higher resolution, so any code doing drawing will automatically
     * look nicer. However, if you are supplying pixel-based data the scale value can be used to determine whether to use a pixel
     * resource with higher resolution data.
     * <p>
     * The scale of a window may change during runtime, if this happens a configure event will be sent to the toplevel window.
     *
     * @return the scale factor
     *
     * @since 3.10
     */
    public static native
    int gdk_window_get_scale_factor(Pointer window);

    /**
     * Retrieves the minimum and natural size of a widget, taking into account the widget’s preference for height-for-width management.
     * <p>
     * This is used to retrieve a suitable size by container widgets which do not impose any restrictions on the child placement.
     * It can be used to deduce toplevel window and menu sizes as well as child widgets in free-form containers such as GtkLayout.
     * <p>
     * Handle with care. Note that the natural height of a height-for-width widget will generally be a smaller size than the minimum
     * height, since the required height for the natural width is generally smaller than the required height for the minimum width.
     * <p>
     * Use gtk_widget_get_preferred_height_and_baseline_for_width() if you want to support baseline alignment.
     *
     * @param widget a GtkWidget instance
     * @param minimum_size location for storing the minimum size, or NULL.
     * @param natural_size location for storing the natural size, or NULL.
     */
    public static native
    void gtk_widget_get_preferred_size(final Pointer widget, final Pointer minimum_size, final Pointer natural_size);
}
