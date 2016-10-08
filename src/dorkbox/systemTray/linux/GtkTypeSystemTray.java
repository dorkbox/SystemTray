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

package dorkbox.systemTray.linux;

import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;

/**
 * Derived from
 * Lantern: https://github.com/getlantern/lantern/ Apache 2.0 License Copyright 2010 Brave New Software Project, Inc.
 */
abstract
class GtkTypeSystemTray extends GtkMenu {

    GtkTypeSystemTray(final SystemTray systemTray) {
        super(systemTray, null);
    }

    public
    String getStatus() {
        synchronized (menuEntries) {
            MenuEntry menuEntry = menuEntries.get(0);
            if (menuEntry instanceof GtkEntryStatus) {
                return menuEntry.getText();
            }
        }

        return null;
    }

    public
    void setStatus(final String statusText) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                // To work around this issue, we destroy then recreate the menu every time something is changed.
                synchronized (menuEntries) {
                    // status is ALWAYS at 0 index...
                    GtkEntry menuEntry = null;
                    if (!menuEntries.isEmpty()) {
                        menuEntry = (GtkEntry) menuEntries.get(0);
                    }

                    if (menuEntry instanceof GtkEntryStatus) {
                        // always delete...
                        remove(menuEntry);
                    }

                    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                    // To work around this issue, we destroy then recreate the menu every time something is changed.
                    deleteMenu();

                    if (menuEntry == null) {
                        menuEntry = new GtkEntryStatus(GtkTypeSystemTray.this, statusText);
                        // status is ALWAYS at 0 index...
                        menuEntries.add(0, menuEntry);
                    }
                    else if (menuEntry instanceof GtkEntryStatus) {
                        // change the text?
                        if (statusText != null) {
                            menuEntry = new GtkEntryStatus(GtkTypeSystemTray.this, statusText);
                            menuEntries.add(0, menuEntry);
                        }
                    }

                    createMenu();
                }
            }
        });
    }
}
