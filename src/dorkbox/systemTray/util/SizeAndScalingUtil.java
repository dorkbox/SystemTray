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
package dorkbox.systemTray.util;

import static com.sun.jna.platform.win32.WinUser.SM_CYMENUCHECK;
import static com.sun.jna.platform.win32.WinUser.SM_CYSMICON;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JMenuItem;

import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import dorkbox.jna.linux.GtkTheme;
import dorkbox.jna.windows.ShCore;
import dorkbox.jna.windows.User32;
import dorkbox.os.OS;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.ui.swing._SwingTray;
import dorkbox.systemTray.ui.swing._WindowsNativeTray;
import dorkbox.util.SwingUtil;

public
class SizeAndScalingUtil {
    // the tray size as best as possible for the current OS
    public static int TRAY_SIZE = 0;
    public static int TRAY_MENU_SIZE = 0;

    public static
    int getMacOSScaleFactor() {
        // apple will ALWAYS return 2.0 on (apple) retina displays. This is enforced by apple

        GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        GraphicsConfiguration graphicsConfig = graphicsDevice.getDefaultConfiguration();
        return (int) graphicsConfig.getDefaultTransform().getScaleX();
    }

    public static
    double getWindowsDpiScaleForMouseClick(int mousePositionX, int mousePositionY) {
        POINT.ByValue pointValue = new POINT.ByValue(mousePositionX, mousePositionY);
        WinUser.HMONITOR monitorFromPoint = User32.User32.MonitorFromPoint(pointValue, WinUser.MONITOR_DEFAULTTONEAREST);

        // I don't know why this has 2 options, but the scale is always the same in both directions...
        IntByReference xScalePtr = new IntByReference();
        IntByReference yScalePtr = new IntByReference();
        ShCore.GetDpiForMonitor(monitorFromPoint, 0, xScalePtr, yScalePtr);

        // 96 is the default scale on windows
        return 96.0D / xScalePtr.getValue();
    }


    public static
    int getTrayImageSize() {
        if (TRAY_SIZE == 0) {
            if (OS.isLinux()) {
                TRAY_SIZE = GtkTheme.getIndicatorSize();
            }
            else if (OS.isMacOsX()) {
                // The base (non-scaled) height is 22px tall, measured via a screen-shot. From what I can tell, they are Apple defined, and cannot be changed.
                // we obviously do not want to be the exact same size, so we give 2px padding on each side.
                TRAY_SIZE = SizeAndScalingUtil.getMacOSScaleFactor() * 18;
            }
            else if (OS.isWindows()) {
                TRAY_SIZE = User32.User32.GetSystemMetrics(SM_CYSMICON);
            } else {
                // reasonable default
                TRAY_SIZE = 32;
            }
        }

        if (TRAY_SIZE == 0) {
            // reasonable default
            TRAY_SIZE = 32;
        }

        return TRAY_SIZE;
    }

    public static
    int getMenuImageSize(final Class<? extends Tray> trayType) {
        if (TRAY_MENU_SIZE == 0) {
            if (OS.isMacOsX()) {
                // Note: Mac (AWT) does not have images in the menu.
                // The base (non-scaled) height is 22px tall, measured via a screen-shot. From what I can tell, they are Apple defined, and cannot be changed.
                // we obviously do not want to be the exact same size, so we give 2px padding on each side.
                TRAY_MENU_SIZE = SizeAndScalingUtil.getMacOSScaleFactor() * 18;
            }
            else if ((trayType == _SwingTray.class) || (trayType == _WindowsNativeTray.class)) {
                // Java does not scale the menu item IMAGE **AT ALL**, we must provide the correct size to begin with

                if (OS.isWindows()) {
                    // http://kynosarges.org/WindowsDpi.html

                    //                     image-size/menu-height
                    //  96 DPI = 100% actual size: 14/17
                    // 144 DPI = 150% actual size: 24/29

                    // gets the height of the default checkmark size, adjusted
                    // This is the closest image size we can get to the actual size programmatically. This is a LOT closer that checking the
                    // largest size a JMenu image can be before the menu size changes.
                    TRAY_MENU_SIZE = User32.User32.GetSystemMetrics(SM_CYMENUCHECK) - 1;

                    //                   image-size/menu-height
                    //  96 DPI = 100% mark size: 14/20
                    // 144 DPI = 150% mark size: 24/30
                } else {
                    final AtomicInteger iconSize = new AtomicInteger();

                    SwingUtil.invokeAndWaitQuietly(()->{
                        JMenuItem jMenuItem = new JMenuItem();

                        // do the same modifications that would also happen (if specified) for the actual displayed menu items
                        if (SystemTray.SWING_UI != null) {
                            jMenuItem.setUI(SystemTray.SWING_UI.getItemUI(jMenuItem, null));
                        }

                        // this is the largest size of an image used in a JMenuItem, before the size of the JMenuItem is forced to be larger
                        int height = SwingUtil.getLargestIconHeightForButton(jMenuItem);
                        iconSize.set(height);
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
