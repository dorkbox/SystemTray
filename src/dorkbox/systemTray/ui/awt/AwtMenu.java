/*
 * Copyright 2014 dorkbox, llc
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
package dorkbox.systemTray.ui.awt;


import java.awt.MenuShortcut;
import java.awt.PopupMenu;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.peer.MenuPeer;
import dorkbox.util.SwingUtil;

// this is a weird composite class, because it must be a Menu, but ALSO a Entry -- so it has both
@SuppressWarnings("ForLoopReplaceableByForEach")
class AwtMenu implements MenuPeer {

    volatile java.awt.Menu _native;
    private final AwtMenu parent;

    // This is NOT a copy constructor!
    @SuppressWarnings("IncompleteCopyConstructor")
    AwtMenu(final AwtMenu parent) {
        this.parent = parent;

        if (parent == null) {
            this._native = new PopupMenu();
        }
        else {
            this._native = new java.awt.Menu();
            parent._native.add(this._native);
        }
    }

    @Override
    public
    void add(final Menu parentMenu, final Entry entry, final int index) {
        // must always be called on the EDT
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                if (entry instanceof Menu) {
                    AwtMenu swingMenu = new AwtMenu(AwtMenu.this);
                    ((Menu) entry).bind(swingMenu, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Separator) {
                    AwtMenuItemSeparator item = new AwtMenuItemSeparator(AwtMenu.this);
                    entry.bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Checkbox) {
                    AwtMenuItemCheckbox item = new AwtMenuItemCheckbox(AwtMenu.this);
                    ((Checkbox) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Status) {
                    AwtMenuItemStatus item = new AwtMenuItemStatus(AwtMenu.this);
                    ((Status) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof MenuItem) {
                    AwtMenuItem item = new AwtMenuItem(AwtMenu.this);
                    ((MenuItem) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
            }
        });
    }

    // is overridden in tray impl
    @Override
    public
    void setImage(final MenuItem menuItem) {
        // no op. You can't have images in an awt menu
    }

    // is overridden in tray impl
    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.setEnabled(menuItem.getEnabled());
            }
        });
    }

    // is overridden in tray impl
    @Override
    public
    void setText(final MenuItem menuItem) {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.setLabel(menuItem.getText());
            }
        });
    }

    @Override
    public
    void setCallback(final MenuItem menuItem) {
        // can't have a callback for menus!
    }

    // is overridden in tray impl
    @Override
    public
    void setShortcut(final MenuItem menuItem) {
        // yikes...
        final int vKey = SwingUtil.getVirtualKey(menuItem.getShortcut());

        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.setShortcut(new MenuShortcut(vKey));
            }
        });
    }

    @Override
    public
    void remove() {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.removeAll();
                _native.deleteShortcut();
                _native.setEnabled(false);
                _native.removeNotify();

                if (parent != null) {
                    parent._native.remove(_native);
                }
            }
        });
    }
}
