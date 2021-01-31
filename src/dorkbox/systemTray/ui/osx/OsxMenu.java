/*
 * Copyright 2021 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.systemTray.ui.osx;

import dorkbox.jna.macos.cocoa.NSCellStateValue;
import dorkbox.jna.macos.cocoa.NSImage;
import dorkbox.jna.macos.cocoa.NSInteger;
import dorkbox.jna.macos.cocoa.NSMenu;
import dorkbox.jna.macos.cocoa.NSMenuItem;
import dorkbox.jna.macos.cocoa.NSString;
import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.peer.MenuPeer;
import dorkbox.systemTray.util.SizeAndScalingUtil;

class OsxMenu implements MenuPeer {
    // the native OSX components
    protected final OsxMenu parent;
    protected final NSMenuItem _native = new NSMenuItem();
    volatile NSMenu _nativeMenu;

    // to prevent GC
    private volatile NSImage image;
    private NSString tooltip;
    private NSString title;
    private NSString keyEquivalent;

    @SuppressWarnings("FieldCanBeLocal")
    private final NSInteger indentationLevel = new NSInteger(1);

    // called by the system tray constructors
    // This is NOT a copy constructor!
    @SuppressWarnings("IncompleteCopyConstructor")
    OsxMenu() {
        this.parent = null;
    }

    OsxMenu(final OsxMenu parent) {
        this.parent = parent;
        _nativeMenu = new NSMenu();

        _native.setSubmenu(_nativeMenu);
        parent.addItem(_native);

        // this is to provide reasonable spacing for the menu item, otherwise it looks weird
        _native.setIndentationLevel(indentationLevel);
        _native.setImage(OsxBaseMenuItem.getTransparentIcon(SizeAndScalingUtil.TRAY_MENU_SIZE));
    }

    @Override
    public
    void add(final Menu parentMenu, final Entry entry, final int index) {
        if (entry instanceof Menu) {
            OsxMenu menu = new OsxMenu(OsxMenu.this);
            ((Menu) entry).bind(menu, parentMenu, parentMenu.getImageResizeUtil());
        }
        else if (entry instanceof Separator) {
            OsxMenuItemSeparator item = new OsxMenuItemSeparator(OsxMenu.this);
            entry.bind(item, parentMenu, parentMenu.getImageResizeUtil());
        }
        else if (entry instanceof Checkbox) {
            OsxMenuItemCheckbox item = new OsxMenuItemCheckbox(OsxMenu.this);
            ((Checkbox) entry).bind(item, parentMenu, parentMenu.getImageResizeUtil());
        }
        else if (entry instanceof Status) {
            OsxMenuItemStatus item = new OsxMenuItemStatus(OsxMenu.this);
            ((Status) entry).bind(item, parentMenu, parentMenu.getImageResizeUtil());
        }
        else if (entry instanceof MenuItem) {
            OsxMenuItem item = new OsxMenuItem(OsxMenu.this);
            ((MenuItem) entry).bind(item, parentMenu, parentMenu.getImageResizeUtil());
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setImage(final MenuItem menuItem) {
        if (menuItem.getImage() != null) {
            _native.setState(NSCellStateValue.NSOnState);

            image = new NSImage(menuItem.getImage());
            _native.setOnStateImage(image);

        }
        else {
            _native.setState(NSCellStateValue.NSOffState);
            _native.setOnStateImage(null);
        }
    }

    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        _native.setEnabled(menuItem.getEnabled());
    }

    @Override
    public
    void setText(final MenuItem menuItem) {
        String text = menuItem.getText();
        if (text == null || text.isEmpty()) {
            title = null;
        }
        else {
            title = new NSString(text);
        }

        _native.setTitle(title);
    }

    @Override
    public
    void setCallback(final MenuItem menuItem) {
        // no op
    }

    @Override
    public
    void setShortcut(final MenuItem menuItem) {
        char shortcut = menuItem.getShortcut();

        if (shortcut != 0) {
            keyEquivalent = new NSString(Character.toString(shortcut).toLowerCase());
        } else {
            keyEquivalent = new NSString("");
        }

        _native.setKeyEquivalent(keyEquivalent);
    }

    @Override
    public
    void setTooltip(final MenuItem menuItem) {
        String tooltip = menuItem.getTooltip();
        if (tooltip == null || tooltip.isEmpty()) {
            this.tooltip = null;
        }
        else {
            this.tooltip = new NSString(tooltip);
        }

        _native.setToolTip(this.tooltip);
    }

    @Override
    public
    void remove() {
        if (parent != null) {
            parent.removeItem(_native);
        }

        title = null;
        tooltip = null;
        keyEquivalent = null;
        image = null;

        _native.setImage(null);
        _native.setTarget(null);
        _native.setAction(null);
    }


    // to make native add/remove easier for children
    void addItem(final NSMenuItem item) {
        _nativeMenu.addItem(item);
    }

    void removeItem(final NSMenuItem item) {
        _nativeMenu.removeItem(item);
    }
}
