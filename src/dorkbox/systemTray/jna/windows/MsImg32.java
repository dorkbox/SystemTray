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
package dorkbox.systemTray.jna.windows;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.W32APIOptions;

import dorkbox.systemTray.jna.windows.structs.BLENDFUNCTION;

public
class MsImg32 {
    static {
        Native.register(NativeLibrary.getInstance("Msimg32", W32APIOptions.DEFAULT_OPTIONS));
    }

    public static final int ETO_OPAQUE = 2;
    public static final int SRCCOPY = 0xCC0020;

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/dd183351(v=vs.85).aspx
     */
    public static native
    boolean AlphaBlend(WinDef.HDC hdcDest, int xoriginDest, int yoriginDest, int wDest, int hDest, WinDef.HDC hdcSrc, int xoriginSrc,
                       int yoriginSrc, int wSrc, int hSrc, BLENDFUNCTION.ByValue ftn);
}
