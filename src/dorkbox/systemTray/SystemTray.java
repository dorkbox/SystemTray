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

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.stream.ImageInputStream;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dorkbox.systemTray.jna.linux.AppIndicator;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.systemTray.nativeUI.NativeUI;
import dorkbox.systemTray.nativeUI._AppIndicatorNativeTray;
import dorkbox.systemTray.nativeUI._AwtTray;
import dorkbox.systemTray.nativeUI._GtkStatusIconNativeTray;
import dorkbox.systemTray.swingUI.SwingUI;
import dorkbox.systemTray.swingUI._AppIndicatorSwingTray;
import dorkbox.systemTray.swingUI._GtkStatusIconSwingTray;
import dorkbox.systemTray.swingUI._SwingTray;
import dorkbox.systemTray.util.ImageUtils;
import dorkbox.systemTray.util.JavaFX;
import dorkbox.systemTray.util.Swt;
import dorkbox.systemTray.util.SystemTrayFixes;
import dorkbox.util.CacheUtil;
import dorkbox.util.IO;
import dorkbox.util.OS;
import dorkbox.util.OSUtil;
import dorkbox.util.Property;
import dorkbox.util.SwingUtil;
import dorkbox.util.process.ShellProcessBuilder;


/**
 * Professional, cross-platform **SystemTray**, **AWT**, **GtkStatusIcon**, and **AppIndicator** support for java applications.
 * <p>
 * This library provides **OS native** menus and **Swing** menus.
 * <ul>
 *     <li> Swing menus are the default prefered type becuase they offer more features (images attached to menu entries, text styling, etc) and
 * a consistent look & feel across all platforms.
 *     </li>
 *     <li> Native menus, should one want them, follow the specified look and feel of that OS, and thus are limited by what is supported on the
 * OS and consequently not consistent across all platforms.
 *     </li>
 * </ul>
 */
@SuppressWarnings({"unused", "Duplicates", "DanglingJavadoc", "WeakerAccess"})
public final
class SystemTray {
    public static final Logger logger = LoggerFactory.getLogger(SystemTray.class);

    public enum TrayType {
        /** Will choose as a 'best guess' which tray type to use based on if native is requested or not */
        AutoDetect,
        /** if native, will have Gtk Menus. Non-native will have Swing menus */
        GtkStatusIcon,
        /** if native, will have Gtk Menus. Non-native will have Swing menus */
        AppIndicator,
        /** if native, will have AWT Menus. Non-native will have Swing menus */
        Swing
    }

    @Property
    /** Enables auto-detection for the system tray. This should be mostly successful.
     * <p>
     * Auto-detection will use DEFAULT_TRAY_SIZE or DEFAULT_MENU_SIZE as a 'base-line' for determining what size to use.
     * <p>
     * If auto-detection fails and the incorrect size is detected or used, disable this and specify the correct DEFAULT_TRAY_SIZE or
     * DEFAULT_MENU_SIZE instead
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
     * Forces the system tray detection to be AutoDetect, GtkStatusIcon, AppIndicator, or Swing.
     * <p>
     * This is an advanced feature, and it is recommended to leave at AutoDetect.
     */
    public static TrayType FORCE_TRAY_TYPE = TrayType.AutoDetect;

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
     * Allows the SystemTray logic to resolve OS inconsistencies for the SystemTray.
     * <p>
     * This is an advanced feature, and it is recommended to leave as true
     */
    public static boolean AUTO_FIX_INCONSISTENCIES = true;

    @Property
    /**
     * This property is provided for debugging any errors in the logic used to determine the system-tray type.
     */
    public static boolean DEBUG = true;


    private static volatile SystemTray systemTray = null;
    private static volatile Tray systemTrayMenu = null;

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

    private static
    boolean isTrayType(final Class<? extends Tray> tray, final TrayType trayType) {
        switch (trayType) {
            case GtkStatusIcon: return (tray == _GtkStatusIconSwingTray.class || tray == _GtkStatusIconNativeTray.class);
            case AppIndicator: return (tray == _AppIndicatorSwingTray.class || tray == _AppIndicatorNativeTray.class);
            case Swing: return (tray == _SwingTray.class || tray == _AwtTray.class);
        }
        return false;
    }

    private static
    Class<? extends Tray> selectType(final boolean useNativeMenus, final TrayType trayType) throws Exception {
        if (trayType == TrayType.GtkStatusIcon) {
            if (useNativeMenus) {
                return _GtkStatusIconNativeTray.class;
            } else {
                return _GtkStatusIconSwingTray.class;
            }
        } else if (trayType == TrayType.AppIndicator) {
            if (useNativeMenus) {
                return _AppIndicatorNativeTray.class;
            }
            else {
                return _AppIndicatorSwingTray.class;
            }
        }
        else if (trayType == TrayType.Swing) {
            if (useNativeMenus) {
                return _AwtTray.class;
            }
            else {
                return _SwingTray.class;
            }
        }

        return null;
    }

    private static
    Class<? extends Tray> selectTypeQuietly(final boolean useNativeMenus, final TrayType trayType) {
        try {
            return selectType(useNativeMenus, trayType);
        } catch (Throwable t) {
            if (DEBUG) {
                logger.error("Cannot initialize {}", trayType.name(), t);
            }
        }

        return null;
    }

    @SuppressWarnings({"ConstantConditions", "StatementWithEmptyBody"})
    private static void init(boolean useNativeMenus) {
        if (systemTray != null) {
            return;
        }

        systemTray = new SystemTray();

//        if (DEBUG) {
//            Properties properties = System.getProperties();
//            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
//                logger.debug(entry.getKey() + " : " + entry.getValue());
//            }
//        }

        if (OS.isMacOsX()) {
            // cannot mix AWT and JavaFX for MacOSX in java7 (fixed in java8) without special stuff.
            // https://bugs.openjdk.java.net/browse/JDK-8116017
            // https://bugs.openjdk.java.net/browse/JDK-8118714
            if (isJavaFxLoaded && OS.javaVersion <= 7 && !System.getProperty("javafx.macosx.embedded", "false").equals("true")) {

                logger.error("MacOSX JavaFX (Java7) is incompatible with the SystemTray by default. See issue: " +
                             "'https://bugs.openjdk.java.net/browse/JDK-8116017'  and 'https://bugs.openjdk.java.net/browse/JDK-8118714'\n" +
                             "To fix this do one of the following: \n" +
                             " - Upgrade to Java 8\n" +
                             " - Add : '-Djavafx.macosx.embedded=true' as a JVM parameter\n" +
                             " - Set the system property via 'System.setProperty(\"javafx.macosx.embedded\", \"true\");'  before JavaFX is" +
                             "initialized, used, or accessed. NOTE: You may need to change the class (that your main method is in) so it does" +
                             " NOT extend the JavaFX 'Application' class.");

                systemTrayMenu = null;
                systemTray = null;
                return;
            }

            // cannot mix Swing and SWT on MacOSX (for all versions of java) so we force native menus instead, which work just fine with SWT
            // http://mail.openjdk.java.net/pipermail/bsd-port-dev/2008-December/000173.html
            if (isSwtLoaded) {
                useNativeMenus = true;
                logger.warn("MacOSX does not support SWT + Swing at the same time. Forcing Native menus instead.");
            }
        }

        // no tray in a headless environment
        if (GraphicsEnvironment.isHeadless()) {
            logger.error("Cannot use the SystemTray in a headless environment");

            systemTrayMenu = null;
            systemTray = null;
            return;
        }

        // Windows can ONLY use Swing (non-native) - AWT/native looks absolutely horrid
        // OSx can use Swing (non-native) or AWT (native) .
        // Linux can use Swing (non-native) menus + (native Icon via GTK or AppIndicator), GtkStatusIcon (native), or AppIndicator (native)
        if (OS.isWindows()) {
            if (useNativeMenus && AUTO_FIX_INCONSISTENCIES) {
                // windows MUST use swing non-native only. AWT (native) looks terrible!
                useNativeMenus = false;
                logger.warn("Windows cannot use a 'native' SystemTray, defaulting to non-native SwingUI");
            }

            if (FORCE_TRAY_TYPE != TrayType.Swing) {
                // windows MUST use swing only!
                FORCE_TRAY_TYPE = TrayType.AutoDetect;
                logger.warn("Windows cannot use the '" + FORCE_TRAY_TYPE + "' SystemTray type, defaulting to SwingUI");
            }
        }
        else if (OS.isMacOsX()) {
            if (FORCE_TRAY_TYPE != TrayType.Swing ) {
                if (useNativeMenus) {
                    logger.warn("MacOsX cannot use the '" + FORCE_TRAY_TYPE + "' SystemTray type, defaulting to SwingUI");
                } else {
                    logger.warn("MacOsX cannot use the '" + FORCE_TRAY_TYPE + "' SystemTray type, defaulting to AWT Native UI");
                }

                // MacOsX MUST use swing (and AWT) only!
                FORCE_TRAY_TYPE = TrayType.AutoDetect;
            }
        }
        else if (OS.isLinux() || OS.isUnix()) {
            // kablooie if SWT/JavaFX is not configured in a way that works with us.
            if (FORCE_TRAY_TYPE != TrayType.Swing) {
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

                        systemTrayMenu = null;
                        systemTray = null;
                        return;
                    } else if (!isSwt_GTK3 && !FORCE_GTK2 && AUTO_FIX_INCONSISTENCIES) {
                        // we must use GTK2, because SWT is GTK2
                        logger.warn("Forcing GTK2 because SWT is GTK2");
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
                        if (OS.javaVersion == 9 && AUTO_FIX_INCONSISTENCIES) {
                            logger.warn("Unable to use the SystemTray when JavaFX is configured to use GTK3 and the SystemTray is " +
                                        "configured to use GTK2. Please configure JavaFX to use GTK2 (via `System.setProperty(\"jdk.gtk.version\", \"3\");`) " +
                                        "before JavaFX is initialized, or set `SystemTray.FORCE_GTK2=false;`  Undoing `FORCE_GTK2`.");

                            FORCE_GTK2 = false;
                        } else {
                            logger.error("Unable to use the SystemTray when JavaFX is configured to use GTK3 and the SystemTray is configured to use " +
                                         "GTK2. Please set `SystemTray.FORCE_GTK2=false;`  if that is not possible then it will not work.");

                            systemTrayMenu = null;
                            systemTray = null;
                            return;
                        }
                    } else if (!isJFX_GTK3 && !FORCE_GTK2 && AUTO_FIX_INCONSISTENCIES) {
                        // we must use GTK2, because JavaFX is GTK2
                        logger.warn("Forcing GTK2 because JavaFX is GTK2");
                        FORCE_GTK2 = true;
                    }
                }
            }
        }

        Class<? extends Tray> trayType = null;

        if (DEBUG) {
            logger.debug("OS: {}", System.getProperty("os.name"));
            logger.debug("Arch: {}", System.getProperty("os.arch"));

            String jvmName = System.getProperty("java.vm.name", "");
            String jvmVersion = System.getProperty("java.version", "");
            String jvmVendor = System.getProperty("java.vm.specification.vendor", "");
            logger.debug("{} {} {}", jvmVendor, jvmName, jvmVersion);


            logger.debug("Is AutoTraySize? {}", AUTO_TRAY_SIZE);
            logger.debug("Is JavaFX detected? {}", isJavaFxLoaded);
            logger.debug("Is SWT detected? {}", isSwtLoaded);
            logger.debug("Is using native menus? {}", useNativeMenus);
            logger.debug("Forced tray type: {}", FORCE_TRAY_TYPE.name());
            logger.debug("FORCE_GTK2: {}", FORCE_GTK2);
        }

        // Note: AppIndicators DO NOT support tooltips. We could try to create one, by creating a GTK widget and attaching it on
        // mouseover or something, but I don't know how to do that. It seems that tooltips for app-indicators are a custom job, as
        // all examined ones sometimes have it (and it's more than just text), or they don't have it at all. There is no mouse-over event.


        // this has to happen BEFORE any sort of swing system tray stuff is accessed
        if (OS.isWindows()) {
            // windows is funky, and is hardcoded to 16x16. We fix that.
            SystemTrayFixes.fixWindows();
        }
        else if (OS.isMacOsX() && useNativeMenus) {
            // macosx doesn't respond to all buttons (but should)
            SystemTrayFixes.fixMacOS();
        }
        else if ((OS.isLinux() || OS.isUnix()) && FORCE_TRAY_TYPE != TrayType.Swing) {
            // see: https://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running

            // For funsies, SyncThing did a LOT of work on compatibility (unfortunate for us) in python.
            // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

            // this can never be swing
            // don't check for SWING type at this spot, it is done elsewhere.
            if (SystemTray.FORCE_TRAY_TYPE != TrayType.AutoDetect) {
                trayType = selectTypeQuietly(useNativeMenus, SystemTray.FORCE_TRAY_TYPE);
            }


            // quick check, because we know that unity uses app-indicator. Maybe REALLY old versions do not. We support 14.04 LTE at least

            // if we are running as ROOT, we *** WILL NOT *** have access to  'XDG_CURRENT_DESKTOP'
            //   *unless env's are preserved, but they are not guaranteed to be
            // see:  http://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running
            String XDG = System.getenv("XDG_CURRENT_DESKTOP");
            if (XDG == null) {
                // maybe we are running as root???
                XDG = "unknown"; // try to autodetect if we should use app indicator or gtkstatusicon
            }


            // BLEH. if gnome-shell is running, IT'S REALLY GNOME!
            // we must ALWAYS do this check!!
            boolean isReallyGnome = OSUtil.DesktopEnv.isGnome();

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
                // Unity is a weird combination. It's "Gnome", but it's not "Gnome Shell".
                if ("unity".equalsIgnoreCase(XDG)) {
                    trayType = selectTypeQuietly(useNativeMenus, TrayType.AppIndicator);
                }
                else if ("xfce".equalsIgnoreCase(XDG)) {
                    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
                    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
                    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25

                    // so far, it is OK to use GtkStatusIcon on XFCE <-> XFCE4 inclusive
                    trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                }
                else if ("lxde".equalsIgnoreCase(XDG)) {
                    trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                }
                else if ("kde".equalsIgnoreCase(XDG)) {
                    if (OSUtil.Linux.isFedora()) {
                        // Fedora KDE requires GtkStatusIcon
                        trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                    } else {
                        // kde (at least, plasma 5.5.6) requires appindicator
                        trayType = selectTypeQuietly(useNativeMenus, TrayType.AppIndicator);
                    }

                    // kde 5.8+ is "high DPI", so we need to adjust the scale. Image resize will do that
                }
                else if ("pantheon".equalsIgnoreCase(XDG)) {
                    // elementaryOS. It only supports appindicator (not gtkstatusicon)
                    // http://bazaar.launchpad.net/~wingpanel-devs/wingpanel/trunk/view/head:/sample/SampleIndicator.vala

                    if (!useNativeMenus && AUTO_FIX_INCONSISTENCIES) {
                        logger.warn("Cannot use non-native menus with pantheon DE. Forcing native menus.");
                        useNativeMenus = true;
                    }

                    // ElementaryOS shows the checkbox on the right, everyone else is on the left.
                    // With eOS, we CANNOT show the spacer image. It does not work.
                    trayType = selectTypeQuietly(useNativeMenus, TrayType.AppIndicator);
                }
                else if ("gnome".equalsIgnoreCase(XDG)) {
                    // check other DE
                    String GDM = System.getenv("GDMSESSION");

                    if (DEBUG) {
                        logger.debug("Currently using the '{}' session type", GDM);
                    }

                    if ("gnome".equalsIgnoreCase(GDM)) {
                        Tray.usingGnome = true;

                        // are we fedora? If so, what version?
                        // now, what VERSION of fedora? 23/24/25/? don't have AppIndicator installed, so we have to use GtkStatusIcon
                        if (OSUtil.Linux.isFedora()) {
                            if (DEBUG) {
                                logger.debug("Running Fedora");
                            }

                            // 23 is gtk, 24/25 is gtk (but also wrong size unless we adjust it. ImageUtil automatically does this)
                            trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                        }
                        else if (OSUtil.Linux.isUbuntu()) {
                            // so far, because of the interaction between gnome3 + ubuntu, the GtkStatusIcon miraculously works.
                            trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                        }
                        else if (OSUtil.Unix.isFreeBSD()) {
                            trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                        }
                        else {
                            // arch likely will have problems unless the correct/appropriate libraries are installed.
                            trayType = selectTypeQuietly(useNativeMenus, TrayType.AppIndicator);
                        }
                    }
                    else if ("cinnamon".equalsIgnoreCase(GDM)) {
                        trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                    }
                    else if ("default".equalsIgnoreCase(GDM)) {
                        // this can be gnome3 on debian

                        if (OSUtil.Linux.isDebian()) {
                            // note: Debian Gnome3 does NOT work! (tested on Debian 8.5 and 8.6 default installs)
                            logger.warn("Debian with Gnome detected. SystemTray support is not known to work.");
                        }

                        trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                    }
                    else if ("gnome-classic".equalsIgnoreCase(GDM)) {
                        trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                    }
                    else if ("gnome-fallback".equalsIgnoreCase(GDM)) {
                        trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                    }
                    else if ("ubuntu".equalsIgnoreCase(GDM)) {
                        trayType = selectTypeQuietly(useNativeMenus, TrayType.AppIndicator);
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
                                        trayType = selectType(useNativeMenus, TrayType.AppIndicator);
                                    } catch (Exception e) {
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
                trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                logger.warn("Unable to determine the system window manager type. Falling back to GtkStatusIcon.");
            }

            // this is bad...
            if (trayType == null) {
                logger.error("SystemTray initialization failed. Unable to load the system tray native libraries. Please write an issue " +
                             "and include your OS type and configuration");

                systemTrayMenu = null;
                systemTray = null;
                return;
            }

            if (isTrayType(trayType, TrayType.AppIndicator)) {
                // if are we running as ROOT, there can be issues (definitely on Ubuntu 16.04, maybe others)!

                // this means we are running as sudo
                String sudoUser = System.getenv("SUDO_USER");
                if (sudoUser != null) {
                    // running as a "sudo" user
                    logger.error("Attempting to load the SystemTray as the 'root' user. This will likely not work because of dbus restrictions.");
                }
                else {
                    // running as root (also can be "sudo" user). A bit slower that checking a sys env, but this is guaranteed to work
                    try {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8196);
                        PrintStream outputStream = new PrintStream(byteArrayOutputStream);

                        // id -u
                        final ShellProcessBuilder shell = new ShellProcessBuilder(outputStream);
                        shell.setExecutable("id");
                        shell.addArgument("-u");
                        shell.start();


                        String output = ShellProcessBuilder.getOutput(byteArrayOutputStream);
                        if ("0".equals(output)) {
                            logger.error("Attempting to load the SystemTray as the 'root' user. This will likely not work because of dbus " +
                                         "restrictions.");
                        }
                    } catch (Throwable e) {
                        if (DEBUG) {
                            logger.error("Cannot get id for root", e);
                        }
                    }
                }
            }
        }

        // this is likely windows OR mac
        if (trayType == null) {
            try {
                trayType = selectType(useNativeMenus, TrayType.Swing);
            } catch (Throwable e) {
                if (DEBUG) {
                    logger.error("Maybe you should grant the AWTPermission `accessSystemTray` in the SecurityManager.", e);
                } else {
                    logger.error("Maybe you should grant the AWTPermission `accessSystemTray` in the SecurityManager.");
                }
            }
        }

        if (trayType == null) {
            // unsupported tray, or unknown type
            logger.error("SystemTray initialization failed. (Unable to discover which implementation to use). Something is seriously wrong.");

            systemTrayMenu = null;
            systemTray = null;
            return;
        }

        ImageUtils.determineIconSize(!useNativeMenus);

        final AtomicReference<Tray> reference = new AtomicReference<Tray>();

        // - appIndicator/gtk require strings (which is the path)
        // - swing version loads as an image (which can be stream or path, we use path)
        CacheUtil.tempDir = "SysTray";

        try {
            if (OS.isLinux() || OS.isUnix()) {
                // NOTE:  appindicator1 -> GTk2, appindicator3 -> GTK3.
                // appindicator3 doesn't support menu icons via GTK2!!
                if (!Gtk.isLoaded) {
                    logger.error("Unable to initialize GTK! Something is severely wrong!");
                    systemTrayMenu = null;
                    systemTray = null;
                    return;
                }


                if (OSUtil.Linux.isArch()) {
                    // arch linux is fun!

                    if (isTrayType(trayType, TrayType.AppIndicator)) {
                        // appindicators

                        // requires the install of libappindicator which is GTK2 (as of 25DEC2016)
                        // requires the install of libappindicator3 which is GTK3 (as of 25DEC2016)

                        if (!AppIndicator.isLoaded) {
                            if (Gtk.isGtk2) {
                                logger.error("Unable to initialize AppIndicator for Arch linux, it requires GTK2! " +
                                             "Please install libappindicator, for example: 'sudo pacman -S libappindicator'");
                                systemTrayMenu = null;
                                systemTray = null;
                                return;
                            } else {
                                logger.error("Unable to initialize AppIndicator for Arch linux, it requires GTK3! " +
                                             "Please install libappindicator3, for example: 'sudo pacman -S libappindicator3'"); // GTK3
                                systemTrayMenu = null;
                                systemTray = null;
                                return;
                            }
                        }
                    }
                }


                if (isTrayType(trayType, TrayType.AppIndicator)) {
                    if (Gtk.isGtk2 && AppIndicator.isVersion3) {
                        try {
                            trayType = selectType(useNativeMenus, TrayType.GtkStatusIcon);
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

                            systemTrayMenu = null;
                            systemTray = null;
                            return;
                        }
                    }

                    if (!AppIndicator.isLoaded) {
                        // YIKES. Try to fallback to GtkStatusIndicator, since AppIndicator couldn't load.
                        logger.warn("Unable to initialize the AppIndicator correctly, falling back to GtkStatusIcon type");
                        trayType = selectTypeQuietly(useNativeMenus, TrayType.GtkStatusIcon);
                    }
                }
            }




            if (isJavaFxLoaded) {
                // This will initialize javaFX dispatch methods
                JavaFX.init();
            }
            else if (isSwtLoaded) {
                // This will initialize swt dispatch methods
                Swt.init();
            }


            if ((isJavaFxLoaded || isSwtLoaded) && SwingUtilities.isEventDispatchThread()) {
                // oh boy! This WILL NOT WORK. Let the dev know
                logger.error("SystemTray initialization for JavaFX or SWT **CAN NOT** occur on the Swing Event Dispatch Thread " +
                             "(EDT). Something is seriously wrong.");

                systemTrayMenu = null;
                systemTray = null;
                return;
            }

            // javaFX and SWT should not start on the EDT!!



            // if it's linux + native menus must not start on the EDT!
            // _AwtTray must be constructed on the EDT however...
            if (isJavaFxLoaded || isSwtLoaded ||
                ((OS.isLinux() || OS.isUnix()) && NativeUI.class.isAssignableFrom(trayType) && trayType != _AwtTray.class)) {
                try {
                    reference.set((Tray) trayType.getConstructors()[0].newInstance(systemTray));
                    logger.info("Successfully Loaded: {}", trayType.getSimpleName());
                } catch (Exception e) {
                    logger.error("Unable to create tray type: '" + trayType.getSimpleName() + "'", e);
                }
            } else {
                if (trayType == _AwtTray.class) {
                    // ensure awt toolkit is initialized.
                    java.awt.Toolkit.getDefaultToolkit();
                }

                // have to construct swing stuff inside the swing EDT
                final Class<? extends Menu> finalTrayType = trayType;
                SwingUtil.invokeAndWait(new Runnable() {
                    @Override
                    public
                    void run() {
                        try {
                            reference.set((Tray) finalTrayType.getConstructors()[0].newInstance(systemTray));
                            logger.info("Successfully Loaded: {}", finalTrayType.getSimpleName());
                        } catch (Exception e) {
                            logger.error("Unable to create tray type: '" + finalTrayType.getSimpleName() + "'", e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Unable to create tray type: '" + trayType.getSimpleName() + "'", e);
        }

        systemTrayMenu = reference.get();



        // verify that we have what we are expecting.
        if (OS.isWindows() && systemTrayMenu instanceof SwingUI) {
            // this configuration is OK.
        } else if (useNativeMenus && systemTrayMenu instanceof NativeUI) {
            // this configuration is OK.
        } else if (!useNativeMenus && systemTrayMenu instanceof SwingUI) {
            // this configuration is OK.
        } else {
            logger.error("Unable to correctly initialize the System Tray. Please write an issue and include your " +
                                       "OS type and configuration");
            systemTrayMenu = null;
            systemTray = null;
            return;
        }


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

    /**
     * Gets the version number.
     */
    public static
    String getVersion() {
        return "2.20";
    }

    /**
     * Returns a SystemTray instance that uses a custom Swing menus, which is more advanced than the native menus. The drawback is that
     * this menu is not native, and so loses the specific Look and Feel of that platform.
     * <p>
     * This always returns the same instance per JVM (it's a singleton), and on some platforms the system tray may not be
     * supported, in which case this will return NULL.
     * <p>
     * If this is using the Swing SystemTray and a SecurityManager is installed, the AWTPermission {@code accessSystemTray} must
     * be granted in order to get the {@code SystemTray} instance. Otherwise this will return null.
     */
    public static
    SystemTray getSwing() {
        init(false);
        return systemTray;
    }

    /**
     * Enables native menus on Linux/OSX instead of the custom swing menu. Windows will always use a custom Swing menu. The drawback is
     * that this menu is native, and sometimes native menus looks absolutely HORRID.
     * <p>
     * This always returns the same instance per JVM (it's a singleton), and on some platforms the system tray may not be
     * supported, in which case this will return NULL.
     * <p>
     * If this is using the Swing SystemTray and a SecurityManager is installed, the AWTPermission {@code accessSystemTray} must
     * be granted in order to get the {@code SystemTray} instance. Otherwise this will return null.
     */
    public static
    SystemTray getNative() {
        init(true);
        return systemTray;
    }

    /**
     * Shuts-down the SystemTray, by removing the menus + tray icon. After calling this method, you MUST call `get()` or `getNative()`
     * again to obtain a new reference to the SystemTray.
     */
    public
    void shutdown() {
        // this will call "dispatchAndWait()" behind the scenes, so it is thread-safe
        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.remove();
        }

        systemTrayMenu = null;
    }

    /**
     * Gets the 'status' string assigned to the system tray
     */
    public
    String getStatus() {
        final Tray tray = systemTrayMenu;
        if (tray != null) {
            return tray.getStatus();
        }

        return "";
    }

    /**
     * Sets a 'status' string at the first position in the popup menu. This 'status' string appears as a disabled menu entry.
     *
     * @param statusText the text you want displayed, null if you want to remove the 'status' string
     */
    public
    void setStatus(String statusText) {
        final Tray tray = systemTrayMenu;
        if (tray != null) {
            tray.setStatus(statusText);
        }
    }

    /**
     * @return the attached menu to this system tray
     */
    public
    Menu getMenu() {
        return systemTrayMenu;
    }

    /**
     * Converts the specified JMenu into a compatible SystemTray menu, using the JMenu icon as the image for the SystemTray. The currently
     * supported menu items are `JMenu`, `JCheckBoxMenuItem`, `JMenuItem`, and `JSeparator`. Because this is a conversion, the JMenu
     * is no longer valid after this action.
     *
     * @return the attached menu to this system tray based on the specified JMenu
     */
    public
    Menu setMenu(final JMenu jMenu) {
        Menu menu = systemTrayMenu;

        if (menu != null) {
            Icon icon = jMenu.getIcon();
            BufferedImage bimage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            setImage(bimage);

            Component[] menuComponents = jMenu.getMenuComponents();
            for (Component c : menuComponents) {
                if (c instanceof JMenu) {
                    menu.add((JMenu) c);
                }
                else if (c instanceof JCheckBoxMenuItem) {
                    menu.add((JCheckBoxMenuItem) c);
                }
                else if (c instanceof JMenuItem) {
                    menu.add((JMenuItem) c);
                }
                else if (c instanceof JSeparator) {
                    menu.add((JSeparator) c);
                }
            }
        }

        return menu;
    }


    /**
     * Shows (if hidden), or hides (if showing) the system tray.
     */
    public
    void setEnabled(final boolean enabled) {
        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setEnabled(enabled);
        }
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
    public
    void setTooltip(final String tooltipText) {
        final Tray tray = systemTrayMenu;
        if (tray != null) {
            tray.setTooltip(tooltipText);
        }
    }


    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imageFile the file of the image to use or null
     */
    public
    void setImage(final File imageFile) {
        if (imageFile == null) {
            throw new NullPointerException("imageFile cannot be null!");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage(imageFile);
        }
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
        if (imagePath == null) {
            throw new NullPointerException("imagePath cannot be null!");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage(imagePath);
        }
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     *<p>
     * This method will cache the image if it needs to be resized to fit.
     *
     * @param imageUrl the URL of the image to use or null
     */
    public
    void setImage(final URL imageUrl) {
        if (imageUrl == null) {
            throw new NullPointerException("imageUrl cannot be null!");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage(imageUrl);
        }
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
        if (imageStream == null) {
            throw new NullPointerException("imageStream cannot be null!");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage(imageStream);
        }
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
        if (image == null) {
            throw new NullPointerException("image cannot be null!");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage(image);
        }
    }

    /**
     * Specifies the new image to set for a menu entry, NULL to delete the image
     * <p>
     * This method will cache the image if it needs to be resized to fit.
     *
     *@param imageStream the ImageInputStream of the image to use
     */
    public
    void setImage(final ImageInputStream imageStream) {
        if (imageStream == null) {
            throw new NullPointerException("image cannot be null!");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage(imageStream);
        }
    }
}

