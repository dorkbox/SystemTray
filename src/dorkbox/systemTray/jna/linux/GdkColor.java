package dorkbox.systemTray.jna.linux;

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

    public int pixel;
    public short red;
    public short green;
    public short blue;

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
