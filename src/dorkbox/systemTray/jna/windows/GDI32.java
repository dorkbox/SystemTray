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

import static com.sun.jna.platform.win32.WinDef.HBITMAP;
import static com.sun.jna.platform.win32.WinDef.HDC;
import static com.sun.jna.platform.win32.WinDef.HFONT;
import static com.sun.jna.platform.win32.WinDef.RECT;
import static com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import static com.sun.jna.platform.win32.WinNT.HANDLE;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.W32APIOptions;

import dorkbox.systemTray.jna.windows.structs.LOGFONT;

public
class GDI32 {
    static {
        Native.register(NativeLibrary.getInstance("GDI32", W32APIOptions.DEFAULT_OPTIONS));
    }

    public static final int ETO_OPAQUE = 2;
    public static final int SRCCOPY = 0xCC0020;

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/dd144938(v=vs.85).aspx
     */
    public static native
    boolean GetTextExtentPoint32(HDC hdc, String lpString, int c, WinUser.SIZE lpSize);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/dd145093(v=vs.85).aspx
     */
    public static native
    COLORREF SetTextColor(HDC hdc, COLORREF crColor);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/dd162964(v=vs.85).aspx
     */
    public static native
    COLORREF SetBkColor(HDC hdc, COLORREF crColor);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/dd162713(v=vs.85).aspx
     */
    public static native
    boolean ExtTextOut(HDC hdc, int X, int Y, int fuOptions, RECT lprc, String lpString, int cbCount, int[] lpDx);

    /**
     * https://msdn.microsoft.com/ru-ru/library/windows/desktop/dd183500(v=vs.85).aspx
     */
    public static native
    HFONT CreateFontIndirect(LOGFONT l);

    /**
     * The SelectObject function selects an object into the specified device context (DC).
     * The new object replaces the previous object of the same type.
     *
     * @param hDC Handle to the DC.
     * @param hGDIObj Handle to the object to be selected.
     *
     * @return If the selected object is not a region and the function succeeds, the return value
     * is a handle to the object being replaced. If the selected object is a region and the
     * function succeeds, the return value is one of the REGION values.
     */
    public static native
    HANDLE SelectObject(HDC hDC, HANDLE hGDIObj);

    /**
     * The CreateCompatibleDC function creates a memory device context (DC) compatible with the specified device.
     *
     * @param hDC Handle to an existing DC. If this handle is NULL, the function creates a memory DC compatible with the
     * application's current screen.
     *
     * @return If the function succeeds, the return value is the handle to a memory DC.
     * If the function fails, the return value is NULL.
     * To get extended error information, call GetLastError.
     */
    public static native
    HDC CreateCompatibleDC(HDC hDC);

    /**
     * The DeleteDC function deletes the specified device context (DC).
     *
     * @param hDC Handle to the device context.
     *
     * @return If the function succeeds, the return value is nonzero.
     * If the function fails, the return value is zero.
     * To get extended error information, call GetLastError.
     */
    public static native
    boolean DeleteDC(HDC hDC);

    /**
     * The DeleteObject function deletes a logical pen, brush, font, bitmap, region, or palette,
     * freeing all system resources associated with the object. After the object is deleted, the
     * specified handle is no longer valid.
     *
     * @param hObject Handle to a logical pen, brush, font, bitmap, region, or palette.
     *
     * @return If the function succeeds, the return value is nonzero.
     * If the specified handle is not valid or is currently selected into a DC, the return value is zero.
     * To get extended error information, call GetLastError.
     */
    public static native
    boolean DeleteObject(HANDLE hObject);

    /**
     * The CreateDIBSection function creates a DIB that applications can write to directly.
     * The function gives you a pointer to the location of the bitmap bit values. You can supply
     * a handle to a file-mapping object that the function will use to create the bitmap, or you
     * can let the system allocate the memory for the bitmap.
     *
     * @param hDC Handle to a device context. If the value of iUsage is DIB_PAL_COLORS, the function uses this
     * device context's logical palette to initialize the DIB colors.
     * @param pbmi Pointer to a BITMAPINFO structure that specifies various attributes of the DIB, including
     * the bitmap dimensions and colors.
     * @param iUsage Specifies the type of data contained in the bmiColors array member of the BITMAPINFO structure
     * pointed to by pbmi (either logical palette indexes or literal RGB values).
     * @param ppvBits Pointer to a variable that receives a pointer to the location of the DIB bit values.
     * @param hSection Handle to a file-mapping object that the function will use to create the DIB. This parameter can be NULL.
     * @param dwOffset Specifies the offset from the beginning of the file-mapping object referenced by hSection where storage
     * for the bitmap bit values is to begin.
     *
     * @return Specifies the offset from the beginning of the file-mapping object referenced by hSection where storage
     * for the bitmap bit values is to begin.
     */
    public static native
    HBITMAP CreateDIBSection(HDC hDC, BITMAPINFO pbmi, int iUsage, PointerByReference ppvBits, Pointer hSection, int dwOffset);
}
