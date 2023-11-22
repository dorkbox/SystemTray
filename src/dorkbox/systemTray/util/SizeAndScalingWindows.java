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

import static com.sun.jna.platform.win32.WinUser.SM_CYMENUCHECK;
import static com.sun.jna.platform.win32.WinUser.SM_CYSMICON;

import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import dorkbox.jna.windows.ShCore;
import dorkbox.jna.windows.User32;
import dorkbox.os.OS;
import dorkbox.systemTray.SystemTray;

/**
 * Size and scaling utility functions specific to Windows
 */
public
class SizeAndScalingWindows {
    public static final int SYSTEM_DPI = systemDPI();
    public static final double SYSTEM_SCALE = SYSTEM_DPI / 96.0;

    /**
     * We cannot ABSOLUTELY fix the scaling mess for Java 8 on scaled displays because it is not DPI aware.
     * HOWEVER ... we can at least make it better by making things LARGER
     */
    public static final boolean SCALE_MENU_FOR_JAVA_8 = SystemTray.AUTO_FIX_INCONSISTENCIES && OS.INSTANCE.getJavaVersion() <= 8 && SYSTEM_SCALE != 1.0;

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

    /**
     * Sets the process as DPI aware, only valid for windows 8.1+
     */
    public static void setDpiAware() {
        if (SCALE_MENU_FOR_JAVA_8) {
            SystemTray.logger.warn("Java version " + OS.INSTANCE.getJavaVersion() + " is not DPI aware, scaling scaling at " + SYSTEM_SCALE +
                                   " will look very tiny so we are adjusting the UI accordingly. NOT ALL ELEMENTS CAN BE ADJUSTED!");
            return;
        }

        try {
            ShCore.SetProcessDpiAwareness(ShCore.DPI_AWARENESS_SYSTEM_AWARE);
        } catch (Throwable e) {
            // Ignore error (call valid only on Windows 8.1+)
        }
        try {
            User32.User32.SetThreadDpiAwarenessContext(User32.DPI_AWARENESS_CONTEXT_SYSTEM_AWARE);
        } catch (Throwable e) {
            // Ignore error (call valid only on Windows 8.1+)
        }
    }

    public static
    double getDpiScaleForMouseClick(int mousePositionX, int mousePositionY) {
        if (SCALE_MENU_FOR_JAVA_8) {
            // java 8 has weird UI problems!
            return 1.0;
        }


        WinDef.POINT.ByValue pointValue = new WinDef.POINT.ByValue(mousePositionX, mousePositionY);
        WinUser.HMONITOR monitorFromPoint = User32.User32.MonitorFromPoint(pointValue, WinUser.MONITOR_DEFAULTTONEAREST);

        // I don't know why this has 2 options, but the scale is always the same in both directions...
        IntByReference xScalePtr = new IntByReference();
        IntByReference yScalePtr = new IntByReference();
        ShCore.GetDpiForMonitor(monitorFromPoint, 0, xScalePtr, yScalePtr);

        // 96 is the default scale on windows
        return 96.0D / xScalePtr.getValue();
    }

    public static
    int getMenuImageSize() {
        if (OVERRIDE_MENU_SIZE > 0) {
            return OVERRIDE_MENU_SIZE;
        }

        double scale = SYSTEM_SCALE;
        if (SCALE_MENU_FOR_JAVA_8) {
            // java 8 has weird UI problems!
            scale = 1.0;
        }

        // gets the height of the default checkmark size, adjusted
        // This is the closest image size we can get to the actual size programmatically.
        // This is a LOT closer than checking the size a JMenu image can be before the menu size changes: SizeAndScaling.getMenuImageSizeGeneric()
        // NOTE: we use Swing to render the menu for windows, so we MUST auto-scale the menu!
        int menuSize = (int) (User32.User32.GetSystemMetrics(SM_CYMENUCHECK) / scale);

        // we REALLY want to round it to an even number!! (odd number have ugly scaling)
        menuSize -= menuSize%2;
        return menuSize;
    }

    public static
    int getTrayImageSize(final SystemTray.TrayType trayType) {
        if (OVERRIDE_TRAY_SIZE > 0) {
            return OVERRIDE_TRAY_SIZE;
        }

        int size = User32.User32.GetSystemMetrics(SM_CYSMICON);

        if (trayType == SystemTray.TrayType.Swing && SystemTray.AUTO_FIX_INCONSISTENCIES && SystemTray.AUTO_SIZE) {
            if (SystemTray.DEBUG) {
                SystemTray.logger.debug("When using the SystemTray on Windows as the SWING type, it will not properly scale to fit the menubar. Because" +
                             " AUTO_SIZE is enabled, the size will be adjusted based on the system scale");
            }
            // we MUST scale for windows swing type, as it does not automatically scale otherwise and LOOKS HORRID.
            return (int) (size / SYSTEM_SCALE);
        }

        // for WINDOWS NATIVE, we do not want to scale, as windows will scale/render as appropriate.
        return size;
    }

    private static int systemDPI() {
        try {
            return User32.User32.GetDpiForSystem();
        } catch (Throwable e) {
            // Ignore error
        }

        // default system DPI
        return 96;
    }
}
