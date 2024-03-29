/*
 * Copyright 2023 dorkbox, llc
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


import dorkbox.systemTray.peer.EntryPeer;
import dorkbox.util.SwingUtil;

class AwtMenuItemSeparator implements EntryPeer {

    private final AwtMenu parent;
    private final java.awt.MenuItem _native = new java.awt.MenuItem("-");


    // this is ALWAYS called on the EDT.
    AwtMenuItemSeparator(final AwtMenu parent) {
        this.parent = parent;
        parent._native.add(_native);
    }

    @Override
    public
    void remove() {
        SwingUtil.INSTANCE.invokeLater(()->parent._native.remove(_native));
    }
}
