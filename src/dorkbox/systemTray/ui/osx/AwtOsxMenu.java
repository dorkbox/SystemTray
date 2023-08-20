/*
 * Copyright 2023 dorkbox, llc
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

import java.awt.Image;
import java.awt.MenuShortcut;
import java.awt.PopupMenu;
import java.io.File;

import javax.swing.ImageIcon;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.MenuPeer;
import dorkbox.systemTray.util.AwtAccessor;
import dorkbox.util.SwingUtil;

// this is a weird composite class, because it must be a Menu, but ALSO a Entry -- so it has both
class AwtOsxMenu implements MenuPeer {

    volatile java.awt.Menu _native;
    private final AwtOsxMenu parent;

    // we cannot access the peer object NORMALLY, so we use tricks (via looking at the osx source code)
    // peerObj will be null for the TrayImpl!
    private final Object peerObj;


    // This is NOT a copy constructor!
    @SuppressWarnings("IncompleteCopyConstructor")
    AwtOsxMenu(final AwtOsxMenu parent) {
        this.parent = parent;

        // are we a menu or a sub-menu?
        if (parent == null) {
            this._native = new PopupMenu();
        }
        else {
            this._native = new java.awt.Menu();
            parent._native.add(this._native);
        }
        this._native.addNotify();

        peerObj = AwtAccessor.getPeer(_native);
    }

    @Override
    public
    void add(final Menu parentMenu, final Entry entry, final int index) {
        // must always be called on the EDT
        SwingUtil.INSTANCE.invokeAndWaitQuietly(()->{
            if (entry instanceof Menu) {
                AwtOsxMenu menu = new AwtOsxMenu(AwtOsxMenu.this);
                ((Menu) entry).bind(menu, parentMenu, parentMenu.getImageResizeUtil());
            }
            else if (entry instanceof Separator) {
                AwtOsxMenuItemSeparator item = new AwtOsxMenuItemSeparator(AwtOsxMenu.this);
                entry.bind(item, parentMenu, parentMenu.getImageResizeUtil());
            }
            else if (entry instanceof Checkbox) {
                AwtOsxMenuItemCheckbox item = new AwtOsxMenuItemCheckbox(AwtOsxMenu.this);
                ((Checkbox) entry).bind(item, parentMenu, parentMenu.getImageResizeUtil());
            }
            else if (entry instanceof Status) {
                AwtOsxMenuItemStatus item = new AwtOsxMenuItemStatus(AwtOsxMenu.this);
                ((Status) entry).bind(item, parentMenu, parentMenu.getImageResizeUtil());
            }
            else if (entry instanceof MenuItem) {
                AwtOsxMenuItem item = new AwtOsxMenuItem(AwtOsxMenu.this);
                ((MenuItem) entry).bind(item, parentMenu, parentMenu.getImageResizeUtil());
            }
        });
    }

    // is overridden in tray impl
    @SuppressWarnings("DuplicatedCode")
    @Override
    public
    void setImage(final MenuItem menuItem) {
        // lucky for us, macOS AWT menu items CAN show images, but it takes a bit of magic.
        // peerObj will be null for the TrayImpl!
        File imageFile = menuItem.getImage();

        if (peerObj != null && imageFile != null) {
            Image image = new ImageIcon(imageFile.getAbsolutePath()).getImage();
            SwingUtil.INSTANCE.invokeLater(()-> {
                try {
                    AwtAccessor.setImage(peerObj, image);
                } catch (Exception e) {
                    SystemTray.logger.error("Unable to setImage for awt-osx menus.", e);
                }
            });
        }
    }

    // is overridden in tray impl
    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        SwingUtil.INSTANCE.invokeLater(()->_native.setEnabled(menuItem.getEnabled()));
    }

    // is overridden in tray impl
    @Override
    public
    void setText(final MenuItem menuItem) {
        SwingUtil.INSTANCE.invokeLater(()->_native.setLabel(menuItem.getText()));
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
        // Will return 0 as the vKey if it's not set (which will remove the shortcut)
        final int vKey = SwingUtil.INSTANCE.getVirtualKey(menuItem.getShortcut());

        SwingUtil.INSTANCE.invokeLater(()->_native.setShortcut(new MenuShortcut(vKey)));
    }

    @SuppressWarnings("DuplicatedCode")
    // is overridden in tray impl
    @Override
    public
    void setTooltip(final MenuItem menuItem) {
        // lucky for us, macOS AWT menu items CAN show tooltips, but it takes a bit of magic.
        // peerObj will be null for the TrayImpl!
        String tooltipText = menuItem.getTooltip();

        if (peerObj != null && tooltipText != null) {
            SwingUtil.INSTANCE.invokeLater(()-> {
                try {
                    AwtAccessor.setToolTipText(peerObj, tooltipText);
                } catch (Exception e) {
                    SystemTray.logger.error("Unable to setTooltip for awt-osx menus.", e);
                }
            });
        }
    }

    @Override
    public
    void remove() {
        SwingUtil.INSTANCE.invokeLater(()->{
            _native.removeAll();
            _native.deleteShortcut();
            _native.setEnabled(false);

            if (parent != null) {
                parent._native.remove(_native);
            }

            _native.removeNotify();
        });
    }
}
