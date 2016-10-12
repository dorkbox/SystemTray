/*
 * Copyright 2015 dorkbox, llc
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
package dorkbox.systemTray.jna.Windows;

import com.sun.jna.Pointer;

import dorkbox.systemTray.jna.JnaHelper;

/**
 * bindings for User32
 * <p>
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
public
class User32 {

    static {
        JnaHelper.register("user32", User32.class);
    }


    /**
     * https://msdn.microsoft.com/en-us/library/windows/desktop/dd144871(v=vs.85).aspx
     *
     * @param shouldBeNull A handle to the window whose DC is to be retrieved. If this value is NULL, GetDC retrieves the DC for the entire
     * screen.
     *
     * @return if the function succeeds, the return value is a handle to the DC for the specified window's client area. If the function
     * fails, the return value is NULL.
     */
    public static native
    Pointer GetDC(Pointer shouldBeNull);

    /**
     * https://msdn.microsoft.com/en-us/library/windows/desktop/dd162920(v=vs.85).aspx
     */
    public static native
    void ReleaseDC(Pointer shouldBeNull, Pointer dcHandle);
}
