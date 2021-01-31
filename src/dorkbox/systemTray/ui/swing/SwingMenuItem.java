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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.SwingConstants;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.MenuItemPeer;
import dorkbox.systemTray.util.EventDispatch;
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.util.SwingUtil;

class SwingMenuItem implements MenuItemPeer {
    // necessary to have the correct scaling + padding for the menu entries.
    static ImageIcon transparentIcon = null;

    /**
     * This should ONLY be called by _SwingTray!
     *
     * @param menuImageSize this is the largest size of an image used in a JMenuItem, before the size of the JMenuItem is forced to be larger
     */
    static
    void createTransparentIcon(int menuImageSize, ImageResizeUtil imageResizeUtil) {
        if (transparentIcon == null) {
            try {
                JMenuItem jMenuItem = new JMenuItem();

                // do the same modifications that would also happen (if specified) for the actual displayed menu items
                if (SystemTray.SWING_UI != null) {
                    jMenuItem.setUI(SystemTray.SWING_UI.getItemUI(jMenuItem, null));
                }

                transparentIcon = new ImageIcon(imageResizeUtil.getTransparentImage(menuImageSize)
                                                               .getAbsolutePath());
            } catch (Exception e) {
                SystemTray.logger.error("Error creating transparent image.", e);
            }
        }
    }


    protected final SwingMenu parent;
    protected final JMenuItem _native = new JMenuItem();

    protected volatile ActionListener callback;


    // this is ALWAYS called on the EDT.
    SwingMenuItem(final SwingMenu parent, final Entry entry) {
        this.parent = parent;

        if (SystemTray.SWING_UI != null) {
            _native.setUI(SystemTray.SWING_UI.getItemUI(_native, entry));
        }

        _native.setHorizontalAlignment(SwingConstants.LEFT);
        parent._native.add(_native);

        _native.setIcon(transparentIcon);
    }

    @Override
    public
    void setImage(final MenuItem menuItem) {
        SwingUtil.invokeLater(()->{
            File imageFile = menuItem.getImage();
            if (imageFile != null) {
                ImageIcon origIcon = new ImageIcon(imageFile.getAbsolutePath());
                _native.setIcon(origIcon);
            }
            else {
                _native.setIcon(transparentIcon);
            }
        });
    }

    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        SwingUtil.invokeLater(()->_native.setEnabled(menuItem.getEnabled()));
    }

    @Override
    public
    void setText(final MenuItem menuItem) {
        SwingUtil.invokeLater(()->_native.setText(menuItem.getText()));
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
        final int vKey = SwingUtil.getVirtualKey(menuItem.getShortcut());

        SwingUtil.invokeLater(()->_native.setMnemonic(vKey));
    }

    @Override
    public
    void setTooltip(final MenuItem menuItem) {
        SwingUtil.invokeLater(()->_native.setToolTipText(menuItem.getTooltip()));
    }

    @Override
    public
    void remove() {
        //noinspection Duplicates
        SwingUtil.invokeLater(()->{
            if (callback != null) {
                _native.removeActionListener(callback);
                callback = null;
            }
            parent._native.remove(_native);
            _native.removeAll();
        });
    }
}
