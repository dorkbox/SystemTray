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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.ImageIcon;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.MenuItemPeer;
import dorkbox.systemTray.util.AwtAccessor;
import dorkbox.systemTray.util.EventDispatch;
import dorkbox.util.SwingUtil;


class AwtOsxMenuItem implements MenuItemPeer {

    private final AwtOsxMenu parent;
    private final java.awt.MenuItem _native = new java.awt.MenuItem();
    private volatile ActionListener callback;

    // we cannot access the peer object NORMALLY, so we use tricks (via looking at the osx source code)
    private final Object peerObj;


    // this is ALWAYS called on the EDT.
    AwtOsxMenuItem(final AwtOsxMenu parent) {
        this.parent = parent;
        parent._native.add(_native);
        peerObj = AwtAccessor.getPeer(_native);
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public
    void setImage(final MenuItem menuItem) {
        // lucky for us, macOS AWT menu items CAN show images, but it takes a bit of magic.
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

    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        SwingUtil.INSTANCE.invokeLater(()->_native.setEnabled(menuItem.getEnabled()));
    }

    @Override
    public
    void setText(final MenuItem menuItem) {
        SwingUtil.INSTANCE.invokeLater(()->_native.setLabel(menuItem.getText()));
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setCallback(final MenuItem menuItem) {
        if (callback != null) {
            _native.removeActionListener(callback);
        }

        callback = menuItem.getCallback();  // can be set to null

        if (callback != null) {
            callback = new ActionListener() {
                final ActionListener cb = menuItem.getCallback();

                @Override
                public
                void actionPerformed(ActionEvent e) {
                    // we want it to run on our own with our own action event info (so it is consistent across all platforms)
                    EventDispatch.runLater(()->{
                        try {
                            cb.actionPerformed(new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, ""));
                        } catch (Throwable throwable) {
                            SystemTray.logger.error("Error calling menu entry {} click event.", menuItem.getText(), throwable);
                        }
                    });
                }
            };

            _native.addActionListener(callback);
        }
    }

    @Override
    public
    void setShortcut(final MenuItem menuItem) {
        // Will return 0 as the vKey if it's not set (which will remove the shortcut)
        final int vKey = SwingUtil.INSTANCE.getVirtualKey(menuItem.getShortcut());

        SwingUtil.INSTANCE.invokeLater(()->_native.setShortcut(new MenuShortcut(vKey)));
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public
    void setTooltip(final MenuItem menuItem) {
        // lucky for us, macOS AWT menu items CAN show tooltips, but it takes a bit of magic.
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

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        SwingUtil.INSTANCE.invokeLater(()->{
            _native.deleteShortcut();
            _native.setEnabled(false);

            if (callback != null) {
                _native.removeActionListener(callback);
                callback = null;
            }
            parent._native.remove(_native);

            _native.removeNotify();
        });
    }
}
