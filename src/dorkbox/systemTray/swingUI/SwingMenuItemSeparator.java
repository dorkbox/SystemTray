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

import javax.swing.JSeparator;

import dorkbox.systemTray.util.EntryHook;
import dorkbox.util.SwingUtil;

class SwingMenuItemSeparator implements EntryHook {

    private final SwingMenu parent;
    private final JSeparator _native = new JSeparator(JSeparator.HORIZONTAL);

    // this is ALWAYS called on the EDT.
    SwingMenuItemSeparator(final SwingMenu parent) {
        this.parent = parent;
        parent._native.add(_native);
    }

    public
    boolean hasImage() {
        return false;
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
