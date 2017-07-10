/*
 * Copyright 2017 dorkbox, llc
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

import java.awt.event.ActionEvent;

import dorkbox.systemTray.peer.MenuItemPeer;

class WindowsMenuItem extends WindowsBaseMenuItem implements MenuItemPeer {

    private final WindowsMenu parent;

    // this is ALWAYS called on the EDT.
    WindowsMenuItem(final WindowsMenu parent, final int index) {
        super(index);
        this.parent = parent;
    }

    @Override
    public
    void setImage(final dorkbox.systemTray.MenuItem menuItem) {
        super.setImage(menuItem.getImage());
    }

    @Override
    public
    void setEnabled(final dorkbox.systemTray.MenuItem menuItem) {
        super.setEnabled(menuItem.getEnabled());
    }

    @Override
    public
    void setText(final dorkbox.systemTray.MenuItem menuItem) {
        super.setText(menuItem.getText());
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setCallback(final dorkbox.systemTray.MenuItem menuItem) {
        super.setCallback(menuItem.getCallback(), new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Override
    public
    void setShortcut(final dorkbox.systemTray.MenuItem menuItem) {
//        char shortcut = menuItem.getShortcut();
//        // yikes...
//        final int vKey = SwingUtil.getVirtualKey(shortcut);
//
//        SwingUtil.invokeLater(new Runnable() {
//            @Override
//            public
//            void run() {
//                _native.setShortcut(new MenuShortcut(vKey));
//            }
//        });
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
//        SwingUtil.invokeLater(new Runnable() {
//            @Override
//            public
//            void run() {
//                _native.deleteShortcut();
//                _native.setEnabled(false);
//
//                if (swingCallback != null) {
//                    _native.removeActionListener(swingCallback);
//                    swingCallback = null;
//                }
//                parent._native.remove(_native);
//
//                _native.removeNotify();
//            }
//        });
    }
}
