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

import java.awt.Image;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

import javax.imageio.stream.ImageInputStream;

import dorkbox.systemTray.util.ImageUtils;

public
class Tray extends Menu {

    // appindicators DO NOT support anything other than PLAIN gtk-menus (which we hack to support swing menus)
    //   they ALSO do not support tooltips, so we cater to the lowest common denominator

    private volatile String statusText;

    public
    Tray() {
        super();
    }

    /**
     * Gets the 'status' string assigned to the system tray
     */
    public
    String getStatus() {
        return statusText;
    }

    /**
     * Sets a 'status' string at the first position in the popup menu. This 'status' string appears as a disabled menu entry.
     *
     * @param statusText the text you want displayed, null if you want to remove the 'status' string
     */
    public
    void setStatus(final String statusText) {
        this.statusText = statusText;

        // status is ALWAYS at 0 index...
        Entry menuEntry = null;

        synchronized (menuEntries) {
            // access on this object must be synchronized for object visibility
            if (!menuEntries.isEmpty()) {
                menuEntry = menuEntries.get(0);
            }
        }

        if (menuEntry instanceof Status) {
            // set the text or delete...

            if (statusText == null) {
                // delete
                remove(menuEntry);
            }
            else {
                // set text
                ((Status) menuEntry).setText(statusText);
            }
        } else {
            // create a new one
            Status status = new Status();
            status.setText(statusText);

            // status is ALWAYS at 0 index...
            // also calls the hook to add it, so we don't need anything special
            add(status, 0);
        }
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imageFile the file of the image to use
     */
    public
    void setImage(final File imageFile) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, imageFile));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imagePath the full path of the image to use
     */
    public
    void setImage(final String imagePath) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, imagePath));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imageUrl the URL of the image to use
     */
    public
    void setImage(final URL imageUrl) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, imageUrl));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imageStream the InputStream of the image to use
     */
    public
    void setImage(final InputStream imageStream) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, imageStream));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param image the image of the image to use
     */
    public
    void setImage(final Image image) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, image));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imageStream the ImageInputStream of the image to use
     */
    public
    void setImage(final ImageInputStream imageStream) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, imageStream));
    }
}
