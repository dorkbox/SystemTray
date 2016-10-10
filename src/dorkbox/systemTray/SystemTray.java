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

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dorkbox.systemTray.linux.GnomeShellExtension;
import dorkbox.systemTray.linux.jna.AppIndicator;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.swing._AppIndicatorTray;
import dorkbox.systemTray.swing._GtkStatusIconTray;
import dorkbox.systemTray.swing._SwingTray;
import dorkbox.systemTray.util.JavaFX;
import dorkbox.systemTray.util.Swt;
import dorkbox.systemTray.util.WindowsSystemTraySwing;
import dorkbox.util.CacheUtil;
import dorkbox.util.IO;
import dorkbox.util.OS;
import dorkbox.util.Property;
import dorkbox.util.SwingUtil;
import dorkbox.util.process.ShellProcessBuilder;


/**
 * Factory and base-class for system tray implementations.
 */
@SuppressWarnings({"unused", "Duplicates", "DanglingJavadoc", "WeakerAccess"})
public
class SystemTray implements Menu {
    public static final Logger logger = LoggerFactory.getLogger(SystemTray.class);

    public static final int TYPE_AUTO_DETECT = 0;
    public static final int TYPE_GTK_STATUSICON = 1;
    public static final int TYPE_APP_INDICATOR = 2;
    public static final int TYPE_SWING = 3;

    @Property
    /** Enables auto-detection for the system tray. This should be mostly successful.
     * <p>
     * Auto-detection will use DEFAULT_WINDOWS_SIZE or DEFAULT_LINUX_SIZE as a 'base-line' for determining what size to use. On Linux,
     * `gsettings get org.gnome.desktop.interface scaling-factor` is used to determine the scale factor (for HiDPI configurations).
     * <p>
     * If auto-detection fails and the incorrect size is detected or used, disable this and specify the correct DEFAULT_WINDOWS_SIZE or
     * DEFAULT_LINUX_SIZE to use them instead
     */
    public static boolean AUTO_TRAY_SIZE = true;

    @Property
    /**
     * Size of the tray, so that the icon can be properly scaled based on OS.
     * <p>
     * This value can be automatically scaled based on the the platform and scaling-factor.
     * - Windows will automatically scale up/down.
     * - GtkStatusIcon will usually automatically scale up/down
     * - AppIndicators will not always automatically scale (it will sometimes display whatever is specified here)
     * <p>
     * You will experience WEIRD graphical glitches if this is NOT a power of 2.
     */
    public static int DEFAULT_TRAY_SIZE = 16;

    @Property
    /**
     * Size of the menu entries, so that the icon can be properly scaled based on OS.
     * <p>
     * You will experience WEIRD graphical glitches if this is NOT a power of 2.
     */
    public static int DEFAULT_MENU_SIZE = 16;

    @Property
    /** Forces the system tray to always choose GTK2 (even when GTK3 might be available). */
    public static boolean FORCE_GTK2 = false;

    @Property
    /**
     * Forces the system tray detection to be Automatic (0), GtkStatusIcon (1), AppIndicator (2), or Swing (3).
     * <p>
     * This is an advanced feature, and it is recommended to leave at 0.
     */
    public static int FORCE_TRAY_TYPE = 0;

    @Property
    /**
     * When in compatibility mode, and the JavaFX/SWT primary windows are closed, we want to make sure that the SystemTray is also closed.
     * This property is available to disable this functionality in situations where you don't want this to happen.
     * <p>
     * This is an advanced feature, and it is recommended to leave as true.
     */
    public static boolean ENABLE_SHUTDOWN_HOOK = true;

    @Property
    /**
     * This property is provided for debugging any errors in the logic used to determine the system-tray type.
     */
    public static boolean DEBUG = true;


    private static volatile SystemTray systemTray = null;
    private static volatile Menu systemTrayMenu = null;

    public final static boolean isJavaFxLoaded;
    public final static boolean isSwtLoaded;

    static {
        boolean isJavaFxLoaded_ = false;
        boolean isSwtLoaded_ = false;
        try {
            // this is important to use reflection, because if JavaFX is not being used, calling getToolkit() will initialize it...
            java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            m.setAccessible(true);
            ClassLoader cl = ClassLoader.getSystemClassLoader();

            // JavaFX Java7,8 is GTK2 only. Java9 can have it be GTK3 if -Djdk.gtk.version=3 is specified
            // see http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
            isJavaFxLoaded_ = (null != m.invoke(cl, "com.sun.javafx.tk.Toolkit")) || (null != m.invoke(cl, "javafx.application.Application"));

            // maybe we should load the SWT version? (In order for us to work with SWT, BOTH must be the same!!
            // SWT is GTK2, but if -DSWT_GTK3=1 is specified, it can be GTK3
            isSwtLoaded_ = null != m.invoke(cl, "org.eclipse.swt.widgets.Display");
        } catch (Throwable e) {
            if (DEBUG) {
                logger.debug("Error detecting javaFX/SWT mode", e);
            }
        }

        isJavaFxLoaded = isJavaFxLoaded_;
        isSwtLoaded = isSwtLoaded_;
    }


    private static void init() {
        if (systemTray != null) {
            return;
        }

        systemTray = new SystemTray();

        // no tray in a headless environment
        if (GraphicsEnvironment.isHeadless()) {
            logger.error("Cannot use the SystemTray in a headless environment");
            throw new HeadlessException();
        }

        Class<? extends Menu> trayType = null;

        if (DEBUG) {
            logger.debug("is JavaFX detected? {}", isJavaFxLoaded);
            logger.debug("is SWT detected? {}", isSwtLoaded);
        } else {
            // windows and mac ONLY support the Swing SystemTray.
            // Linux CAN support Swing SystemTray, but it looks like crap (so we wrote our own GtkStatusIcon/AppIndicator)
            if (OS.isWindows() && FORCE_TRAY_TYPE != TYPE_SWING) {
                throw new RuntimeException("Windows is incompatible with the specified option for FORCE_TRAY_TYPE: " + FORCE_TRAY_TYPE);
            } else if (OS.isMacOsX() && FORCE_TRAY_TYPE != TYPE_SWING) {
                throw new RuntimeException("MacOSx is incompatible with the specified option for FORCE_TRAY_TYPE: " + FORCE_TRAY_TYPE);
            }
        }

        // kablooie if SWT is not configured in a way that works with us.
        if (FORCE_TRAY_TYPE != TYPE_SWING && OS.isLinux()) {
            if (isSwtLoaded) {
                // Necessary for us to work with SWT based on version info. We can try to set us to be compatible with whatever it is set to
                // System.setProperty("SWT_GTK3", "0");

                // was SWT forced?
                String swt_gtk3 = System.getProperty("SWT_GTK3");
                boolean isSwt_GTK3 = swt_gtk3 != null && !swt_gtk3.equals("0");
                if (!isSwt_GTK3) {
                    // check a different property
                    String property = System.getProperty("org.eclipse.swt.internal.gtk.version");
                    isSwt_GTK3 = property != null && !property.startsWith("2.");
                }

                if (isSwt_GTK3 && FORCE_GTK2) {
                    logger.error("Unable to use the SystemTray when SWT is configured to use GTK3 and the SystemTray is configured to use " +
                                 "GTK2. Please configure SWT to use GTK2, via `System.setProperty(\"SWT_GTK3\", \"0\");` before SWT is " +
                                 "initialized, or set `SystemTray.FORCE_GTK2=false;`");

                    throw new RuntimeException("SWT configured to use GTK3 and is incompatible with the SystemTray GTK2.");
                } else if (!isSwt_GTK3 && !FORCE_GTK2) {
                    // we must use GTK2, because SWT is GTK2
                    if (DEBUG) {
                        logger.debug("Forcing GTK2 because SWT is GTK2");
                    }
                    FORCE_GTK2 = true;
                }
            }
            else if (isJavaFxLoaded) {
                // JavaFX Java7,8 is GTK2 only. Java9 can MAYBE have it be GTK3 if `-Djdk.gtk.version=3` is specified
                // see
                // http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
                // https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
                // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.
                boolean isJFX_GTK3 = System.getProperty("jdk.gtk.version", "2").equals("3");
                if (isJFX_GTK3 && FORCE_GTK2) {
                    // if we are java9, then we can change it -- otherwise we cannot.
                    if (OS.javaVersion == 9) {
                        logger.error("Unable to use the SystemTray when JavaFX is configured to use GTK3 and the SystemTray is configured to use " +
                                     "GTK2. Please configure JavaFX to use GTK2 (via `System.setProperty(\"jdk.gtk.version\", \"3\");`) " +
                                     "before JavaFX is initialized, or set `SystemTray.FORCE_GTK2=false;`");

                        throw new RuntimeException("JavaFX configured to use GTK3 and is incompatible with the SystemTray GTK2.");
                    } else {
                        logger.error("Unable to use the SystemTray when JavaFX is configured to use GTK3 and the SystemTray is configured to use " +
                                     "GTK2. Please set `SystemTray.FORCE_GTK2=false;`  if that is not possible then it will not work.");

                        throw new RuntimeException("JavaFX configured to use GTK3 and is incompatible with the SystemTray GTK2.");
                    }
                } else if (!isJFX_GTK3 && !FORCE_GTK2) {
                    // we must use GTK2, because JavaFX is GTK2
                    if (DEBUG) {
                        logger.debug("Forcing GTK2 because JavaFX is GTK2");
                    }
                    FORCE_GTK2 = true;
                }
            }
        }

        if (FORCE_TRAY_TYPE < 0 || FORCE_TRAY_TYPE > 3) {
            throw new RuntimeException("Invalid option for FORCE_TRAY_TYPE: " + FORCE_TRAY_TYPE);
        }

        if (DEBUG) {
            switch (FORCE_TRAY_TYPE) {
                case 1: logger.debug("Forced tray type: GtkStatusIcon"); break;
                case 2: logger.debug("Forced tray type: AppIndicator"); break;
                case 3: logger.debug("Forced tray type: Swing"); break;

                default: logger.debug("Auto-detecting tray type"); break;
            }
            logger.debug("FORCE_GTK2: {}", FORCE_GTK2);
        }

        // Note: AppIndicators DO NOT support tooltips. We could try to create one, by creating a GTK widget and attaching it on
        // mouseover or something, but I don't know how to do that. It seems that tooltips for app-indicators are a custom job, as
        // all examined ones sometimes have it (and it's more than just text), or they don't have it at all.

        if (FORCE_TRAY_TYPE != TYPE_SWING && OS.isLinux()) {
            // see: https://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running

            // For funsies, SyncThing did a LOT of work on compatibility (unfortunate for us) in python.
            // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

            // load up our libraries
            // NOTE:
            //  ALSO WHAT VERSION OF GTK to use? appindiactor1 -> GTk2, appindicator3 -> GTK3.
            // appindicator3 doesn't support menu icons via GTK2!!
            if (Gtk.isGtk2 || AppIndicator.isVersion3) {
                if (DEBUG) {
                    logger.debug("Loading libraries");
                }
            }

            if (SystemTray.FORCE_TRAY_TYPE == SystemTray.TYPE_GTK_STATUSICON) {
                try {
                    trayType = _GtkStatusIconTray.class;
                } catch (Throwable e1) {
                    if (DEBUG) {
                        logger.error("Cannot initialize _GtkStatusIconTray", e1);
                    }
                }
            }
            else if (SystemTray.FORCE_TRAY_TYPE == SystemTray.TYPE_APP_INDICATOR) {
                try {
                    trayType = _AppIndicatorTray.class;
                } catch (Throwable e1) {
                    if (DEBUG) {
                        logger.error("Cannot initialize _AppIndicatorTray", e1);
                    }
                }
            }
            // don't check for SWING type at this spot, it is done elsewhere.



            // quick check, because we know that unity uses app-indicator. Maybe REALLY old versions do not. We support 14.04 LTE at least
            String XDG = System.getenv("XDG_CURRENT_DESKTOP");


            // BLEH. if gnome-shell is running, IT'S REALLY GNOME!
            // we must ALWAYS do this check!!
            boolean isReallyGnome = false;
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8196);
                PrintStream outputStream = new PrintStream(byteArrayOutputStream);

                // ps a | grep [g]nome-shell
                final ShellProcessBuilder shell = new ShellProcessBuilder(outputStream);
                shell.setExecutable("ps");
                shell.addArgument("a");
                shell.start();


                String output = ShellProcessBuilder.getOutput(byteArrayOutputStream);
                isReallyGnome = output.contains("gnome-shell");
            } catch (Throwable e) {
                if (DEBUG) {
                    logger.error("Cannot detect if gnome-shell is running", e);
                }
            }

            if (isReallyGnome) {
                if (DEBUG) {
                    logger.error("Auto-detected that gnome-shell is running");
                }
                XDG = "gnome";
            }

            if (DEBUG) {
                logger.debug("Currently using the '{}' desktop", XDG);

//                Properties properties = System.getProperties();
//                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
//                    logger.debug(entry.getKey() + " : " + entry.getValue());
//                }
            }

            if (trayType == null) {
                if ("unity".equalsIgnoreCase(XDG)) {
                    try {
                        trayType = _AppIndicatorTray.class;
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot initialize _AppIndicatorTray", e);
                        }
                    }
                }
                else if ("xfce".equalsIgnoreCase(XDG)) {
                    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
                    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
                    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25

                    // so far, it is OK to use GtkStatusIcon on XFCE <-> XFCE4 inclusive
                    try {
                        trayType = _GtkStatusIconTray.class;
                    } catch (Throwable e1) {
                        if (DEBUG) {
                            logger.error("Cannot initialize _GtkStatusIconTray", e1);
                        }
                    }
                }
                else if ("lxde".equalsIgnoreCase(XDG)) {
                    try {
                        trayType = _GtkStatusIconTray.class;
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot initialize _GtkStatusIconTray", e);
                        }
                    }
                }
                else if ("kde".equalsIgnoreCase(XDG)) {
                    // kde (at least, plasma 5.5.6) requires appindicator
                    try {
                        trayType = _AppIndicatorTray.class;
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot initialize _AppIndicatorTray", e);
                        }
                    }
                }
                else if ("gnome".equalsIgnoreCase(XDG)) {
                    // check other DE
                    String GDM = System.getenv("GDMSESSION");

                    if (DEBUG) {
                        logger.debug("Currently using the '{}' session type", GDM);
                    }

                    if ("cinnamon".equalsIgnoreCase(GDM)) {
                        try {
                            trayType = _GtkStatusIconTray.class;
                        } catch (Throwable e) {
                            if (DEBUG) {
                                logger.error("Cannot initialize _GtkStatusIconTray", e);
                            }
                        }
                    }
                    else if ("gnome-classic".equalsIgnoreCase(GDM)) {
                        try {
                            trayType = _GtkStatusIconTray.class;
                        } catch (Throwable e) {
                            if (DEBUG) {
                                logger.error("Cannot initialize _GtkStatusIconTray", e);
                            }
                        }
                    }
                    else if ("gnome-fallback".equalsIgnoreCase(GDM)) {
                        try {
                            trayType = _GtkStatusIconTray.class;
                        } catch (Throwable e) {
                            if (DEBUG) {
                                logger.error("Cannot initialize _GtkStatusIconTray", e);
                            }
                        }
                    }
                    else if ("ubuntu".equalsIgnoreCase(GDM)) {
                        // have to install the gnome extension AND customize the restart command
                        trayType = null;
                        // unity panel service??
                        GnomeShellExtension.SHELL_RESTART_COMMAND = "unity --replace &";
                    }
                }
            }


            // is likely 'gnome' (gnome-shell exists on the platform), but it can also be unknown (or something completely different),
            // install extension and go from there
            if (isReallyGnome) {
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
                        if (DEBUG) {
                            logger.debug("Installing gnome-shell extension");
                        }

                        GnomeShellExtension.install(output);
                        // we might be running gnome-shell, we MIGHT NOT. If we are forced to be app-indicator or swing, don't do this.
                        if (trayType == null) {
                            trayType = _GtkStatusIconTray.class;
                        }
                    }
                } catch (Throwable e) {
                    if (DEBUG) {
                        logger.error("Cannot auto-detect gnome-shell version", e);
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
                                        trayType = _AppIndicatorTray.class;
                                    } catch (Throwable e) {
                                        if (DEBUG) {
                                            logger.error("AppIndicator support detected, but unable to load the library. Falling back to GTK", e);
                                        } else {
                                            logger.error("AppIndicator support detected, but unable to load the library. Falling back to GTK");
                                        }
                                    }
                                    break;
                                }
                            } finally {
                                IO.closeQuietly(bin);
                            }
                        }
                    }
                } catch (Throwable e) {
                    if (DEBUG) {
                        logger.error("Error detecting gnome version", e);
                    }
                }
            }


            // fallback...
            if (trayType == null) {
                trayType = _GtkStatusIconTray.class;
                logger.error("Unable to load the system tray native library. Please write an issue and include your OS type and " +
                             "configuration");
            }
        }


        // this has to happen BEFORE any sort of swing system tray stuff is accessed
        if (OS.isWindows()) {
            // windows is funky, and is hardcoded to 16x16. We fix that.
            WindowsSystemTraySwing.fix();
        }

        // this is windows OR mac
        if (trayType == null && java.awt.SystemTray.isSupported()) {
            try {
                java.awt.SystemTray.getSystemTray();
                trayType = _SwingTray.class;
            } catch (Throwable e) {
                if (DEBUG) {
                    logger.error("Maybe you should grant the AWTPermission `accessSystemTray` in the SecurityManager.", e);
                } else {
                    logger.error("Maybe you should grant the AWTPermission `accessSystemTray` in the SecurityManager.");
                }
            }
        }

        if (trayType == null) {
            // unsupported tray
            logger.error("Unable to discover what tray implementation to use!");
            systemTrayMenu = null;
        }
        else {
            final AtomicReference<Menu> reference = new AtomicReference<Menu>();

            /*
             *  appIndicator/gtk require strings (which is the path)
             *  swing version loads as an image (which can be stream or path, we use path)
             */
            CacheUtil.tempDir = "SysTray";

            try {
                if (OS.isLinux() &&
                    trayType == _AppIndicatorTray.class &&
                    Gtk.isGtk2 &&
                    AppIndicator.isVersion3) {

                    try {
                        trayType = _GtkStatusIconTray.class;
                        logger.warn("AppIndicator3 detected with GTK2, falling back to GTK2 system tray type.  " +
                                    "Please install libappindicator1 OR GTK3, for example: 'sudo apt-get install libappindicator1'");
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot initialize _GtkStatusIconTray", e);
                        }
                        logger.error("AppIndicator3 detected with GTK2 and unable to fallback to using GTK2 system tray type." +
                                     "AppIndicator3 requires GTK3 to be fully functional, and while this will work -- " +
                                     "the menu icons WILL NOT be visible." +
                                     " Please install libappindicator1 OR GTK3, for example: 'sudo apt-get install libappindicator1'");
                    }
                }

                // have to construct swing stuff inside the swing EDT
                // this is the safest way to do this.
                final Class<? extends Menu> finalTrayType = trayType;
                SwingUtil.invokeAndWait(new Runnable() {
                    @Override
                    public
                    void run() {
                        try {
                            reference.set((Menu) finalTrayType.getConstructors()[0].newInstance(systemTray));
                            logger.info("Successfully Loaded: {}", finalTrayType.getSimpleName());
                        } catch (Exception e) {
                            logger.error("Unable to create tray type: '" + finalTrayType.getSimpleName() + "'", e);
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Unable to create tray type: '" + trayType.getSimpleName() + "'", e);
            }

            systemTrayMenu = reference.get();


            // These install a shutdown hook in JavaFX/SWT, so that when the main window is closed -- the system tray is ALSO closed.
            if (ENABLE_SHUTDOWN_HOOK) {
                if (isJavaFxLoaded) {
                    // Necessary because javaFX **ALSO** runs a gtk main loop, and when it stops (if we don't stop first), we become unresponsive.
                    // Also, it's nice to have us shutdown at the same time as the main application
                    JavaFX.onShutdown(new Runnable() {
                        @Override
                        public
                        void run() {
                            if (systemTray != null) {
                                systemTray.shutdown();
                            }
                        }
                    });
                }
                else if (isSwtLoaded) {
                    // this is because SWT **ALSO** runs a gtk main loop, and when it stops (if we don't stop first), we become unresponsive
                    // Also, it's nice to have us shutdown at the same time as the main application
                    Swt.onShutdown(new Runnable() {
                        @Override
                        public
                        void run() {
                            if (systemTray != null) {
                                systemTray.shutdown();
                            }
                        }
                    });
                }
            }
        }
    }


    /**
     * Gets the version number.
     */
    public static
    String getVersion() {
        return "2.20";
    }

    /**
     * This always returns the same instance per JVM (it's a singleton), and on some platforms the system tray may not be
     * supported, in which case this will return NULL.
     *
     * <p>If this is using the Swing SystemTray and a SecurityManager is installed, the AWTPermission {@code accessSystemTray} must
     * be granted in order to get the {@code SystemTray} instance. Otherwise this will return null.
     */
    public static
    SystemTray get() {
        init();
        return systemTray;
    }

    public
    void shutdown() {
        final Menu menu = systemTrayMenu;

        if (menu instanceof _AppIndicatorTray) {
            ((_AppIndicatorTray) menu).shutdown();
        }
        else if (menu instanceof _GtkStatusIconTray) {
            ((_GtkStatusIconTray) menu).shutdown();
        } else {
            // swing
            ((_SwingTray) menu).shutdown();
        }
    }

    /**
     * Gets the 'status' string assigned to the system tray
     */
    public
    String getStatus() {
        final Menu menu = systemTrayMenu;
        if (menu instanceof _AppIndicatorTray) {
            return ((_AppIndicatorTray) menu).getStatus();
        }
        else if (menu instanceof _GtkStatusIconTray) {
            return ((_GtkStatusIconTray) menu).getStatus();
        } else {
            // swing
            return ((_SwingTray) menu).getStatus();
        }
    }

    /**
     * Sets a 'status' string at the first position in the popup menu. This 'status' string appears as a disabled menu entry.
     *
     * @param statusText the text you want displayed, null if you want to remove the 'status' string
     */
    public
    void setStatus(String statusText) {
        final Menu menu = systemTrayMenu;

        if (menu instanceof _AppIndicatorTray) {
            ((_AppIndicatorTray) menu).setStatus(statusText);
        }
        else if (menu instanceof _GtkStatusIconTray) {
            ((_GtkStatusIconTray) menu).setStatus(statusText);
        } else {
            // swing
            ((_SwingTray) menu).setStatus(statusText);
        }
    }

    /**
     * @return the parent menu (of this menu) or null if we are the root menu
     */
    public
    Menu getParent() {
        return null;
    }

    @Override
    public
    SystemTray getSystemTray() {
        return this;
    }

    /**
     * @return the attached menu to this system tray
     */
    public
    Menu getMenu() {
        return systemTrayMenu;
    }

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
    public
    void addSeparator() {
        systemTrayMenu.addSeparator();
    }

    /**
     * Shows (if hidden), or hides (if showing) the system tray.
     */
    @Override
    public
    void setEnabled(final boolean enabled) {
        systemTrayMenu.setEnabled(enabled);
    }

    /**
     * Does nothing. You cannot get the text for the system tray
     */
    @Override
    public
    String getText() {
        return "";
    }

    /**
     * Does nothing. You cannot set the text for the system tray
     */
    @Override
    public
    void setText(final String newText) {
        // NO OP.
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param imageFile the file of the image to use or null
     */
    @Override
    public
    void setImage(final File imageFile) {
        if (imageFile == null) {
            throw new NullPointerException("imageFile cannot be null!");
        }

        systemTrayMenu.setImage(imageFile);
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param imagePath the full path of the image to use or null
     */
    @Override
    public
    void setImage(final String imagePath) {
        if (imagePath == null) {
            throw new NullPointerException("imagePath cannot be null!");
        }

        systemTrayMenu.setImage(imagePath);
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param imageUrl the URL of the image to use or null
     */
    @Override
    public
    void setImage(final URL imageUrl) {
        if (imageUrl == null) {
            throw new NullPointerException("imageUrl cannot be null!");
        }

        systemTrayMenu.setImage(imageUrl);
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use
     */
    @Override
    public
    void setImage(final String cacheName, final InputStream imageStream) {
        if (cacheName == null) {
            setImage(imageStream);
        } else {
            if (imageStream == null) {
                throw new NullPointerException("imageStream cannot be null!");
            }

            systemTrayMenu.setImage(cacheName, imageStream);
        }
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *
     * This method **DOES NOT CACHE** the result, so multiple lookups for the same inputStream result in new files every time. This is
     * also NOT RECOMMENDED, but is provided for simplicity.
     *
     * @param imageStream the InputStream of the image to use
     */
    @Override
    public
    void setImage(final InputStream imageStream) {
        if (imageStream == null) {
            throw new NullPointerException("imageStream cannot be null!");
        }

        systemTrayMenu.setImage(imageStream);
    }

    /**
     * By default, we always have an image for the system tray
     */
    @Override
    public
    boolean hasImage() {
        return true;
    }

    /**
     * Does nothing. The system tray cannot have a callback when opened
     */
    @Override
    public
    void setCallback(final Action callback) {
        // NO OP.
    }

    /**
     * Does nothing. The system tray cannot be opened via a shortcut key
     */
    @Override
    public
    void setShortcut(final char key) {
        // NO OP.
    }

    /**
     * Removes the system tray. This is the same as calling `shutdown()`
     */
    public
    void remove() {
        shutdown();
    }

    /**
     * Gets the menu entry for a specified text
     *
     * @param menuText the menu entry text to use to find the menu entry. The first result found is returned
     */
    public final
    Entry get(final String menuText) {
        return systemTrayMenu.get(menuText);
    }

    /**
     * Gets the first menu entry, ignoring status and spacers
     */
    public final
    Entry getFirst() {
        return systemTrayMenu.getFirst();
    }

    /**
     * Gets the last menu entry, ignoring status and spacers
     */
    public final
    Entry getLast() {
        return systemTrayMenu.getLast();
    }

    /**
     * Gets the menu entry for a specified index (zero-index), ignoring status and spacers
     *
     * @param menuIndex the menu entry index to use to retrieve the menu entry.
     */
    public final
    Entry get(final int menuIndex) {
        return systemTrayMenu.get(menuIndex);
    }





    /**
     * Adds a menu entry to the tray icon with text (no image)
     *
     * @param menuText string of the text you want to appear
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public final
    Entry addEntry(String menuText, Action callback) {
        return addEntry(menuText, (String) null, callback);
    }

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imagePath the image (full path required) to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public final
    Entry addEntry(String menuText, String imagePath, Action callback) {
        return systemTrayMenu.addEntry(menuText, imagePath, callback);
    }

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageUrl the URL of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public final
    Entry addEntry(String menuText, URL imageUrl, Action callback) {
        return systemTrayMenu.addEntry(menuText, imageUrl, callback);
    }

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param cacheName @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    Entry addEntry(String menuText, String cacheName, InputStream imageStream, Action callback) {
        return systemTrayMenu.addEntry(menuText, cacheName, imageStream, callback);
    }

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public final
    Entry addEntry(String menuText, InputStream imageStream, Action callback) {
        return systemTrayMenu.addEntry(menuText, imageStream, callback);
    }





    /**
     * Adds a sub-menu entry with text (no image)
     *
     * @param menuText string of the text you want to appear
     */
    public
    Menu addMenu(String menuText) {
        return addMenu(menuText, (String) null);
    }

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imagePath the image (full path required) to use. If null, no image will be used
     */
    public
    Menu addMenu(String menuText, String imagePath) {
        return systemTrayMenu.addMenu(menuText, imagePath);
    }

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageUrl the URL of the image to use. If null, no image will be used
     */
    public
    Menu addMenu(String menuText, URL imageUrl) {
        return systemTrayMenu.addMenu(menuText, imageUrl);
    }

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param cacheName @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     */
    public
    Menu addMenu(String menuText, String cacheName, InputStream imageStream) {
        return systemTrayMenu.addMenu(menuText, cacheName, imageStream);
    }

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     */
    public
    Menu addMenu(String menuText, InputStream imageStream) {
        return systemTrayMenu.addMenu(menuText, imageStream);
    }

    /**
     * Adds a swing widget as a menu entry.
     *
     * @param widget the JComponent that is to be added as an entry
     */
// TODO: buggy. The menu will **sometimes** stop responding to the "enter" key after this. Mnemonics still work however.
//    @Override
//    public
//    Entry addWidget(final JComponent widget) {
//        return systemTrayMenu.addWidget(widget);
//    }



    /**
     *  This removes a menu entry from the dropdown menu.
     *
     * @param entry This is the menu entry to remove
     */
    public final
    void remove(final Entry entry) {
        systemTrayMenu.remove(entry);
    }

    /**
     *  This removes a sub-menu entry from the dropdown menu.
     *
     * @param menu This is the menu entry to remove
     */
    @Override
    public final
    void remove(final Menu menu) {
        systemTrayMenu.remove(menu);
    }

    /**
     *  This removes al menu entries from this menu
     */
    @Override
    public final
    void removeAll() {
        systemTrayMenu.removeAll();
    }

    /**
     *  This removes a menu entry (via the text label) from the dropdown menu.
     *
     * @param menuText This is the label for the menu entry to remove
     */
    public final
    void remove(final String menuText) {
        systemTrayMenu.remove(menuText);
    }
}

