package dorkbox.systemTray.util;

import dorkbox.systemTray.MenuItem;

/**
 *
 */
public
interface MenuItemHook extends EntryHook {
    void setImage(MenuItem menuItem);

    void setEnabled(MenuItem menuItem);

    void setText(MenuItem menuItem);

    void setCallback(MenuItem menuItem);

    void setShortcut(MenuItem menuItem);
}
