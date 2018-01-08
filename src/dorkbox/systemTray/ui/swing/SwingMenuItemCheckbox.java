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

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.CheckboxPeer;
import dorkbox.systemTray.util.EventDispatch;
import dorkbox.systemTray.util.HeavyCheckMark;
import dorkbox.util.FontUtil;
import dorkbox.util.SwingUtil;

class SwingMenuItemCheckbox extends SwingMenuItem implements CheckboxPeer {

    // these have to be volatile, because they can be changed from any thread
    private volatile boolean isChecked = false;

    private static volatile ImageIcon checkedIcon;

    // this is ALWAYS called on the EDT.
    SwingMenuItemCheckbox(final SwingMenu parent, final Entry entry) {
        super(parent, entry);

        if (checkedIcon == null) {
            try {
                JMenuItem jMenuItem = new JMenuItem();

                // do the same modifications that would also happen (if specified) for the actual displayed menu items
                if (SystemTray.SWING_UI != null) {
                    jMenuItem.setUI(SystemTray.SWING_UI.getItemUI(jMenuItem, null));
                }

                // Having the checkmark size the same size as the letter X is a reasonably nice size.
                int size = FontUtil.getFontHeight(jMenuItem.getFont(), "X");

                // this is the largest size of an image used in a JMenuItem, before the size of the JMenuItem is forced to be larger
                int menuImageSize = SystemTray.get()
                                              .getMenuImageSize();

                String checkmarkPath;
                if (SystemTray.SWING_UI != null) {
                    checkmarkPath = SystemTray.SWING_UI.getCheckMarkIcon(jMenuItem.getForeground(), size, menuImageSize);
                } else {
                    checkmarkPath = HeavyCheckMark.get(jMenuItem.getForeground(), size, menuImageSize);
                }

                checkedIcon = new ImageIcon(checkmarkPath);
            } catch(Exception e) {
                SystemTray.logger.error("Error creating check-mark image.", e);
            }
        }
    }

    @Override
    public
    void setEnabled(final Checkbox menuItem) {
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
    void setText(final Checkbox menuItem) {
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
    void setCallback(final Checkbox menuItem) {
        if (callback != null) {
            _native.removeActionListener(callback);
        }

        callback = menuItem.getCallback(); // can be set to null

        if (callback != null) {
            callback = new ActionListener() {
                final ActionListener cb = menuItem.getCallback();

                @Override
                public
                void actionPerformed(ActionEvent e) {
                    // this will run on the EDT, since we are calling it from the EDT
                    menuItem.setChecked(!isChecked);

                    // we want it to run on our own with our own action event info (so it is consistent across all platforms)
                    EventDispatch.runLater(new Runnable() {
                        @Override
                        public
                        void run() {
                            try {
                                cb.actionPerformed(new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, ""));
                            } catch (Throwable throwable) {
                                SystemTray.logger.error("Error calling menu checkbox entry {} click event.", menuItem.getText(), throwable);
                            }
                        }
                    });
                }
            };

            _native.addActionListener(callback);
        }
    }

    @Override
    public
    void setShortcut(final Checkbox menuItem) {
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
    void setChecked(final Checkbox menuItem) {
        boolean checked = menuItem.getChecked();

        // only dispatch if it's actually different
        if (checked != this.isChecked) {
            this.isChecked = checked;

            SwingUtil.invokeLater(new Runnable() {
                @Override
                public
                void run() {
                    if (isChecked) {
                        _native.setIcon(checkedIcon);
                    }
                    else {
                        _native.setIcon(SwingMenuItem.transparentIcon);
                    }
                }
            });
        }
    }
}
