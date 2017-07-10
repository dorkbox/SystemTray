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
package dorkbox.systemTray.jna.windows.structs;

import static com.sun.jna.platform.win32.WinDef.DWORD;
import static com.sun.jna.platform.win32.WinDef.HBITMAP;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;

/**
 * http://msdn.microsoft.com/en-us/library/windows/desktop/ms648052(v=vs.85).aspx
 */
public
class ICONINFO extends Structure {
    public boolean IsIcon;
    public DWORD xHotspot;
    public DWORD yHotspot;
    public HBITMAP MaskBitmap;
    public HBITMAP ColorBitmap;


    public
    ICONINFO() {
    }

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("IsIcon", "xHotspot", "yHotspot", "MaskBitmap", "ColorBitmap");
    }

    public static
    class ByValue extends ICONINFO implements Structure.ByValue {}
}
