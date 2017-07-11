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

import static com.sun.jna.platform.win32.WinDef.HDC;
import static com.sun.jna.platform.win32.WinDef.POINT;
import static com.sun.jna.platform.win32.WinUser.SM_CYMENUCHECK;
import static dorkbox.util.jna.windows.GDI32.GetDeviceCaps;
import static dorkbox.util.jna.windows.GDI32.LOGPIXELSX;

import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JMenuItem;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.jna.linux.GtkTheme;
import dorkbox.systemTray.swingUI._SwingTray;
import dorkbox.util.OS;
import dorkbox.util.OSUtil;
import dorkbox.util.SwingUtil;
import dorkbox.util.jna.windows.ShCore;
import dorkbox.util.jna.windows.User32;

public
class SizeAndScalingUtil {
    // the tray size as best as possible for the current OS
    static int TRAY_SIZE = 0;
    static int TRAY_MENU_SIZE = 0;

    static {
//        if (OSUtil.Windows.isWindows8_1_plus()) {
//            ShCore.SetProcessDpiAwareness(ProcessDpiAwareness.PROCESS_SYSTEM_DPI_AWARE);
//        }
    }

    private static
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
     */
    public static
    int getWindowsLogicalDPI() {
        // get the logical resolution
        HDC screen = User32.IMPL.GetDC(null);
        int logical_dpiX = GetDeviceCaps(screen, LOGPIXELSX);
        User32.IMPL.ReleaseDC(null, screen);

        if (SystemTray.DEBUG) {
            SystemTray.logger.debug("Windows logical DPI: '{}'", logical_dpiX);
        }

        return logical_dpiX;
    }

    public static
    int getWindowsPrimaryMonitorHardwareDPI() {
        // WINDOWS 8.1+ ONLY! Parts of this API were added in Windows 8.1, so this will not work at all for < 8.1
        if (OSUtil.Windows.isWindows8_1_plus()) {
            // FROM: https://blogs.msdn.microsoft.com/oldnewthing/20070809-00/?p=25643
            // to get the **PRIMARY** monitor, pass in point 0,0

            IntByReference hardware_dpiX = new IntByReference();
            // get the primary monitor handle
            Pointer pointer = User32.IMPL.MonitorFromPoint(new POINT(0, 0), 1);// MONITOR_DEFAULTTOPRIMARY -> 1

            ShCore.GetDpiForMonitor(pointer, 1, hardware_dpiX, new IntByReference()); // don't care about y

            int value = hardware_dpiX.getValue();

            if (SystemTray.DEBUG) {
                SystemTray.logger.debug("Windows hardware DPI: '{}'", value);
            }

            return value;
        }

        return 0;
    }

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
                int[] version = OSUtil.Windows.getVersion();
                if (SystemTray.DEBUG) {
                    SystemTray.logger.debug("Windows version: '{}'", Arrays.toString(version));
                }

                // http://kynosarges.org/WindowsDpi.html

                // 96 DPI = 100% scaling
                // 120 DPI = 125% scaling
                // 144 DPI = 150% scaling
                // 192 DPI = 200% scaling
                final double defaultDPI = 96.0;

                // windows 8/8.1/10 are the only windows OSes to do scaling properly (XP/Vista/7 do DPI scaling, which is terrible anyways)

                // XP - 7.0 - only global DPI settings, no scaling
                // 8.0 - only global DPI settings + scaling
                // 8.1 - 10 - global + per-monitor DPI settings + scaling

                // get the logical resolution
                int windowsLogicalDPI = getWindowsLogicalDPI();

                if (!OSUtil.Windows.isWindows8_1_plus()) {
                    // < Windows 8.1 doesn't do scaling + DPI changes, they just "magnify" (but not scale w/ DPI) the icon + change the font size.
                    // 96 DPI = 16
                    // 120 DPI = 20 (16 * 1.25)
                    // 144 DPI = 24 (16 * 1.5)
                    TRAY_SIZE = 16;
                    return TRAY_SIZE;
                }
                else {
                    // Windows 8.1+ does proper scaling, so an icon at a higher resolution is drawn, instead of drawing the "original"
                    // resolution image and scaling it up to the new size

                    // 96 DPI = 16
                    // 120 DPI = 20 (16 * 1.25)
                    // 144 DPI = 24 (16 * 1.5)
                    TRAY_SIZE = (int) (16 * (windowsLogicalDPI / defaultDPI));
                    return TRAY_SIZE;
                }

// NOTE: can override DPI settings
//         * At a 100% scaling, the DPI is 96.
//
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


                // https://msdn.microsoft.com/en-us/library/bb773352(v=vs.85).aspx
                // provide both a 16x16 pixel icon and a 32x32 icon

                // https://msdn.microsoft.com/en-us/library/dn742495.aspx
                // Use an icon with 16x16, 20x20, and 24x24 pixel versions. The larger versions are used in high-dpi display mode
            } else {
                // reasonable default
                TRAY_SIZE = 32;
            }
        }

        return TRAY_SIZE;
}

    public static
    int getMenuImageSize(final Class<? extends Tray> trayType) {
        if (TRAY_MENU_SIZE == 0) {
            if (OS.isMacOsX()) {
                // Note: Mac (AWT) does not have images in the menu.
                // these are the standard icon sizes. From what I can tell, they are Apple defined, and cannot be changed.
                if (SizeAndScalingUtil.getMacOSScaleFactor() == 2.0D) {
                    TRAY_MENU_SIZE = 32;
                }
                else {
                    TRAY_MENU_SIZE = 16;
                }
            }
            else if ((trayType == _SwingTray.class)) {
                // Java does not scale the menu item IMAGE **AT ALL**, we must provide the correct size to begin with

                if (OS.isWindows()) {
                    // http://kynosarges.org/WindowsDpi.html


                    //                     image-size/menu-height
                    //  96 DPI = 100% actual size: 14/17
                    // 144 DPI = 150% actual size: 24/29

                    // gets the height of the default checkmark size, adjusted
                    // This is the closest image size we can get to the actual size programmatically. This is a LOT closer that checking the
                    // largest size a JMenu image can be before the menu size changes.
                    TRAY_MENU_SIZE = User32.IMPL.GetSystemMetrics(SM_CYMENUCHECK) - 1;

                    //                   image-size/menu-height
                    //  96 DPI = 100% mark size: 14/20
                    // 144 DPI = 150% mark size: 24/30
                } else {
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
                            int height = SwingUtil.getLargestIconHeightForButton(jMenuItem);
                            iconSize.set(height);
                        }
                    });
                    TRAY_MENU_SIZE = iconSize.get();
                }
            }
            else if (OS.isLinux()) {
                // AppIndicator or GtkStatusIcon
                TRAY_MENU_SIZE = GtkTheme.getMenuEntryImageSize();
            } else {
                // reasonable default
                TRAY_MENU_SIZE = 16;
            }
        }

        return TRAY_MENU_SIZE;
    }
}
