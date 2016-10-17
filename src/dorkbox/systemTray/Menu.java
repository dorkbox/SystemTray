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
package dorkbox.systemTray;

import java.awt.event.ActionListener;
import java.io.InputStream;
import java.net.URL;

/**
 * Represents a cross-platform menu that is displayed by the tray-icon or as a sub-menu
 */
public
interface Menu extends Entry {

    /**
     * @return the parent menu (of this menu) or null if we are the root menu
     */
    Menu getParent();

    /**
     * @return the system tray that this menu is ultimately attached to
     */
    SystemTray getSystemTray();

    /**
     * Adds a spacer to the dropdown menu. When menu entries are removed, any menu spacer that ends up at the top/bottom of the drop-down
     * menu, will also be removed. For example:
     *
     * Original     Entry3 deleted     Result
     *
     * <Status>         <Status>       <Status>
     * Entry1           Entry1         Entry1
     * Entry2      ->   Entry2    ->   Entry2
     * <Spacer>         (deleted)
     * Entry3           (deleted)
     */
    void addSeparator();


    /**
     *  This removes al menu entries from this menu
     */
    void removeAll();

    /**
     * Gets the menu entry for a specified text
     *
     * @param menuText the menu entry text to use to find the menu entry. The first result found is returned
     */
    Entry get(final String menuText);

    /**
     * Gets the first menu entry or sub-menu, ignoring status and separators
     */
    Entry getFirst();

    /**
     * Gets the last menu entry or sub-menu, ignoring status and separators
     */
    Entry getLast();

    /**
     * Gets the menu entry or sub-menu for a specified index (zero-index), ignoring status and separators
     *
     * @param menuIndex the menu entry index to use to retrieve the menu entry.
     */
    Entry get(final int menuIndex);



    /**
     * Adds a menu entry with text (no image)
     *
     * @param menuText string of the text you want to appear
     * @param callback callback that will be executed when this menu entry is clicked
     */
    Entry addEntry(String menuText, ActionListener callback);

    /**
     * Adds a menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imagePath the image (full path required) to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    Entry addEntry(String menuText, String imagePath, ActionListener callback);

    /**
     * Adds a menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageUrl the URL of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    Entry addEntry(String menuText, URL imageUrl, ActionListener callback);

    /**
     * Adds a menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param cacheName @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    Entry addEntry(String menuText, String cacheName, InputStream imageStream, ActionListener callback);

    /**
     * Adds a menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    Entry addEntry(String menuText, InputStream imageStream, ActionListener callback);



    /**
     * Adds a check-box menu entry with text
     *
     * @param menuText string of the text you want to appear
     * @param callback callback that will be executed when this menu entry is clicked
     */
    Checkbox addCheckbox(String menuText, ActionListener callback);



    /**
     * Adds a sub-menu entry with text (no image)
     *
     * @param menuText string of the text you want to appear
     */
    Menu addMenu(String menuText);

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imagePath the image (full path required) to use. If null, no image will be used
     */
    Menu addMenu(String menuText, String imagePath);

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageUrl the URL of the image to use. If null, no image will be used
     */
    Menu addMenu(String menuText, URL imageUrl);

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param cacheName @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     */
    Menu addMenu(String menuText, String cacheName, InputStream imageStream);

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     */
    Menu addMenu(String menuText, InputStream imageStream);


    /**
     * Adds a swing widget as a menu entry.
     *
     * @param widget the JComponent that is to be added as an entry
     */
// TODO: buggy. The menu will **sometimes** stop responding to the "enter" key after this. Mnemonics still work however.
//    Entry addWidget(JComponent widget);


    /**
     *  This removes a menu entry from the dropdown menu.
     *
     * @param entry This is the menu entry to remove
     */
    void remove(final Entry entry);

    /**
     *  This removes a sub-menu entry from the dropdown menu.
     *
     * @param menu This is the menu entry to remove
     */
    void remove(final Menu menu);

    /**
     *  This removes a menu entry or sub-menu (via the text label) from the dropdown menu.
     *
     * @param menuText This is the label for the menu entry or sub-menu to remove
     */
    void remove(final String menuText);
}
