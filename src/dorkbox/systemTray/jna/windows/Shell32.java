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
import com.sun.jna.win32.W32APIOptions;

import dorkbox.systemTray.jna.windows.structs.NOTIFYICONDATA;

public
class Shell32 {
    static {
        Native.register(NativeLibrary.getInstance("shell32", W32APIOptions.DEFAULT_OPTIONS));
    }

    static public final int NIM_ADD = 0x0;
    static public final int NIM_MODIFY = 0x1;
    static public final int NIM_DELETE = 0x2;

    public static native
    boolean Shell_NotifyIcon(int dwMessage, NOTIFYICONDATA lpdata);
}
