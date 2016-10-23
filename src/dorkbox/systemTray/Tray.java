package dorkbox.systemTray;

import dorkbox.systemTray.util.Status;

/**
 *
 */
public
class Tray extends Menu {

    // appindicators DO NOT support anything other than PLAIN gtk-menus (which we hack to support swing menus)
    //   they ALSO do not support tooltips, so we cater to the lowest common denominator
    // trayIcon.setToolTip("app name");

    private volatile String statusText;

    public
    Tray() {
        super();
    }

    /**
     * Gets the 'status' string assigned to the system tray
     */
    public final
    String getStatus() {
        return statusText;
    }

    /**
     * Sets a 'status' string at the first position in the popup menu. This 'status' string appears as a disabled menu entry.
     *
     * @param statusText the text you want displayed, null if you want to remove the 'status' string
     */
    public final
    void setStatus(final String statusText) {
        this.statusText = statusText;

        synchronized (menuEntries) {
            // status is ALWAYS at 0 index...
            Entry menuEntry = null;
            if (!menuEntries.isEmpty()) {
                menuEntry = menuEntries.get(0);
            }

            if (menuEntry instanceof Status) {
                // set the text or delete...

                if (statusText == null) {
                    // delete
                    remove(menuEntry);
                }
                else {
                    // set text
                    ((Status) menuEntry).setText(statusText);
                }
            } else {
                // create a new one
                Status status = new Status();
                status.setText(statusText);

                // status is ALWAYS at 0 index...
                // also calls the hook to add it, so we don't need anything special
                add(status, 0);
            }
        }
    }
}
