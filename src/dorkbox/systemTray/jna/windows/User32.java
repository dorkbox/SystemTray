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

import static com.sun.jna.platform.win32.WinDef.HDC;
import static com.sun.jna.platform.win32.WinDef.HICON;
import static com.sun.jna.platform.win32.WinDef.HINSTANCE;
import static com.sun.jna.platform.win32.WinDef.HMENU;
import static com.sun.jna.platform.win32.WinDef.HWND;
import static com.sun.jna.platform.win32.WinDef.LPARAM;
import static com.sun.jna.platform.win32.WinDef.LRESULT;
import static com.sun.jna.platform.win32.WinDef.POINT;
import static com.sun.jna.platform.win32.WinDef.RECT;
import static com.sun.jna.platform.win32.WinDef.WPARAM;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinNT;

import dorkbox.systemTray.jna.windows.structs.ICONINFO;
import dorkbox.systemTray.jna.windows.structs.MENUITEMINFO;
import dorkbox.systemTray.jna.windows.structs.NONCLIENTMETRICS;
import dorkbox.util.OS;

@SuppressWarnings("WeakerAccess")
public
interface User32 {
    User32 IMPL = OS.is64bit() ? new User32_64() : new User32_32();

    int WM_QUIT = 18;

    int SPI_GETNONCLIENTMETRICS = 0x0029;
    int COLOR_MENU = 4;
    int COLOR_MENUTEXT = 7;
    int COLOR_HIGHLIGHTTEXT = 14;
    int COLOR_HIGHLIGHT = 13;
    int COLOR_GRAYTEXT = 17;

    int GWL_WNDPROC = -4;

    int WM_LBUTTONUP = 0x202;
    int WM_RBUTTONUP = 0x205;

    int MF_BYPOSITION = 0x400;

    /**
     * This is overridden by the 64-bit version to be SetWindowLongPtr instead.
     */
    int SetWindowLong(HWND hWnd, int nIndex, Callback procedure);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms647626(v=vs.85).aspx
     */
    HMENU CreatePopupMenu();

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms647616(v=vs.85).aspx
     */
    boolean AppendMenu(HMENU hMenu, int uFlags, int uIDNewItem, String lpNewItem);

    /**
     * https://msdn.microsoft.com/en-us/library/windows/desktop/ms647629(v=vs.85).aspx
     */
    boolean DeleteMenu(HMENU hMenu, int uPosition, int uFlags);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms647631(v=vs.85).aspx
     */
    boolean DestroyMenu(HMENU hMenu);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms648002(v=vs.85).aspx
     */
    boolean TrackPopupMenu(HMENU hMenu, int uFlags, int x, int y, int nReserved, HWND hWnd, RECT prcRect);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms648001(v=vs.85).aspx
     */
    boolean SetMenuItemInfo(HMENU hMenu, int uItem, boolean fByPosition, MENUITEMINFO lpmii);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms647980(v=vs.85).aspx
     */
    boolean GetMenuItemInfo(HMENU hMenu, int uItem, boolean fByPosition, MENUITEMINFO lpmii);

    /**
     * Brings the thread that created the specified window into the foreground
     * and activates the window. Keyboard input is directed to the window, and
     * various visual cues are changed for the user. The system assigns a
     * slightly higher priority to the thread that created the foreground window
     * than it does to other threads.
     *
     * @param hWnd A handle to the window that should be activated and brought to
     * the foreground.
     *
     * @return If the window was brought to the foreground, the return value is
     * nonzero.
     */
    boolean SetForegroundWindow(HWND hWnd);

    /**
     * The GetSystemMetrics function retrieves various system metrics (widths
     * and heights of display elements) and system configuration settings. All
     * dimensions retrieved by GetSystemMetrics are in pixels.
     *
     * @param nIndex System metric or configuration setting to retrieve. This
     * parameter can be one of the following values. Note that all
     * SM_CX* values are widths and all SM_CY* values are heights.
     * Also note that all settings designed to return Boolean data
     * represent TRUE as any nonzero value, and FALSE as a zero
     * value.
     *
     * @return If the function succeeds, the return value is the requested
     * system metric or configuration setting. If the function fails,
     * the return value is zero. GetLastError does not provide extended
     * error information.
     */
    int GetSystemMetrics(int nIndex);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms633500(v=vs.85).aspx
     */
    HWND FindWindowEx(HWND hwndParent, HWND hwndChildAfter, String lpszClass, String lpszWindow);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms644950(v=vs.85).aspx
     */
    LRESULT SendMessage(HWND hWnd, int Msg, WPARAM wParam, LPARAM lParam);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms648062(v=vs.85).aspx
     */
    HICON CreateIconIndirect(ICONINFO piconinfo);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms632682(v=vs.85).aspx
     */
    boolean DestroyWindow(HWND hWnd);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms724947(v=vs.85).aspx
     */
    boolean SystemParametersInfo(int uiAction, int uiParam, NONCLIENTMETRICS pvParam, int fWinIni);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms724371(v=vs.85).aspx
     */
    COLORREF GetSysColor(int nIndex);

    /**
     * This function places a message in the message queue associated with the
     * thread that created the specified window and then returns without waiting
     * for the thread to process the message. Messages in a message queue are
     * retrieved by calls to the GetMessage or PeekMessage function.
     *
     * @param hWnd Handle to the window whose window procedure is to receive the
     * message.
     * @param msg Specifies the message to be posted.
     * @param wParam Specifies additional message-specific information.
     * @param lParam Specifies additional message-specific information.
     */
    void PostMessage(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);

    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms632680(v=vs.85).aspx
     */
    HWND CreateWindowEx(int dwExStyle,
                        String lpClassName,
                        String lpWindowName,
                        int dwStyle,
                        int x,
                        int y,
                        int nWidth,
                        int nHeight,
                        HWND hWndParent,
                        HMENU hMenu,
                        HINSTANCE hInstance,
                        WinNT.HANDLE lpParam);


    /**
     * http://msdn.microsoft.com/en-us/library/windows/desktop/ms644947(v=vs.85).aspx
     */
    LRESULT DefWindowProc(HWND hWnd, int Msg, WPARAM wParam, LPARAM lParam);

    boolean GetMessage(MSG lpMsg, Pointer hWnd, int wMsgFilterMin, int wMsgFilterMax);

    boolean TranslateMessage(MSG lpMsg);

    boolean DispatchMessage(MSG lpMsg);

    int RegisterWindowMessage(WString lpString);

    /**
     * This function retrieves a handle to a display device context (DC) for the
     * client area of the specified window. The display device context can be
     * used in subsequent graphics display interface (GDI) functions to draw in
     * the client area of the window.
     *
     * @param hWnd Handle to the window whose device context is to be retrieved.
     * If this value is NULL, GetDC retrieves the device context for
     * the entire screen.
     *
     * @return The handle the device context for the specified window's client
     * area indicates success. NULL indicates failure. To get extended
     * error information, call GetLastError.
     */
    HDC GetDC(HWND hWnd);

    /**
     * This function releases a device context (DC), freeing it for use by other
     * applications. The effect of ReleaseDC depends on the type of device
     * context.
     *
     * @param hWnd Handle to the window whose device context is to be released.
     * @param hDC Handle to the device context to be released.
     *
     * @return The return value specifies whether the device context is
     * released. 1 indicates that the device context is released. Zero
     * indicates that the device context is not released.
     */
    int ReleaseDC(HWND hWnd, HDC hDC);

    boolean GetCursorPos(POINT point);
}
