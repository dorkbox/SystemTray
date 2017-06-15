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

import dorkbox.systemTray.gnomeShell.Extension;
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.util.OSUtil;

// This is public ONLY so that it is in the scope for SwingUI and NativeUI system tray components
public
class Tray extends Menu {
    // true if we are using gnome (and things depend on it) or false
    public static volatile boolean usingGnome = false;

    protected static
    void installExtension() {
        // do we need to install the GNOME extension??
        if (Tray.usingGnome) {
            if (OSUtil.Linux.isArch()) {
                if (SystemTray.DEBUG) {
                    SystemTray.logger.debug("Running Arch Linux.");
                }
                if (!Extension.isInstalled()) {
                    SystemTray.logger.info("You may need a work-around for showing the SystemTray icon - we suggest installing the " +
                                           "the [Top Icons] plugin (https://extensions.gnome.org/extension/1031/topicons/) which moves " +
                                           "icons from the *notification drawer* (it is normally collapsed) at the bottom left corner " +
                                           "of the screen to the menu panel next to the clock.");
                }
            } else {
                // Automatically install the extension for everyone except Arch. It's bonkers.
                Extension.install();
            }
        }
    }

    // appindicators DO NOT support anything other than PLAIN gtk-menus
    //   they ALSO do not support tooltips!
    // https://bugs.launchpad.net/indicator-application/+bug/527458/comments/12

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
     * @param statusText the text you want displayed, null if you want to remove the 'status' text
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

    // method that is meant to be overridden by the tray implementations
    protected
    void setTooltip_(final String tooltipText) {
        // default is NO OP
    }

    /**
     * Specifies the tooltip text, usually this is used to brand the SystemTray icon with your product's name.
     * <p>
     * The maximum length is 64 characters long, and it is not supported on all Operating Systems and Desktop
     * Environments.
     * <p>
     * For more details on Linux see https://bugs.launchpad.net/indicator-application/+bug/527458/comments/12.
     *
     * @param tooltipText the text to use as tooltip for the tray icon, null to remove
     */
    final
    void setTooltip(final String tooltipText) {
        // this is a safety precaution, since the behavior of really long text is undefined.
        if (tooltipText.length() > 64) {
            throw new RuntimeException("Tooltip text cannot be longer than 64 characters.");
        }

        setTooltip_(tooltipText);
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imageFile the file of the image to use
     */
    @Override
    public
    void setImage(final File imageFile) {
        setImage_(ImageResizeUtil.shouldResizeOrCache(true, imageFile));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imagePath the full path of the image to use
     */
    @Override
    public
    void setImage(final String imagePath) {
        setImage_(ImageResizeUtil.shouldResizeOrCache(true, imagePath));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imageUrl the URL of the image to use
     */
    @Override
    public
    void setImage(final URL imageUrl) {
        setImage_(ImageResizeUtil.shouldResizeOrCache(true, imageUrl));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imageStream the InputStream of the image to use
     */
    @Override
    public
    void setImage(final InputStream imageStream) {
        setImage_(ImageResizeUtil.shouldResizeOrCache(true, imageStream));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param image the image of the image to use
     */
    @Override
    public
    void setImage(final Image image) {
        setImage_(ImageResizeUtil.shouldResizeOrCache(true, image));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imageStream the ImageInputStream of the image to use
     */
    @Override
    public
    void setImage(final ImageInputStream imageStream) {
        setImage_(ImageResizeUtil.shouldResizeOrCache(true, imageStream));
    }
}
