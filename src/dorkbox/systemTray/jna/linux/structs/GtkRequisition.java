package dorkbox.systemTray.jna.linux.structs;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;

/**
 * https://developer.gimp.org/api/2.0/gtk/GtkWidget.html#GtkRequisition
 */
public
class GtkRequisition extends Structure {

    public int width;
    public int height;

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("width", "height");
    }
}
