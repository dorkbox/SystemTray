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
package dorkbox.systemTray.jna.linux.structs;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import dorkbox.util.Keep;

@Keep
public
class GtkStyle extends Structure {
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

    public static
    class ByReference extends GtkStyle implements Structure.ByReference {
    }

    public
    class ByValue extends GtkStyle implements Structure.ByValue {
    }

    // required, even though it's "private" in the corresponding C code. OTHERWISE the memory offsets are INCORRECT.
    public GObjectStruct parent_instance;

    /** fg: foreground for drawing GtkLabel */
    public GdkColor fg[] = new GdkColor[5];

    /** bg: the usual background color, gray by default */
    public GdkColor bg[] = new GdkColor[5];

    public GdkColor light[] = new GdkColor[5];
    public GdkColor dark[] = new GdkColor[5];
    public GdkColor mid[] = new GdkColor[5];

    /**
     * text: text for entries and text widgets (although in GTK 1.2 sometimes fg gets used, this is more or less a bug and fixed in GTK 2.0).
     */
    public GdkColor text[] = new GdkColor[5];

    /** base: background when using text, colored white in the default theme. */
    public GdkColor base[] = new GdkColor[5];

    /** Halfway between text/base */
    public GdkColor text_aa[] = new GdkColor[5];

    public GdkColor black;
    public GdkColor white;
    public Pointer /*PangoFontDescription*/ font_desc;
    public int xthickness;
    public int ythickness;
    public Pointer /*cairo_pattern_t*/  background[] = new Pointer[5];

    public
    void debug(final int gtkState) {
        System.err.println("base " + base[gtkState].getColor());
        System.err.println("text " + text[gtkState].getColor());
        System.err.println("text_aa " + text_aa[gtkState].getColor());
        System.err.println("bg " + bg[gtkState].getColor());
        System.err.println("fg " + fg[gtkState].getColor());
    }


    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("parent_instance",
                             "fg",
                             "bg",
                             "light",
                             "dark",
                             "mid",
                             "text",
                             "base",
                             "text_aa",
                             "black",
                             "white",
                             "font_desc",
                             "xthickness",
                             "ythickness",
                             "background");
    }
}
