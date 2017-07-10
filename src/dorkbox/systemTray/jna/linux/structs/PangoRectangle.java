package dorkbox.systemTray.jna.linux.structs;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;

/**
 * https://developer.gnome.org/pango/stable/pango-Glyph-Storage.html#PangoRectangle
 */
public
class PangoRectangle extends Structure {

    public int x;
    public int y;
    public int width;
    public int height;

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("x", "y", "width", "height");
    }
}
