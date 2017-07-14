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
package dorkbox.systemTray.ui.swing;

import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.MenuPeer;
import dorkbox.util.Swing;

// this is a weird composite class, because it must be a Menu, but ALSO a Entry -- so it has both (and duplicate code)
@SuppressWarnings("ForLoopReplaceableByForEach")
class SwingMenu implements MenuPeer {

    final JComponent _native;
    private final SwingMenu parent;

    // This is NOT a copy constructor!
    @SuppressWarnings("IncompleteCopyConstructor")
    SwingMenu(final SwingMenu parent, final Menu entry) {
        this.parent = parent;

        if (parent == null) {
            TrayPopup trayPopup = new TrayPopup();
            if (SystemTray.SWING_UI != null) {
                trayPopup.setUI(SystemTray.SWING_UI.getMenuUI(trayPopup, null));
            }
            this._native = trayPopup;
        }
        else {
            JMenu jMenu = new JMenu();
            JPopupMenu popupMenu = jMenu.getPopupMenu(); // ensure the popup menu is created

            if (SystemTray.SWING_UI != null) {
                jMenu.setUI(SystemTray.SWING_UI.getItemUI(jMenu, entry));
                popupMenu.setUI(SystemTray.SWING_UI.getMenuUI(popupMenu, entry));
            }

            this._native = jMenu;
            parent._native.add(jMenu);
        }
    }

    @Override
    public
    void add(final Menu parentMenu, final Entry entry, final int index) {
        // must always be called on the EDT
        Swing.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                // don't add this entry if it's already been added via another method. Because of threading via swing/gtk, entries can
                // POSSIBLY get added twice. Once via add() and once via bind().
                if (entry.hasPeer()) {
                    return;
                }

                if (entry instanceof Menu) {
                    SwingMenu swingMenu = new SwingMenu(SwingMenu.this, (Menu) entry);
                    ((Menu) entry).bind(swingMenu, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Separator) {
                    SwingMenuItemSeparator item = new SwingMenuItemSeparator(SwingMenu.this);
                    entry.bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Checkbox) {
                    SwingMenuItemCheckbox item = new SwingMenuItemCheckbox(SwingMenu.this, entry);
                    ((Checkbox) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Status) {
                    SwingMenuItemStatus item = new SwingMenuItemStatus(SwingMenu.this, entry);
                    ((Status) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof MenuItem) {
                    SwingMenuItem item = new SwingMenuItem(SwingMenu.this, entry);
                    ((MenuItem) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
            }
        });
    }

    // is overridden in tray impl
    @Override
    public
    void setImage(final MenuItem menuItem) {
        Swing.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                File imageFile = menuItem.getImage();
                if (imageFile != null) {
                    ImageIcon origIcon = new ImageIcon(imageFile.getAbsolutePath());
                    ((JMenu) _native).setIcon(origIcon);
                }
                else {
                    ((JMenu) _native).setIcon(null);
                }
            }
        });
    }

    // is overridden in tray impl
    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        Swing.invokeLater(new Runnable() {
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
        Swing.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                ((JMenu) _native).setText(menuItem.getText());
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
        char shortcut = menuItem.getShortcut();
        // yikes...
        final int vKey = Swing.getVirtualKey(shortcut);

        Swing.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                ((JMenu) _native).setMnemonic(vKey);
            }
        });
    }

    /**
     * This removes all menu entries from this menu AND this menu from it's parent
     */
    @Override
    public synchronized
    void remove() {
        Swing.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.setVisible(false);
                _native.removeAll();

                if (parent != null) {
                    parent._native.remove(_native);
                }
                else {
                    // have to dispose of the tray popup hidden frame, otherwise the app will never close (because this will hold it open)
                    ((TrayPopup) _native).close();
                }
            }
        });
    }
}
