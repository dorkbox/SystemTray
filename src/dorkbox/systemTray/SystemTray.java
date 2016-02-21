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
import dorkbox.systemTray.linux.GtkSystemTray;
import dorkbox.systemTray.linux.jna.AppIndicator;
import dorkbox.systemTray.linux.jna.GtkSupport;
import dorkbox.systemTray.swing.SwingSystemTray;
import dorkbox.util.OS;
import dorkbox.util.Property;
import dorkbox.util.process.ShellProcessBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * Factory and base-class for system tray implementations.
 */
@SuppressWarnings({"unused", "Duplicates"})
public abstract
class SystemTray {
    protected static final Logger logger = LoggerFactory.getLogger(SystemTray.class);

    @Property
    /** Size of the tray, so that the icon can properly scale based on OS. (if it's not exact) */
    public static int TRAY_SIZE = 22;

    @Property
    /** Forces the system tray to always choose GTK2 (even when GTK3 might be available). */
    public static boolean FORCE_GTK2 = false;

    @Property
    /**
     * Forces the system to enter into JavaFX/SWT compatibility mode, where it will use GTK2 AND will not start/stop the GTK main loop.
     * This is only necessary if autodetection fails.
     */
    public static boolean COMPATIBILITY_MODE = false;

    @Property
    /**
     * When in compatibility mode, and the JavaFX/SWT primary windows are closed, we want to make sure that the SystemTray is also closed.
     * This property is available to disable this functionality in situations where you don't want this to happen.
     */
    public static boolean ENABLE_SHUTDOWN_HOOK = true;

    private static volatile SystemTray systemTray = null;
    static boolean isKDE = false;

    private static void init() {
        if (systemTray != null) {
            return;
        }

        // no tray in a headless environment
        if (GraphicsEnvironment.isHeadless()) {
            throw new HeadlessException();
        }

        Class<? extends SystemTray> trayType = null;

        boolean isJavaFxLoaded = false;
        boolean isSwtLoaded = false;
        try {
            // First check if JavaFX is loaded - if it's NOT LOADED, then we only proceed if JAVAFX_COMPATIBILITY_MODE is enabled.
            // this is important, because if JavaFX is not being used, calling getToolkit() will initialize it...
            java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            m.setAccessible(true);
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            isJavaFxLoaded = null != m.invoke(cl, "com.sun.javafx.tk.Toolkit");
            isSwtLoaded = null != m.invoke(cl, "org.eclipse.swt.widgets.Display");
        } catch (Throwable ignored) {
        }

        // maybe we should load the SWT version? (In order for us to work with SWT, BOTH must be GTK2!!
        COMPATIBILITY_MODE = OS.isLinux() && (isJavaFxLoaded || isSwtLoaded);

        // kablooie if SWT is not configured in a way that works with us.
        if (OS.isLinux() && isSwtLoaded) {
            // Necessary for us to work with SWT
            // System.setProperty("SWT_GTK3", "0"); // Necessary for us to work with SWT

            // was SWT forced?
            boolean isSwt_GTK3 = !System.getProperty("SWT_GTK3").equals("0");
            if (!isSwt_GTK3) {
                // check a different property
                isSwt_GTK3 = !System.getProperty("org.eclipse.swt.internal.gtk.version").startsWith("2.");
            }

            if (isSwt_GTK3) {
                logger.error("Unable to use the SystemTray when SWT is configured to use GTK3. Please configure SWT to use GTK2, one such " +
                             "example is to set the system property `System.setProperty(\"SWT_GTK3\", \"0\");` before SWT is initialized");

                throw new RuntimeException("SWT configured to use GTK3 and is incompatible with the SystemTray.");
            }
        }


        // Note: AppIndicators DO NOT support tooltips. We could try to create one, by creating a GTK widget and attaching it on
        // mouseover or something, but I don't know how to do that. It seems that tooltips for app-indicators are a custom job, as
        // all examined ones sometimes have it (and it's more than just text), or they don't have it at all.

        if (OS.isWindows()) {
            // the tray icon size in windows is DIFFERENT than on Mac (TODO: test on mac with retina stuff).
            TRAY_SIZE -= 4;
        }

        if (OS.isLinux()) {
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
                try {
                    trayType = AppIndicatorTray.class;
                } catch (Throwable ignored) {
                    // we can fail on AppIndicator, so this is the fallback
                    //noinspection EmptyCatchBlock
                    try {
                        trayType = GtkSystemTray.class;
                    } catch (Throwable i) {
                    }
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

                                        if (AppIndicator.IS_VERSION_3) {

                                        }
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
                logger.error("Unable to load the system tray native library. Please write an issue and include your OS type and " +
                             "configuration");
            }
        }

        // this is windows OR mac
        if (trayType == null && java.awt.SystemTray.isSupported()) {
            try {
                java.awt.SystemTray.getSystemTray();
                trayType = SwingSystemTray.class;
            } catch (Throwable ignored) {
                logger.error("Maybe you should grant the AWTPermission `accessSystemTray` in the SecurityManager.");
            }
        }

        if (trayType == null) {
            // unsupported tray
            logger.error("Unable to discover what tray implementation to use!");
            systemTray = null;
        }
        else {
            SystemTray systemTray_ = null;

            try {
                ImageUtil.init();

                // the order of checking here is critical -- AppIndicator.IS_VERSION_3 initializes `appindicator` and `gtk`
                if (OS.isLinux() &&
                    trayType == AppIndicatorTray.class &&
                    AppIndicator.IS_VERSION_3 && // this initializes the appindicator (since we specified that via the trayType)
                    GtkSupport.isGtk2) {

                    // NOTE:
                    //  ALSO WHAT VERSION OF GTK to use? appindiactor1 -> GTk2, appindicator3 -> GTK3.
                    // appindicator3 doesn't support menu icons via GTK2. AT THIS POINT, we DO NOT have GTK3
                    try {
                        trayType = GtkSystemTray.class;
                        logger.warn("AppIndicator3 detected with GTK2, falling back to GTK2 system tray type.  " +
                                    "Please install libappindicator1 OR GTK3, for example: 'sudo apt-get install libappindicator1'");
                    } catch (Throwable ignored) {
                        logger.error("AppIndicator3 detected with GTK2 and unable to fallback to using GTK2 system tray type." +
                                     "AppIndicator3 requires GTK3 to be fully functional, and while this will work -- the menu icons WILL " +
                                     "NOT be visible." +
                                     " Please install libappindicator1 OR GTK3, for example: 'sudo apt-get install libappindicator1'");
                    }
                }

                systemTray_ = (SystemTray) trayType.getConstructors()[0].newInstance();

                logger.info("Successfully Loaded: {}", trayType.getSimpleName());
            } catch (NoSuchAlgorithmException e) {
                logger.error("Unsupported hashing algorithm!");
            } catch (Exception e) {
                logger.error("Unable to create tray type: '" + trayType.getSimpleName() + "'", e);
            }

            systemTray = systemTray_;


            // These install a shutdown hook in JavaFX/SWT, so that when the main window is closed -- the system tray is ALSO closed.
            if (COMPATIBILITY_MODE && ENABLE_SHUTDOWN_HOOK) {
                if (isJavaFxLoaded) {
                    // Necessary because javaFX **ALSO** runs a gtk main loop, and when it stops (if we don't stop first), we become unresponsive.
                    // Also, it's nice to have us shutdown at the same time as the main application

                    // com.sun.javafx.tk.Toolkit.getToolkit()
                    //                          .addShutdownHook(new Runnable() {
                    //                              @Override
                    //                              public
                    //                              void run() {
                    //                                  systemTray.shutdown();
                    //                              }
                    //                          });

                    try {
                        Class<?> clazz = Class.forName("com.sun.javafx.tk.Toolkit");
                        Method method = clazz.getMethod("getToolkit");
                        Object o = method.invoke(null);
                        Method runnable = o.getClass()
                                           .getMethod("addShutdownHook", Runnable.class);
                        runnable.invoke(o, new Runnable() {
                            @Override
                            public
                            void run() {
                                systemTray.shutdown();
                            }
                        });
                    } catch (Throwable ignored) {
                        logger.error("Unable to insert shutdown hook into JavaFX. Please create an issue with your OS and Java " +
                                     "version so we may further investigate this issue.");
                    }
                }
                else if (isSwtLoaded) {
                    // this is because SWT **ALSO** runs a gtk main loop, and when it stops (if we don't stop first), we become unresponsive
                    // Also, it's nice to have us shutdown at the same time as the main application

                    // During compile time (for production), this class is not compiled, and instead is copied over as a pre-compiled file
                    // This is so we don't have to rely on having SWT as part of the classpath during build.
                    try {
                        Class<?> clazz = Class.forName("dorkbox.systemTray.swt.Swt");
                        Method method = clazz.getMethod("onShutdown", Runnable.class);
                        Object o = method.invoke(null, new Runnable() {
                            @Override
                            public
                            void run() {
                                systemTray.shutdown();
                            }
                        });
                    } catch (Throwable ignored) {
                        logger.error("Unable to insert shutdown hook into SWT. Please create an issue with your OS and Java " +
                                     "version so we may further investigate this issue.");
                    }
                }
            }
        }
    }


    /**
     * Gets the version number.
     */
    public static
    String getVersion() {
        return "2.12";
    }

    /**
     * This always returns the same instance per JVM (it's a singleton), and on some platforms the system tray may not be
     * supported, in which case this will return NULL.
     *
     * <p>If this is using the Swing SystemTray and a SecurityManager is installed, the AWTPermission {@code accessSystemTray} must
     * be granted in order to get the {@code SystemTray} instance. Otherwise this will return null.
     */
    public static
    SystemTray getSystemTray() {
        init();
        return systemTray;
    }

    protected final java.util.List<MenuEntry> menuEntries = new ArrayList<MenuEntry>();

    protected
    SystemTray() {
    }


    /**
     * Must be wrapped in a synchronized block for object visibility
     */
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
     * Gets the 'status' string assigned to the system tray
     */
    public abstract
    String getStatus();

    /**
     * Sets a 'status' string at the first position in the popup menu. This 'status' string appears as a disabled menu entry.
     *
     * @param statusText the text you want displayed, null if you want to remove the 'status' string
     */
    public abstract
    void setStatus(String statusText);

    protected abstract
    void setIcon_(String iconPath);

    /**
     * Changes the tray icon used.
     *
     * Because the cross-platform, underlying system uses a file path to load icons for the system tray,
     * this will directly use the contents of the specified file.
     *
     * @param imagePath the path of the icon to use
     */
    public
    void setIcon(String imagePath) {
        final String fullPath = ImageUtil.iconPath(imagePath);
        setIcon_(fullPath);
    }

    /**
     * Changes the tray icon used.
     *
     * Because the cross-platform, underlying system uses a file path to load icons for the system tray, this will copy the contents of
     * the URL to a temporary location on disk, based on the path specified by the URL.
     *
     * @param imageUrl the URL of the icon to use
     */
    public
    void setIcon(URL imageUrl) {
        final String fullPath = ImageUtil.iconPath(imageUrl);
        setIcon_(fullPath);
    }

    /**
     * Changes the tray icon used.
     *
     * Because the cross-platform, underlying system uses a file path to load icons for the system tray, this will copy the contents of
     * the imageStream to a temporary location on disk, based on the `cacheName` specified.
     *
     * @param cacheName the name to use for lookup in the cache for the iconStream
     * @param imageStream the InputStream of the icon to use
     */
    public
    void setIcon(String cacheName, InputStream imageStream) {
        final String fullPath = ImageUtil.iconPath(cacheName, imageStream);
        setIcon_(fullPath);
    }

    /**
     * Changes the tray icon used.
     *
     * Because the cross-platform, underlying system uses a file path to load icons for the system tray, this will copy the contents of
     * the imageStream to a temporary location on disk.
     *
     * This method **DOES NOT CACHE** the result, so multiple lookups for the same inputStream result in new files every time. This is
     * also NOT RECOMMENDED, but is provided for simplicity.
     *
     * @param imageStream the InputStream of the icon to use
     */
    @Deprecated
    public
    void setIcon(InputStream imageStream) {
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
        addMenuEntry(menuText, (String) null, callback);
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
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageUrl the URL of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public abstract
    void addMenuEntry(String menuText, URL imageUrl, SystemTrayMenuAction callback);

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param cacheName @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public abstract
    void addMenuEntry(String menuText, String cacheName, InputStream imageStream, SystemTrayMenuAction callback);

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
    void addMenuEntry(String menuText, InputStream imageStream, SystemTrayMenuAction callback);


    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param origMenuText the original menu text
     * @param newMenuText the new menu text (this will replace the original menu text)
     */
    public final
    void updateMenuEntry_Text(String origMenuText, String newMenuText) {
        synchronized (menuEntries) {
            MenuEntry menuEntry = getMenuEntry(origMenuText);

            if (menuEntry == null) {
                throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
            }
            else {
                menuEntry.setText(newMenuText);
            }
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param origMenuText the original menu text
     * @param imagePath the new path for the image to use or null to delete the image
     */
    public final
    void updateMenuEntry_Image(String origMenuText, String imagePath) {
        synchronized (menuEntries) {
            MenuEntry menuEntry = getMenuEntry(origMenuText);

            if (menuEntry == null) {
                throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
            }
            else {
                menuEntry.setImage(imagePath);
            }
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param origMenuText the original menu text
     * @param imageUrl the new URL for the image to use or null to delete the image
     */
    public final
    void updateMenuEntry_Image(String origMenuText, URL imageUrl) {
        synchronized (menuEntries) {
            MenuEntry menuEntry = getMenuEntry(origMenuText);

            if (menuEntry == null) {
                throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
            }
            else {
                menuEntry.setImage(imageUrl);
            }
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use or null to delete the image
     */
    public final
    void updateMenuEntry_Image(String origMenuText, String cacheName, InputStream imageStream) {
        synchronized (menuEntries) {
            MenuEntry menuEntry = getMenuEntry(origMenuText);

            if (menuEntry == null) {
                throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
            }
            else {
                menuEntry.setImage(cacheName, imageStream);
            }
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
    public final
    void updateMenuEntry_Image(String origMenuText, InputStream imageStream) {
        synchronized (menuEntries) {
            MenuEntry menuEntry = getMenuEntry(origMenuText);

            if (menuEntry == null) {
                throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
            }
            else {
                menuEntry.setImage(imageStream);
            }
        }
    }

    /**
     * Updates (or changes) the menu entry's callback.
     *
     * @param origMenuText the original menu text
     * @param newCallback the new callback (this will replace the original callback)
     */
    public final
    void updateMenuEntry_Callback(String origMenuText, SystemTrayMenuAction newCallback) {
        synchronized (menuEntries) {
            MenuEntry menuEntry = getMenuEntry(origMenuText);

            if (menuEntry != null) {
                menuEntry.setCallback(newCallback);
            }
            else {
                throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
            }
        }
    }


    /**
     * Updates (or changes) the menu entry's text and callback. This effectively replaces the menu entry with a new one.
     *
     * @param origMenuText the original menu text
     * @param newMenuText the new menu text (this will replace the original menu text)
     * @param newCallback the new callback (this will replace the original callback)
     */
    public final
    void updateMenuEntry(String origMenuText, String newMenuText, SystemTrayMenuAction newCallback) {
        synchronized (menuEntries) {
            MenuEntry menuEntry = getMenuEntry(origMenuText);

            if (menuEntry == null) {
                throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
            }
            else {
                menuEntry.setText(newMenuText);
                menuEntry.setCallback(newCallback);
            }
        }
    }


    /**
     *  This removes a menu entry from the dropdown menu.
     *
     * @param menuEntry This is the menu entry to remove
     */
    public final
    void removeMenuEntry(final MenuEntry menuEntry) {
        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for menuEntry");
        }

        final String label = menuEntry.getText();

        synchronized (menuEntries) {
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
        }
        throw new NullPointerException("Menu entry '" + label + "'not found in list while trying to remove it.");
    }


    /**
     *  This removes a menu entry (via the text label) from the dropdown menu.
     *
     * @param menuText This is the label for the menu entry to remove
     */
    public final
    void removeMenuEntry(final String menuText) {
        synchronized (menuEntries) {
            MenuEntry menuEntry = getMenuEntry(menuText);

            if (menuEntry == null) {
                throw new NullPointerException("No menu entry exists for string '" + menuText + "'");
            }
            else {
                removeMenuEntry(menuEntry);
            }
        }
    }
}

