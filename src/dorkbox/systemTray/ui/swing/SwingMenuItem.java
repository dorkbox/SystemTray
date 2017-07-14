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
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.util.Swing;

class SwingMenuItem implements MenuItemPeer {

    // necessary to have the correct scaling + padding for the menu entries.
    protected static ImageIcon transparentIcon;

    protected final SwingMenu parent;
    protected final JMenuItem _native = new JMenuItem();

    protected volatile ActionListener callback;


    // this is ALWAYS called on the EDT.
    SwingMenuItem(final SwingMenu parent, Entry entry) {
        this.parent = parent;

        if (SystemTray.SWING_UI != null) {
            _native.setUI(SystemTray.SWING_UI.getItemUI(_native, entry));
        }

        _native.setHorizontalAlignment(SwingConstants.LEFT);
        parent._native.add(_native);

        if (transparentIcon == null) {
            try {
                JMenuItem jMenuItem = new JMenuItem();

                // do the same modifications that would also happen (if specified) for the actual displayed menu items
                if (SystemTray.SWING_UI != null) {
                    jMenuItem.setUI(SystemTray.SWING_UI.getItemUI(jMenuItem, null));
                }

                // this is the largest size of an image used in a JMenuItem, before the size of the JMenuItem is forced to be larger
                int menuImageSize = SystemTray.get()
                                              .getMenuImageSize();

                transparentIcon = new ImageIcon(ImageResizeUtil.getTransparentImage(menuImageSize)
                                                               .getAbsolutePath());
            } catch (Exception e) {
                SystemTray.logger.error("Error creating transparent image.", e);
            }
        }

        _native.setIcon(transparentIcon);
    }

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
        Swing.invokeLater(new Runnable() {
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
        Swing.invokeLater(new Runnable() {
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
        if (callback != null) {
            _native.removeActionListener(callback);
        }

        if (menuItem.getCallback() != null) {
            callback = new ActionListener() {
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

            _native.addActionListener(callback);
        }
        else {
            callback = null;
        }
    }

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
                _native.setMnemonic(vKey);
            }
        });
    }

    @Override
    public
    void remove() {
        //noinspection Duplicates
        Swing.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                if (callback != null) {
                    _native.removeActionListener(callback);
                    callback = null;
                }
                parent._native.remove(_native);
                _native.removeAll();
            }
        });
    }
}
