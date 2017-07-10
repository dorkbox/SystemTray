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

import static com.sun.jna.platform.win32.WinDef.HWND;
import static com.sun.jna.platform.win32.WinDef.LPARAM;
import static com.sun.jna.platform.win32.WinDef.LRESULT;
import static com.sun.jna.platform.win32.WinDef.WPARAM;
import static com.sun.jna.platform.win32.WinUser.WM_USER;
import static dorkbox.systemTray.jna.windows.User32.GWL_WNDPROC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.jna.WString;

import dorkbox.systemTray.SystemTray;

@SuppressWarnings({"Convert2Lambda", "UnusedAssignment", "Convert2Diamond", "FieldCanBeLocal"})
public
class WindowsEventDispatch implements Runnable {
    private static final String NAME = "WindowsEventDispatch";


    public static final int WM_TASKBARCREATED = User32.IMPL.RegisterWindowMessage(new WString("TaskbarCreated"));
    public static final int WM_COMMAND = 0x0111;
    public static final int WM_SHELLNOTIFY = WM_USER + 1;
    public static final int WM_MEASUREITEM = 44;
    public static final int WM_DRAWITEM = 43;

    public static final int MF_POPUP = 0x00000010;


    private static final WindowsEventDispatch edt = new WindowsEventDispatch();
    private final static Map<Integer, List<Listener>> messageIDs = new HashMap<Integer, List<Listener>>();

    private static final Object lock = new Object();
    private static int referenceCount = 0;


    private Thread dispatchThread;

    // keep these around to prevent GC
    private WNDPROC WndProc;

    // used to dispatch messages
    private volatile HWND hWnd;


    private
    WindowsEventDispatch() {
    }

    public static
    void start() {
        synchronized (lock) {
            int ref = referenceCount++;

            if (ref == 0) {
                edt.start_();
            }

            try {
                // wait for the dispatch thread to start
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static
    void stop() {
        synchronized (lock) {
            if (--referenceCount == 0) {
                edt.stop_();
            }
        }
    }

    public static
    HWND get() {
        return edt.hWnd;
    }

    // always from inside lock!
    private
    void start_() {
        dispatchThread = new Thread(this, NAME);
        dispatchThread.start();
    }

    // always from inside lock!
    private void
    stop_() {
        WPARAM wparam = new WPARAM(0);
        LPARAM lparam = new LPARAM(0);
        User32.IMPL.SendMessage(hWnd, User32.WM_QUIT, wparam, lparam);

        try {
            // wait for the dispatch thread to quit (but only if we are not on the dispatch thread)
            if (!Thread.currentThread().equals(dispatchThread)) {
                dispatchThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("Java8MapApi")
    public static
    void addListener(final int messageId, final Listener listener) {
        synchronized (messageIDs) {
            List<Listener> listeners = messageIDs.get(messageId);
            if (listeners == null) {
                listeners = new ArrayList<Listener>();
                messageIDs.put(messageId, listeners);
            }

            listeners.add(listener);
        }
    }
    public static
    void removeListener(final Listener listener) {
        synchronized (messageIDs) {
            for (Map.Entry<Integer, List<Listener>> entry : messageIDs.entrySet()) {
                List<Listener> value = entry.getValue();
                if (value.remove(listener)) {
                    return;
                }
            }
        }
    }

    @Override
    public
    void run() {
        WndProc = new WNDPROC() {
            @Override
            public
            LRESULT callback(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam) {
                List<Listener> listeners = null;
                synchronized (messageIDs) {
                    listeners = messageIDs.get(msg);
                    if (listeners != null) {
                        // make a copy, in case a listener action modifies the message listener
                        listeners = new ArrayList<Listener>(listeners);
                    }
                }

                if (listeners != null) {
                    for (final Listener listener : listeners) {
                        if (listener != null) {
                            try {
                                listener.run(hWnd, wParam, lParam);
                            } catch (Exception e) {
                                SystemTray.logger.error("Error executing listener.", e);
                            }
                        }
                    }
                }

                return User32.IMPL.DefWindowProc(hWnd, msg, wParam, lParam);
            }
        };

        hWnd = User32.IMPL.CreateWindowEx(0, "STATIC", NAME, 0, 0, 0, 0, 0, null, null, null,
                              null);
        if (hWnd == null) {
            throw new GetLastErrorException();
        }

        User32.IMPL.SetWindowLong(hWnd, GWL_WNDPROC, WndProc);

        synchronized (lock) {
            lock.notifyAll();
        }

        MSG msg = new MSG();
        while (User32.IMPL.GetMessage(msg, null, 0, 0)) {
            User32.IMPL.TranslateMessage(msg);
            User32.IMPL.DispatchMessage(msg);
        }

        if (hWnd != null) {
            if (!User32.IMPL.DestroyWindow(hWnd)) {
                throw new GetLastErrorException();
            }
            hWnd = null;
        }
    }
}
