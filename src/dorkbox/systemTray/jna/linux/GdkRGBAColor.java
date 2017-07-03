package dorkbox.systemTray.jna.linux;

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
