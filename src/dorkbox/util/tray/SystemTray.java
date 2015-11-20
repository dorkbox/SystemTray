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
package dorkbox.util.tray;

import dorkbox.util.OS;
import dorkbox.util.Property;
import dorkbox.util.jna.linux.AppIndicator;
import dorkbox.util.jna.linux.AppIndicatorQuery;
import dorkbox.util.jna.linux.GtkSupport;
import dorkbox.util.process.ShellProcessBuilder;
import dorkbox.util.tray.linux.AppIndicatorTray;
import dorkbox.util.tray.linux.GnomeShellExtension;
import dorkbox.util.tray.linux.GtkSystemTray;
import dorkbox.util.tray.swing.SwingSystemTray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * Interface for system tray implementations.
 */
@SuppressWarnings("unused")
public abstract
class SystemTray {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static MessageDigest digest;

    protected static final Logger logger = LoggerFactory.getLogger(SystemTray.class);

    @Property
    /** Size of the tray, so that the icon can properly scale based on OS. (if it's not exact) */
    public static int TRAY_SIZE = 22;

    @Property
    /** Location of the icon (to make it easier when specifying icons) */
    public static String ICON_PATH = "";

    private static final long runtimeRandom = new SecureRandom().nextLong();
    private static Class<? extends SystemTray> trayType;

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
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                logger.error("Unsupported hashing algorithm!");
                trayType = null;
            }
        }
    }

    public static
    SystemTray create(final String iconPath) {
        if (trayType != null) {
            try {
                Constructor<?> constructor = trayType.getConstructors()[0];
                Object o = constructor.newInstance(iconPath);
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


    /**
     * Changes the tray icon used
     *
     * @param iconName the path of the icon to use
     */
    public abstract
    void setIcon(String iconName);


    /**
     * Adds a menu entry to the tray icon with text (no image)
     *
     * @param menuText string of the text you want to appear
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public final
    void addMenuEntry(String menuText, SystemTrayMenuAction callback) {
        addMenuEntry(menuText, null, callback);
    }


    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imagePath the image (full path required) to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public abstract
    void addMenuEntry(String menuText, String imagePath, SystemTrayMenuAction callback);


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
     * @param imagePath the new path for the image to use. Null to remove an image.
     */
    public final synchronized
    void updateMenuEntry_Image(String origMenuText, String imagePath) {
        MenuEntry menuEntry = getMenuEntry(origMenuText);

        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
        else {
            menuEntry.setImage(imagePath);
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


    /**
     * UNSAFE. must be called inside sync
     *
     *  appIndicator/gtk require strings (which is the path)
     *  swing version loads as an image (which can be stream or path, we use path)
     */
    protected final
    String iconPath(String fileName) {
        // is file sitting on drive
        File iconTest;
        if (ICON_PATH.isEmpty()) {
            iconTest = new File(fileName);
        }
        else {
            iconTest = new File(ICON_PATH, fileName);
        }
        if (iconTest.isFile() && iconTest.canRead()) {
            return iconTest.getAbsolutePath();
        }
        else {
            if (!ICON_PATH.isEmpty()) {
                fileName = ICON_PATH + "/" + fileName;
            }

            String extension = "";
            int dot = fileName.lastIndexOf('.');
            if (dot > -1) {
                extension = fileName.substring(dot + 1);
            }

            // maybe it's in somewhere else.
            URL systemResource = Thread.currentThread().getContextClassLoader().getResource(fileName);
            if (systemResource == null) {
                // maybe it's in the system classloader?
                systemResource = ClassLoader.getSystemResource(fileName);
            }

            if (systemResource != null) {
                // copy out to a temp file, as a hash of the file
                String resourceFileName = systemResource.getPath();
                byte[] bytes = resourceFileName.getBytes(UTF_8);
                File newFile;
                File tempDir = new File(System.getProperty("java.io.tmpdir"));

                // can be wimpy, only one at a time
                synchronized (SystemTray.this) {
                    // delete all icons in the temp dir (they can accumulate)
                    String ICON_PREFIX = "SYSTRAY";

                    File[] files = tempDir.listFiles();
                    if (files != null) {
                        for (int i = 0; i < files.length; i++) {
                            File file = files[i];
                            if (file.getName()
                                    .startsWith(ICON_PREFIX)) {

                                //noinspection ResultOfMethodCallIgnored
                                file.delete();
                            }
                        }
                    }

                    digest.reset();
                    digest.update(bytes);


                    // For KDE4, it must also be unique across runs
                    String getenv = System.getenv("XDG_CURRENT_DESKTOP");
                    if (getenv != null && getenv.contains("kde")) {
                        byte[] longBytes = new byte[8];
                        ByteBuffer wrap = ByteBuffer.wrap(longBytes);
                        wrap.putLong(runtimeRandom);
                        digest.update(longBytes);
                    }

                    byte[] hashBytes = digest.digest();
                    String hash = new BigInteger(1, hashBytes).toString(32);

                    newFile = new File(tempDir, ICON_PREFIX + hash + '.' + extension).getAbsoluteFile();
                    newFile.deleteOnExit();
                }

                InputStream inStream = null;
                OutputStream outStream = null;

                try {
                    inStream = systemResource.openStream();
                    outStream = new FileOutputStream(newFile);

                    byte[] buffer = new byte[2048];
                    int read;
                    while ((read = inStream.read(buffer)) > 0) {
                        outStream.write(buffer, 0, read);
                    }

                    return newFile.getAbsolutePath();
                } catch (IOException e) {
                    // Send up exception
                    String message = "Unable to copy icon '" + fileName + "' to location: '" + newFile.getAbsolutePath() + "'";
                    logger.error(message, e);
                    throw new RuntimeException(message);
                } finally {
                    try {
                        if (inStream != null) {
                            inStream.close();
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        if (outStream != null) {
                            outStream.close();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // Send up exception
        String message = "Unable to find icon '" + fileName + "'";
        logger.error(message);
        throw new RuntimeException(message);
    }
}
