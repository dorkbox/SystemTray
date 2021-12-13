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

import dorkbox.jna.linux.GtkTheme;

/**
 * Size and scaling utility functions specific to Linux
 */
public
class SizeAndScalingLinux {
    /**
     * Allows overriding of the system tray MENU size (this is what shows in the system tray).
     *
     * NOTE: Any value >0 will be used.
     */
    public static volatile int OVERRIDE_MENU_SIZE = 0;

    /**
     * Allows overriding of the system tray ICON size (this is what shows in the system tray)
     *
     * NOTE: Any value >0 will be used.
     */
    public static volatile int OVERRIDE_TRAY_SIZE = 0;

    public static
    int getMenuImageSize() {
        if (OVERRIDE_MENU_SIZE > 0) {
            return OVERRIDE_MENU_SIZE;
        }

        // AppIndicator or GtkStatusIcon
        return GtkTheme.getMenuEntryImageSize();
    }

    public static
    int getTrayImageSize() {
        if (OVERRIDE_TRAY_SIZE > 0) {
            return OVERRIDE_TRAY_SIZE;
        }

        return GtkTheme.getIndicatorSize();
    }
}
