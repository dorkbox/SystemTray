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

import java.awt.Font;

import javax.swing.JMenuItem;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.StatusPeer;
import dorkbox.util.SwingUtil;

class SwingMenuItemStatus implements StatusPeer {

    private final SwingMenu parent;
    private final JMenuItem _native = new JMenuItem();

    // this is ALWAYS called on the EDT.
    SwingMenuItemStatus(final SwingMenu parent, final Entry entry) {
        this.parent = parent;

        if (SystemTray.SWING_UI != null) {
            _native.setUI(SystemTray.SWING_UI.getItemUI(_native, entry));
        }

        // status is ALWAYS at 0 index...
        parent._native.add(_native, 0);

        Font font = _native.getFont();
        Font font1 = font.deriveFont(Font.BOLD);
        _native.setFont(font1);

        // this makes sure it can't be selected
        _native.setEnabled(false);
    }

    @Override
    public
    void setText(final Status menuItem) {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                _native.setText(menuItem.getText());
            }
        });
    }

    @Override
    public
    void remove() {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                parent._native.remove(_native);
                _native.removeAll();
            }
        });
    }
}
