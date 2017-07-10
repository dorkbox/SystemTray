package dorkbox.systemTray.jna.windows;

import com.sun.jna.IntegerType;
import com.sun.jna.Pointer;

public
class Parameter extends IntegerType {
    public
    Parameter() {
        this(0);
    }

    public
    Parameter(long value) {
        super(Pointer.SIZE, value);
    }
}
