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
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import dorkbox.util.Keep;

import java.util.Arrays;
import java.util.List;

public
interface Gtk extends Library {

    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-x11-2.0.so.0 | grep gtk
    // objdump -T /usr/lib/x86_64-linux-gnu/libgtk-3.so.0 | grep gtk
    Gtk INSTANCE = GtkSupport.get();
    Function gtk_status_icon_position_menu = GtkSupport.gtk_status_icon_position_menu;

    int FALSE = 0;
    int TRUE = 1;


    @Keep
    class GdkEventButton extends Structure {
        public int type;
        public Pointer window;
        public int send_event;
        public int time;
        public double x;
        public double y;
        public Pointer axes;
        public int state;
        public int button;
        public Pointer device;
        public double x_root;
        public double y_root;

        @Override
        protected
        List<String> getFieldOrder() {
            return Arrays.asList("type", "window", "send_event", "time", "x", "y", "axes", "state", "button", "device", "x_root", "y_root");
        }
    }

    boolean gtk_init_check(int argc, String[] argv);

    /**
     * Runs the main loop until gtk_main_quit() is called. You can nest calls to gtk_main(). In that case gtk_main_quit() will make the
     * innermost invocation of the main loop return.
     */
    void gtk_main();


    /** sks for the current nesting level of the main loop. Useful to determine (at startup) if GTK is already runnign */
    int gtk_main_level();

    /**
     * Makes the innermost invocation of the main loop return when it regains control. ONLY CALL FROM THE GtkSupport class, UNLESS you know
     * what you're doing!
     */
    void gtk_main_quit();

    void gdk_threads_init();

    // tricky business. This should only be in the dispatch thread
    void gdk_threads_enter();
    void gdk_threads_leave();

    Pointer gtk_menu_new();

    Pointer gtk_menu_item_new();

    Pointer gtk_menu_item_new_with_label(String label);

    // to create a menu entry WITH an icon.
    Pointer gtk_image_new_from_file(String iconPath);


    Pointer gtk_image_menu_item_new_with_label(String label);

    void gtk_image_menu_item_set_image(Pointer image_menu_item, Pointer image);

    void gtk_image_menu_item_set_always_show_image(Pointer menu_item, int forceShow);

    Pointer gtk_bin_get_child(Pointer parent);

    void gtk_label_set_text(Pointer label, String text);

    void gtk_label_set_markup(Pointer label, Pointer markup);

    void gtk_label_set_use_markup(Pointer label, int gboolean);

    Pointer gtk_status_icon_new();

    void gtk_status_icon_set_from_file(Pointer widget, String lablel);

    void gtk_status_icon_set_visible(Pointer widget, boolean visible);

    // app indicators don't support this, and we cater to the lowest common denominator
//    void gtk_status_icon_set_tooltip(Pointer widget, String tooltipText);

    void gtk_status_icon_set_title(Pointer widget, String titleText);

    void gtk_status_icon_set_name(Pointer widget, String name);

    void gtk_menu_popup(Pointer menu, Pointer widget, Pointer bla, Function func, Pointer data, int button, int time);

    void gtk_menu_item_set_label(Pointer menu_item, String label);

    void gtk_menu_shell_append(Pointer menu_shell, Pointer child);

    void gtk_menu_shell_deactivate(Pointer menu_shell, Pointer child);

    void gtk_widget_set_sensitive(Pointer widget, int sensitive);

    void gtk_container_remove(Pointer menu, Pointer subItem);

    void gtk_widget_show(Pointer widget);

    void gtk_widget_show_all(Pointer widget);

    void gtk_widget_destroy(Pointer widget);
}

