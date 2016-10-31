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
package dorkbox.systemTray.nativeUI;

import java.awt.MenuShortcut;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.CheckboxPeer;
import dorkbox.util.SwingUtil;

class AwtMenuItemCheckbox implements CheckboxPeer {

    private final AwtMenu parent;
    private final java.awt.CheckboxMenuItem _native = new java.awt.CheckboxMenuItem();

    private volatile ActionListener swingCallback;

    // this is ALWAYS called on the EDT.
    AwtMenuItemCheckbox(final AwtMenu parent) {
        this.parent = parent;
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
                _native.setLabel(menuItem.getText());
            }
        });
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setCallback(final Checkbox menuItem) {
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
    void setShortcut(final Checkbox menuItem) {
        char shortcut = menuItem.getShortcut();
        // yikes...
        final int vKey = SwingUtil.getVirtualKey(shortcut);

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
    void setChecked(final Checkbox checkbox) {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.setState(checkbox.getChecked());
            }
        });
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.deleteShortcut();
                _native.setEnabled(false);

                if (swingCallback != null) {
                    _native.removeActionListener(swingCallback);
                    swingCallback = null;
                }
                parent._native.remove(_native);

                _native.removeNotify();
            }
        });
    }
}
