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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.MenuItemPeer;
import dorkbox.systemTray.util.EventDispatch;
import dorkbox.util.SwingUtil;

class AwtMenuItem implements MenuItemPeer {

    private final AwtMenu parent;
    private final java.awt.MenuItem _native = new java.awt.MenuItem();

    private volatile ActionListener callback;

    // this is ALWAYS called on the EDT.
    AwtMenuItem(final AwtMenu parent) {
        this.parent = parent;
        parent._native.add(_native);
    }

    @Override
    public
    void setImage(final dorkbox.systemTray.MenuItem menuItem) {
        // no op. (awt menus cannot show images)
    }

    @Override
    public
    void setEnabled(final dorkbox.systemTray.MenuItem menuItem) {
        SwingUtil.invokeLater(()->_native.setEnabled(menuItem.getEnabled()));
    }

    @Override
    public
    void setText(final dorkbox.systemTray.MenuItem menuItem) {
        SwingUtil.invokeLater(()->_native.setLabel(menuItem.getText()));
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setCallback(final dorkbox.systemTray.MenuItem menuItem) {
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
    void setShortcut(final dorkbox.systemTray.MenuItem menuItem) {
        // Will return 0 as the vKey if it's not set (which will remove the shortcut)
        final int vKey = SwingUtil.getVirtualKey(menuItem.getShortcut());

        SwingUtil.invokeLater(()->_native.setShortcut(new MenuShortcut(vKey)));
    }

    @Override
    public
    void setTooltip(final MenuItem menuItem) {
        // no op. (awt menus cannot show tooltips)
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        SwingUtil.invokeLater(()->{
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
