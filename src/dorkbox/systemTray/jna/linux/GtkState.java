package dorkbox.systemTray.jna.linux;

/**
 *
 */
@SuppressWarnings({"unused", "PointlessBitwiseExpression"})
public
class GtkState {
    public static final int NORMAL = 0x0; // normal state.
    public static final int ACTIVE = 0x1; // pressed-in or activated; e.g. buttons while the mouse button is held down.
    public static final int PRELIGHT = 0x2; // color when the mouse is over an activatable widget.
    public static final int SELECTED = 0x3; // color when something is selected, e.g. when selecting some text to cut/copy.
    public static final int INSENSITIVE = 0x4; // color when the mouse is over an activatable widget.

    public static final int FLAG_NORMAL = 0;
    public static final int FLAG_ACTIVE = 1 << 0;
    public static final int FLAG_PRELIGHT = 1 << 1;
    public static final int FLAG_SELECTED = 1 << 2;
    public static final int FLAG_INSENSITIVE = 1 << 3;
    public static final int FLAG_INCONSISTENT = 1 << 4;
    public static final int FLAG_FOCUSED = 1 << 5;
    public static final int FLAG_BACKDROP = 1 << 6;
}
