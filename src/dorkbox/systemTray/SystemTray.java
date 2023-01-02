/*
 * Copyright 2021 dorkbox, llc
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

import static dorkbox.systemTray.util.AutoDetectTrayType.fromClass;
import static dorkbox.systemTray.util.AutoDetectTrayType.isTrayType;
import static dorkbox.systemTray.util.AutoDetectTrayType.selectType;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Executors;
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

import dorkbox.jna.linux.AppIndicator;
import dorkbox.jna.linux.Gtk;
import dorkbox.jna.linux.GtkCheck;
import dorkbox.jna.linux.GtkEventDispatch;
import dorkbox.jna.rendering.RenderProvider;
import dorkbox.os.OS;
import dorkbox.systemTray.ui.swing.SwingUIFactory;
import dorkbox.systemTray.util.AutoDetectTrayType;
import dorkbox.systemTray.util.EventDispatch;
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.systemTray.util.LinuxSwingUI;
import dorkbox.systemTray.util.SizeAndScalingUtil;
import dorkbox.systemTray.util.SystemTrayFixes;
import dorkbox.systemTray.util.WindowsSwingUI;
import dorkbox.util.CacheUtil;
import dorkbox.util.SwingUtil;
import jdk.internal.event.Event;


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
@SuppressWarnings({"unused", "Duplicates", "WeakerAccess"})
public final
class SystemTray {
    public static final Logger logger = LoggerFactory.getLogger(SystemTray.class);

    public enum TrayType {
        /** Will choose as a 'best guess' which tray type to use */
        AutoDetect,
        Gtk,
        AppIndicator,
        WindowsNative,
        Swing,
        Osx,
        Awt;

        public TrayType safeFromString(String trayName) {
            try {
                return valueOf(trayName);
            } catch (Exception e) {
                return AutoDetect;
            }
        }
    }

    /** Enables auto-detection for the system tray. This should be mostly successful. */
    public static volatile boolean AUTO_SIZE = OS.INSTANCE.getBoolean(SystemTray.class.getCanonicalName() + ".AUTO_SIZE", true);

    /** Forces the system tray to always choose GTK2 (even when GTK3 might be available). */
    public static volatile boolean FORCE_GTK2 = OS.INSTANCE.getBoolean(SystemTray.class.getCanonicalName() + ".FORCE_GTK2", false);

    /** Prefer to load GTK3 before trying to load GTK2. */
    public static volatile boolean PREFER_GTK3 = OS.INSTANCE.getBoolean(SystemTray.class.getCanonicalName() + ".PREFER_GTK3", true);

    /**
     * Forces the system tray detection to be AutoDetect, GtkStatusIcon, AppIndicator, WindowsNotifyIcon, Swing, or AWT.
     * <p>
     * This is an advanced feature, and it is recommended to leave at AutoDetect.
     */
    public static volatile TrayType FORCE_TRAY_TYPE = TrayType.AppIndicator.safeFromString(
            OS.INSTANCE.getProperty(SystemTray.class.getCanonicalName() + ".FORCE_TRAY_TYPE", TrayType.AutoDetect.name()));

    /**
     * Allows the SystemTray logic to resolve OS inconsistencies for the SystemTray.
     * <p>
     * This is an advanced feature, and it is recommended to leave as true
     */
    public static volatile boolean AUTO_FIX_INCONSISTENCIES = OS.INSTANCE.getBoolean(SystemTray.class.getCanonicalName() +
                                                                            ".AUTO_FIX_INCONSISTENCIES", true);

    /**
     * Allows the SystemTray logic to ignore if root is detected. Usually when running as root it won't work (because of how DBUS
     * operates), but in rare situations, it might work.
     * <p>
     * This is an advanced feature, and it is recommended to leave as true
     */
    public static volatile boolean ENABLE_ROOT_CHECK = OS.INSTANCE.getBoolean(SystemTray.class.getCanonicalName() + ".ENABLE_ROOT_CHECK", true);

    /**
     * This property is provided for debugging any errors in the logic used to determine the system-tray type.
     */
    public static volatile boolean DEBUG = OS.INSTANCE.getBoolean(SystemTray.class.getCanonicalName() + ".DEBUG", false);

    /**
     * Allows a custom look and feel for the Swing UI, if defined. See the test example for specific use.
     */
    public static volatile SwingUIFactory SWING_UI = null;

    /**
     * Gets the version number.
     */
    public static
    String getVersion() {
        return "4.2";
    }

    static {
        // Add this project to the updates system, which verifies this class + UUID + version information
        dorkbox.updates.Updates.INSTANCE.add(SystemTray.class, "b35c107332d844559a3f877fcef42a21", getVersion());
    }

    /**
     * Enables native menus on Windows/Linux/macOS instead of the swing menu. The drawback is that this menu is native, and sometimes
     * native menus looks absolutely HORRID.
     * <p>
     * This always returns the same instance per JVM (it's a singleton), and on some platforms the system tray may not be
     * supported, in which case this will return NULL.
     * <p>
     * If this is using the Swing SystemTray and a SecurityManager is installed, the AWTPermission {@code accessSystemTray} must
     * be granted in order to get the {@code SystemTray} instance. Otherwise, this will return null.
     *
     * If you create MORE than 1 system tray, you should use {{@link SystemTray#get(String)}} instead, and specify a unique name for
     * each instance
     */
    public static
    SystemTray get() {
        return get("SystemTray");
    }

    /**
     * Enables native menus on Windows/Linux/macOS instead of the swing menu. The drawback is that this menu is native, and sometimes
     * native menus looks absolutely HORRID.
     * <p>
     * This always returns the same instance per JVM (it's a singleton), and on some platforms the system tray may not be
     * supported, in which case this will return NULL.
     * <p>
     * If this is using the Swing SystemTray and a SecurityManager is installed, the AWTPermission {@code accessSystemTray} must
     * be granted in order to get the {@code SystemTray} instance. Otherwise, this will return null.
     *
     * @param trayName This is the name assigned to the system tray instance. If you create MORE than 1 system tray,
     *                  you must make sure to use different names (or un-predicable things can happen!).
     */
    @SuppressWarnings({"ConstantConditions", "StatementWithEmptyBody"})
    public static synchronized
    SystemTray get(String trayName) {
        // we must recreate the menu if we call get() after remove()!

//        if (DEBUG) {
//            Properties properties = System.getProperties();
//            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
//                logger.debug(entry.getKey() + " : " + entry.getValue());
//            }
//        }

        // no tray in a headless environment
        if (GraphicsEnvironment.isHeadless()) {
            logger.error("Cannot use the SystemTray in a headless environment");

            return null;
        }

        // if we have a render provider, we must make sure that it is supported
        if (!RenderProvider.isSupported()) {
            // versions of SWT older than v4.4, are INCOMPATIBLE with us.
            // Of note, v4.3 is the "last released" version of SWT by eclipse AND IT WILL NOT WORK!!
            // for NEWER versions of SWT via maven, use http://maven-eclipse.github.io/maven
            if (RenderProvider.isSwt()) {
                logger.error("Unable to use currently loaded version of SWT, it is TOO OLD. Please use version 4.4+");
            }

            return null;
        }

        // if we already have a system tray by this name, return it (do not allow duplicate tray names)
        SystemTray existingTray = AutoDetectTrayType.getInstance(trayName);
        if (existingTray != null) {
            if (DEBUG) {
                logger.info("Returning existing tray: " + trayName);
            }
            return existingTray;
        }



        boolean isNix = OS.INSTANCE.isLinux() || OS.INSTANCE.isUnix();
        boolean isWindows = OS.INSTANCE.isWindows();
        boolean isMacOsX = OS.INSTANCE.isMacOsX();


        // Windows can ONLY use Swing (non-native) or WindowsNotifyIcon (native) - AWT looks absolutely horrid and is not an option
        // OSx can use Swing (non-native) or AWT (native).
        // Linux can use Swing (non-native), AWT (native), GtkStatusIcon (native), or AppIndicator (native)
        if (isWindows) {
            if (FORCE_TRAY_TYPE != TrayType.AutoDetect && FORCE_TRAY_TYPE != TrayType.Swing && FORCE_TRAY_TYPE != TrayType.WindowsNative) {
                logger.warn("Windows cannot use the '" + FORCE_TRAY_TYPE + "' SystemTray type on windows, auto-detecting implementation!");

                // windows MUST use swing/windows-notify-icon only!
                FORCE_TRAY_TYPE = TrayType.AutoDetect;
            }
        }
        else if (isMacOsX) {
            if (RenderProvider.isSwt() && FORCE_TRAY_TYPE == TrayType.Swing) {
                // cannot mix Swing and SWT on MacOSX (for all versions of java) so we force ATW menus instead, which work just fine with SWT
                // http://mail.openjdk.java.net/pipermail/bsd-port-dev/2008-December/000173.html
                if (AUTO_FIX_INCONSISTENCIES) {
                    logger.warn("Unable to load Swing + SWT (for all versions of Java). Using the AWT Tray type instead.");

                    FORCE_TRAY_TYPE = TrayType.Awt;
                }
                else {
                    logger.error("Unable to load Swing + SWT (for all versions of Java). " +
                                 "Please set `SystemTray.AUTO_FIX_INCONSISTENCIES=true;` to automatically fix this problem.\"");

                    return null;
                }
            }

            if (FORCE_TRAY_TYPE != TrayType.AutoDetect && FORCE_TRAY_TYPE != TrayType.Swing && FORCE_TRAY_TYPE != TrayType.Awt) {
                // MacOsX can only use swing and AWT
                FORCE_TRAY_TYPE = TrayType.AutoDetect;

                logger.warn("MacOS cannot use the '" + FORCE_TRAY_TYPE + "' SystemTray type, defaulting to the AWT Tray type instead.");
            }
        }
        else if (isNix) {
            // linux/unix can use all the tray types. AWT looks horrid. GTK versions are really sensitive...

            // this checks to see if Swing/SWT/JavaFX has loaded GTK yet, and if so, what version they loaded.
            // if swing is used, we have to do some extra checks...
            int loadedGtkVersion = GtkCheck.getLoadedGtkVersion();
            if (loadedGtkVersion == 2) {
                if (AUTO_FIX_INCONSISTENCIES) {
                    if (!FORCE_GTK2) {
                        if (RenderProvider.isJavaFX()) {
                            // JavaFX Java7,8 is GTK2 only. Java9 can MAYBE have it be GTK3 if `-Djdk.gtk.version=3` is specified
                            // see
                            // http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
                            // https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
                            // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.

                            if (OS.INSTANCE.getJavaVersion() < 11) {
                                // we must use GTK2, because JavaFX is GTK2 (java9 had javafx, but java11 DOES NOT)
                                FORCE_GTK2 = true;
                                if (DEBUG) {
                                    logger.debug("Forcing GTK2 because JavaFX is GTK2");
                                }
                            } else {
                                // java 11+
                                // https://github.com/javafxports/openjdk-jfx/issues/217
                                // uses GTK3 by default now. so UNLESS we set the javafx parameter to force GTK2, we don't.
                                String gtkVer = System.getProperty("jdk.gtk.version", "0");
                                if (gtkVer.startsWith("2")) {
                                    FORCE_GTK2 = true;
                                    if (DEBUG) {
                                        logger.debug("Forcing GTK2 because JavaFX System property `jdk.gtk.version` was set to 2 (so we force GTK2)");
                                    }
                                }
                            }
                        }
                        else if (RenderProvider.isSwt() && RenderProvider.getGtkVersion() != 3) {
                            // Necessary for us to work with SWT based on version info. We can try to set us to be compatible with whatever it is set to
                            // System.setProperty("SWT_GTK3", "0"); // this doesn't have any affect on newer versions of SWT

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

                        return null;
                    }
                }
            }
            else if (loadedGtkVersion == 3) {
                if (AUTO_FIX_INCONSISTENCIES) {
                    if (RenderProvider.isJavaFX()) {
                        // JavaFX Java7,8 is GTK2 only. Java9 can MAYBE have it be GTK3 if `-Djdk.gtk.version=3` is specified
                        // see
                        // http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
                        // https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
                        // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.

                        if (FORCE_GTK2) {
                            // if we are java9, then we can change it -- otherwise we cannot.
                            if (OS.INSTANCE.getJavaVersion() >= 9) {
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
                    else if (RenderProvider.isSwt()) {
                        if (FORCE_GTK2) {
                            FORCE_GTK2 = false;
                            logger.warn("Unable to use the SystemTray when SWT is configured to use GTK3 and the SystemTray is configured to use " +
                                        "GTK2. Please set `SystemTray.FORCE_GTK2=false;`");
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

                    if (RenderProvider.isJavaFX()) {
                        // JavaFX Java7,8 is GTK2 only. Java9 can MAYBE have it be GTK3 if `-Djdk.gtk.version=3` is specified
                        // see
                        // http://mail.openjdk.java.net/pipermail/openjfx-dev/2016-May/019100.html
                        // https://docs.oracle.com/javafx/2/system_requirements_2-2-3/jfxpub-system_requirements_2-2-3.htm
                        // from the page: JavaFX 2.2.3 for Linux requires gtk2 2.18+.

                        if (FORCE_GTK2) {
                            // if we are java9, then we can change it -- otherwise we cannot.
                            if (OS.INSTANCE.getJavaVersion() >= 9) {
                                logger.error("Unable to use the SystemTray when JavaFX is configured to use GTK3 and the SystemTray is " +
                                             "configured to use GTK2. Please configure JavaFX to use GTK2 (via `System.setProperty(\"jdk.gtk.version\", \"3\");`) " +
                                             "before JavaFX is initialized, or set `SystemTray.FORCE_GTK2=false;`  Aborting.");

                            }
                            else {
                                logger.error("Unable to use the SystemTray when JavaFX is configured to use GTK3 and the SystemTray is configured to use " +
                                             "GTK2. Please set `SystemTray.FORCE_GTK2=false;`  Aborting.");
                            }

                            return null;
                        }
                    }
                    else if (RenderProvider.isSwt()) {
                        // Necessary for us to work with SWT based on version info. We can try to set us to be compatible with whatever it is set to
                        if (FORCE_GTK2) {
                            logger.error("Unable to use the SystemTray when SWT is configured to use GTK3 and the SystemTray is configured to use " +
                                         "GTK2. Please set `SystemTray.FORCE_GTK2=false;`");

                            return null;
                        }
                    }

                    else if (FORCE_GTK2) {
                        logger.error("Unable to use the SystemTray when Swing is configured to use GTK3 and the SystemTray is " +
                                     "configured to use GTK2. Aborting.");

                        return null;
                    }
                }
            }
            else {
                // we don't know what was loaded.
                // this is only a big deal for us if we are DIFFERENT than what SWING is using. Since swing isn't always used
                // (ie: headless/javaFX can also be used), we ** DO NOT ** want to accidentally load swing if we don't have to

                if (RenderProvider.isDefault()) {
                    // we have to make sure that SWING/GTK stuff is GTK2!
                    // THIS IS NOT DOCUMENTED ANYWHERE...
                    // NOTE: Refer to bug 4912613 for details regarding support for GTK 2.0/2.2

                    // only do this is values have not already been set!
                    String previousValue = System.getProperty("swing.gtk.version", "0");
                    if (previousValue != null && previousValue.startsWith("3")) {
                        if (FORCE_GTK2) {
                            // whoops! there is something setting GTK to version 3! abort with an error message.
                            logger.error("Unable to use the SystemTray when there is a mismatch for GTK loaded preferences. Please correctly " +
                                         "set `SystemTray.FORCE_GTK2=true` and System property `swing.gtk.version=\"2.2\".  Aborting...");
                            return null;
                        }
                    }

                    // now check another setting
                    previousValue = System.getProperty("jdk.gtk.version", "0");
                    if (previousValue != null && previousValue.startsWith("3")) {
                        if (FORCE_GTK2) {
                            // whoops! there is something setting GTK to version 3! abort with an error message.
                            logger.error("Unable to use the SystemTray when there is a mismatch for GTK loaded preferences. Please correctly " +
                                         "set `SystemTray.FORCE_GTK2=true` and System property `jdk.gtk.version=\"2.2\".  Aborting...");
                            return null;
                        }
                    }

                    if ("0".equals(previousValue) && AUTO_FIX_INCONSISTENCIES) {
                        // this means nothing was set!

                        if (PREFER_GTK3) {
                            System.setProperty("swing.gtk.version", "3");
                            System.setProperty("jdk.gtk.version", "3");
                        } else {
                            System.setProperty("swing.gtk.version", "2");
                            System.setProperty("jdk.gtk.version", "2");
                        }
                    }
                }
            }
        }



        if (DEBUG) {
            logger.debug("Version {}", getVersion());
            logger.debug("OS: {}", System.getProperty("os.name"));
            logger.debug("Arch: {}", System.getProperty("os.arch"));

            String jvmName = System.getProperty("java.vm.name", "");
            String jvmVersion = System.getProperty("java.version", "");
            String jvmVendor = System.getProperty("java.vm.specification.vendor", "");
            logger.debug("{} {} {}", jvmVendor, jvmName, jvmVersion);
            logger.debug("JPMS enabled: {}", OS.INSTANCE.getUsesJpms());


            logger.debug("Is Auto sizing tray/menu? {}", AUTO_SIZE);
            logger.debug("Is JavaFX detected? {}", RenderProvider.isJavaFX());
            logger.debug("Is SWT detected? {}", RenderProvider.isSwt());

            logger.debug("Java Swing L&F: {}", UIManager.getLookAndFeel().getID());
            if (FORCE_TRAY_TYPE == TrayType.AutoDetect) {
                logger.debug("Auto-detecting tray type");
            }
            else {
                logger.debug("Forced tray type: {}", FORCE_TRAY_TYPE.name());
            }

            if (OS.INSTANCE.isLinux()) {
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
            trayType = AutoDetectTrayType.get(trayName);
        } else {
            trayType = selectType(SystemTray.FORCE_TRAY_TYPE);
        }

        if (trayType == null) {
            if (OS.DesktopEnv.INSTANCE.isChromeOS()) {
                logger.error("ChromeOS detected and it is not supported. Aborting.");
            }

            return null;
        }



        // fix various incompatibilities with selected tray types
        if (isNix) {
            // Ubuntu UNITY has issues with GtkStatusIcon (it won't work at all...)
            if (isTrayType(trayType, TrayType.Gtk)) {
                OS.DesktopEnv.Env de = OS.DesktopEnv.INSTANCE.getEnv();

                if (OS.Linux.INSTANCE.isUbuntu() && OS.DesktopEnv.INSTANCE.isUnity(de)) {
                    if (AUTO_FIX_INCONSISTENCIES) {
                        // GTK2 does not support AppIndicators!
                        if (Gtk.isGtk2) {
                            trayType = selectType(TrayType.Swing);
                            logger.warn("Forcing Swing Tray type because Ubuntu Unity display environment removed support for GtkStatusIcons " +
                                        "and GTK2+ was specified.");
                        }
                        else {
                            // we must use AppIndicator because Ubuntu Unity removed GtkStatusIcon support
                            SystemTray.FORCE_TRAY_TYPE = TrayType.AppIndicator; // this is required because of checks inside of AppIndicator...
                            trayType = selectType(TrayType.AppIndicator);

                            logger.warn("Forcing AppIndicator because Ubuntu Unity display environment removed support for GtkStatusIcons.");
                        }
                    }
                    else {
                        logger.error("Unable to use the GtkStatusIcons when running on Ubuntu with the Unity display environment, and thus" +
                                     " the SystemTray will not work. " +
                                     "Please set `SystemTray.AUTO_FIX_INCONSISTENCIES=true;` to automatically fix this problem.");

                        return null;
                    }
                }

                if (de == OS.DesktopEnv.Env.Gnome) {
                    boolean hasWeirdOsProblems = OS.Linux.INSTANCE.isKali() || (OS.Linux.INSTANCE.isFedora());
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
                if (SystemTray.ENABLE_ROOT_CHECK && OS.Linux.INSTANCE.isRoot()) {
                    // if are we running as ROOT, there can be issues (definitely on Ubuntu 16.04, maybe others)!
                    if (AUTO_FIX_INCONSISTENCIES) {
                        trayType = selectType(TrayType.Swing);

                        logger.warn("Attempting to load the SystemTray as the 'root/sudo' user. This will likely not work because of dbus " +
                                    "restrictions. Using the Swing Tray type instead. Please refer to the readme notes or issue #63 on " +
                                    "how to work around this.");

                    } else {
                        logger.error("Attempting to load the SystemTray as the 'root/sudo' user. This will likely NOT WORK because of dbus " +
                                     "restrictions. Please refer to the readme notes or issue #63 on how to work around this.");
                    }
                }


                if (OS.Linux.INSTANCE.isElementaryOS() && OS.Linux.INSTANCE.getElementaryOSVersion()[0] >= 5) {
                    // in version 5.0+, they REMOVED support for appindicators. You can add it back via some extra work.
                    // see: https://git.dorkbox.com/dorkbox/elementary-indicators

                    // or you can download
                    // https://launchpad.net/~elementary-os/+archive/ubuntu/stable/+files/wingpanel-indicator-ayatana_2.0.3+r27+pkg17~ubuntu0.4.1.1_amd64.deb
                    // then dpkg -i filename

                    // check if this library is installed.
                    if (!new File("/usr/share/doc/wingpanel-indicator-ayatana").isDirectory()) {
                        logger.error("Unable to use the SystemTray as-is with this version of ElementaryOS. By default, tray icons *are not* supported, but a" +
                                     " workaround has been developed. Please see: https://git.dorkbox.com/dorkbox/elementary-indicators");

                        return null;
                    }
                }
            }
        }


        if (trayType == null) {
            // unsupported tray, or unknown type
            trayType = selectType(TrayType.Swing);

            logger.error("SystemTray initialization failed. (Unable to discover which implementation to use). Falling back to the Swing Tray.");
        }


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
                    trayType = selectType(TrayType.Swing);

                    logger.error("Unable to initialize GTK! Something is severely wrong! Using the Swing Tray type instead.");
                }

                // this will to load the app-indicator library
                else if (isTrayType(trayType, TrayType.AppIndicator)) {
                    if (!AppIndicator.isLoaded) {
                        // YIKES. AppIndicator couldn't load.

                        String installer = AppIndicator.getInstallString(GtkCheck.isGtk2);
                        String packageInstall = OS.Linux.PackageManager.INSTANCE.getType().getInstallString() + " " + installer;
                        String installString = "Please install " + installer + ", for example: '" + packageInstall + "'.";


                        // can we fallback to swing? KDE does not work for this...
                        if (AUTO_FIX_INCONSISTENCIES && java.awt.SystemTray.isSupported() && !OS.DesktopEnv.INSTANCE.isKDE()) {
                            trayType = selectType(TrayType.Swing);

                            logger.warn("Unable to initialize the AppIndicator correctly. Using the Swing Tray type instead.");
                            logger.warn(installString);
                        }
                        else {
                            // no swing, have to emit instructions how to fix the error.
                            logger.error("AppIndicator unable to load.  " + installString);

                            return null;
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
                         (isTrayType(trayType, TrayType.Swing) || isTrayType(trayType, TrayType.WindowsNative))) {
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

            if (!RenderProvider.isDefault() && SwingUtilities.isEventDispatchThread()) {
                // This WILL NOT WORK. Let the dev know
                logger.error("SystemTray initialization for JavaFX or SWT **CAN NOT** occur on the Swing Event Dispatch Thread " +
                             "(EDT). Something is seriously wrong.");

                return null;
            }

            if (isTrayType(trayType, TrayType.Swing) ||
                isTrayType(trayType, TrayType.Awt) ||
                isTrayType(trayType, TrayType.Osx) ||
                isTrayType(trayType, TrayType.WindowsNative)) {

                // ensure AWT toolkit is initialized.
                // OSX is based off of AWT now, instead of creating our own dispatch
                java.awt.Toolkit.getDefaultToolkit();
            }


            if (AUTO_FIX_INCONSISTENCIES) {
                // this logic has to be before we create the system Tray, but after AWT/GTK is started (if applicable)
                if (isWindows && isTrayType(trayType, TrayType.Swing)) {
                    // we don't permit AWT for windows (it looks absolutely HORRID)

                    // Our default for windows is now a native tray icon (instead of the swing tray icon), but we preserve the use of Swing
                    // windows hard-codes the image size for AWT/SWING tray types
                    SystemTrayFixes.fixWindows(trayImageSize);
                }
                else if (isMacOsX && (isTrayType(trayType, TrayType.Awt) ||
                                      isTrayType(trayType, TrayType.Osx))) {
                    // Swing on macOS is pretty bland. AWT (with fixes) looks fantastic (and is native)

                    // AWT on macosx doesn't respond to all buttons (but should)
                    SystemTrayFixes.fixMacOS();
                }
                else if (isNix && isTrayType(trayType, TrayType.Swing)) {
                    // linux/mac doesn't have transparent backgrounds for swing and hard-codes the image size
                    SystemTrayFixes.fixLinux(trayImageSize);
                }
            }






            // initialize the tray icon height
            // this is during init, so we can statically access this everywhere else. Multiple instances of this will always have the same value
            SizeAndScalingUtil.getMenuImageSize(trayType);


            //  Permits us to take action when the menu is "removed" from the system tray, so we can correctly add it back later.
            Runnable onRemoveEvent = ()->{
                // must remove ourselves from the init() map (since we want to be able to access things)
                AutoDetectTrayType.removeSystemTrayHook(trayName);

                // this is thread-safe
                if (!AutoDetectTrayType.hasOtherTrays()) {
                    EventDispatch.shutdown();
                }
            };

            // the cache name **MUST** be combined with the currently logged-in user, otherwise permissions get screwed up
            // when there is more than 1 user logged in at the same time!
            CacheUtil cache = new CacheUtil(trayName + "Cache" + "_" + System.getProperty("user.name"));
            ImageResizeUtil imageResizeUtil = new ImageResizeUtil(cache);


            // the "menu" in this case is the ACTUAL menu that shows up in the system tray (the icon + submenu, etc)
            final AtomicReference<Tray> reference = new AtomicReference<>();

            // javaFX and SWT **CAN NOT** start on the EDT!!
            // linux + GTK/AppIndicator + windows-native menus must not start on the EDT!
            // AWT + Swing + AWT-macOS must be constructed on the EDT however...
            if (RenderProvider.isDefault() &&
                (isTrayType(trayType, TrayType.Swing) || isTrayType(trayType, TrayType.Awt) || isTrayType(trayType, TrayType.Osx))) {
                // have to construct swing stuff inside the swing EDT
                final Class<? extends Menu> finalTrayType = trayType;
                SwingUtil.invokeAndWait(()->{
                    try {
                        reference.set((Tray) finalTrayType.getConstructors()[0].newInstance(trayName, imageResizeUtil, onRemoveEvent));
                    } catch (Exception e) {
                        logger.error("Unable to create tray type: '{}'", finalTrayType.getSimpleName(), e);
                    }
                });
            }
            else {
                reference.set((Tray) trayType.getConstructors()[0].newInstance(trayName, imageResizeUtil, onRemoveEvent));
            }

            // we have a weird circle dependency thing going on!
            Tray systemTrayMenu = reference.get();

            if (systemTrayMenu == null) {
                logger.error("Unable to create tray type: '{}'", trayType.getSimpleName());
                return null;
            }

            if (DEBUG) {
                logger.info("Successfully loaded type: {}", trayType.getSimpleName());
            } else {
                logger.info("Successfully loaded");
            }

            SystemTray systemTray = new SystemTray(trayName, systemTrayMenu, imageResizeUtil);
            AutoDetectTrayType.setInstance(trayName, systemTray);

            // we ALWAYS want to add a **JVM** shutdown hook!
            Runnable shutdownRunnable = AutoDetectTrayType.getShutdownHook(trayName);
            Runtime.getRuntime().addShutdownHook(new Thread(shutdownRunnable));

            return systemTray;
        } catch (Exception e) {
            logger.error("Unable to create tray type: '{}'", trayType.getSimpleName(), e);
        }

        return null;
    }

    /** Default name of the application, sometimes shows on tray-icon mouse over. Not used for all OSes, but mostly for Linux */
    private final String trayName;
    private final Tray menu;
    private final ImageResizeUtil imageResizeUtil;

    private
    SystemTray(final String trayName, final Tray systemTrayMenu, final ImageResizeUtil imageResizeUtil) {
        this.trayName = trayName;
        this.menu = systemTrayMenu;
        this.imageResizeUtil = imageResizeUtil;
    }

    /**
     * Shuts-down the SystemTray, by removing the menus + tray icon. After calling this method, you MUST call `get()` or `get(name)`
     * again to obtain a new SystemTray instance.
     */
    public
    void shutdown() {
        // this will shut down and do what it needs to. The onRemoveEvent cleans up.
        menu.remove();
    }

    /**
     * Shuts-down the SystemTray, by removing the menus + tray icon. After calling this method, you MUST call `get()` or `get(name)`
     * again to obtain a new SystemTray instance.
     *
     * @param runOnShutdown the action to run after the system tray has finished shutting down
     */
    public
    void shutdown(Runnable runOnShutdown) {
        shutdown();

        // wait for the system tray Event dispatch to finish running, then run our shutdown logic. This must happen on a different thread
        EventDispatch.runLater(new Runnable() {
            @Override
            public
            void run() {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public
                    void run() {
                        EventDispatch.waitForShutdown();
                        runOnShutdown.run();
                    }
                });
                thread.setName("Shutdown");
                thread.setDaemon(true);
                thread.run();
            }
        });
    }

    /**
     * Gets the 'status' string assigned to the system tray
     */
    public
    String getStatus() {
        return menu.getStatus();
    }

    /**
     * Sets a 'status' string at the first position in the popup menu. This 'status' string appears as a disabled menu entry.
     *
     * @param statusText the text you want displayed, null if you want to remove the 'status' string
     */
    public
    Menu setStatus(String statusText) {
        menu.setStatus(statusText);
        return menu;
    }

    /**
     * @return the attached menu to this system tray
     */
    public
    Menu getMenu() {
        return menu;
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
        Icon icon = jMenu.getIcon();
        if (icon != null) {
            BufferedImage bimage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            setImage(bimage);
        }

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

        return menu;
    }


    /**
     * Shows (if hidden), or hides (if showing) the system tray.
     */
    public
    Menu setEnabled(final boolean enabled) {
        menu.setEnabled(enabled);
        return menu;
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
    Menu setTooltip(final String tooltipText) {
        menu.setTooltip(tooltipText);
        return menu;
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

        menu.setImageFromTray(imageResizeUtil.shouldResizeOrCache(true, imageFile));
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imagePath the full path of the image to use or null
     */
    public
    Menu setImage(final String imagePath) {
        if (imagePath == null) {
            throw new NullPointerException("imagePath");
        }

        menu.setImageFromTray(imageResizeUtil.shouldResizeOrCache(true, imagePath));
        return menu;
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imageUrl the URL of the image to use or null
     */
    public
    Menu setImage(final URL imageUrl) {
        if (imageUrl == null) {
            throw new NullPointerException("imageUrl");
        }

        menu.setImageFromTray(imageResizeUtil.shouldResizeOrCache(true, imageUrl));
        return menu;
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param imageStream the InputStream of the image to use
     */
    public
    Menu setImage(final InputStream imageStream) {
        if (imageStream == null) {
            throw new NullPointerException("imageStream");
        }

        menu.setImageFromTray(imageResizeUtil.shouldResizeOrCache(true, imageStream));
        return menu;
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     * @param image the image of the image to use
     */
    public
    Menu setImage(final Image image) {
        if (image == null) {
            throw new NullPointerException("image");
        }

        menu.setImageFromTray(imageResizeUtil.shouldResizeOrCache(true, image));
        return menu;
    }

    /**
     * Specifies the new image to set for the tray icon.
     * <p>
     * If AUTO_SIZE, then this method resize the image (best guess), otherwise the image "as-is" will be used
     *
     *@param imageStream the ImageInputStream of the image to use
     */
    public
    Menu setImage(final ImageInputStream imageStream) {
        if (imageStream == null) {
            throw new NullPointerException("image");
        }

        menu.setImageFromTray(imageResizeUtil.shouldResizeOrCache(true, imageStream));
        return menu;
    }

    /**
     * @return the system tray image size, accounting for OS and theme differences
     */
    public
    int getTrayImageSize() {
        return SizeAndScalingUtil.getTrayImageSize();
    }


    /**
     * This is called during tray initialization. Since multiple tray menus will always be the same type, this is can be cached
     *
     * @return the system tray menu image size, accounting for OS and theme differences.
     */
    public
    int getMenuImageSize() {
        return SizeAndScalingUtil.TRAY_MENU_SIZE;
    }

    /**
     * @return the tray type used to create the system tray
     */
    public
    TrayType getType() {
        return fromClass(menu.getClass());
    }

    /**
     * This removes all menu entries from the tray icon menu AND removes the tray icon from the system tray!
     * <p>
     * You will need to recreate ALL parts of the menu to see the tray icon + menu again!
     */
    public
    void remove() {
        // we must recreate the menu via init() if we call get() after remove()! (onRemoveEvent does this)
        menu.remove();
    }
}
