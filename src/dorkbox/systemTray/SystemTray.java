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
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.stream.ImageInputStream;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dorkbox.systemTray.gnomeShell.AppIndicatorExtension;
import dorkbox.systemTray.gnomeShell.DummyFile;
import dorkbox.systemTray.gnomeShell.LegacyExtension;
import dorkbox.systemTray.ui.awt._AwtTray;
import dorkbox.systemTray.ui.gtk._AppIndicatorNativeTray;
import dorkbox.systemTray.ui.gtk._GtkStatusIconNativeTray;
import dorkbox.systemTray.ui.osx._OsxNativeTray;
import dorkbox.systemTray.ui.swing.SwingUIFactory;
import dorkbox.systemTray.ui.swing._SwingTray;
import dorkbox.systemTray.ui.swing._WindowsNativeTray;
import dorkbox.systemTray.util.EventDispatch;
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.systemTray.util.LinuxSwingUI;
import dorkbox.systemTray.util.SizeAndScalingUtil;
import dorkbox.systemTray.util.SystemTrayFixes;
import dorkbox.systemTray.util.WindowsSwingUI;
import dorkbox.util.CacheUtil;
import dorkbox.util.IO;
import dorkbox.util.JavaFX;
import dorkbox.util.OS;
import dorkbox.util.OSUtil;
import dorkbox.util.OSUtil.DesktopEnv;
import dorkbox.util.Property;
import dorkbox.util.SwingUtil;
import dorkbox.util.Swt;
import dorkbox.util.jna.linux.AppIndicator;
import dorkbox.util.jna.linux.Gtk;
import dorkbox.util.jna.linux.GtkCheck;
import dorkbox.util.jna.linux.GtkEventDispatch;


/**
 * Professional, cross-platform **SystemTray**, **AWT**, **GtkStatusIcon**, and **AppIndicator** support for Java applications.
 * <p>
 * This library provides **OS native** menus and **Swing** menus.
 * <ul>
 *     <li> Swing menus are the default preferred type because they offer more features (images attached to menu entries, text styling, etc) and
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
        /** Will choose as a 'best guess' which tray type to use */
        AutoDetect,
        GtkStatusIcon,
        AppIndicator,
        WindowsNotifyIcon,
        Swing,
        OSXStatusItem,
        AWT
    }

    @Property
    /** Enables auto-detection for the system tray. This should be mostly successful. */
    public static boolean AUTO_SIZE = true;

    @Property
    /** Default name of the application, sometimes shows on tray-icon mouse over. Not used for all OSes, but mostly for Linux */
    public static String APP_NAME = "SystemTray";

    @Property
    /** Forces the system tray to always choose GTK2 (even when GTK3 might be available). */
    public static boolean FORCE_GTK2 = false;

    @Property
    /** Prefer to load GTK3 before trying to load GTK2. */
    public static boolean PREFER_GTK3 = true;

    @Property
    /**
     * Forces the system tray detection to be AutoDetect, GtkStatusIcon, AppIndicator, WindowsNotifyIcon, Swing, or AWT.
     * <p>
     * This is an advanced feature, and it is recommended to leave at AutoDetect.
     */
    public static TrayType FORCE_TRAY_TYPE = TrayType.AutoDetect;

    @Property
    /**
     * When in compatibility mode, and the JavaFX/SWT primary windows are closed, we want to make sure that the SystemTray is also closed.
     * Additionally, when using the Swing tray type, Windows does not always remove the tray icon if the JVM is stopped, and this makes
     * sure that the tray is also removed from the notification area.
     * <p>
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
     * Allows the SystemTray logic to ignore if root is detected. Usually when running as root it won't work (because of how DBUS
     * operates), but in rare situations, it might work.
     * <p>
     * This is an advanced feature, and it is recommended to leave as true
     */
    public static boolean ENABLE_ROOT_CHECK = true;

    @Property
    /**
     * Allows a custom look and feel for the Swing UI, if defined. See the test example for specific use.
     */
    public static SwingUIFactory SWING_UI = null;

    @Property
    /**
     * This property is provided for debugging any errors in the logic used to determine the system-tray type.
     */
    public static boolean DEBUG = false;


    private static volatile SystemTray systemTray = null;
    private static volatile Tray systemTrayMenu = null;


    private static
    boolean isTrayType(final Class<? extends Tray> tray, final TrayType trayType) {
        switch (trayType) {
            case GtkStatusIcon: return tray == _GtkStatusIconNativeTray.class;
            case AppIndicator: return tray == _AppIndicatorNativeTray.class;
            case WindowsNotifyIcon: return tray == _WindowsNativeTray.class;
            case Swing: return tray == _SwingTray.class;
            case OSXStatusItem: return tray == _OsxNativeTray.class;
            case AWT: return tray == _AwtTray.class;
        }

        return false;
    }

    private static
    Class<? extends Tray> selectType(final TrayType trayType) throws Exception {
        if (trayType == TrayType.GtkStatusIcon) {
            return _GtkStatusIconNativeTray.class;
        }
        else if (trayType == TrayType.AppIndicator) {
            return _AppIndicatorNativeTray.class;
        }
        else if (trayType == TrayType.WindowsNotifyIcon) {
            return _WindowsNativeTray.class;
        }
        else if (trayType == TrayType.Swing) {
            return _SwingTray.class;
        }
        else if (trayType == TrayType.OSXStatusItem) {
            return _OsxNativeTray.class;
        }
        else if (trayType == TrayType.AWT) {
            return _AwtTray.class;
        }

        return null;
    }

    private static
    Class<? extends Tray> selectTypeQuietly(final TrayType trayType) {
        try {
            return selectType(trayType);
        } catch (Throwable t) {
            if (DEBUG) {
                logger.error("Cannot initialize {}", trayType.name(), t);
            }
        }

        return null;
    }

    // This will return what the default "autodetect" tray type should be
    private static
    Class<? extends Tray> getAutoDetectTrayType() {
        if (OS.isWindows()) {
            try {
                return selectType(TrayType.WindowsNotifyIcon);
            } catch (Throwable e) {
                logger.error("Something is wrong. Please create an issue with this error message!", e);
            }
        }
        else if (OS.isMacOsX()) {
            // macos can ONLY use the OSXStatusItem or AWT if you want it to follow the L&F of the OS. It is the default.
            try {
                return selectType(TrayType.OSXStatusItem);
            } catch (Throwable e) {
                logger.error("Unable to select the OSX native status item. Falling back to using AWT.", e);

                try {
                    return selectType(TrayType.AWT);
                } catch (Throwable e1) {
                    logger.error("You might need to grant the AWTPermission `accessSystemTray` to the SecurityManager.");
                }
            }
        }
        else if ((OS.isLinux() || OS.isUnix())) {
            // see: https://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running

            // For funsies, SyncThing did a LOT of work on compatibility (unfortunate for us) in python.
            // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

            // quick check, because we know that unity uses app-indicator. Maybe REALLY old versions do not. We support 14.04 LTE at least
            OSUtil.DesktopEnv.Env de = OSUtil.DesktopEnv.get();

            if (DEBUG) {
                logger.debug("Currently using the '{}' desktop environment" + OS.LINE_SEPARATOR + OSUtil.Linux.getInfo(), de);
            }

            switch (de) {
                case Gnome: {
                    // check other DE / OS combos that are based on gnome
                    String GDM = System.getenv("GDMSESSION");

                    if (DEBUG) {
                        logger.debug("Currently using the '{}' session type", GDM);
                    }

                    if ("gnome".equalsIgnoreCase(GDM) || "default".equalsIgnoreCase(GDM)) {
                        // UGH. At least ubuntu un-butchers gnome.
                        if (OSUtil.Linux.isUbuntu()) {
                            // so far, because of the interaction between gnome3 + ubuntu, the GtkStatusIcon miraculously works.
                            return selectTypeQuietly(TrayType.GtkStatusIcon);
                        }

                        // "default" can be gnome3 on debian/kali


                        // for everyone else, we have to check the gnome version.
                        // gnome2 -> everything is glorious and just works.
                        // gnome3 -> someone started sniffing glue.
                        //   < 3.16  - It's in the notification tray. SystemTray works, but will only show via SUPER+M.
                        //   < 3.26  - (3.16 introduced the legacy tray, and removed gtkstatus icon "normal" placement) legacy icons via shell extensions work + GTK workarounds
                        //   >= 3.26 - (3.26 removed the legacy tray) app-indicator icons via shell extensions + libappindicator work


                        String gnomeVersion = DesktopEnv.getGnomeVersion();
                        if (gnomeVersion == null) {
                            // this shouldn't ever happen!

                            logger.error("GNOME shell detected, but UNDEFINED shell version. This should never happen. Falling back to GtkStatusIcon. " +
                                         "Please create an issue with as many details as possible.");

                            return selectTypeQuietly(TrayType.GtkStatusIcon);
                        }

                        if (DEBUG) {
                            logger.debug("Gnome Version: {}", gnomeVersion);
                        }

                        // get the major/minor/patch, if possible.
                        int major = 0;
                        double minorAndPatch = 0.0D;

                        // this isn't the BEST way to do this, but it's simple and easy to understand
                        String[] split = gnomeVersion.split("\\.",2);

                        try {
                            major = Integer.parseInt(split[0]);
                            minorAndPatch = Double.parseDouble(split[1]);
                        } catch (Exception ignored) {
                        }



                        if (major == 2) {
                            return selectTypeQuietly(TrayType.GtkStatusIcon);
                        }
                        else if (major == 3) {
                            if (minorAndPatch < 16.0D) {
                                logger.warn("SystemTray works, but will only show via SUPER+M.");
                                return selectTypeQuietly(TrayType.GtkStatusIcon);
                            }
                            else if (minorAndPatch < 26.0D) {
                                Tray.gtkGnomeWorkaround = true;

                                LegacyExtension.install();

                                // now, what VERSION of fedora? "normal" fedora doesn't have AppIndicator installed, so we have to use GtkStatusIcon
                                // 23 is gtk, 24/25/26 is gtk (but also wrong size unless we adjust it. ImageUtil automatically does this)
                                return selectTypeQuietly(TrayType.GtkStatusIcon);
                            }
                            else {
                                // 'pure' gnome3 DOES NOT support legacy tray icons any more. This ability has ENTIRELY been removed. NOTE: Ubuntu still supports these via app-indicators.
                                // the work-around for fedora is to install libappindicator + the appindicator extension

                                // install the appindicator Gnome extension
                                if (!AppIndicatorExtension.isInstalled()) {
                                    AppIndicatorExtension.install();

                                    logger.error("You must log out and then in again for system tray settings to apply.");
                                    return null;
                                }

                                return selectTypeQuietly(TrayType.AppIndicator);
                            }
                        }
                        else {
                            logger.error("GNOME shell detected, but UNKNOWN shell version. This should never happen. Falling back to GtkStatusIcon. " +
                                         "Please create an issue with as many details as possible.");

                            return selectTypeQuietly(TrayType.GtkStatusIcon);
                        }
                    }
                    else if ("cinnamon".equalsIgnoreCase(GDM)) {
                        return selectTypeQuietly(TrayType.GtkStatusIcon);
                    }
                    else if ("gnome-classic".equalsIgnoreCase(GDM)) {
                        return selectTypeQuietly(TrayType.GtkStatusIcon);
                    }
                    else if ("gnome-fallback".equalsIgnoreCase(GDM)) {
                        return selectTypeQuietly(TrayType.GtkStatusIcon);
                    }
                    else if ("ubuntu".equalsIgnoreCase(GDM)) {
                        int[] version = OSUtil.Linux.getUbuntuVersion();

                        // ubuntu 17.10+ uses the NEW gnome DE, which screws up previous Ubuntu workarounds, since it's now mostly Gnome
                        if (version[0] == 17 && version[1] == 10) {
                            // this is gnome 3.26.1, so we install the Gnome extension
                            Tray.gtkGnomeWorkaround = true;
                            LegacyExtension.install();
                        }
                        else if (version[0] >= 18) {
                            // ubuntu 18.04 doesn't need the extension BUT does need a logout-login (or gnome-shell restart) for it to work

                            // we copy over a config file so we know if we have already restarted the shell or shown the warning. A logout-login will also work.
                            DummyFile.install();
                        }

                        return selectTypeQuietly(TrayType.AppIndicator);
                    }

                    logger.error("GNOME shell detected, but UNKNOWN type. This should never happen. Falling back to GtkStatusIcon. " +
                                 "Please create an issue with as many details as possible.");

                    return selectTypeQuietly(TrayType.GtkStatusIcon);
                }
                case KDE: {
                    // kde 5.8+ is "high DPI", so we need to adjust the scale. Image resize will do that

                    double plasmaVersion = DesktopEnv.getPlasmaVersion();

                    if (DEBUG) {
                        logger.debug("KDE Plasma Version: {}", plasmaVersion);
                    }

                    if (plasmaVersion == 0.0) {
                        // this shouldn't ever happen!

                        logger.error("KDE Plasma detected, but UNDEFINED shell version. This should never happen. Falling back to GtkStatusIcon. " +
                                     "Please create an issue with as many details as possible.");

                        return selectTypeQuietly(TrayType.GtkStatusIcon);
                    }

                    if (plasmaVersion <= 5.5) {
                        // older versions use GtkStatusIcon
                        return selectTypeQuietly(TrayType.GtkStatusIcon);
                    } else {
                        // newer versions use appindicator, but the user MIGHT have to install libappindicator
                        return selectTypeQuietly(TrayType.AppIndicator);
                    }
                }
                case Unity: {
                    // Ubuntu Unity is a weird combination. It's "Gnome", but it's not "Gnome Shell".
                    return selectTypeQuietly(TrayType.AppIndicator);
                }
                case Unity7: {
                    // Ubuntu Unity7 (17.04, which has MIR) is a weird combination. It's "Gnome", but it's not "Gnome Shell".
                    return selectTypeQuietly(TrayType.AppIndicator);
                }
                case XFCE: {
                    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
                    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
                    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25

                    // so far, it is OK to use GtkStatusIcon on XFCE <-> XFCE4 inclusive
                    return selectTypeQuietly(TrayType.GtkStatusIcon);
                }
                case LXDE: {
                    return selectTypeQuietly(TrayType.GtkStatusIcon);
                }
                case MATE: {
                    return selectTypeQuietly(TrayType.GtkStatusIcon);
                }
                case Pantheon: {
                    // elementaryOS. It only supports appindicator (not gtkstatusicon)
                    // http://bazaar.launchpad.net/~wingpanel-devs/wingpanel/trunk/view/head:/sample/SampleIndicator.vala

                    // in version 5.0+, they REMOVED support for appindicators. You can add it back via
                    // see: https://git.dorkbox.com/dorkbox/elementary-indicators

                    // ElementaryOS shows the checkbox on the right, everyone else is on the left.
                    // With eOS, we CANNOT show the spacer image. It does not work.
                    return selectTypeQuietly(TrayType.AppIndicator);
                }
                case ChromeOS:
                    // ChromeOS cannot use the swing tray (ChromeOS is not supported!), nor AppIndicaitor/GtkStatusIcon, as those
                    // libraries do not exist on ChromeOS. Additionally, Java cannot load external libraries unless they are in /bin,
                    // BECAUSE of the `noexec` bit set. If JNA is moved into /bin, and the JNA library is specified to load from that
                    // location, we can use JNA.
                    return null;
            }

            // Try to autodetect if we can use app indicators (or if we need to fallback to GTK indicators)
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
                                    return selectType(TrayType.AppIndicator);
                                } catch (Exception e) {
                                    if (DEBUG) {
                                        logger.error("AppIndicator support detected, but unable to load the library. Falling back to GTK",
                                                     e);
                                    }
                                    else {
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

            if (OS.isLinux()) {
                // now just blanket query what we are to guess...
                if (OSUtil.Linux.isUbuntu()) {
                    return selectTypeQuietly(TrayType.AppIndicator);
                }
                else if (OSUtil.Linux.isFedora()) {
                    return selectTypeQuietly(TrayType.AppIndicator);
                } else {
                    // AppIndicators are now the "default" for most linux distro's.
                    return selectTypeQuietly(TrayType.AppIndicator);
                }
            }
        }

        throw new RuntimeException("This OS is not supported. Please create an issue with the details from `SystemTray.DEBUG=true;`");
    }

    @SuppressWarnings({"ConstantConditions", "StatementWithEmptyBody"})
    private static
    void init() {
        if (systemTray != null) {
            return;
        }

//        if (DEBUG) {
//            Properties properties = System.getProperties();
//            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
//                logger.debug(entry.getKey() + " : " + entry.getValue());
//            }
//        }

        // no tray in a headless environment
        if (GraphicsEnvironment.isHeadless()) {
            logger.error("Cannot use the SystemTray in a headless environment");

            systemTrayMenu = null;
            systemTray = null;
            return;
        }

        boolean isNix = OS.isLinux() || OS.isUnix();
        boolean isWindows = OS.isWindows();
        boolean isMacOsX = OS.isMacOsX();

        // Windows can ONLY use Swing (non-native) or WindowsNotifyIcon (native) - AWT looks absolutely horrid and is not an option
        // OSx can use Swing (non-native) or AWT (native).
        // Linux can use Swing (non-native), AWT (native), GtkStatusIcon (native), or AppIndicator (native)
        if (isWindows) {
            if (FORCE_TRAY_TYPE != TrayType.AutoDetect && FORCE_TRAY_TYPE != TrayType.Swing && FORCE_TRAY_TYPE != TrayType.WindowsNotifyIcon) {
                // windows MUST use swing/windows-notify-icon only!
                FORCE_TRAY_TYPE = TrayType.AutoDetect;

                logger.warn("Windows cannot use the '" + FORCE_TRAY_TYPE + "' SystemTray type, defaulting to swing implementation");
            }
        }
        else if (isMacOsX) {
            // cannot mix Swing/AWT and JavaFX for MacOSX in java7 (fixed in java8) without special stuff.
            // https://bugs.openjdk.java.net/browse/JDK-8116017
            // https://bugs.openjdk.java.net/browse/JDK-8118714
            if (JavaFX.isLoaded && OS.javaVersion <= 7 && !System.getProperty("javafx.macosx.embedded", "false").equals("true")) {

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


            if (Swt.isLoaded) {
                // versions of SWT older than v4.4, are INCOMPATIBLE with us.
                // Of note, v4.3 is the "last released" version of SWT by eclipse AND IT WILL NOT WORK!!
                // for NEWER versions of SWT via maven, use http://maven-eclipse.github.io/maven
                if (Swt.getVersion() < 4430) {
                    logger.error("Unable to use currently loaded version of SWT, it is TOO OLD. Please use version 4.4+");

                    systemTrayMenu = null;
                    systemTray = null;
                    return;
                }

                // cannot mix Swing and SWT on MacOSX (for all versions of java) so we force native menus instead, which work just fine with SWT
                // http://mail.openjdk.java.net/pipermail/bsd-port-dev/2008-December/000173.html
                if (FORCE_TRAY_TYPE == TrayType.Swing) {
                    if (AUTO_FIX_INCONSISTENCIES) {
                        logger.warn("Unable to load Swing + SWT (for all versions of Java). Using the AWT Tray type instead.");

                        FORCE_TRAY_TYPE = TrayType.AWT;
                    }
                    else {
                        logger.error("Unable to load Swing + SWT (for all versions of Java). " +
                                     "Please set `SystemTray.AUTO_FIX_INCONSISTENCIES=true;` to automatically fix this problem.\"");

                        systemTrayMenu = null;
                        systemTray = null;
                        return;
                    }
                }
            }

            if (FORCE_TRAY_TYPE != TrayType.AutoDetect && FORCE_TRAY_TYPE != TrayType.Swing && FORCE_TRAY_TYPE != TrayType.AWT) {
                // MacOsX can only use swing and AWT
                FORCE_TRAY_TYPE = TrayType.AutoDetect;

                logger.warn("MacOS cannot use the '" + FORCE_TRAY_TYPE + "' SystemTray type, defaulting to the AWT Tray type instead.");
            }
        }
        else if (isNix) {
            // linux/unix can use all of the tray types. AWT looks horrid. GTK versions are really sensitive...

            // this checks to see if Swing/SWT/JavaFX has loaded GTK yet, and if so, what version they loaded.
            int loadedGtkVersion = GtkCheck.getLoadedGtkVersion();
            if (loadedGtkVersion == 2) {
                if (AUTO_FIX_INCONSISTENCIES) {
                    if (!FORCE_GTK2) {
                        if (JavaFX.isLoaded) {
                            // JavaFX Java7,8 is GTK2 only. Java9 can MAYBE have it be GTK3 if `-Djdk.gtk.version=3` is specified
                            // see
                            // http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
                            // https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
                            // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.

                            // we must use GTK2, because JavaFX is GTK2
                            FORCE_GTK2 = true;
                            if (DEBUG) {
                                logger.debug("Forcing GTK2 because JavaFX is GTK2");
                            }
                        }
                        else if (Swt.isLoaded) {
                            // Necessary for us to work with SWT based on version info. We can try to set us to be compatible with whatever it is set to
                            // System.setProperty("SWT_GTK3", "0");

                            // we must use GTK2, because SWT is GTK2
                            FORCE_GTK2 = true;
                            if (DEBUG) {
                                logger.debug("Forcing GTK2 because SWT is GTK2");
                            }
                        }
                        else {
                            // we are NOT using javaFX/SWT and our UI is GTK2 and we want GTK3
                            // JavaFX/SWT can be GTK3, but Swing is not GTK3.

                            // we must use GTK2 because Java is configured to use GTK2
                            FORCE_GTK2 = true;
                            if (DEBUG) {
                                logger.debug("Forcing GTK2 because Java has already loaded GTK2");
                            }
                        }
                    } else {
                        // we are already forcing GTK2, so no extra actions necessary
                    }
                } else {
                    // !AUTO_FIX_INCONSISTENCIES

                    if (!FORCE_GTK2) {
                        // clearly the app developer did not want us to automatically fix anything, and have not correctly specified how
                        // to load GTK, so abort with an error message.
                        logger.error("Unable to use the SystemTray when there is a mismatch for GTK loaded preferences. Please correctly " +
                                     "set `SystemTray.FORCE_GTK2=true` or set `SystemTray.AUTO_FIX_INCONSISTENCIES=true`.  Aborting...");

                        systemTrayMenu = null;
                        systemTray = null;
                        return;
                    }
                }
            }
            else if (loadedGtkVersion == 3) {
                if (AUTO_FIX_INCONSISTENCIES) {
                    if (JavaFX.isLoaded) {
                        // JavaFX Java7,8 is GTK2 only. Java9 can MAYBE have it be GTK3 if `-Djdk.gtk.version=3` is specified
                        // see
                        // http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
                        // https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
                        // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.

                        if (FORCE_GTK2) {
                            // if we are java9, then we can change it -- otherwise we cannot.
                            if (OS.javaVersion == 9) {
                                FORCE_GTK2 = false;
                                logger.warn("Unable to use the SystemTray when JavaFX is configured to use GTK3 and the SystemTray is " +
                                            "configured to use GTK2. Please configure JavaFX to use GTK2 (via `System.setProperty(\"jdk.gtk.version\", \"3\");`) " +
                                            "before JavaFX is initialized, or set `SystemTray.FORCE_GTK2=false;`  Undoing `FORCE_GTK2`.");

                            }
                        }

                        if (!PREFER_GTK3) {
                            // we should use GTK3, since that is what is already loaded
                            PREFER_GTK3 = true;
                            if (DEBUG) {
                                logger.debug("Preferring GTK3 even though specified otherwise, because JavaFX is GTK3");
                            }
                        }
                    }
                    else if (Swt.isLoaded) {
                        // Necessary for us to work with SWT based on version info. We can try to set us to be compatible with whatever it is set to
                        // System.setProperty("SWT_GTK3", "0");

                        if (FORCE_GTK2) {
                            FORCE_GTK2 = false;
                            logger.warn("Unable to use the SystemTray when SWT is configured to use GTK3 and the SystemTray is configured to use " +
                                        "GTK2. Please configure SWT to use GTK2, via `System.setProperty(\"SWT_GTK3\", \"0\");` before SWT is " +
                                        "initialized, or set `SystemTray.FORCE_GTK2=false;`");
                        }

                        if (!PREFER_GTK3) {
                            // we should use GTK3, since that is what is already loaded
                            PREFER_GTK3 = true;
                            if (DEBUG) {
                                logger.debug("Preferring GTK3 even though specified otherwise, because SWT is GTK3");
                            }
                        }
                    }
                    else {
                        // we are NOT using javaFX/SWT and our UI is GTK3 and we want GTK3
                        // JavaFX/SWT can be GTK3, but Swing is (maybe in the future?) GTK3.

                        if (FORCE_GTK2) {
                            FORCE_GTK2 = false;
                            logger.warn("Unable to use the SystemTray when Swing is configured to use GTK3 and the SystemTray is " +
                                        "configured to use GTK2. Undoing `FORCE_GTK2.");
                        }

                        if (!PREFER_GTK3) {
                            // we should use GTK3, since that is what is already loaded
                            PREFER_GTK3 = true;
                            if (DEBUG) {
                                logger.debug("Preferring GTK3 even though specified otherwise, because Java has already loaded GTK3");
                            }
                        }
                    }

                } else {
                    // !AUTO_FIX_INCONSISTENCIES

                    if (JavaFX.isLoaded) {
                        // JavaFX Java7,8 is GTK2 only. Java9 can MAYBE have it be GTK3 if `-Djdk.gtk.version=3` is specified
                        // see
                        // http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
                        // https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
                        // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.

                        if (FORCE_GTK2) {
                            // if we are java9, then we can change it -- otherwise we cannot.
                            if (OS.javaVersion == 9) {
                                logger.error("Unable to use the SystemTray when JavaFX is configured to use GTK3 and the SystemTray is " +
                                            "configured to use GTK2. Please configure JavaFX to use GTK2 (via `System.setProperty(\"jdk.gtk.version\", \"3\");`) " +
                                            "before JavaFX is initialized, or set `SystemTray.FORCE_GTK2=false;`  Aborting.");

                            }
                            else {
                                logger.error("Unable to use the SystemTray when JavaFX is configured to use GTK3 and the SystemTray is configured to use " +
                                             "GTK2. Please set `SystemTray.FORCE_GTK2=false;`  Aborting.");
                            }

                            systemTrayMenu = null;
                            systemTray = null;
                            return;
                        }
                    }
                    else if (Swt.isLoaded) {
                        // Necessary for us to work with SWT based on version info. We can try to set us to be compatible with whatever it is set to
                        // System.setProperty("SWT_GTK3", "0");

                        if (FORCE_GTK2) {
                            logger.error("Unable to use the SystemTray when SWT is configured to use GTK3 and the SystemTray is configured to use " +
                                         "GTK2. Please configure SWT to use GTK2, via `System.setProperty(\"SWT_GTK3\", \"0\");` before SWT is " +
                                         "initialized, or set `SystemTray.FORCE_GTK2=false;`");

                            systemTrayMenu = null;
                            systemTray = null;
                            return;
                        }
                    }

                    else if (FORCE_GTK2) {
                        logger.error("Unable to use the SystemTray when Swing is configured to use GTK3 and the SystemTray is " +
                                    "configured to use GTK2. Aborting.");

                        systemTrayMenu = null;
                        systemTray = null;
                        return;
                    }
                }
            }
        }



        if (DEBUG) {
            logger.debug("OS: {}", System.getProperty("os.name"));
            logger.debug("Arch: {}", System.getProperty("os.arch"));

            String jvmName = System.getProperty("java.vm.name", "");
            String jvmVersion = System.getProperty("java.version", "");
            String jvmVendor = System.getProperty("java.vm.specification.vendor", "");
            logger.debug("{} {} {}", jvmVendor, jvmName, jvmVersion);


            logger.debug("Is Auto sizing tray/menu? {}", AUTO_SIZE);
            logger.debug("Is JavaFX detected? {}", JavaFX.isLoaded);
            logger.debug("Is SWT detected? {}", Swt.isLoaded);
            if (Swt.isLoaded) {
                logger.debug("SWT version: {}", Swt.getVersion());
            }

            logger.debug("Java Swing L&F: {}", UIManager.getLookAndFeel().getID());
            if (FORCE_TRAY_TYPE == TrayType.AutoDetect) {
                logger.debug("Auto-detecting tray type");
            }
            else {
                logger.debug("Forced tray type: {}", FORCE_TRAY_TYPE.name());
            }

            if (OS.isLinux()) {
                logger.debug("Force GTK2: {}", FORCE_GTK2);
                logger.debug("Prefer GTK3: {}", PREFER_GTK3);
            }
        }

        // Note: AppIndicators DO NOT support tooltips. We could try to create one, by creating a GTK widget and attaching it on
        // mouseover or something, but I don't know how to do that. It seems that tooltips for app-indicators are a custom job, as
        // all examined ones sometimes have it (and it's more than just text), or they don't have it at all. There is no mouse-over event.


        // this has to happen BEFORE any sort of swing system tray stuff is accessed
        Class<? extends Tray> trayType;
        if (SystemTray.FORCE_TRAY_TYPE == TrayType.AutoDetect) {
            trayType = getAutoDetectTrayType();
        } else {
            trayType = selectTypeQuietly(SystemTray.FORCE_TRAY_TYPE);
        }

        if (trayType == null) {
            if (OSUtil.DesktopEnv.isChromeOS()) {
                logger.error("ChromeOS detected and it is not supported. Aborting.");
            }

            systemTrayMenu = null;
            systemTray = null;
            return;
        }




        // fix various incompatibilities with selected tray types
        if (isNix) {
            // Ubuntu UNITY has issues with GtkStatusIcon (it won't work at all...)
            if (isTrayType(trayType, TrayType.GtkStatusIcon)) {
                OSUtil.DesktopEnv.Env de = OSUtil.DesktopEnv.get();

                if (OSUtil.Linux.isUbuntu() && OSUtil.DesktopEnv.isUnity(de)) {
                    if (AUTO_FIX_INCONSISTENCIES) {
                        // GTK2 does not support AppIndicators!
                        if (Gtk.isGtk2) {
                            trayType = selectTypeQuietly(TrayType.Swing);
                            logger.warn("Forcing Swing Tray type because Ubuntu Unity display environment removed support for GtkStatusIcons " +
                                        "and GTK2+ was specified.");
                        }
                        else {
                            // we must use AppIndicator because Ubuntu Unity removed GtkStatusIcon support
                            SystemTray.FORCE_TRAY_TYPE = TrayType.AppIndicator; // this is required because of checks inside of AppIndicator...
                            trayType = selectTypeQuietly(TrayType.AppIndicator);

                            logger.warn("Forcing AppIndicator because Ubuntu Unity display environment removed support for GtkStatusIcons.");
                        }
                    }
                    else {
                        logger.error("Unable to use the GtkStatusIcons when running on Ubuntu with the Unity display environment, and thus" +
                                     " the SystemTray will not work. " +
                                     "Please set `SystemTray.AUTO_FIX_INCONSISTENCIES=true;` to automatically fix this problem.");

                        systemTrayMenu = null;
                        systemTray = null;
                        return;
                    }
                }

                if (de == OSUtil.DesktopEnv.Env.Gnome) {
                    boolean hasWeirdOsProblems = OSUtil.Linux.isKali() || (OSUtil.Linux.isFedora());
                    if (hasWeirdOsProblems) {
                        // Fedora and Kali linux has some WEIRD graphical oddities via GTK3. GTK2 looks just fine.
                        PREFER_GTK3 = false;

                        if (DEBUG) {
                            logger.debug("Preferring GTK2 because this OS has weird graphical issues with GTK3 status icons");
                        }
                    }
                }
            }

            if (isTrayType(trayType, TrayType.AppIndicator)) {
                if (SystemTray.ENABLE_ROOT_CHECK &&  OSUtil.Linux.isRoot()) {
                    // if are we running as ROOT, there can be issues (definitely on Ubuntu 16.04, maybe others)!
                    if (AUTO_FIX_INCONSISTENCIES) {
                        trayType = selectTypeQuietly(TrayType.Swing);

                        logger.warn("Attempting to load the SystemTray as the 'root/sudo' user. This will likely not work because of dbus " +
                                     "restrictions. Using the Swing Tray type instead. Please refer to the readme notes or issue #63 on " +
                                    "how to work around this.");

                    } else {
                        logger.error("Attempting to load the SystemTray as the 'root/sudo' user. This will likely NOT WORK because of dbus " +
                                     "restrictions. Please refer to the readme notes or issue #63 on how to work around this.");
                    }
                }


                if (OSUtil.Linux.isElementaryOS() && OSUtil.Linux.getElementaryOSVersion()[0] >= 5) {
                    // in version 5.0+, they REMOVED support for appindicators. You can add it back via some extra work.
                    // see: https://git.dorkbox.com/dorkbox/elementary-indicators

                    // or you can download
                    // https://launchpad.net/~elementary-os/+archive/ubuntu/stable/+files/wingpanel-indicator-ayatana_2.0.3+r27+pkg17~ubuntu0.4.1.1_amd64.deb
                    // then dpkg -i filename

                    // check if this library is installed.
                    if (!new File("/usr/share/doc/wingpanel-indicator-ayatana").isDirectory()) {
                        logger.error("Unable to use the SystemTray as-is with this version of ElementaryOS. By default, tray icons *are not* supported, but a" +
                                     " workaround has been developed. Please see: https://git.dorkbox.com/dorkbox/elementary-indicators");

                        systemTrayMenu = null;
                        systemTray = null;
                        return;
                    }
                }
            }
        }


        if (trayType == null) {
            // unsupported tray, or unknown type
            trayType = selectTypeQuietly(TrayType.Swing);

            logger.error("SystemTray initialization failed. (Unable to discover which implementation to use). Falling back to the Swing Tray.");
        }


        final AtomicReference<Tray> reference = new AtomicReference<Tray>();

        // - appIndicator/gtk require strings (which is the path)
        // - swing version loads as an image (which can be stream or path, we use path)
        CacheUtil.tempDir = "SystemTrayImages";


        try {
            // at this point, the tray type is what it should be. If there are failures or special cases, all types will fall back to Swing.

            if (isNix) {
                // linux/unix need access to GTK, so load it up before the tray is loaded!
                // Swing gets the image size info VIA gtk, so this is important as well.
                GtkEventDispatch.startGui(FORCE_GTK2, PREFER_GTK3, DEBUG);
                GtkEventDispatch.waitForEventsToComplete();

                if (DEBUG) {
                    // output what version of GTK we have loaded.
                    logger.debug("GTK Version: " + Gtk.MAJOR + "." + Gtk.MINOR + "." + Gtk.MICRO);
                    logger.debug("Is the system already running GTK? {}", Gtk.alreadyRunningGTK);
                }

                if (!Gtk.isLoaded) {
                    trayType = selectTypeQuietly(TrayType.Swing);

                    logger.error("Unable to initialize GTK! Something is severely wrong! Using the Swing Tray type instead.");
                }

                // this will to load the app-indicator library
                else if (isTrayType(trayType, TrayType.AppIndicator)) {
                    if (!AppIndicator.isLoaded) {
                        // YIKES. AppIndicator couldn't load.

                        // can we fallback to swing? KDE does not work for this...
                        if (AUTO_FIX_INCONSISTENCIES && java.awt.SystemTray.isSupported() && !OSUtil.DesktopEnv.isKDE()) {
                            trayType = selectTypeQuietly(TrayType.Swing);

                            logger.warn("Unable to initialize the AppIndicator correctly. Using the Swing Tray type instead.");
                            logger.warn(AppIndicator.getInstallString(GtkCheck.isGtk2));
                        }
                        else {
                            // no swing, have to emit instructions how to fix the error.
                            logger.error("AppIndicator unable to load.  " + AppIndicator.getInstallString(GtkCheck.isGtk2));

                            systemTrayMenu = null;
                            systemTray = null;
                            return;
                        }
                    }
                }
            }



            // have to make adjustments BEFORE the tray/menu image size calculations
            if (AUTO_FIX_INCONSISTENCIES && SystemTray.SWING_UI == null) {
                if (isNix && isTrayType(trayType, TrayType.Swing)) {
                    SystemTray.SWING_UI = new LinuxSwingUI();
                }
                else if (isWindows &&
                         (isTrayType(trayType, TrayType.Swing) || isTrayType(trayType, TrayType.WindowsNotifyIcon))) {
                    SystemTray.SWING_UI = new WindowsSwingUI();
                }
            }


            // initialize tray/menu image sizes. This must be BEFORE the system tray has been created
            int trayImageSize = SizeAndScalingUtil.getTrayImageSize();
            int menuImageSize = SizeAndScalingUtil.getMenuImageSize(trayType);

            if (DEBUG) {
                logger.debug("Tray indicator image size: {}", trayImageSize);
                logger.debug("Tray menu image size: {}", menuImageSize);
            }

            if (AUTO_FIX_INCONSISTENCIES) {
                // this logic has to be before we create the system Tray, but after GTK is started (if applicable)
                if (isWindows && isTrayType(trayType, TrayType.Swing)) {
                    // we don't permit AWT for windows (it looks absolutely HORRID)

                    // Our default for windows is now a native tray icon (instead of the swing tray icon), but we preserve the use of Swing
                    // windows hard-codes the image size for AWT/SWING tray types
                    SystemTrayFixes.fixWindows(trayImageSize);
                }
                else if (isMacOsX && (isTrayType(trayType, TrayType.AWT) || isTrayType(trayType, TrayType.Swing))) {
                    // macosx doesn't respond to all buttons (but should)
                    SystemTrayFixes.fixMacOS();
                }
                else if (isNix && isTrayType(trayType, TrayType.Swing)) {
                    // linux/mac doesn't have transparent backgrounds for swing and hard-codes the image size
                    SystemTrayFixes.fixLinux(trayImageSize);
                }
            }




            if ((JavaFX.isLoaded || Swt.isLoaded) && SwingUtilities.isEventDispatchThread()) {
                // This WILL NOT WORK. Let the dev know
                logger.error("SystemTray initialization for JavaFX or SWT **CAN NOT** occur on the Swing Event Dispatch Thread " +
                             "(EDT). Something is seriously wrong.");

                systemTrayMenu = null;
                systemTray = null;
                return;
            }

            if (isTrayType(trayType, TrayType.Swing) || isTrayType(trayType, TrayType.AWT) || isTrayType(trayType, TrayType.WindowsNotifyIcon)) {
                // ensure AWT toolkit is initialized.
                java.awt.Toolkit.getDefaultToolkit();
            }



            // javaFX and SWT **CAN NOT** start on the EDT!!
            // linux + GTK/AppIndicator + windows-native menus must not start on the EDT!
            systemTray = new SystemTray();

            // AWT/Swing must be constructed on the EDT however...
            if (!JavaFX.isLoaded && !Swt.isLoaded &&
                (isTrayType(trayType, TrayType.Swing) || isTrayType(trayType, TrayType.AWT))) {
                // have to construct swing stuff inside the swing EDT
                final Class<? extends Menu> finalTrayType = trayType;
                SwingUtil.invokeAndWait(new Runnable() {
                    @Override
                    public
                    void run() {
                        try {
                            reference.set((Tray) finalTrayType.getConstructors()[0].newInstance(systemTray));
                        } catch (Exception e) {
                            logger.error("Unable to create tray type: '" + finalTrayType.getSimpleName() + "'", e);
                        }
                    }
                });
            }
            else {
                try {
                    reference.set((Tray) trayType.getConstructors()[0].newInstance(systemTray));
                } catch (Exception e) {
                    logger.error("Unable to create tray type: '" + trayType.getSimpleName() + "'", e);
                }
            }
        } catch (Exception e) {
            logger.error("Unable to create tray type: '{}'", trayType.getSimpleName(), e);
        }

        systemTrayMenu = reference.get();
        if (systemTrayMenu != null) {
            if (DEBUG) {
                logger.info("Successfully loaded type: {}", trayType.getSimpleName());
            } else {
                logger.info("Successfully loaded");
            }
        }

        // These install a shutdown hook in JavaFX/SWT, so that when the main window is closed -- the system tray is ALSO closed.
        if (ENABLE_SHUTDOWN_HOOK) {
            if (JavaFX.isLoaded) {
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
            else if (Swt.isLoaded) {
                // this is because SWT **ALSO** runs a gtk main loop, and when it stops (if we don't stop first), we become unresponsive
                // Also, it's nice to have us shutdown at the same time as the main application
                dorkbox.util.Swt.onShutdown(new Runnable() {
                    @Override
                    public
                    void run() {
                        if (systemTray != null) {
                            systemTray.shutdown();
                        }
                    }
                });
            }
            else if (isTrayType(trayType, TrayType.Swing) ||
                     isTrayType(trayType, TrayType.WindowsNotifyIcon) ||
                     isTrayType(trayType, TrayType.OSXStatusItem)) {
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public
                    void run() {
                        if (systemTray != null) {
                            systemTray.shutdown();
                        }
                    }
                }));
            }
        }
    }

    /**
     * Gets the version number.
     */
    public static
    String getVersion() {
        return "3.17";
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
    SystemTray get() {
        init();
        return systemTray;
    }

    /**
     * Shuts-down the SystemTray, by removing the menus + tray icon. After calling this method, you MUST call `get()` or `getNative()`
     * again to obtain a new reference to the SystemTray.
     */
    public
    void shutdown() {
        // this is thread-safe
        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.remove();
        }

        systemTrayMenu = null;
        EventDispatch.shutdown();
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
            throw new NullPointerException("imageFile");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage_(ImageResizeUtil.shouldResizeOrCache(true, imageFile));
        }
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imagePath the full path of the image to use or null
     */
    public
    void setImage(final String imagePath) {
        if (imagePath == null) {
            throw new NullPointerException("imagePath");
        }

        final Tray tray = systemTrayMenu;
        if (tray != null) {
            tray.setImage_(ImageResizeUtil.shouldResizeOrCache(true, imagePath));
        }
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imageUrl the URL of the image to use or null
     */
    public
    void setImage(final URL imageUrl) {
        if (imageUrl == null) {
            throw new NullPointerException("imageUrl");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage_(ImageResizeUtil.shouldResizeOrCache(true, imageUrl));
        }
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imageStream the InputStream of the image to use
     */
    public
    void setImage(final InputStream imageStream) {
        if (imageStream == null) {
            throw new NullPointerException("imageStream");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage_(ImageResizeUtil.shouldResizeOrCache(true, imageStream));
        }
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param image the image of the image to use
     */
    public
    void setImage(final Image image) {
        if (image == null) {
            throw new NullPointerException("image");
        }

        final Menu menu = systemTrayMenu;
        if (menu != null) {
            menu.setImage_(ImageResizeUtil.shouldResizeOrCache(true, image));
        }
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     *@param imageStream the ImageInputStream of the image to use
     */
    public
    void setImage(final ImageInputStream imageStream) {
        if (imageStream == null) {
            throw new NullPointerException("image");
        }

        final Tray tray = systemTrayMenu;
        if (tray != null) {
            tray.setImage_(ImageResizeUtil.shouldResizeOrCache(true, imageStream));
        }
    }

    /**
     * @return the system tray image size, accounting for OS and theme differences
     */
    public
    int getTrayImageSize() {
        return SizeAndScalingUtil.getTrayImageSize();
    }


    /**
     * @return the system tray menu image size, accounting for OS and theme differences
     */
    public
    int getMenuImageSize() {
        return SizeAndScalingUtil.getMenuImageSize(systemTrayMenu.getClass());
    }
}

