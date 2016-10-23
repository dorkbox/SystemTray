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

import java.awt.Image;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

import dorkbox.systemTray.util.ImageUtils;
import dorkbox.systemTray.util.MenuItemHook;

/**
 * This represents a common menu-entry, that is cross platform in nature
 */
@SuppressWarnings({"unused", "SameParameterValue", "WeakerAccess"})
public
class MenuItem extends Entry {
    private volatile String text;
    private volatile File imageFile;
    private volatile ActionListener callback;

    // default enabled is always true
    private volatile boolean enabled = true;
    private volatile char mnemonicKey;

    public
    MenuItem() {
        this(null, null, null, false);
    }

    public
    MenuItem(final String text) {
        this(text, null, null, false);
    }

    public
    MenuItem(final String text, final ActionListener callback) {
        this(text, null, callback, false);
    }

    public
    MenuItem(final String text, final String imagePath) {
        this(text, imagePath, null);
    }

    public
    MenuItem(final String text, final File imageFile) {
        this(text, imageFile, null);
    }

    public
    MenuItem(final String text, final URL imageUrl) {
        this(text, imageUrl, null);
    }

    public
    MenuItem(final String text, final InputStream imageStream) {
        this(text, imageStream, null);
    }

    public
    MenuItem(final String text, final Image image) {
        this(text, image, null);
    }

    public
    MenuItem(final String text, final String imagePath, final ActionListener callback) {
        this(text, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath, true), callback, false);
    }

    public
    MenuItem(final String text, final File imageFile, final ActionListener callback) {
        this(text, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageFile, true), callback, false);
    }

    public
    MenuItem(final String text, final URL imageUrl, final ActionListener callback) {
        this(text, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl, true), callback, false);
    }

    public
    MenuItem(final String text, final InputStream imageStream, final ActionListener callback) {
        this(text, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream, true), callback, false);
    }

    public
    MenuItem(final String text, final Image image, final ActionListener callback) {
        this(text, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, image, true), callback, false);
    }

    // the last parameter (unused) is there so the signature is different
    private
    MenuItem(final String text, final File imageFile, final ActionListener callback, final boolean unused) {
        this.text = text;
        this.imageFile = imageFile;
        this.callback = callback;
    }

    /**
     * @param hook the platform specific implementation for all actions for this type
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param systemTray the system tray (which is the object that sits in the system tray)
     */
    public synchronized
    void bind(final MenuItemHook hook, final Menu parent, final SystemTray systemTray) {
        super.bind(hook, parent, systemTray);

        hook.setImage(this);
        hook.setEnabled(this);
        hook.setText(this);
        hook.setCallback(this);
        hook.setShortcut(this);
    }

    private
    void setImage_(final File imageFile) {
        this.imageFile = imageFile;

        if (hook != null) {
            ((MenuItemHook) hook).setImage(this);
        }
    }

    /**
     * Gets the File (which is the only cross-platform solution) that is assigned to this menu entry.
     * <p>
     * This file can also be a cached file, depending on how the image was assigned to this entry.
     */
    public
    File getImage() {
        return imageFile;
    }

    /**
     * Gets the callback assigned to this menu entry
     */
    public
    ActionListener getCallback() {
        return callback;
    }

    /**
     * @return true if this item is enabled, or false if it is disabled.
     */
    public
    boolean getEnabled() {
        return this.enabled;
    }

    /**
     * Enables, or disables the entry.
     */
    public
    void setEnabled(final boolean enabled) {
        this.enabled = enabled;

        if (hook != null) {
            ((MenuItemHook) hook).setEnabled(this);
        }
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
            ((MenuItemHook) hook).setText(this);
        }
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image.
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imageFile the file of the image to use or null
     */
    public
    void setImage(final File imageFile) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageFile, true));
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image.
     *
     * @param imageFile the file of the image to use or null
     * @param cacheImage true to cache the image (only if the image is resized as necessary)
     */
    public
    void setImage(final File imageFile, final boolean cacheImage) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageFile, cacheImage));
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imagePath the full path of the image to use or null
     */
    public
    void setImage(final String imagePath) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath, true));
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param imagePath the full path of the image to use or null
     * @param cacheImage true to cache the image (only if the image is resized as necessary)
     */
    public
    void setImage(final String imagePath, final boolean cacheImage) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath, cacheImage));
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imageUrl the URL of the image to use or null
     */
    public
    void setImage(final URL imageUrl) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl, true));
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param imageUrl the URL of the image to use or null
     * @param cacheImage true to cache the image (only if the image is resized as necessary)
     */
    public
    void setImage(final URL imageUrl, final boolean cacheImage) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl, cacheImage));
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imageStream the InputStream of the image to use
     */
    public
    void setImage(final InputStream imageStream) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream, true));
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param imageStream the InputStream of the image to use
     * @param cacheImage true to cache the image (only if the image is resized as necessary)
     */
    public
    void setImage(final InputStream imageStream, final boolean cacheImage) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream, cacheImage));
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param image the image of the image to use
     */
    public
    void setImage(final Image image) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, image, true));
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param image the image of the image to use
     * @param cacheImage true to cache the image (only if the image is resized as necessary)
     */
    public
    void setImage(final Image image, final boolean cacheImage) {
        setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, image, cacheImage));
    }

    /**
     * @return true if this menu entry has an image assigned to it, or is just text.
     */
    public
    boolean hasImage() {return imageFile != null;}

    /**
     * Sets a callback for a menu entry. This is the action that occurs when one clicks the menu entry
     *
     * @param callback the callback to set. If null, the callback is safely removed.
     */
    public
    void setCallback(final ActionListener callback) {
        this.callback = callback;

        if (hook != null) {
            ((MenuItemHook) hook).setCallback(this);
        }
    }

    /**
     * Gets the shortcut key for this menu entry (Mnemonic) which is what menu entry uses to be "selected" via the keyboard while the
     * menu is displayed.
     *
     * Mnemonics are case-insensitive, and if the character defined by the mnemonic is found within the text, the first occurrence
     * of it will be underlined.
     */
    public
    char getShortcut() {
        return this.mnemonicKey;
    }

    /**
     * Sets a menu entry shortcut key (Mnemonic) so that menu entry can be "selected" via the keyboard while the menu is displayed.
     *
     * Mnemonics are case-insensitive, and if the character defined by the mnemonic is found within the text, the first occurrence
     * of it will be underlined.
     *
     * @param key this is the key to set as the mnemonic
     */
    public
    void setShortcut(final char key) {
        this.mnemonicKey = key;

        if (hook != null) {
            ((MenuItemHook) hook).setShortcut(this);
        }
    }

    @Override
    public synchronized
    void remove() {
        if (hook != null) {
            setImage_(null);
            setText(null);
            setCallback(null);
        }

        super.remove();
    }
}
