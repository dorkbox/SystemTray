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

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;

/**
 * https://developer.gnome.org/gdk3/stable/gdk3-Colors.html
 *
 * GdkColor has been deprecated since version 3.14 and should not be used in newly-written code.
 */
public
class GdkColor extends Structure {

    /* The color type.
     *   A color consists of red, green and blue values in the
     *    range 0-65535 and a pixel value. The pixel value is highly
     *    dependent on the depth and colormap which this color will
     *    be used to draw into. Therefore, sharing colors between
     *    colormaps is a bad idea.
     */
    public int pixel;
    public short red;
    public short green;
    public short blue;

    /**
     * Convert to positive int (value between 0 and 65535, these are 16 bits per pixel) that is from 0-255
     */
    private static int convert(int inputColor) {
        return (inputColor & 0x0000FFFF >> 8) & 0xFF;
    }

    public int red() {
        return convert(red);
    }

    public int green() {
        return convert(green);
    }

    public int blue() {
        return convert(blue);
    }

    public
    Color getColor() {
        read(); // have to read the struct members first!
        return new Color(red(), green(), blue());
    }

    @Override
    public
    String toString() {
        return "[r=" + red() + ",g=" + green() + ",b=" + blue() + "]";
    }

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("pixel", "red", "green", "blue");
    }


    public
    class ByValue extends GdkColor implements Structure.ByValue {}


    public static
    class ByReference extends GdkColor implements Structure.ByReference {}
}
