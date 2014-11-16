package dorkbox.util.tray.linux;

import com.sun.jna.Pointer;

/**
 * Can only access this from within a synchronized block!
 */
class MenuEntry {

  private final int hashCode;
  public Pointer dashboardItem;

  public MenuEntry() {
    long time = System.nanoTime();
    this.hashCode = (int) (time ^ time >>> 32);
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
    return this.hashCode == other.hashCode;
  }
}
