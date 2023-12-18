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

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

/**
 * Size and scaling utility functions specific to macOS
 */
public
class SizeAndScalingMacOS {
    public static final int SYSTEM_DPI = getScreenDPI();
    public static final double SYSTEM_SCALE = getSystemScale();

    /**
     * Allows overriding of the system tray MENU size (this is what shows in the system tray).
     * NOTE: Any value >0 will be used.
     */
    public static volatile int OVERRIDE_MENU_SIZE = 0;

    /**
     * Allows overriding of the system tray ICON size (this is what shows in the system tray)
     * NOTE: Any value >0 will be used.
     */
    public static volatile int OVERRIDE_TRAY_SIZE = 0;

    /**
     * This is for the DEFAULT screen, not the current screen!!
     *
     * apple will ALWAYS return 2.0 on (apple) retina displays. This is enforced by apple.
     */
    public static
    double getSystemScale() {
        GraphicsDevice graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        GraphicsConfiguration graphicsConfig = graphicsDevice.getDefaultConfiguration();
        return graphicsConfig.getDefaultTransform().getScaleX();
    }

    /**
     * This is for the DEFAULT screen, not the current screen!!
     */
    public static
    int getScreenDPI() {
        // find the display device of interest
        final GraphicsDevice defaultScreenDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        // on OS X, it would be CGraphicsDevice
        if (defaultScreenDevice instanceof sun.awt.CGraphicsDevice) {
            final sun.awt.CGraphicsDevice device = (sun.awt.CGraphicsDevice) defaultScreenDevice;

            // this is the missing correction factor, it's equal to 2 on HiDPI a.k.a. Retina displays
            final int scaleFactor = device.getScaleFactor();

            // now we can compute the real DPI of the screen.
            // we cannot have fractions of a resolution.
            final double realDPI = scaleFactor * ((int)device.getXResolution() + (int)device.getYResolution()) / 2.0;
            return (int) realDPI;
        }

        // shouldn't get here, but just in case!
        return 0;
    }


    public static
    int getMenuImageSize() {
        if (OVERRIDE_MENU_SIZE > 0) {
            return OVERRIDE_MENU_SIZE;
        }

        // scaling isn't in effect for menu images. The larger the image, the larger the menu height. It can be any size you want in reality.
        // 16 is the max size it can be BEFORE the menu height starts getting larger, which then causes menu items WITH and WITHOUT images
        // to be different heights.
        return 16;
    }

    public static
    int getTrayImageSize() {
        if (OVERRIDE_TRAY_SIZE > 0) {
            return OVERRIDE_TRAY_SIZE;
        }

        // menu items can have scaling applied for a nice looking icon.
        return (int) (20 * SYSTEM_SCALE);
    }
}
