package dorkbox.systemTray.jna.linux;

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
