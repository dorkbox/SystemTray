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
 * https://developer.gnome.org/gdk3/stable/gdk3-RGBA-Colors.html#GdkRGBA
 */
public
class GdkRGBAColor extends Structure {

    // these are from 0.0 to 1.0 inclusive
    public double red;
    public double green;
    public double blue;
    public double alpha;

    public float red() {
        return (float) red;
    }

    public float green() {
        return (float) green;
    }

    public float blue() {
        return (float) blue;
    }

    public
    Color getColor() {
        read(); // have to read the struct members first!
        return new Color(red(), green(), blue());
    }


    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("red", "green", "blue", "alpha");
    }


    public
    class ByValue extends GdkRGBAColor implements Structure.ByValue {}


    public
    class ByReference extends GdkRGBAColor implements Structure.ByReference {}
}
