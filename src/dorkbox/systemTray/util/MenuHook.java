package dorkbox.systemTray.util;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;

/**
 *
 */
public
interface MenuHook extends MenuItemHook {
    void add(Menu parentMenu, Entry entry, int index);
}
