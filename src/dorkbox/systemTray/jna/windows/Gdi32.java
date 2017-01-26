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
package dorkbox.systemTray.jna.windows;

import com.sun.jna.Pointer;

import dorkbox.systemTray.jna.JnaHelper;

/**
 * bindings for GDI32
 *
 * Direct-mapping, See: https://github.com/java-native-access/jna/blob/master/www/DirectMapping.md
 */
public
class Gdi32 {

    static {
        JnaHelper.register("gdi32", Gdi32.class);
    }

    /**
     * Number of pixels per logical inch along the screen width. In a system with multiple display monitors, this value is the same for
     * all monitors.
     */
    public static final int LOGPIXELSX = 88;


    /**
     * The GetDeviceCaps function retrieves device-specific information for the specified device.
     *
     * https://msdn.microsoft.com/en-us/library/dd144877(v=vs.85).aspx
     *
     * @param handle A handle to the DC.
     * @param nIndex The item to be returned.
     */
    public static native int GetDeviceCaps(Pointer handle, int nIndex);
}
