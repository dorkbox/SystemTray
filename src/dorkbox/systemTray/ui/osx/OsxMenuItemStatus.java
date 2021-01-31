/*
 * Copyright 2021 dorkbox, llc
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

import dorkbox.jna.macos.cocoa.NSString;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.peer.StatusPeer;

class OsxMenuItemStatus extends OsxBaseMenuItem implements StatusPeer {

    // to prevent GC
    private NSString title;

    OsxMenuItemStatus(final OsxMenu parent) {
        super(parent);
        _native.setEnabled(false);
    }

    @Override
    public
    void setText(final Status menuItem) {
        String text = menuItem.getText();

        if (text == null || text.isEmpty()) {
            title = null;
        }
        else {
            title = new NSString(text);
        }

        _native.setTitle(title);
    }

    @Override
    public
    void remove() {
        super.remove();

        title = null;
    }
}
