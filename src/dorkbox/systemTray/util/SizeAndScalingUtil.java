/*
 * Copyright 2017 dorkbox, llc
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

import static dorkbox.util.jna.windows.Gdi32.GetDeviceCaps;
import static dorkbox.util.jna.windows.Gdi32.LOGPIXELSX;

import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JMenuItem;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.jna.linux.GtkTheme;
import dorkbox.systemTray.nativeUI._AwtTray;
import dorkbox.systemTray.swingUI._SwingTray;
import dorkbox.util.OS;
import dorkbox.util.OSUtil;
import dorkbox.util.SwingUtil;
import dorkbox.util.jna.windows.POINT;
import dorkbox.util.jna.windows.ShCore;
import dorkbox.util.jna.windows.User32;
import dorkbox.util.process.ShellProcessBuilder;

public
class SizeAndScalingUtil {
    // the tray size as best as possible for the current OS
    static int TRAY_SIZE = 0;
    static int TRAY_MENU_SIZE = 0;


    public static
    double getScalingFactor222() {
        double scalingFactor = getLinuxScalingFactor();

        if (scalingFactor > 1) {
            return scalingFactor;
        }

        // fedora 23+ has a different size for the indicator (NOT default 16px)
        int fedoraVersion = OSUtil.Linux.getFedoraVersion();

        if (fedoraVersion >= 23) {
            System.err.println("FEDORA SCALE? ");
//                    if (SystemTray.DEBUG) {
//                        SystemTray.logger.debug("Adjusting tray/menu scaling for FEDORA " + fedoraVersion);
//                    }

            return 2;
//                    trayScalingFactor = 2;
        }

        return 0;
    }

    public static
    double getLinuxScalingFactor() {
        // Linux is similar enough, that it just uses this method
        // https://wiki.archlinux.org/index.php/HiDPI

        // 96 DPI is the default

        OSUtil.DesktopEnv.Env env = OSUtil.DesktopEnv.get();

        // sometimes the scaling-factor is set
        if (OSUtil.DesktopEnv.isGnome()) {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8196);
                PrintStream outputStream = new PrintStream(byteArrayOutputStream);

                // gsettings get org.gnome.desktop.interface scaling-factor
                final ShellProcessBuilder shellVersion = new ShellProcessBuilder(outputStream);
                shellVersion.setExecutable("gsettings");
                shellVersion.addArgument("get");
                shellVersion.addArgument("org.gnome.desktop.interface");
                shellVersion.addArgument("scaling-factor");
                shellVersion.start();

                String output = ShellProcessBuilder.getOutput(byteArrayOutputStream);

                if (!output.isEmpty()) {
                    // DEFAULT icon size is 16. HiDpi changes this scale, so we should use it as well.
                    // should be: uint32 0  or something
                    if (output.contains("uint32")) {
                        String value = output.substring(output.indexOf("uint") + 7, output.length());

                        // 0 is disabled (no scaling)
                        // 1 is enabled (default scale)
                        // 2 is 2x scale
                        // 3 is 3x scale
                        // etc

                        return Integer.parseInt(value);

                        // A setting of 2, 3, etc, which is all you can do with scaling-factor
                        // To enable HiDPI, use gsettings:
                        // gsettings set org.gnome.desktop.interface scaling-factor 2
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        else if (OSUtil.DesktopEnv.isKDE()) {
//                By default, the DPI value in KDE is set to 96 DPI. To find the accurate display scaling value we need to divide the accurate DPI by 96, so it is 269 ÷ 96 which is 2.8.
            // xdpyinfo | grep dots.
//                resolution:    96x96 dots per inch
        }



            /*
            Gnome: Since version 3.10, Gnome auto-detects screen resolution and sets a 192 DPI value on HiDPI monitors. You can verify this by running "xrdb -query". Therefore, LibreOffice looks good out of the box.

KDE: In 4.x system settings, under Application Appearance - Fonts, you can set the "Force fonts DPI" to 192. When this is set, the LibreOffice HiDPI mode kicks in.

Xfce: It is possible to manually set the DPI to 192 as well.

Unity: System settings → Displays and using the slider "Scale for menu and title bars", set the value to 2. ("xrdb -query" returns 192 dpi after setting the scale factor 2, and the scale factor may be 2 by default for HIDPI screens in the future.)





            X Server

Some programs use the DPI given by the X server. Examples are i3 (source) and Chromium (source).
To verify that the X Server has properly detected the physical dimensions of your monitor, use the xdpyinfo utility from the xorg-xdpyinfo package:
$ xdpyinfo | grep -B 2 resolution
screen #0:
  dimensions:    3200x1800 pixels (423x238 millimeters)
  resolution:    192x192 dots per inch
             */



        // KDE is bonkers. Gnome is "ok"
        String XDG = System.getenv("XDG_CURRENT_DESKTOP");
        if (XDG == null) {
            if (OSUtil.DesktopEnv.isKDE()) {
                XDG = "kde";
            }
        }

        if ("kde".equalsIgnoreCase(XDG)) {
            double plasmaVersion = OSUtil.DesktopEnv.getPlasmaVersion();




            // 1 = 16
            // 2 = 32
            // 4 = 64
            // 8 = 128
            if (plasmaVersion > 0) {
                return 2;
//                    trayScalingFactor = 2;
//                        menuScalingFactor = 1.4;
            }
//                else if (SystemTray.DEBUG) {
//                    SystemTray.logger.error("Cannot check plasmashell version");
//                }
        }
        else {
            // it's likely a Gnome/unity environment

            // NOTE: ALSO from here:
//            xdpyinfo | grep dots reported 96x96 dots



        }

        return 0;
    }


    public static
    double getMacOSScaleFactor() {
        // apple will ALWAYS return 2.0 on (apple) retina displays. If it's a non-standard, then who knows...

        // java6 way of getting it...
        if (OS.javaVersion == 6) {
            Object obj = Toolkit.getDefaultToolkit().getDesktopProperty("apple.awt.contentScaleFactor");
            if (obj instanceof Float) {
                // 1 = retina not available (regular display). Usually, for retina enabled displays returns 2.
                if ((Float) obj > 1.9F) {
                    // this means it's really 2.0F
                    return 2.0D;
                } else {
                    return 1.0D;
                }
            }
        }

        // java7+ way of getting it.
        GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        try {
            Field field = graphicsDevice.getClass().getDeclaredField("scale");
            if (field != null) {
                field.setAccessible(true);
                Object scale = field.get(graphicsDevice);

                if (scale instanceof Integer) {
                    return (Integer) scale;
                }
            }
        } catch (Exception ignored) {
        }

        // apple.awt.contentScaleFactor or graphics scale does not ALWAYS work, so we draw on the screen THEN see if it was scaled.
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = GraphicsEnvironment.getLocalGraphicsEnvironment().createGraphics(img);
        AffineTransform transform = g.getFontRenderContext()
                                     .getTransform();

        return transform.getScaleX();
    }


    /**
     * Number of pixels per logical inch along the screen width. In a system with multiple display monitors, this value is the
     * same for all monitors.
     *
     * @return
     */
    public static
    int getLogicalDPI() {
        // get the logical resolution
        Pointer screen = User32.GetDC(null);
        int logical_dpiX = GetDeviceCaps(screen, LOGPIXELSX);
        User32.ReleaseDC(null, screen);

        return logical_dpiX;
    }

    public static
    int getPrimaryMonitorHardwareDPI() {
        // WINDOWS 8.1+ ONLY! Parts of this API were added in Windows 8.1, so this will not work at all for < 8.1
        if (OSUtil.Windows.isWindows8_1_plus()) {
            // FROM: https://blogs.msdn.microsoft.com/oldnewthing/20070809-00/?p=25643
            // to get the **PRIMARY** monitor, pass in point 0,0

            IntByReference hardware_dpiX = new IntByReference();
            // get the primary monitor handle
            Pointer pointer = User32.MonitorFromPoint(new POINT(0, 0).asValue(), 1);// MONITOR_DEFAULTTOPRIMARY -> 1

            ShCore.GetDpiForMonitor(pointer, 1, hardware_dpiX, new IntByReference()); // don't care about y

            return hardware_dpiX.getValue();
        }

        return 0;
    }

//
//        /**
//         * At a 100% scaling, the DPI is 96.
//         */
//        private static final float WIN_DEFAULT_DPI = 96;
//
//        Integer winDPIScaling;
//    if (PlatformDetector.isWin7()) {
//            winDPIScaling = 1;
//        } else {
//            // Win 8 or later.
//            winDPIScaling = RegistryUtil.getRegistryIntValue(
//                            RegistryUtil.HKEY_CURRENT_USER,
//                            "Control Panel\\Desktop",
//                            "Win8DpiScaling");
//            if(winDPIScaling == null){
//                winDPIScaling = 0;
//            }
//        }
//
//        Integer desktopDPIOverride;
//    if (PlatformDetector.isWin7()) {
//            desktopDPIOverride = 0;
//        } else {
//            // Win 8 or later.
//            desktopDPIOverride = RegistryUtil.getRegistryIntValue(
//                            RegistryUtil.HKEY_CURRENT_USER,
//                            "Control Panel\\Desktop",
//                            "DesktopDPIOverride");
//            if(desktopDPIOverride == null){
//                desktopDPIOverride = 0;
//            }
//
//        }
//
//
//    if (winDPIScaling == 1 && desktopDPIOverride == 0){
//            // There is scaling, but on override (magnifying glass).
//            Integer logPixels = RegistryUtil.getRegistryIntValue(
//                            RegistryUtil.HKEY_CURRENT_USER,
//                            "Control Panel\\Desktop",
//                            "LogPixels");
//
//            if (logPixels != null && logPixels != WIN_DEFAULT_DPI){
//                this.scalingFactor = ((float)logPixels)/WIN_DEFAULT_DPI;
//            }
//        }



    public static
    int getTrayImageSize(final Class<? extends Tray> trayType) {
        if (TRAY_SIZE == 0) {
            if (OS.isLinux()) {
                TRAY_SIZE = GtkTheme.getIndicatorSize(trayType);
            }
            else if (OS.isMacOsX()) {
                // these are the standard icon sizes. From what I can tell, they are Apple defined, and cannot be changed.
                if (SizeAndScalingUtil.getMacOSScaleFactor() == 2.0D) {
                    TRAY_SIZE = 44;
                } else {
                    TRAY_SIZE = 22;
                }
            }
            else if (OS.isWindows()) {
                // registry setting?
                // HKEY_CURRENT_USER\Control Panel\Appearance\New Schemes\Current Settings SaveAll\Sizes\0

                // no idea?
                TRAY_SIZE = 32;
            } else {
                // no idea?
                TRAY_SIZE = 32;
            }
        }

        System.err.println("Tray size: " + TRAY_SIZE);


        return TRAY_SIZE;



        // default DPI, tray size in xbunutu (GtkStatusIcon)is 22px
        // for more complete info on the linux side of things...
        // https://wiki.archlinux.org/index.php/HiDPI

//
//        double trayScalingFactor = 0;
//
//        // Linux USUALLY has it to 16 (when possible), and Mac (AWT) does not have images in the menu.
//        int menuSize = 16;
//
//        if (SystemTray.AUTO_SIZE) {
//            if (OS.isWindows()) {
//                int[] version = OSUtil.Windows.getVersion();
//                if (SystemTray.DEBUG) {
//                    SystemTray.logger.debug("Windows version: '{}'", Arrays.toString(version));
//                }
//
//
//                // windows 8/8.1/10 are the only windows OSes to do scaling properly (XP/Vista/7 do DPI scaling, which is terrible anyways)
//                // we are going to let windows manage scaling the icon correctly, but we are BY DEFAULT going to give it a large size to scale
//
//                // vista - 8.0 - only global DPI settings
//                // 8.1 - 10 - global + per-monitor DPI settings
//
//                // get the logical resolution
//                int logical_dpiX = OSUtil.Windows.getLogicalDPI();
//
//                // WINDOWS 8.1+ ONLY! Parts of this API were added in Windows 8.1, so this will not work at all for < 8.1
//                if (OSUtil.Windows.isWindows8_1_plus()) {
//                    logical_dpiX = OSUtil.Windows.getPrimaryMonitorHardwareDPI();
//                    if (SystemTray.DEBUG) {
//                        SystemTray.logger.debug("Windows hardware DPI: '{}'", logical_dpiX);
//                    }
//                }
//
//                // 96 DPI = 100% scaling
//                // 120 DPI = 125% scaling
//                // 144 DPI = 150% scaling
//                // 192 DPI = 200% scaling
//
//                // just a note on scaling...
//                // We want to scale the image as best we can beforehand, so there is an attempt to have it look good.
//                // Java does not scale the menu item IMAGE **AT ALL**
//
//                if (SystemTray.DEBUG) {
//                    SystemTray.logger.debug("Windows logical DPI: '{}'", logical_dpiX);
//                }
//            } else if (OS.isLinux() || OS.isUnix()) {
//                // GtkStatusIcon will USUALLY automatically scale the icon
//                // AppIndicator MIGHT scale the icon (depends on the OS)
////
////                if (Gtk.isGtk2) {
////
////                } else {
////
////                }
////
////                String css = Gtk.getCss();
////                if (css != null) {
////
////                }
//
//
//
//
//                System.err.println("");
//
//
//                trayScalingFactor = OSUtil.Linux.getScalingFactor();
//            } else if (OS.isMacOsX()) {
////                if (SystemTray.DEBUG) {
////                    SystemTray.logger.debug("Mac is HiDPI: '{}'", OSUtil.MacOS.isHiDPI());
////                }
//
//                // let's hope this wasn't just hardcoded like windows...
//                int height;
//
//                if (!SwingUtilities.isEventDispatchThread()) {
//                    // MacOS must execute this on the EDT... It is very strict compared to win/linux
//                    final AtomicInteger h = new AtomicInteger(0);
//                    SwingUtil.invokeAndWaitQuietly(new Runnable() {
//                        @Override
//                        public
//                        void run() {
//                            h.set((int) java.awt.SystemTray.getSystemTray()
//                                                           .getTrayIconSize()
//                                                           .getHeight());
//                        }
//                    });
//                    height = h.get();
//                } else {
//                    height = (int) java.awt.SystemTray.getSystemTray()
//                                                      .getTrayIconSize()
//                                                      .getHeight();
//                }
//
//                if (height < 32) {
//                    // lock in at 32
//                    trayScalingFactor = 2;
//                }
//                else if ((height & (height - 1)) == 0) {
//                    // is this a power of 2 number? If so, we can use it
////                    trayScalingFactor = height/SystemTray.DEFAULT_TRAY_SIZE;
//                }
//                else {
//                    // don't know how exactly to determine this, but we are going to assume very high "HiDPI" for this...
//                    // the OS should go up/down as needed.
//                    trayScalingFactor = 8;
//                }
//            }
//        }
//
//        // windows, mac, linux(GtkStatusIcon) will automatically scale the tray size
//        //  the menu entry icon size will NOT get scaled (it will show whatever we specify)
//        // we want to make sure our "scaled" size is appropriate for the OS.
//
//        // the DEFAULT scale is 16
//        if (trayScalingFactor > 1) {
////            TRAY_SIZE = (int) (SystemTray.DEFAULT_TRAY_SIZE * trayScalingFactor);
//        } else {
////            TRAY_SIZE = SystemTray.DEFAULT_TRAY_SIZE;
//        }
//
//        // critical to adjust the menu image BASED ON THE L&F (if Swing)... otherwise just a sane default
//        // Swing COULD be used for OSes **other than windows**, which is why this here and not in a code block above
//        if (trayType == _SwingTray.class) {
//            JMenuItem jMenuItem = new JMenuItem("`Tj|┃"); // `Tj|┃ are glyphs that are at usually at the extremes of the font
//
//            // do the same modifications that would also happen (if specified) for the actual displayed menu items
//            if (SystemTray.SWING_UI != null) {
//                jMenuItem.setUI(SystemTray.SWING_UI.getItemUI(jMenuItem, null));
//            }
//
//            int menuItemHeight = (int) jMenuItem.getPreferredSize().getHeight();
//            int size = SwingUtil.getLargestIconHeightForButton(jMenuItem);
//            NATURAL_SWING_ICON_SIZE = size;
//
//            // this is the largest icon BEFORE changing the natural size of the JMenuItem
//            System.out.println("Natural menu size of " + menuItemHeight + "px for imageIcon size of " + size + "x" + size);
//
//            // 96 DPI = 100% scaling
//            // 120 DPI = 125% scaling
//            // 144 DPI = 150% scaling
//            // 192 DPI = 200% scaling
//
//            // default values for system L&F:
//            // ubuntu (metal) : 15px (16px adjusted)
//            // windows XP (windows) : 15px (16px adjusted) @ 96dpi
//            // windows 7 (windows) : 15px (16px adjusted) @ 96dpi, 21px (22px adjusted)  @ 144dpi
//            // windows 8.1 (windows) : 22px @ 96dpi, 24px @ 120dpi, 31px (30px adjusted) @ 144dpi
//            // windows 10 (windows) : 22px @ 96dpi, 24px @ 120dpi, 31px (30px adjusted) @ 144dpi
//
//            // if within a close range to 16px, it is preferred, since icons will look the absolute best when scaled to this resolution (power of 2)
//            // 15 -> 16
//            // 17 -> 16
//            // all others -> even (ok) or odd (bad)
////            if (requestedSize == 15) {
////                menuSize = 16;
////            }
////            else if (requestedSize == 17) {
////                menuSize = 16;
////            }
////            else if ((requestedSize & 1) != 0) {
////                // is odd. Go larger if margins have space, otherwise go smaller by one pixel
////                if (margin.bottom > 1 && margin.top > 1) {
////                    menuSize = requestedSize + 1;
////                } else {
////                    menuSize = requestedSize - 1;
////                }
////            }
////            else {
////                // is even
////                menuSize = requestedSize;
////            }
//
//            if (SystemTray.DEBUG) {
//                SystemTray.logger.debug("Swing L&F: " + UIManager.getLookAndFeel().getName());
////                SystemTray.logger.debug("Swing L&F menu item height w/o margins: {} adjusted to {}", requestedSize, menuSize);
//            }
//        }
//
////        ENTRY_SIZE = menuSize;
////        ENTRY_SIZE = 16;
//        ENTRY_SIZE = 64;
//        TRAY_SIZE = 32;
//
//        if (SystemTray.DEBUG) {
//            SystemTray.logger.debug("ScalingFactor is '{}', tray icon size is '{}', menu item size is '{}'.", trayScalingFactor,
//                                    TRAY_SIZE, ENTRY_SIZE);
//        }




        // windows is funky, and is hardcoded to 16x16. We fix that.
        // Windows XP is 16x16

        // Should have checks, so we can set different sizes: 16, 32, 48 and 256 sizes

        // https://msdn.microsoft.com/en-us/library/bb773352(v=vs.85).aspx
        // provide both a 16x16 pixel icon and a 32x32 icon

        // https://msdn.microsoft.com/en-us/library/dn742495.aspx
        // Use an icon with 16x16, 20x20, and 24x24 pixel versions. The larger versions are used in high-dpi display mode

    }


    public static
    int getMenuImageSize(final Class<? extends Tray> trayType) {
        if (TRAY_MENU_SIZE == 0) {
            if ((trayType == _SwingTray.class || trayType == _AwtTray.class) && !OS.isMacOsX()) {

//                   if (OS.isWindows()) {
                    // registry setting?
                    // HKEY_CURRENT_USER\Control Panel\Appearance\New Schemes\Current Settings SaveAll\Sizes\0

                // Swing or AWT. While not "normal", this is absolutely a possible combination. Also, GTK is not loaded!
                final AtomicInteger iconSize = new AtomicInteger();

                SwingUtil.invokeAndWaitQuietly(new Runnable() {
                    @Override
                    public
                    void run() {
                        JMenuItem jMenuItem = new JMenuItem();

                        // do the same modifications that would also happen (if specified) for the actual displayed menu items
                        if (SystemTray.SWING_UI != null) {
                            jMenuItem.setUI(SystemTray.SWING_UI.getItemUI(jMenuItem, null));
                        }

                        // this is the largest size of an image used in a JMenuItem, before the size of the JMenuItem is forced to be larger
                        int largestIconHeightForButton = SwingUtil.getLargestIconHeightForButton(jMenuItem);

                        // Having the menu images the size of "X" is a reasonably nice size.
                        // if the we don't want the images to be larger than
//                            largestIconHeightForButton = FontUtil.getFontHeight(jMenuItem.getFont(), "X");
//                            largestIconHeightForButton = FontUtil.getAlphaNumericFontHeight(jMenuItem.getFont());
//                            largestIconHeightForButton = FontUtil.getMaxFontHeight(jMenuItem.getFont());
                        iconSize.set(largestIconHeightForButton);
                    }
                });

                TRAY_MENU_SIZE = iconSize.get();
            }

            else if (OS.isLinux()) {
                TRAY_MENU_SIZE = 16;

//        void
//        gtk_widget_get_preferred_height (GtkWidget *widget,
//                                         gint *minimum_height,
//                                         gint *natural_height);

//                g_param_spec_int ("check-icon-size",
//                                  "Check icon size",
//                                  "Check icon size",
//                                  -1, G_MAXINT, 40,
//                                  G_PARAM_READWRITE));


//                    -GtkCheckButton-indicator-size: 15;
//                    -GtkCheckMenuItem-indicator-size: 14;



            }
            else if (OS.isMacOsX()) {
                // these are the standard icon sizes. From what I can tell, they are Apple defined, and cannot be changed.
                if (SizeAndScalingUtil.getMacOSScaleFactor() == 2.0D) {
                    TRAY_MENU_SIZE = 32;
                } else {
                    TRAY_MENU_SIZE = 16;
                }
            } else {
                // no idea?
                TRAY_MENU_SIZE = 16;
            }
        }

        System.err.println("Tray menu image size: " + TRAY_MENU_SIZE);

        return TRAY_MENU_SIZE;
    }
}
