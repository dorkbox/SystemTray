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

import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JMenuItem;

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
    double getDpiScaleForMouseClick(int mousePositionX, int mousePositionY) {
        if (OS.isWindows()) {
            // manual scaling is only necessary for windows
            return SizeAndScalingWindows.getDpiScaleForMouseClick(mousePositionX, mousePositionY);
        } else {
            return 1.0;
        }
    }


    public static
    int getTrayImageSize() {
        if (TRAY_SIZE == 0) {
            if (OS.isLinux()) {
                TRAY_SIZE = SizeAndScalingLinux.getTrayImageSize();
            }
            else if (OS.isMacOsX()) {
                TRAY_SIZE = SizeAndScalingMacOS.getTrayImageSize();
            }
            else if (OS.isWindows()) {
                TRAY_SIZE = SizeAndScalingWindows.getTrayImageSize();
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
                TRAY_MENU_SIZE = SizeAndScalingMacOS.getMenuImageSize();
            }
            else if ((trayType == _SwingTray.class) || (trayType == _WindowsNativeTray.class)) {
                // Java does not scale the menu item IMAGE **AT ALL**, we must provide the correct size to begin with

                if (OS.isWindows()) {
                    TRAY_MENU_SIZE = SizeAndScalingWindows.getMenuImageSize();
                } else {
                    // generic method to do this, but not as accurate
                    TRAY_MENU_SIZE = getMenuImageSizeGeneric();
                }
            }
            else if (OS.isLinux()) {
                TRAY_MENU_SIZE = SizeAndScalingLinux.getMenuImageSize();
            } else {
                // reasonable default
                TRAY_MENU_SIZE = 16;
            }
        }

        return TRAY_MENU_SIZE;
    }

    public static
    int getMenuImageSizeGeneric() {
        // generic method to do this, but not as accurate
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
        return iconSize.get();
    }
}
