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

package dorkbox.systemTray;

import java.io.InputStream;
import java.net.URL;

/**
 * This represents a common menu-entry, that is cross platform in nature
 */
public
interface MenuEntry {
    /**
     * @return the text label that the menu entry has assigned
     */
    String getText();

    /**
     * Specifies the new text to set for a menu entry
     *
     * @param newText the new text to set
     */
    void setText(String newText);

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param imagePath the full path of the image to use or null
     */
    void setImage(String imagePath);

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param imageUrl the URL of the image to use or null
     */
    void setImage(URL imageUrl);

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use
     */
    void setImage(String cacheName, InputStream imageStream);

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * This method **DOES NOT CACHE** the result, so multiple lookups for the same inputStream result in new files every time. This is
     * also NOT RECOMMENDED, but is provided for simplicity.
     *
     * @param imageStream the InputStream of the image to use
     */
    void setImage(InputStream imageStream);

    /**
     * @return true if this menu entry has an image assigned to it, or is just text.
     */
    boolean hasImage();

    /**
     * Sets a callback for a menu entry. This is the action that occurs when one clicks the menu entry
     *
     * @param callback the callback to set. If null, the callback is safely removed.
     */
    void setCallback(SystemTrayMenuAction callback);

    /**
     * Removes this menu entry from the menu and releases all system resources associated with this menu entry
     */
    void remove();
}
