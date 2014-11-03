package dorkbox.util.tray.linux;

import com.sun.jna.Pointer;

import dorkbox.util.jna.linux.Gobject;

/**
 * Can only access this from within a synchronized block!
 */
class MenuEntry {
    public final int         hashCode;
    public Pointer           dashboardItem;
    public Gobject.GCallback gtkCallback;

    public MenuEntry() {
        long time = System.nanoTime();
        this.hashCode = (int)(time^time>>>32);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MenuEntry other = (MenuEntry) obj;
        if (this.hashCode != other.hashCode) {
            return false;
        }
        return true;
    }
}