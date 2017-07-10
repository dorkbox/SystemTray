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

import static com.sun.jna.platform.win32.WinDef.HDC;
import static com.sun.jna.platform.win32.WinDef.HWND;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinDef;

/**
 * http://msdn.microsoft.com/en-us/library/windows/desktop/bb775802(v=vs.85).aspx
 */
public class DRAWITEMSTRUCT extends Structure {

    public static class ByValue extends DRAWITEMSTRUCT implements Structure.ByValue {
    }

    public static class ByReference extends DRAWITEMSTRUCT implements Structure.ByReference {
    }

    public static final int ODT_BUTTON = 4;
    public static final int ODT_COMBOBOX = 3;
    public static final int ODT_LISTBOX = 2;
    public static final int ODT_LISTVIEW = 102;
    public static final int ODT_MENU = 1;
    public static final int ODT_STATIC = 5;
    public static final int ODT_TAB = 101;

    public static final int ODS_SELECTED = 1;

    public int CtlType;
    public int CtlID;
    public int itemID;
    public int itemAction;
    public int itemState;
    public HWND hwndItem;
    public HDC hDC;
    public WinDef.RECT rcItem;
    public BaseTSD.ULONG_PTR itemData;

    public DRAWITEMSTRUCT() {
    }

    public DRAWITEMSTRUCT(Pointer p) {
        super(p);

        read();
    }

    @Override
    protected
    List<String> getFieldOrder() {
        return Arrays.asList("CtlType", "CtlID", "itemID", "itemAction", "itemState", "hwndItem", "hDC", "rcItem", "itemData");
    }
}
