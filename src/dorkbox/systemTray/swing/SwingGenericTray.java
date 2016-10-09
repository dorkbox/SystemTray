package dorkbox.systemTray.swing;

import javax.swing.JComponent;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.util.ImageUtils;

/**
 *
 */
public abstract
class SwingGenericTray extends SwingMenu {
    /**
     * Called in the EDT
     *
     * @param systemTray
     *                 the system tray (which is the object that sits in the system tray)
     * @param parent
     * @param _native
     */
    SwingGenericTray(final SystemTray systemTray, final Menu parent, final JComponent _native) {
        super(systemTray, parent, _native);

        ImageUtils.determineIconSize();
    }

    public
    String getStatus() {
        synchronized (menuEntries) {
            MenuEntry menuEntry = menuEntries.get(0);
            if (menuEntry instanceof SwingEntryStatus) {
                return menuEntry.getText();
            }
        }

        return null;
    }

    public
    void setStatus(final String statusText) {
        final SwingMenu _this = this;
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    // status is ALWAYS at 0 index...
                    SwingEntry menuEntry = null;
                    if (!menuEntries.isEmpty()) {
                        menuEntry = (SwingEntry) menuEntries.get(0);
                    }

                    if (menuEntry instanceof SwingEntryStatus) {
                        // set the text or delete...

                        if (statusText == null) {
                            // delete
                            remove(menuEntry);
                        }
                        else {
                            // set text
                            menuEntry.setText(statusText);
                        }

                    } else {
                        // create a new one
                        menuEntry = new SwingEntryStatus(_this, statusText);
                        // status is ALWAYS at 0 index...
                        menuEntries.add(0, menuEntry);
                    }
                }
            }
        });
    }
}
