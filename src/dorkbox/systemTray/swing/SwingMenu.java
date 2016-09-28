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
package dorkbox.systemTray.swing;


import java.io.File;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.util.SwingUtil;

public
class SwingMenu extends Menu {

    volatile SwingSystemTrayMenuPopup _native;

    /**
     * @param systemTray
     *                 the system tray (which is the object that sits in the system tray)
     * @param parent
     *                 the parent of this menu, null if the parent is the system tray
     */
    public
    SwingMenu(final SystemTray systemTray, final Menu parent) {
        super(systemTray, parent);

        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                _native = new SwingSystemTrayMenuPopup();
            }
        });
    }

    protected
    void dispatch(final Runnable runnable) {
        // this will properly check if we are running on the EDT
        SwingUtil.invokeLater(runnable);
    }

    @Override
    public
    void addMenuSpacer() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    synchronized (menuEntries) {
                        MenuEntry menuEntry = new SwingMenuEntrySpacer(SwingMenu.this);
                        menuEntries.add(menuEntry);
                    }
                }
            }
        });
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    protected
    void addMenuEntry_(final String menuText, final File imagePath, final SystemTrayMenuAction callback) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(menuText);

                    if (menuEntry != null) {
                        throw new IllegalArgumentException("Menu entry already exists for given label '" + menuText + "'");
                    }
                    else {
                        // must always be called on the EDT
                        menuEntry = new SwingMenuEntryItem(SwingMenu.this, callback);
                        menuEntry.setText(menuText);
                        menuEntry.setImage(imagePath);

                        menuEntries.add(menuEntry);
                    }
                }
            }
        });
    }
}
