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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dorkbox.systemTray.linux.AppIndicatorTray;
import dorkbox.systemTray.linux.GnomeShellExtension;
import dorkbox.systemTray.linux.GtkSystemTray;
import dorkbox.systemTray.linux.jna.AppIndicator;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.swing.SwingSystemTray;
import dorkbox.systemTray.util.ImageUtils;
import dorkbox.systemTray.util.JavaFX;
import dorkbox.systemTray.util.Swt;
import dorkbox.systemTray.util.WindowsSystemTraySwing;
import dorkbox.util.CacheUtil;
import dorkbox.util.IO;
import dorkbox.util.OS;
import dorkbox.util.Property;
import dorkbox.util.process.ShellProcessBuilder;


/**
 * Factory and base-class for system tray implementations.
 */
@SuppressWarnings({"unused", "Duplicates", "DanglingJavadoc", "WeakerAccess"})
public abstract
class SystemTray {
    public static final Logger logger = LoggerFactory.getLogger(SystemTray.class);

    public static final int TYPE_AUTO_DETECT = 0;
    public static final int TYPE_GTK_STATUSICON = 1;
    public static final int TYPE_APP_INDICATOR = 2;
    public static final int TYPE_SWING = 3;

    @Property
    /** How long to wait when updating menu entries before the request times-out */
    public static final int TIMEOUT = 2;

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
     * - Windows will automatically scale up/down.
     * <p>
     * You will experience WEIRD graphical glitches if this is NOT a power of 2.
     */
    public static int DEFAULT_WINDOWS_SIZE = 32;

    @Property
    /**
     * Size of the tray, so that the icon can be properly scaled based on OS.
     * - GtkStatusIcon will usually automatically scale up/down
     * - AppIndicators will not always automatically scale (it will sometimes display whatever is specified here)
     * <p>
     * You will experience WEIRD graphical glitches if this is NOT a power of 2.
     */
    public static int DEFAULT_LINUX_SIZE = 16;

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
    public static int FORCE_TRAY_TYPE = 3;

    @Property
    /**
     * When in compatibility mode, and the JavaFX/SWT primary windows are closed, we want to make sure that the SystemTray is also closed.
     * This property is available to disable this functionality in situations where you don't want this to happen.
     */
    public static boolean ENABLE_SHUTDOWN_HOOK = true;

    @Property
    /**
     * This property is provided for debugging any errors in the logic used to determine the system-tray type.
     */
    public static boolean DEBUG = true;


    private static volatile SystemTray systemTray = null;

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

        // no tray in a headless environment
        if (GraphicsEnvironment.isHeadless()) {
            logger.error("Cannot use the SystemTray in a headless environment");
            throw new HeadlessException();
        }

        Class<? extends SystemTray> trayType = null;

        boolean isKDE = false;

        if (DEBUG) {
            logger.debug("is JavaFX detected? {}", isJavaFxLoaded);
            logger.debug("is SWT detected? {}", isSwtLoaded);
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
                    trayType = GtkSystemTray.class;
                } catch (Throwable e1) {
                    if (DEBUG) {
                        logger.error("Cannot initialize GtkSystemTray", e1);
                    }
                }
            }
            else if (SystemTray.FORCE_TRAY_TYPE == SystemTray.TYPE_APP_INDICATOR) {
                try {
                    trayType = AppIndicatorTray.class;
                } catch (Throwable e1) {
                    if (DEBUG) {
                        logger.error("Cannot initialize AppIndicatorTray", e1);
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
            }


            if (trayType == null) {
                if ("unity".equalsIgnoreCase(XDG)) {
                    try {
                        trayType = AppIndicatorTray.class;
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot initialize AppIndicatorTray", e);
                        }
                    }
                }
                else if ("xfce".equalsIgnoreCase(XDG)) {
                    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
                    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
                    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25

                    // XFCE4 is OK to use appindicator, <XFCE4 we use GTKStatusIcon. God i wish there was an easy way to do this.
                    boolean isNewXFCE = false;
                    try {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8196);
                        PrintStream outputStream = new PrintStream(byteArrayOutputStream);

                        // ps aux | grep [x]fce
                        final ShellProcessBuilder shell = new ShellProcessBuilder(outputStream);
                        shell.setExecutable("ps");
                        shell.addArgument("aux");
                        shell.start();

                        String output = ShellProcessBuilder.getOutput(byteArrayOutputStream);
                        // should last us the next 20 years or so. XFCE development is glacially slow.
                        isNewXFCE = output.contains("/xfce4/") || output.contains("/xfce5/") ||
                                    output.contains("/xfce6/") || output.contains("/xfce7/");
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot detect what version of XFCE is running", e);
                        }
                    }

                    if (DEBUG) {
                        logger.error("Is 'new' version of XFCE?  {}", isNewXFCE);
                    }

                    if (isNewXFCE) {
                        try {
                            trayType = AppIndicatorTray.class;
                        } catch (Throwable e) {
                            if (DEBUG) {
                                logger.error("Cannot initialize AppIndicatorTray", e);
                            }

                            // we can fail on AppIndicator, so this is the fallback
                            try {
                                trayType = GtkSystemTray.class;
                            } catch (Throwable e1) {
                                if (DEBUG) {
                                    logger.error("Cannot initialize GtkSystemTray", e1);
                                }
                            }
                        }
                    } else {
                        try {
                            trayType = GtkSystemTray.class;
                        } catch (Throwable e1) {
                            if (DEBUG) {
                                logger.error("Cannot initialize GtkSystemTray", e1);
                            }
                        }
                    }
                }
                else if ("lxde".equalsIgnoreCase(XDG)) {
                    try {
                        trayType = GtkSystemTray.class;
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot initialize GtkSystemTray", e);
                        }
                    }
                }
                else if ("kde".equalsIgnoreCase(XDG)) {
                    isKDE = true;
                    try {
                        trayType = AppIndicatorTray.class;
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot initialize AppIndicatorTray", e);
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
                            trayType = GtkSystemTray.class;
                        } catch (Throwable e) {
                            if (DEBUG) {
                                logger.error("Cannot initialize GtkSystemTray", e);
                            }
                        }
                    }
                    else if ("gnome-classic".equalsIgnoreCase(GDM)) {
                        try {
                            trayType = GtkSystemTray.class;
                        } catch (Throwable e) {
                            if (DEBUG) {
                                logger.error("Cannot initialize GtkSystemTray", e);
                            }
                        }
                    }
                    else if ("gnome-fallback".equalsIgnoreCase(GDM)) {
                        try {
                            trayType = GtkSystemTray.class;
                        } catch (Throwable e) {
                            if (DEBUG) {
                                logger.error("Cannot initialize GtkSystemTray", e);
                            }
                        }
                    }
                    else if ("ubuntu".equalsIgnoreCase(GDM)) {
                        // have to install the gnome extension AND customize the restart command
                        trayType = null;
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
                            trayType = GtkSystemTray.class;
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
                                        trayType = AppIndicatorTray.class;
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
                trayType = GtkSystemTray.class;
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
                trayType = SwingSystemTray.class;
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
            systemTray = null;
        }
        else {
            SystemTray systemTray_ = null;

            /*
             *  appIndicator/gtk require strings (which is the path)
             *  swing version loads as an image (which can be stream or path, we use path)
             *
             *  For KDE4, it must also be unique across runs
             */
            CacheUtil.setUniqueCachePerRun = isKDE;
            CacheUtil.tempDir = "SysTray";

            try {
                if (OS.isLinux() &&
                    trayType == AppIndicatorTray.class &&
                    Gtk.isGtk2 &&
                    AppIndicator.isVersion3) {

                    try {
                        trayType = GtkSystemTray.class;
                        logger.warn("AppIndicator3 detected with GTK2, falling back to GTK2 system tray type.  " +
                                    "Please install libappindicator1 OR GTK3, for example: 'sudo apt-get install libappindicator1'");
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot initialize GtkSystemTray", e);
                        }
                        logger.error("AppIndicator3 detected with GTK2 and unable to fallback to using GTK2 system tray type." +
                                     "AppIndicator3 requires GTK3 to be fully functional, and while this will work -- " +
                                     "the menu icons WILL NOT be visible." +
                                     " Please install libappindicator1 OR GTK3, for example: 'sudo apt-get install libappindicator1'");
                    }
                }

                systemTray_ = (SystemTray) trayType.getConstructors()[0].newInstance();

                logger.info("Successfully Loaded: {}", trayType.getSimpleName());
            } catch (Exception e) {
                logger.error("Unable to create tray type: '" + trayType.getSimpleName() + "'", e);
            }

            systemTray = systemTray_;


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
    SystemTray getSystemTray() {
        init();
        return systemTray;
    }

    protected final java.util.List<MenuEntry> menuEntries = new ArrayList<MenuEntry>();

    protected
    SystemTray() {
    }

    /**
     * Necessary to guarantee all updates occur on the dispatch thread
     */
    protected abstract
    void dispatch(Runnable runnable);

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
    void setIcon_(File iconPath);

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
        setIcon_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, imagePath));
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
        setIcon_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, imageUrl));
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
        setIcon_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, cacheName, imageStream));
    }

    /**
     * Changes the tray icon used.
     *
     * Because the cross-platform, underlying system uses a file path to load icons for the system tray, this will copy the contents of
     * the imageStream to a temporary location on disk.
     *
     * @param imageStream the InputStream of the icon to use
     */
    public
    void setIcon(InputStream imageStream) {
        setIcon_(ImageUtils.resizeAndCache(ImageUtils.TRAY_SIZE, imageStream));
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
    void updateMenuEntry(final String origMenuText, final String newMenuText) {
        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue =  new AtomicBoolean(true);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(origMenuText);

                    if (menuEntry == null) {
                        hasValue.set(false);
                    }
                    else {
                        menuEntry.setText(newMenuText);
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!hasValue.get()) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
    }

    /**
     * Updates (or changes) the menu entry's image (as a String).
     *
     * @param origMenuText the original menu text
     * @param imagePath the new path for the image to use or null to delete the image
     */
    public final
    void updateMenuEntry_AsImage(final String origMenuText, final String imagePath) {
        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue =  new AtomicBoolean(true);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(origMenuText);

                    if (menuEntry == null) {
                        hasValue.set(false);
                    }
                    else {
                        menuEntry.setImage(imagePath);
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!hasValue.get()) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param origMenuText the original menu text
     * @param imageUrl the new URL for the image to use or null to delete the image
     */
    public final
    void updateMenuEntry(final String origMenuText, final URL imageUrl) {
        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue =  new AtomicBoolean(true);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(origMenuText);

                    if (menuEntry == null) {
                        hasValue.set(false);

                    }
                    else {
                        menuEntry.setImage(imageUrl);
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!hasValue.get()) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use or null to delete the image
     */
    public final
    void updateMenuEntry(final String origMenuText, final String cacheName, final InputStream imageStream) {
        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue =  new AtomicBoolean(true);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(origMenuText);

                    if (menuEntry == null) {
                        hasValue.set(false);
                    }
                    else {
                        menuEntry.setImage(cacheName, imageStream);
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!hasValue.get()) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
    }

    /**
     * Updates (or changes) the menu entry's text.
     *
     * @param origMenuText the original menu text
     * @param imageStream the new path for the image to use or null to delete the image
     */
    public final
    void updateMenuEntry(final String origMenuText, final InputStream imageStream) {
        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue =  new AtomicBoolean(true);

        dispatch(new Runnable() {
            @SuppressWarnings("deprecation")
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(origMenuText);

                    if (menuEntry == null) {
                        hasValue.set(false);
                    }
                    else {
                        menuEntry.setImage(imageStream);
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!hasValue.get()) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
        }
    }

    /**
     * Updates (or changes) the menu entry's callback.
     *
     * @param origMenuText the original menu text
     * @param newCallback the new callback (this will replace the original callback)
     */
    public final
    void updateMenuEntry(final String origMenuText, final SystemTrayMenuAction newCallback) {
        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue =  new AtomicBoolean(true);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(origMenuText);

                    if (menuEntry == null) {
                        hasValue.set(false);
                    }
                    else {
                        menuEntry.setCallback(newCallback);
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!hasValue.get()) {
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
    public final
    void updateMenuEntry(final String origMenuText, final String newMenuText, final SystemTrayMenuAction newCallback) {
        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue =  new AtomicBoolean(true);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(origMenuText);

                    if (menuEntry == null) {
                        hasValue.set(false);
                    }
                    else {
                        menuEntry.setText(newMenuText);
                        menuEntry.setCallback(newCallback);
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!hasValue.get()) {
            throw new NullPointerException("No menu entry exists for string '" + origMenuText + "'");
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

        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue =  new AtomicBoolean(false);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                try {
                    synchronized (menuEntries) {
                        for (Iterator<MenuEntry> iterator = menuEntries.iterator(); iterator.hasNext(); ) {
                            final MenuEntry entry = iterator.next();
                            if (entry.getText()
                                     .equals(label)) {
                                iterator.remove();

                                // this will also reset the menu
                                menuEntry.remove();
                                hasValue.set(true);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error removing menu entry from list.", e);
                } finally {
                    countDownLatch.countDown();
                }
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!hasValue.get()) {
            throw new NullPointerException("Menu entry '" + label + "'not found in list while trying to remove it.");
        }
    }


    /**
     *  This removes a menu entry (via the text label) from the dropdown menu.
     *
     * @param menuText This is the label for the menu entry to remove
     */
    public final
    void removeMenuEntry(final String menuText) {
        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue =  new AtomicBoolean(true);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                dispatch(new Runnable() {
                    @Override
                    public
                    void run() {
                        synchronized (menuEntries) {
                            MenuEntry menuEntry = getMenuEntry(menuText);

                            if (menuEntry == null) {
                                hasValue.set(false);
                            }
                            else {
                                removeMenuEntry(menuEntry);
                            }
                        }
                        countDownLatch.countDown();
                    }
                });
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (!hasValue.get()) {
            throw new NullPointerException("No menu entry exists for string '" + menuText + "'");
        }
    }
}

