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

import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import dorkbox.jna.windows.ShCore;
import dorkbox.jna.windows.User32;

/**
 * Size and scaling utility functions specific to Windows
 */
public
class SizeAndScalingWindows {
    public static
    double getDpiScaleForMouseClick(int mousePositionX, int mousePositionY) {
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

        // http://kynosarges.org/WindowsDpi.html

        //                     image-size/menu-height
        //  96 DPI = 100% actual size: 14/17
        // 144 DPI = 150% actual size: 24/29

        // gets the height of the default checkmark size, adjusted
        // This is the closest image size we can get to the actual size programmatically. This is a LOT closer that checking the
        // largest size a JMenu image can be before the menu size changes.
        return User32.User32.GetSystemMetrics(SM_CYMENUCHECK) - 1;

        //                   image-size/menu-height
        //  96 DPI = 100% mark size: 14/20
        // 144 DPI = 150% mark size: 24/30
    }

    public static
    int getTrayImageSize() {
        return User32.User32.GetSystemMetrics(SM_CYSMICON);
    }
}
