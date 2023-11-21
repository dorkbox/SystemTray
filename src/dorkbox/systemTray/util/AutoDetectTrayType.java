/*
 * Copyright 2023 dorkbox, llc
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
package dorkbox.systemTray.util;

import static dorkbox.systemTray.SystemTray.DEBUG;
import static dorkbox.systemTray.SystemTray.logger;

import java.io.BufferedReader;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import dorkbox.os.OS;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTray.TrayType;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.gnomeShell.AppIndicatorExtension;
import dorkbox.systemTray.gnomeShell.DummyFile;
import dorkbox.systemTray.gnomeShell.LegacyExtension;
import dorkbox.systemTray.ui.awt._AwtTray;
import dorkbox.systemTray.ui.gtk._AppIndicatorNativeTray;
import dorkbox.systemTray.ui.gtk._GtkStatusIconNativeTray;
import dorkbox.systemTray.ui.osx._OsxAwtTray;
import dorkbox.systemTray.ui.swing._SwingTray;
import dorkbox.systemTray.ui.swing._WindowsNativeTray;
import dorkbox.util.FileUtil;

/**
 * Auto-Detection of system tray type, with basic conversion utilities
 */
public
class AutoDetectTrayType {
    // we want to have "singleton" access for a specified SystemTray NAME, so that (should one want) different parts of an application
    // can add entries to the menu without having to pass the SystemTray object around
    private static final Map<String, SystemTray> traySingletons = new HashMap<>();


    public static
    Class<? extends Tray> selectType(final TrayType trayType) {
        if (trayType == TrayType.Gtk) {
            return _GtkStatusIconNativeTray.class;
        }
        else if (trayType == TrayType.AppIndicator) {
            return _AppIndicatorNativeTray.class;
        }
        else if (trayType == TrayType.WindowsNative) {
            return _WindowsNativeTray.class;
        }
        else if (trayType == TrayType.Swing) {
            return _SwingTray.class;
        }
        else if (trayType == TrayType.Osx) {
            return _OsxAwtTray.class;
        }
        else if (trayType == TrayType.Awt) {
            return _AwtTray.class;
        }

        return null;
    }

    public static
    TrayType fromClass(final Class<? extends Tray> trayClass) {
        if (trayClass == _GtkStatusIconNativeTray.class) {
            return TrayType.Gtk;
        }
        else if (trayClass == _AppIndicatorNativeTray.class) {
            return TrayType.AppIndicator;
        }
        else if (trayClass == _WindowsNativeTray.class) {
            return TrayType.WindowsNative;
        }
        else if (trayClass == _SwingTray.class) {
            return TrayType.Swing;
        }
        else if (trayClass == _OsxAwtTray.class) {
            return TrayType.Osx;
        }
        else if (trayClass == _AwtTray.class) {
            return TrayType.Awt;
        }

        return null;
    }

    /**
     * @return what the default "autodetect" tray type should be
     */
    @SuppressWarnings("DuplicateBranchesInSwitch")
    public static
    TrayType get(final String trayName) {
        if (OS.INSTANCE.isWindows()) {
            return TrayType.WindowsNative;
        }
        else if (OS.INSTANCE.isMacOsX()) {
            // macOS can ONLY use AWT if you want it to follow the L&F of the OS. It is the default.
            return TrayType.Osx;
        }
        else if ((OS.INSTANCE.isLinux() || OS.INSTANCE.isUnix())) {
            // see: https://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running

            // For funsies, SyncThing did a LOT of work on compatibility (unfortunate for us) in python.
            // https://github.com/syncthing/syncthing-gtk/blob/b7a3bc00e3bb6d62365ae62b5395370f3dcc7f55/syncthing_gtk/statusicon.py

            // quick check, because we know that unity uses app-indicator. Maybe REALLY old versions do not. We support 14.04 LTE at least
            OS.DesktopEnv.Env de = OS.DesktopEnv.INSTANCE.getEnv();

            if (DEBUG) {
                logger.debug("Currently using the '{}' desktop environment" + OS.INSTANCE.getLINE_SEPARATOR() + OS.Linux.INSTANCE.getInfo(), de);
            }

            switch (de) {
                case Gnome: {
                    // check other DE / OS combos that are based on gnome
                    String GDM = System.getenv("GDMSESSION");

                    // fix for some linux OS where this session variable is not set
                    if (GDM == null) {
                        if (DEBUG) {
                            logger.debug("GDMSESSION value is not set by OS. Checking '/etc/os-release' for more info.");
                        }

                        // see: https://github.com/dorkbox/SystemTray/issues/125
                        if (OS.Linux.INSTANCE.isPop()) {
                            GDM = "ubuntu";   // special case for popOS! (it is ubuntu-like, but does not set GDMSESSION)

                            if (DEBUG) {
                                logger.debug("Detected popOS! Using 'ubuntu' for that configuration.");
                            }
                        }
                    }

                    if (DEBUG) {
                        logger.debug("Currently using the '{}' session type", GDM);
                    }

                    if ("gnome".equalsIgnoreCase(GDM) || "default".equalsIgnoreCase(GDM)) {
                        // UGH. At least ubuntu un-butchers gnome.
                        if (OS.Linux.INSTANCE.isUbuntu()) {
                            // so far, because of the interaction between gnome3 + ubuntu, the GtkStatusIcon miraculously works.
                            return TrayType.Gtk;
                        }

                        // "default" can be gnome3 on debian/kali


                        // for everyone else, we have to check the gnome version.
                        // gnome2 -> everything is glorious and just works.
                        // gnome3 -> someone started sniffing glue.
                        //   < 3.16  - It's in the notification tray. SystemTray works, but will only show via SUPER+M.
                        //   < 3.26  - (3.16 introduced the legacy tray, and removed gtkstatus icon "normal" placement) legacy icons via shell extensions work + GTK workarounds
                        //   >= 3.26 - (3.26 removed the legacy tray) app-indicator icons via shell extensions + libappindicator work


                        String gnomeVersion = OS.DesktopEnv.INSTANCE.getGnomeVersion();
                        if (gnomeVersion == null) {
                            // this shouldn't ever happen!

                            logger.error("GNOME shell detected, but UNDEFINED shell version. This should never happen. Falling back to GtkStatusIcon. " +
                                         "Please create an issue with as many details as possible.");

                            return TrayType.Gtk;
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
                            return TrayType.Gtk;
                        }
                        else if (major == 3) {
                            if (minorAndPatch < 16.0D) {
                                logger.warn("SystemTray works, but will only show via SUPER+M.");
                                return TrayType.Gtk;
                            }
                            else if (minorAndPatch < 26.0D) {
                                Tray.gtkGnomeWorkaround = true;

                                LegacyExtension.install(trayName);

                                // now, what VERSION of fedora? "normal" fedora doesn't have AppIndicator installed, so we have to use GtkStatusIcon
                                // 23 is gtk, 24/25/26 is gtk (but also wrong size unless we adjust it. ImageUtil automatically does this)
                                return TrayType.Gtk;
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

                                return TrayType.AppIndicator;
                            }
                        }
                        else {
                            logger.error("GNOME shell detected, but UNSUPPORTED shell version (" + gnomeVersion + "). Falling back to GtkStatusIcon. " +
                                         "Please create an issue with as many details as possible.");

                            return TrayType.Gtk;
                        }
                    }
                    else if ("cinnamon".equalsIgnoreCase(GDM)) {
                        return TrayType.Gtk;
                    }
                    else if ("gnome-classic".equalsIgnoreCase(GDM)) {
                        return TrayType.Gtk;
                    }
                    else if ("gnome-fallback".equalsIgnoreCase(GDM)) {
                        return TrayType.Gtk;
                    }
                    else if ("awesome".equalsIgnoreCase(GDM)) {
                        return TrayType.Gtk;
                    }
                    else if ("ubuntu".equalsIgnoreCase(GDM)) {
                        // NOTE: popOS can also get here. It will also version check (since it's ubuntu-like)
                        int[] version = OS.Linux.INSTANCE.getUbuntuVersion();

                        // ubuntu 17.10+ uses the NEW gnome DE, which screws up previous Ubuntu workarounds, since it's now mostly Gnome
                        if (version[0] == 17 && version[1] == 10) {
                            // this is gnome 3.26.1, so we install the Gnome extension
                            Tray.gtkGnomeWorkaround = true;
                            LegacyExtension.install(trayName);
                        }
                        else if (version[0] >= 18) {
                            // ubuntu 18.04 doesn't need the extension BUT does need a logout-login (or gnome-shell restart) for it to work

                            // we copy over a config file so we know if we have already restarted the shell or shown the warning. A logout-login will also work.
                            DummyFile.install();
                        }

                        return TrayType.AppIndicator;
                    }

                    logger.error("GNOME shell detected, but UNKNOWN type. This should never happen. Falling back to GtkStatusIcon. " +
                                 "Please create an issue with as many details as possible.");

                    return TrayType.Gtk;
                }
                case KDE: {
                    // kde 5.8+ is "high DPI", so we need to adjust the scale. Image resize will do that

                    String plasmaVersion = OS.DesktopEnv.INSTANCE.getPlasmaVersionFull();

                    if (DEBUG) {
                        logger.debug("KDE Plasma Version: {}", plasmaVersion);
                    }

                    if (plasmaVersion == null) {
                        // this shouldn't ever happen!

                        logger.error("KDE Plasma detected, but UNDEFINED shell version. This should never happen. Falling back to GtkStatusIcon. " +
                                     "Please create an issue with as many details as possible.");

                        return TrayType.Gtk;
                    }

                    String[] versionParts = plasmaVersion.split("\\.");
                    int majorVersion = Integer.parseInt(versionParts[0]);
                    int minorVersion = Integer.parseInt(versionParts[1]);
                    if (majorVersion < 5 || (majorVersion == 5 && minorVersion < 5)) {
                        // older versions use GtkStatusIcon
                        return TrayType.Gtk;
                    } else {
                        // newer versions use appindicator, but the user MIGHT have to install libappindicator
                        return TrayType.AppIndicator;
                    }
                }
                case Unity: {
                    // Ubuntu Unity is a weird combination. It's "Gnome", but it's not "Gnome Shell".
                    return TrayType.AppIndicator;
                }
                case Unity7: {
                    // Ubuntu Unity7 (17.04, which has MIR) is a weird combination. It's "Gnome", but it's not "Gnome Shell".
                    return TrayType.AppIndicator;
                }
                case XFCE: {
                    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
                    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
                    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25

                    // so far, it is OK to use GtkStatusIcon on XFCE <-> XFCE4 inclusive
                    return TrayType.Gtk;
                }
                case LXDE: {
                    return TrayType.Gtk;
                }
                case MATE: {
                    return TrayType.Gtk;
                }
                case Pantheon: {
                    // elementaryOS. It only supports appindicator (not gtkstatusicon)
                    // http://bazaar.launchpad.net/~wingpanel-devs/wingpanel/trunk/view/head:/sample/SampleIndicator.vala

                    // in version 5.0+, they REMOVED support for appindicators. You can add it back via
                    // see: https://git.dorkbox.com/dorkbox/elementary-indicators

                    // ElementaryOS shows the checkbox on the right, everyone else is on the left.
                    // With eOS, we CANNOT show the spacer image. It does not work.
                    return TrayType.AppIndicator;
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

                        String line = FileUtil.INSTANCE.readFirstLine(status);
                        if (line != null && line.contains("indicator-app")) {
                            // make sure we can also load the library (it might be the wrong version)
                            return TrayType.AppIndicator;
                        }
                    }
                }
            } catch (Throwable e) {
                if (DEBUG) {
                    logger.error("Error detecting appindicator status", e);
                }
            }


            if (OS.INSTANCE.isLinux()) {
                // now just blanket query what we are to guess...
                if (OS.Linux.INSTANCE.isUbuntu()) {
                    return TrayType.AppIndicator;
                }
                else if (OS.Linux.INSTANCE.isFedora()) {
                    // newer version of fedora are GTK only
                    return TrayType.Gtk;
                } else {
                    // AppIndicators are now the "default" for most linux distro's.
                    return TrayType.AppIndicator;
                }
            }
        }

        throw new RuntimeException("This OS is not supported. Please create an issue with the details from `SystemTray.DEBUG=true;`");
    }


    public static
    Runnable getShutdownHook(final String trayName) {
        return ()->{
            // check if we have been removed or not (when we stop via SystemTray.remove(), we don't want to run the EventDispatch again)
            synchronized (traySingletons) {
                if (traySingletons.containsKey(trayName)) {
                    // we haven't been removed by anything else

                    // we have to make sure we shutdown on our own thread (and not the JavaFX/SWT/AWT/etc thread)
                    EventDispatch.runLater(()->{
                        synchronized (traySingletons) {
                            // Only perform this action ONCE!
                            SystemTray systemTray = traySingletons.remove(trayName);
                            if (systemTray != null) {
                                systemTray.shutdown();
                            }
                        }
                    });
                }
            }
        };
    }

    public static
    void setInstance(final String trayName, final SystemTray systemTray) {
        // must add ourselves under the specified tray name for retrieval later
        // earlier on inside SystemTray initialization, if the tray-name already exists, THAT tray will be returned (instead of a
        // new one getting created)
        synchronized (traySingletons) {
            traySingletons.put(trayName, systemTray);
        }
    }

    public static
    void removeSystemTrayHook(final String trayName) {
        synchronized (traySingletons) {
            traySingletons.remove(trayName);
        }
    }

    public static
    boolean hasOtherTrays() {
        synchronized (traySingletons) {
            return !traySingletons.isEmpty();
        }
    }

    public static
    SystemTray getInstance(final String trayName) {
        synchronized (traySingletons) {
            return traySingletons.get(trayName);
        }
    }
}
