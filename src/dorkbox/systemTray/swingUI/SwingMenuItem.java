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
package dorkbox.systemTray.swingUI;

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
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.util.SwingUtil;

class SwingMenuItem implements MenuItemPeer {

    // necessary to have the correct scaling + padding for the menu entries.
    private static ImageIcon transparentIcon;

    private final SwingMenu parent;
    private final JMenuItem _native = new JMenuItem();

    private volatile ActionListener swingCallback;


    // this is ALWAYS called on the EDT.
    SwingMenuItem(final SwingMenu parent, Entry entry) {
        this.parent = parent;

        if (SystemTray.SWING_UI != null) {
            _native.setUI(SystemTray.SWING_UI.getItemUI(_native, entry));
        }

        _native.setHorizontalAlignment(SwingConstants.LEFT);
        parent._native.add(_native);

        if (transparentIcon == null) {
            File uncheckedFile = ImageResizeUtil.getTransparentImage();
            transparentIcon = new ImageIcon(uncheckedFile.getAbsolutePath());
        }

        _native.setIcon(transparentIcon);
    }

    @Override
    public
    void setImage(final MenuItem menuItem) {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                File imageFile = menuItem.getImage();
                if (imageFile != null) {
                    ImageIcon origIcon = new ImageIcon(imageFile.getAbsolutePath());
                    _native.setIcon(origIcon);
                }
                else {
                    _native.setIcon(transparentIcon);
                }
            }
        });
    }

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

    @Override
    public
    void setText(final MenuItem menuItem) {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.setText(menuItem.getText());
            }
        });
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setCallback(final MenuItem menuItem) {
        if (swingCallback != null) {
            _native.removeActionListener(swingCallback);
        }

        if (menuItem.getCallback() != null) {
            swingCallback = new ActionListener() {
                @Override
                public
                void actionPerformed(ActionEvent e) {
                    // we want it to run on the EDT, but with our own action event info (so it is consistent across all platforms)
                    ActionListener cb = menuItem.getCallback();
                    if (cb != null) {
                        try {
                            cb.actionPerformed(new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, ""));
                        } catch (Throwable throwable) {
                            SystemTray.logger.error("Error calling menu entry {} click event.", menuItem.getText(), throwable);
                        }
                    }
                }
            };

            _native.addActionListener(swingCallback);
        }
        else {
            swingCallback = null;
        }
    }

    @Override
    public
    void setShortcut(final MenuItem menuItem) {
        char shortcut = menuItem.getShortcut();
        // yikes...
        final int vKey = SwingUtil.getVirtualKey(shortcut);

        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.setMnemonic(vKey);
            }
        });
    }

    @Override
    public
    void remove() {
        //noinspection Duplicates
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                if (swingCallback != null) {
                    _native.removeActionListener(swingCallback);
                    swingCallback = null;
                }
                parent._native.remove(_native);
                _native.removeAll();
            }
        });
    }
}
