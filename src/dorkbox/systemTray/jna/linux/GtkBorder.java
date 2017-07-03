package dorkbox.systemTray.jna.linux;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;

public
class GtkBorder extends Structure {
    public short left;
    public short right;
    public short top;
    public short bottom;

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("left", "right", "top", "bottom");
    }
}
