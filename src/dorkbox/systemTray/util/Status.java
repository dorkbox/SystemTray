/*
 * Copyright 2015 dorkbox, llc
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

package dorkbox.systemTray.util;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.SystemTray;

/**
 * This represents a common menu-status entry, that is cross platform in nature
 */
public
class Status extends Entry {
    private volatile String text;

    public
    Status() {
    }

    /**
     * @param hook the platform specific implementation for all actions for this type
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param systemTray the system tray (which is the object that sits in the system tray)
     */
    public synchronized
    void bind(final MenuStatusHook hook, final Menu parent, final SystemTray systemTray) {
        super.bind(hook, parent, systemTray);

        hook.setText(this);
    }

    /**
     * @return the text label that the menu entry has assigned
     */
    public final
    String getText() {
        return text;
    }

    /**
     * Specifies the new text to set for a menu entry
     *
     * @param text the new text to set
     */
    public
    void setText(final String text) {
        this.text = text;

        if (hook != null) {
            ((MenuStatusHook) hook).setText(this);
        }
    }
}
