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

import static com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import static com.sun.jna.platform.win32.WinDef.HBITMAP;
import static com.sun.jna.platform.win32.WinDef.HMENU;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * http://msdn.microsoft.com/en-us/library/windows/desktop/ms647578(v=vs.85).aspx
 */
public
class MENUITEMINFO extends Structure {

    public static final int MFS_ENABLED  = 0x00000000;
    public static final int MFS_DISABLED = 0x00000003;

    public static final int MFS_GRAYED   = 0x00000003;
    public static final int MFS_DEFAULT  = 0x00001000;

    public static final int MFS_CHECKED  = 0x00000008;
    public static final int MFS_UNCHECKED= 0x00000000;

    public static final int MFS_HILITE   = 0x00000080;
    public static final int MFS_UNHILITE = 0x00000000;

    public static final int MIIM_DATA    = 0x00000020;


    public int cbSize;
    public int fMask;
    public int fType;
    public int fState;
    public int wID;

    public HMENU hSubMenu;
    public HBITMAP hbmpChecked;
    public HBITMAP hbmpUnchecked;
    public ULONG_PTR dwItemData;
    public String dwTypeData;
    public int cch;
    public HBITMAP hbmpItem;

    public
    MENUITEMINFO() {
        cbSize = size();
    }

    public
    MENUITEMINFO(Pointer p) {
        super(p);
    }

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("cbSize",
                             "fMask",
                             "fType",
                             "fState",
                             "wID",
                             "hSubMenu",
                             "hbmpChecked",
                             "hbmpUnchecked",
                             "dwItemData",
                             "dwTypeData",
                             "cch",
                             "hbmpItem");
    }
}
