/*
 * Copyright 2018 dorkbox, llc
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

import dorkbox.systemTray.peer.EntryPeer;
import dorkbox.util.jna.macos.cocoa.NSMenuItem;

class OsxMenuItemSeparator implements EntryPeer {

    private final NSMenuItem _native = NSMenuItem.separatorItem();
    private final OsxMenu parent;

    OsxMenuItemSeparator(final OsxMenu parent) {
        this.parent = parent;
        parent.addItem(_native);
    }

    @Override
    public
    void remove() {
        parent.removeItem(_native);
    }
}
