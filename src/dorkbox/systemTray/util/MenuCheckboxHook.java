package dorkbox.systemTray.util;

import dorkbox.systemTray.Checkbox;

/**
 *
 */
public
interface MenuCheckboxHook extends EntryHook {

    void setEnabled(Checkbox menuItem);

    void setText(Checkbox menuItem);

    void setCallback(Checkbox menuItem);

    void setShortcut(Checkbox menuItem);

    void setChecked(Checkbox checkbox);
}
