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

import static com.sun.jna.platform.win32.WinDef.HMENU;
import static com.sun.jna.platform.win32.WinDef.HWND;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

import dorkbox.systemTray.jna.windows.structs.ICONINFO;
import dorkbox.systemTray.jna.windows.structs.MENUITEMINFO;
import dorkbox.systemTray.jna.windows.structs.NONCLIENTMETRICS;

/**
 * On first glance, this appears to be unnecessary to have a DirectMapping class implement an interface - however this is so different
 * methods can be overridden by the correct 64bit versions, otherwise multiple copies of this library would have to be loaded (one for
 * "normal", and another for the "special case").
 *
 * Doing it this way greatly simplifies the API while maintaining Direct Mapping, at the cost of a slightly more complex code hierarchy.
 */
public
class User32_64 implements User32 {
    static {
        Native.register(NativeLibrary.getInstance("user32", W32APIOptions.DEFAULT_OPTIONS));
    }

    @Override
    public
    int SetWindowLong(HWND hWnd, int nIndex, Callback procedure) {
        return SetWindowLongPtr(hWnd, nIndex, procedure);
    }

    // should be used instead of SetWindowLong for 64 versions
    public native
    int SetWindowLongPtr(HWND hWnd, int nIndex, Callback procedure);

    @Override
    public native
    HMENU CreatePopupMenu();

    @Override
    public native
    boolean AppendMenu(final HMENU hMenu, final int uFlags, final int uIDNewItem, final String lpNewItem);

    @Override
    public native
    boolean DeleteMenu(final HMENU hMenu, final int uPosition, final int uFlags);

    @Override
    public native
    boolean DestroyMenu(final HMENU hMenu);

    @Override
    public native
    boolean TrackPopupMenu(final HMENU hMenu,
                           final int uFlags,
                           final int x,
                           final int y,
                           final int nReserved,
                           final HWND hWnd,
                           final WinDef.RECT prcRect);

    @Override
    public native
    boolean SetMenuItemInfo(final HMENU hMenu, final int uItem, final boolean fByPosition, final MENUITEMINFO lpmii);

    @Override
    public native
    boolean GetMenuItemInfo(final HMENU hMenu, final int uItem, final boolean fByPosition, final MENUITEMINFO lpmii);

    @Override
    public native
    boolean SetForegroundWindow(final HWND hWnd);

    @Override
    public native
    int GetSystemMetrics(final int nIndex);

    @Override
    public native
    HWND FindWindowEx(final HWND hwndParent, final HWND hwndChildAfter, final String lpszClass, final String lpszWindow);

    @Override
    public native
    WinDef.LRESULT SendMessage(final HWND hWnd, final int Msg, final WinDef.WPARAM wParam, final WinDef.LPARAM lParam);

    @Override
    public native
    WinDef.HICON CreateIconIndirect(final ICONINFO piconinfo);

    @Override
    public native
    boolean DestroyWindow(final HWND hWnd);

    @Override
    public native
    boolean SystemParametersInfo(final int uiAction, final int uiParam, final NONCLIENTMETRICS pvParam, final int fWinIni);

    @Override
    public native
    COLORREF GetSysColor(final int nIndex);

    @Override
    public native
    void PostMessage(final HWND hWnd, final int msg, final WinDef.WPARAM wParam, final WinDef.LPARAM lParam);

    @Override
    public native
    HWND CreateWindowEx(final int dwExStyle,
                        final String lpClassName,
                        final String lpWindowName,
                        final int dwStyle,
                        final int x,
                        final int y,
                        final int nWidth,
                        final int nHeight,
                        final HWND hWndParent,
                        final HMENU hMenu,
                        final WinDef.HINSTANCE hInstance,
                        final WinNT.HANDLE lpParam);

    @Override
    public native
    WinDef.LRESULT DefWindowProc(final HWND hWnd, final int Msg, final WinDef.WPARAM wParam, final WinDef.LPARAM lParam);

    @Override
    public native
    boolean GetMessage(final MSG lpMsg, final Pointer hWnd, final int wMsgFilterMin, final int wMsgFilterMax);

    @Override
    public native
    boolean TranslateMessage(final MSG lpMsg);

    @Override
    public native
    boolean DispatchMessage(final MSG lpMsg);

    @Override
    public native
    int RegisterWindowMessage(final WString lpString);

    @Override
    public native
    WinDef.HDC GetDC(final HWND hWnd);

    @Override
    public native
    int ReleaseDC(final HWND hWnd, final WinDef.HDC hDC);

    @Override
    public native
    boolean GetCursorPos(final WinDef.POINT point);
}
