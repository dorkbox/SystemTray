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

import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JMenuItem;

import dorkbox.os.OS;
import dorkbox.systemTray.SystemTray;
import dorkbox.util.SwingUtil;

public
class SizeAndScaling {
    // the tray size as best as possible for the current OS
    public static int TRAY_SIZE = 0;
    public static int TRAY_MENU_SIZE = 0;

    public static
    void initSizes(final SystemTray.TrayType trayType) {
        getTrayImageSize(trayType);
        getMenuImageSize(trayType);

        if (SystemTray.DEBUG) {
            SystemTray.logger.debug("Tray image size: {}", TRAY_SIZE);
            SystemTray.logger.debug("Tray menu image size: {}", TRAY_MENU_SIZE);

            // not impl for everything yet MACOS is "normal" or '2x' for scale
            if (OS.INSTANCE.isWindows()) {
                SystemTray.logger.debug("SystemDPI: " + SizeAndScalingWindows.SYSTEM_DPI);
                SystemTray.logger.debug("System Scale: " + SizeAndScalingWindows.SYSTEM_SCALE);
            }
            else if (OS.INSTANCE.isLinux()) {
                SystemTray.logger.debug("SystemDPI: " + SizeAndScalingLinux.SYSTEM_DPI);
                SystemTray.logger.debug("System Scale: " + SizeAndScalingLinux.SYSTEM_SCALE);
            }
            else if (OS.INSTANCE.isMacOsX()) {
                SystemTray.logger.debug("SystemDPI: " + SizeAndScalingMacOS.SYSTEM_DPI);
                SystemTray.logger.debug("System Scale: " + SizeAndScalingMacOS.SYSTEM_SCALE);
            }
        }
    }

    public static
    double getDpiScaleForMouseClick(int mousePositionX, int mousePositionY) {
        if (OS.INSTANCE.isWindows()) {
            // manual scaling is only necessary for windows
            return SizeAndScalingWindows.getDpiScaleForMouseClick(mousePositionX, mousePositionY);
        } else {
            return 1.0;
        }
    }


    public static
    void getTrayImageSize(final SystemTray.TrayType trayType) {
        if (TRAY_SIZE == 0) {
            if (OS.INSTANCE.isLinux()) {
                TRAY_SIZE = SizeAndScalingLinux.getTrayImageSize();
            }
            else if (OS.INSTANCE.isMacOsX()) {
                TRAY_SIZE = SizeAndScalingMacOS.getTrayImageSize();
            }
            else if (OS.INSTANCE.isWindows()) {
                TRAY_SIZE = SizeAndScalingWindows.getTrayImageSize(trayType);
            } else {
                // reasonable default
                TRAY_SIZE = 32;
            }
        }

        if (TRAY_SIZE == 0) {
            // reasonable default
            TRAY_SIZE = 32;
        }
    }

    public static
    void getMenuImageSize(final SystemTray.TrayType trayType) {
        if (TRAY_MENU_SIZE == 0) {
            if (OS.INSTANCE.isMacOsX()) {
                TRAY_MENU_SIZE = SizeAndScalingMacOS.getMenuImageSize();
            }
            else if (trayType == SystemTray.TrayType.Swing || trayType == SystemTray.TrayType.WindowsNative) {
                // Java does not scale the menu item IMAGE **AT ALL**, we must provide the correct size to begin with

                if (OS.INSTANCE.isWindows()) {
                    TRAY_MENU_SIZE = SizeAndScalingWindows.getMenuImageSize();
                } else {
                    // generic method to do this, but not as accurate
                    TRAY_MENU_SIZE = getMenuImageSizeGeneric();
                }
            }
            else if (OS.INSTANCE.isLinux()) {
                TRAY_MENU_SIZE = SizeAndScalingLinux.getMenuImageSize();
            } else {
                // reasonable default
                TRAY_MENU_SIZE = 16;
            }
        }
    }

    private static
    int getMenuImageSizeGeneric() {
        // generic method to do this, but not as accurate
        final AtomicInteger iconSize = new AtomicInteger();

        SwingUtil.INSTANCE.invokeAndWaitQuietly(()->{
            JMenuItem jMenuItem = new JMenuItem();

            // do the same modifications that would also happen (if specified) for the actual displayed menu items
            if (SystemTray.SWING_UI != null) {
                jMenuItem.setUI(SystemTray.SWING_UI.getItemUI(jMenuItem, null));
            }

            // this is the largest size of an image used in a JMenuItem, before the size of the JMenuItem is forced to be larger
            int height = SwingUtil.INSTANCE.getLargestIconHeightForButton(jMenuItem);
            iconSize.set(height);
        });
        return iconSize.get();
    }
}
