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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.CheckboxPeer;
import dorkbox.systemTray.util.EventDispatch;
import dorkbox.util.SwingUtil;

class AwtMenuItemCheckbox implements CheckboxPeer {

    private final AwtMenu parent;
    private final java.awt.CheckboxMenuItem _native = new java.awt.CheckboxMenuItem();

    // these have to be volatile, because they can be changed from any thread
    private volatile ItemListener callback;
    private volatile boolean isChecked = false;

    // this is ALWAYS called on the EDT.
    AwtMenuItemCheckbox(final AwtMenu parent) {
        this.parent = parent;
        parent._native.add(_native);
    }

    @Override
    public
    void setEnabled(final Checkbox menuItem) {
        SwingUtil.invokeLater(()->_native.setEnabled(menuItem.getEnabled()));
    }

    @Override
    public
    void setText(final Checkbox menuItem) {
        SwingUtil.invokeLater(()->_native.setLabel(menuItem.getText()));
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setCallback(final Checkbox menuItem) {
        // of critical note: AWT only works with ItemListener -- but we use ActionListener for everything, so here we make things compatible
        if (callback != null) {
            _native.removeItemListener(callback);
        }

        ActionListener callback = menuItem.getCallback();  // can be set to null

        if (callback != null) {
            this.callback = new ItemListener() {
                final ActionListener cb = menuItem.getCallback();

                @Override
                public
                void itemStateChanged(final ItemEvent e) {
                    // this will run on the EDT, since we are calling it from the EDT
                    menuItem.setChecked(!isChecked);

                    // we want it to run on our own with our own action event info (so it is consistent across all platforms)
                    EventDispatch.runLater(()->{
                        try {
                            cb.actionPerformed(new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, ""));
                        } catch (Throwable throwable) {
                            SystemTray.logger.error("Error calling menu checkbox entry {} click event.", menuItem.getText(), throwable);
                        }
                    });
                }
            };

            _native.addItemListener(this.callback);
        }
    }

    @Override
    public
    void setShortcut(final Checkbox menuItem) {
        // Will return 0 as the vKey if it's not set (which will remove the shortcut)
        final int vKey = SwingUtil.getVirtualKey(menuItem.getShortcut());

        SwingUtil.invokeLater(()->_native.setShortcut(new MenuShortcut(vKey)));
    }

    @Override
    public
    void setTooltip(final Checkbox menuItem) {
        // no op. (awt menus cannot show tooltips)
    }

    @Override
    public
    void setChecked(final Checkbox menuItem) {
        boolean checked = menuItem.getChecked();

        // only dispatch if it's actually different
        if (checked != this.isChecked) {
            this.isChecked = checked;

            SwingUtil.invokeLater(()->_native.setState(isChecked));
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        SwingUtil.invokeLater(()->{
            _native.deleteShortcut();
            _native.setEnabled(false);

            if (callback != null) {
                _native.removeItemListener(callback);
                callback = null;
            }
            parent._native.remove(_native);

            _native.removeNotify();
        });
    }
}
