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

import com.sun.jna.Function;
import com.sun.jna.Pointer;

import dorkbox.systemTray.jna.linux.structs.GtkStyle;

/**
 * Bindings for GTK+ 2. Bindings that are exclusively for GTK+ 3 are in that respective class
 * <p>
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
public
class Gtk2 implements Gtk {
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-x11-2.0.so.0 | grep gtk

    @Override
    public native
    int gdk_threads_add_idle_full(final int priority, final FuncCallback function, final Pointer data, final Pointer notify);

    @Override
    public native
    boolean gtk_init_check(final int argc);

    @Override
    public native
    void gtk_main();

    @Override
    public native
    int gtk_main_level();

    @Override
    public native
    void gtk_main_quit();

    @Override
    public native
    Pointer gtk_menu_new();

    @Override
    public native
    void gtk_menu_item_set_submenu(final Pointer menuEntry, final Pointer menu);

    @Override
    public native
    Pointer gtk_separator_menu_item_new();

    @Override
    public native
    Pointer gtk_image_new_from_file(final String iconPath);

    @Override
    public native
    void gtk_check_menu_item_set_active(final Pointer check_menu_item, final boolean isChecked);

    @Override
    public native
    Pointer gtk_image_menu_item_new_with_mnemonic(final String label);

    @Override
    public native
    Pointer gtk_check_menu_item_new_with_mnemonic(final String label);

    @Override
    public native
    void gtk_image_menu_item_set_image(final Pointer image_menu_item, final Pointer image);

    @Override
    public native
    void gtk_image_menu_item_set_always_show_image(final Pointer menu_item, final boolean forceShow);

    @Override
    public native
    Pointer gtk_status_icon_new();

    @Override
    public native
    Pointer gdk_get_default_root_window();

    @Override
    public native
    Pointer gdk_screen_get_default();

    @Override
    public native
    double gdk_screen_get_resolution(final Pointer screen);

    @Override
    public native
    void gtk_status_icon_set_from_file(final Pointer widget, final String label);

    @Override
    public native
    void gtk_status_icon_set_visible(final Pointer widget, final boolean visible);

    @Override
    public native
    void gtk_status_icon_set_tooltip_text(final Pointer widget, final String tooltipText);

    @Override
    public native
    void gtk_status_icon_set_title(final Pointer widget, final String titleText);

    @Override
    public native
    void gtk_status_icon_set_name(final Pointer widget, final String name);

    @Override
    public native
    void gtk_menu_popup(final Pointer menu,
                        final Pointer widget,
                        final Pointer bla,
                        final Function func,
                        final Pointer data,
                        final int button,
                        final int time);

    @Override
    public native
    void gtk_menu_item_set_label(final Pointer menu_item, final String label);

    @Override
    public native
    void gtk_menu_shell_append(final Pointer menu_shell, final Pointer child);

    @Override
    public native
    void gtk_widget_set_sensitive(final Pointer widget, final boolean sensitive);

    @Override
    public native
    void gtk_widget_show_all(final Pointer widget);

    @Override
    public native
    void gtk_container_remove(final Pointer parentWidget, final Pointer widget);

    @Override
    public native
    void gtk_widget_destroy(final Pointer widget);

    @Override
    public native
    Pointer gtk_settings_get_for_screen(final Pointer screen);

    @Override
    public native
    GtkStyle gtk_rc_get_style(final Pointer widget);

    @Override
    public native
    void gtk_container_add(final Pointer offscreen, final Pointer widget);

    @Override
    public native
    Pointer gtk_bin_get_child(final Pointer bin);

    @Override
    public native
    Pointer gtk_label_get_layout(final Pointer label);

    @Override
    public native
    void pango_layout_get_pixel_extents(final Pointer layout, final Pointer ink_rect, final Pointer logical_rect);

    @Override
    public native
    void gtk_widget_realize(final Pointer widget);

    @Override
    public native
    Pointer gtk_offscreen_window_new();

    @Override
    public native
    void gtk_widget_size_request(final Pointer widget, final Pointer requisition);

    @Override
    public native
    Pointer gtk_image_menu_item_new_from_stock(final String stock_id, final Pointer accel_group);
}
