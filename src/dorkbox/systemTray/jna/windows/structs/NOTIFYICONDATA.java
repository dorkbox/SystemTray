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

import static com.sun.jna.platform.win32.WinDef.HWND;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef;

/**
 * http://msdn.microsoft.com/en-us/library/windows/desktop/bb773352(v=vs.85).aspx
 */
public class NOTIFYICONDATA extends Structure {
    static public final int NIF_MESSAGE = 0x1;
    static public final int NIF_ICON = 0x2;
    static public final int NIF_TIP = 0x4;
    static public final int NIF_STATE = 0x8;

    static public final int NIF_INFO = 0x10;

    static public final int NIIF_NONE = 0x0;
    static public final int NIIF_INFO = 0x1;
    static public final int NIIF_WARNING = 0x2;
    static public final int NIIF_ERROR = 0x3;
    static public final int NIIF_USER = 0x4;

    public int cbSize;
    public HWND hWnd;
    public int uID;
    public int uFlags;
    public int uCallbackMessage;
    public WinDef.HICON hIcon;

    public char[] szTip = new char[128];

    public int dwState;
    public int dwStateMask;

    public char[] szInfo = new char[256];
    public int uTimeoutOrVersion; // {UINT uTimeout; UINT uVersion;};

    public char[] szInfoTitle = new char[64];
    public int dwInfoFlags;

    public
    NOTIFYICONDATA() {
        cbSize = size();
    }

    public
    void setTooltip(String s) {
        uFlags |= NIF_TIP;

        System.arraycopy(s.toCharArray(), 0, szTip, 0, Math.min(s.length(), szTip.length));
        szTip[s.length()] = '\0';
    }

    public
    void setBalloon(String title, String message, int millis, int niif) {
        uFlags |= NIF_INFO;

        System.arraycopy(message.toCharArray(), 0, szInfo, 0, Math.min(message.length(), szInfo.length));
        szInfo[message.length()] = '\0';

        uTimeoutOrVersion = millis;

        System.arraycopy(title.toCharArray(), 0, szInfoTitle, 0, Math.min(title.length(), szInfoTitle.length));
        szInfoTitle[title.length()] = '\0';

        dwInfoFlags = niif;
    }

    public
    void setIcon(WinDef.HICON hIcon) {
        uFlags |= NIF_ICON;
        this.hIcon = hIcon;
    }

    public void setCallback(int callback) {
        uFlags |= NIF_MESSAGE;
        uCallbackMessage = callback;
    }

    @Override
    protected List<String> getFieldOrder () {
        return Arrays.asList("cbSize",
                             "hWnd",
                             "uID",
                             "uFlags",
                             "uCallbackMessage",
                             "hIcon",
                             "szTip",
                             "dwState",
                             "dwStateMask",
                             "szInfo",
                             "uTimeoutOrVersion",
                             "szInfoTitle",
                             "dwInfoFlags");
    }
}
