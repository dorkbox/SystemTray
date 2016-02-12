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

import dorkbox.systemTray.linux.AppIndicatorTray;
import dorkbox.systemTray.linux.GnomeShellExtension;
import dorkbox.systemTray.swing.SwingSystemTray;
import dorkbox.util.OS;
import dorkbox.util.Property;
import dorkbox.util.jna.linux.AppIndicator;
import dorkbox.util.jna.linux.AppIndicatorQuery;
import dorkbox.util.jna.linux.GtkSupport;
import dorkbox.util.process.ShellProcessBuilder;
import dorkbox.systemTray.linux.GtkSystemTray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * Interface for system tray implementations.
 */
@SuppressWarnings("unused")
public abstract
class SystemTray {
    protected static final Logger logger = LoggerFactory.getLogger(SystemTray.class);

    @Property
    /** Size of the tray, so that the icon can properly scale based on OS. (if it's not exact) */
    public static int TRAY_SIZE = 22;

    private static Class<? extends SystemTray> trayType;

    static boolean isKDE = false;

    static {
        // Note: AppIndicators DO NOT support tooltips. We could try to create one, by creating a GTK widget and attaching it on
        // mouseover or something, but I don't know how to do that. It seems that tooltips for app-indicators are a custom job, as
        // all examined ones sometimes have it (and it's more than just text), or they don't have it at all.

        if (OS.isWindows()) {
            // the tray icon size in windows is DIFFERENT than on Mac (TODO: test on mac with retina stuff).
            TRAY_SIZE -= 4;
        }

        if (OS.isLinux()) {
            if (GtkSupport.isSupported) {
                // see: https://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running

                // quick check, because we know that unity uses app-indicator. Maybe REALLY old versions do not. We support 14.04 LTE at least
                String XDG = System.getenv("XDG_CURRENT_DESKTOP");
                if ("Unity".equalsIgnoreCase(XDG)) {
                    try {
                        trayType = AppIndicatorTray.class;
                    } catch (Throwable ignored) {
                    }
                }
                else if ("XFCE".equalsIgnoreCase(XDG)) {
                    // XFCE uses a BAD version of libappindicator by default, which DOES NOT support images in the menu.
                    // if we have libappindicator1, we are OK. if we don't, fallback to GTKSystemTray
                    try {
                        if (AppIndicatorQuery.get_v1() != null) {
                            trayType = AppIndicatorTray.class;
                        } else {
                            trayType = GtkSystemTray.class;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                else if ("LXDE".equalsIgnoreCase(XDG)) {
                    try {
                        trayType = GtkSystemTray.class;
                    } catch (Throwable ignored) {
                    }
                }
                else if ("KDE".equalsIgnoreCase(XDG)) {
                    isKDE = true;
                    try {
                        trayType = AppIndicatorTray.class;
                    } catch (Throwable ignored) {
                    }
                }
                else if ("GNOME".equalsIgnoreCase(XDG)) {
                    // check other DE
                    String GDM = System.getenv("GDMSESSION");

                    if ("cinnamon".equalsIgnoreCase(GDM)) {
                        try {
                            trayType = GtkSystemTray.class;
                        } catch (Throwable ignored) {
                        }
                    }
                    else if ("gnome-classic".equalsIgnoreCase(GDM)) {
                        try {
                            trayType = GtkSystemTray.class;
                        } catch (Throwable ignored) {
                        }
                    }
                    else if ("gnome-fallback".equalsIgnoreCase(GDM)) {
                        try {
                            trayType = GtkSystemTray.class;
                        } catch (Throwable ignored) {
                        }
                    }


                    // unknown exactly, install extension and go from there
                    if (trayType == null) {
                        // if the "topicons" extension is installed, don't install us (because it will override what we do, where ours
                        // is more specialized - so it only modified our tray icon (instead of ALL tray icons)

                        try {
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8196);
                            PrintStream outputStream = new PrintStream(byteArrayOutputStream);

                            // gnome-shell --version
                            final ShellProcessBuilder shellVersion = new ShellProcessBuilder(outputStream);
                            shellVersion.setExecutable("gnome-shell");
                            shellVersion.addArgument("--version");
                            shellVersion.start();

                            String output = ShellProcessBuilder.getOutput(byteArrayOutputStream);

                            if (!output.isEmpty()) {
                                GnomeShellExtension.install(logger, output);
                                trayType = GtkSystemTray.class;
                            }
                        } catch (Throwable ignored) {
                            trayType = null;
                        }
                    }
                }

                // Try to autodetect if we can use app indicators (or if we need to fallback to GTK indicators)
                if (trayType == null) {
                    BufferedReader bin = null;
                    try {
                        // the ONLY guaranteed way to determine if indicator-application-service is running (and thus, using app-indicator),
                        // is to look through all /proc/<pid>/status, and first line should be Name:\tindicator-appli
                        File proc = new File("/proc");
                        File[] listFiles = proc.listFiles();
                        if (listFiles != null) {
                            for (File procs : listFiles) {
                                String name = procs.getName();

                                if (!Character.isDigit(name.charAt(0))) {
                                    continue;
                                }

                                File status = new File(procs, "status");
                                if (!status.canRead()) {
                                    continue;
                                }

                                try {
                                    bin = new BufferedReader(new FileReader(status));
                                    String readLine = bin.readLine();

                                    if (readLine != null && readLine.contains("indicator-app")) {
                                        // make sure we can also load the library (it might be the wrong version)
                                        try {
                                            //noinspection unused
                                            final AppIndicator instance = AppIndicator.INSTANCE;
                                            trayType = AppIndicatorTray.class;
                                        } catch (Throwable e) {
                                            logger.error("AppIndicator support detected, but unable to load the library. Falling back to GTK");
                                            e.printStackTrace();
                                        }
                                        break;
                                    }
                                } finally {
                                    if (bin != null) {
                                        bin.close();
                                        bin = null;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (bin != null) {
                            try {
                                bin.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }


                // fallback...
                if (trayType == null) {
                    trayType = GtkSystemTray.class;
                }

                if (trayType == null) {
                    logger.error("Unable to load the system tray native library. Please write an issue and include your OS type and " +
                                 "configuration");
                }
            }
        }

        // this is windows OR mac
        if (trayType == null && java.awt.SystemTray.isSupported()) {
            trayType = SwingSystemTray.class;
        }

        if (trayType == null) {
            // unsupported tray
            logger.error("Unsupported tray type!");
        }
        else {
            try {
                ImageUtil.init();
            } catch (NoSuchAlgorithmException e) {
                logger.error("Unsupported hashing algorithm!");
                trayType = null;
            }
        }
    }

    /**
     * Gets the version number.
     */
    public static
    String getVersion() {
        return "1.15";
    }

    /**
     * Because the cross-platform, underlying system uses a file path to load icons for the system tray, this will directly use the
     * contents of the specified file.
     *
     * @param iconPath the full path for an icon to use
     *
     * @return a new SystemTray instance with the specified path for the icon
     */
    public static
    SystemTray create(String iconPath) {
        if (trayType != null) {
            try {
                iconPath = ImageUtil.iconPath(iconPath);
                Object o = trayType.getConstructors()[0].newInstance(iconPath);
                return (SystemTray) o;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        // unsupported
        return null;
    }

    /**
     * Because the cross-platform, underlying system uses a file path to load icons for the system tray, this will copy the contents of
     * the URL to a temporary location on disk, based on the path specified by the URL.
     *
     * @param iconUrl the URL for the icon to use
     *
     * @return a new SystemTray instance with the specified URL for the icon
     */
    public static
    SystemTray create(final URL iconUrl) {
        if (trayType != null) {
            try {
                String iconPath = ImageUtil.iconPath(iconUrl);
                Object o = trayType.getConstructors()[0].newInstance(iconPath);
                return (SystemTray) o;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        // unsupported
        return null;
    }

    /**
     * Because the cross-platform, underlying system uses a file path to load icons for the system tray, this will copy the contents of
     * the iconStream to a temporary location on disk, based on the `cacheName` specified.
     *
     * @param cacheName the name to use for the cache lookup for the iconStream. This can be anything you want, but should be
     *                  consistently unique
     * @param iconStream the InputStream to load the icon from
     *
     * @return a new SystemTray instance with the specified InputStream for the icon
     */
    public static
    SystemTray create(final String cacheName, final InputStream iconStream) {
        if (trayType != null) {
            try {
                String iconPath = ImageUtil.iconPath(cacheName, iconStream);
                Object o = trayType.getConstructors()[0].newInstance(iconPath);
                return (SystemTray) o;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        // unsupported
        return null;
    }

    /**
     * Because the cross-platform, underlying system uses a file path to load icons for the system tray, this will copy the contents of
     * the iconStream to a temporary location on disk.
     *
     * This method **DOES NOT CACHE** the result, so multiple lookups for the same inputStream result in new files every time. This is
     * also NOT RECOMMENDED, but is provided for simplicity.
     *
     * @param iconStream the InputStream to load the icon from
     *
     * @return a new SystemTray instance with the specified InputStream for the icon
     */
    @Deprecated
    public static
    SystemTray create(final InputStream iconStream) {
        if (trayType != null) {
            try {
                String iconPath = ImageUtil.iconPathNoCache(iconStream);
                Object o = trayType.getConstructors()[0].newInstance(iconPath);
                return (SystemTray) o;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        // unsupported
        return null;
    }



    protected final java.util.List<MenuEntry> menuEntries = new ArrayList<MenuEntry>();

    protected
    SystemTray() {
    }

    protected
    MenuEntry getMenuEntry(String menuText) {
        for (MenuEntry entry : menuEntries) {
            if (entry.getText().equals(menuText)) {
                return entry;
            }
        }

        return null;
    }


    public abstract
    void shutdown();


    /**
     * Sets a 'status' string at the first position in the popup menu. This 'status' string appears as a disabled menu entry.
     *
     * @param infoString the text you want displayed, null if you want to remove the 'status' string
     */
    public abstract
    void setStatus(String infoString);

    protected abstract
    void setIcon_(String iconPath);

    /**
     * Changes the tray icon used.
     *
     * @param imagePath the path of the icon to use
     */
    public
    void setIcon(String imagePath) throws IOException {
        final String fullPath = ImageUtil.iconPath(imagePath);
        setIcon_(fullPath);
    }

    /**
     * Changes the tray icon used.
     *
     * @param imageUrl the URL of the icon to use
     */
    public
    void setIcon(URL imageUrl) throws IOException {
        final String fullPath = ImageUtil.iconPath(imageUrl);
        setIcon_(fullPath);
    }

    /**
     * Changes the tray icon used.
     *
     * @param cacheName the name to use for lookup in the cache for the iconStream
     * @param imageStream the InputStream of the icon to use
     */
    public
    void setIcon(String cacheName, InputStream imageStream) throws IOException {
        final String fullPath = ImageUtil.iconPath(cacheName, imageStream);
        setIcon_(fullPath);
    }

    /**
     * Changes the tray icon used.
     *
     * This method **DOES NOT CACHE** the result, so multiple lookups for the same inputStream result in new files every time. This is
     * also NOT RECOMMENDED, but is provided for simplicity.
     *
     * @param imageStream the InputStream of the icon to use
     */
    @Deprecated
    public
    void setIcon(InputStream imageStream) throws IOException {
        final String fullPath = ImageUtil.iconPathNoCache(imageStream);
        setIcon_(fullPath);
    }


    /**
     * Adds a menu entry to the tray icon with text (no image)
     *
     * @param menuText string of the text you want to appear
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public final
    void addMenuEntry(String menuText, SystemTrayMenuAction callback) {
        try {
            addMenuEntry(menuText, (String) null, callback);
        } catch (IOException e) {
            // should never happen
            e.printStackTrace();
        }
    }


    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imagePath the image (full path required) to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public abstract
    void addMenuEntry(String menuText, String imagePath, SystemTrayMenuAction callback) throws IOException;

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageUrl the URL of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public abstract
    void addMenuEntry(String menuText, URL imageUrl, SystemTrayMenuAction callback) throws IOException;

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param cacheName @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public abstract
    void addMenuEntry(String menuText, String cacheName, InputStream imageStream, SystemTrayMenuAction callback) throws IOException;

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * This method **DOES NOT CACHE** the result, so multiple lookups for the same inputStream result in new files every time. This is
     * also NOT RECOMMENDED, but is provided for simplicity.
     *
     * @param menuText string of the text you want to appear
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    @Deprecated
    public abstract
    void addMenuEntry(String menuText, InputStream imageStream, SystemTrayMenuAction callback) throws IOException;


    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param origMenuText the original menu text
     * @param newMenuText the new menu text (this will replace the original menu text)
     */
    public final synchronized
    void updateMenuEntry_Text(String origMenuText, String newMenuText) {
        MenuEntry menuEntry = getMenuEntry(origMenuText);

        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
        else {
            menuEntry.setText(newMenuText);
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param origMenuText the original menu text
     * @param imagePath the new path for the image to use or null to delete the image
     */
    public final synchronized
    void updateMenuEntry_Image(String origMenuText, String imagePath) throws IOException {
        MenuEntry menuEntry = getMenuEntry(origMenuText);

        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
        else {
            menuEntry.setImage(imagePath);
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param origMenuText the original menu text
     * @param imageUrl the new URL for the image to use or null to delete the image
     */
    public final synchronized
    void updateMenuEntry_Image(String origMenuText, URL imageUrl) throws IOException {
        MenuEntry menuEntry = getMenuEntry(origMenuText);

        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
        else {
            menuEntry.setImage(imageUrl);
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use or null to delete the image
     */
    public final synchronized
    void updateMenuEntry_Image(String origMenuText, String cacheName, InputStream imageStream) throws IOException {
        MenuEntry menuEntry = getMenuEntry(origMenuText);

        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
        else {
            menuEntry.setImage(cacheName, imageStream);
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * This method **DOES NOT CACHE** the result, so multiple lookups for the same inputStream result in new files every time. This is
     * also NOT RECOMMENDED, but is provided for simplicity.
     *
     * @param origMenuText the original menu text
     * @param imageStream the new path for the image to use or null to delete the image
     */
    @Deprecated
    public final synchronized
    void updateMenuEntry_Image(String origMenuText, InputStream imageStream) throws IOException {
        MenuEntry menuEntry = getMenuEntry(origMenuText);

        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
        else {
            menuEntry.setImage(imageStream);
        }
    }

    /**
     * Updates (or changes) the menu entry's callback.
     *
     * @param origMenuText the original menu text
     * @param newCallback the new callback (this will replace the original callback)
     */
    public final synchronized
    void updateMenuEntry_Callback(String origMenuText, SystemTrayMenuAction newCallback) {
        MenuEntry menuEntry = getMenuEntry(origMenuText);

        if (menuEntry != null) {
            menuEntry.setCallback(newCallback);
        }
        else {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
    }


    /**
     * Updates (or changes) the menu entry's text and callback. This effectively replaces the menu entry with a new one.
     *
     * @param origMenuText the original menu text
     * @param newMenuText the new menu text (this will replace the original menu text)
     * @param newCallback the new callback (this will replace the original callback)
     */
    public final synchronized
    void updateMenuEntry(String origMenuText, String newMenuText, SystemTrayMenuAction newCallback) {
        MenuEntry menuEntry = getMenuEntry(origMenuText);

        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
        else {
            menuEntry.setText(newMenuText);
            menuEntry.setCallback(newCallback);
        }
    }


    /**
     *  This removes a menu entry from the dropdown menu.
     *
     * @param menuEntry This is the menu entry to remove
     */
    public final synchronized
    void removeMenuEntry(final MenuEntry menuEntry) {
        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for menuEntry");
        }

        final String label = menuEntry.getText();

        for (Iterator<MenuEntry> iterator = menuEntries.iterator(); iterator.hasNext(); ) {
            final MenuEntry entry = iterator.next();
            if (entry.getText()
                     .equals(label)) {
                iterator.remove();

                // this will also reset the menu
                menuEntry.remove();
                return;
            }
        }
        throw new NullPointerException("Menu entry '" + label + "'not found in list while trying to remove it.");
    }


    /**
     *  This removes a menu entry (via the text label) from the dropdown menu.
     *
     * @param menuText This is the label for the menu entry to remove
     */
    public final synchronized
    void removeMenuEntry(final String menuText) {
        MenuEntry menuEntry = getMenuEntry(menuText);

        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for string '" + menuText + "'");
        }
        else {
            removeMenuEntry(menuEntry);
        }
    }
}

