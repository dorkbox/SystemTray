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
package dorkbox.systemTray.nativeUI;

import static com.sun.jna.platform.win32.WinDef.HWND;
import static com.sun.jna.platform.win32.WinDef.LPARAM;
import static com.sun.jna.platform.win32.WinDef.POINT;
import static com.sun.jna.platform.win32.WinDef.WPARAM;
import static com.sun.jna.platform.win32.WinUser.WM_QUIT;
import static dorkbox.util.jna.windows.Shell32.NIM_ADD;
import static dorkbox.util.jna.windows.Shell32.NIM_DELETE;
import static dorkbox.util.jna.windows.Shell32.NIM_MODIFY;
import static dorkbox.util.jna.windows.Shell32.Shell_NotifyIcon;
import static dorkbox.util.jna.windows.User32.WM_LBUTTONUP;
import static dorkbox.util.jna.windows.User32.WM_RBUTTONUP;
import static dorkbox.util.jna.windows.WindowsEventDispatch.WM_SHELLNOTIFY;
import static dorkbox.util.jna.windows.WindowsEventDispatch.WM_TASKBARCREATED;

import java.io.File;

import javax.swing.ImageIcon;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.util.ImageUtil;
import dorkbox.util.jna.windows.HBITMAPWrap;
import dorkbox.util.jna.windows.HICONWrap;
import dorkbox.util.jna.windows.Kernel32;
import dorkbox.util.jna.windows.Listener;
import dorkbox.util.jna.windows.Shell32;
import dorkbox.util.jna.windows.User32;
import dorkbox.util.jna.windows.WindowsEventDispatch;
import dorkbox.util.jna.windows.structs.NOTIFYICONDATA;


/**
 * Native implementation of a System tray on Windows.
 */
class _WindowsNativeTray extends Tray implements NativeUI {
    private final Listener quitListener;
    private final Listener menuListener;

    // is the system tray visible or not.
    private volatile boolean visible = true;

    private volatile File imageFile;
    private volatile HICONWrap imageIcon;

    private volatile String tooltipText = "";
    private final Listener showListener;

    private _WindowsNativeTray(final dorkbox.systemTray.SystemTray systemTray) {
        super();

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.

        // this should be a swing menu, not a "native" menu, which will not be themed to match the OS style. It looks
        // marginally better than AWT. This was verified via a completely native menu system (not the AWT menu system).
        // The menu is currently AWT to allow this class to compile
        final AwtMenu windowsMenu = new AwtMenu(null) {
            @Override
            public
            void setEnabled(final MenuItem menuItem) {
                boolean enabled = menuItem.getEnabled();

                if (visible && !enabled) {
                    // hide
                    hide();
                }
                else if (!visible && enabled) {
                    // show
                    show();
                }
            }

            @Override
            public
            void setImage(final MenuItem menuItem) {
                imageFile = menuItem.getImage();

                if (imageIcon != null) {
                    imageIcon.close();
                }
                imageIcon = convertImage(imageFile);

                NOTIFYICONDATA nid = new NOTIFYICONDATA();
                nid.hWnd = WindowsEventDispatch.get();
                nid.setIcon(imageIcon);

                if (!Shell32.Shell_NotifyIcon(NIM_MODIFY, nid)) {
                    SystemTray.logger.error("Error setting the image for the tray. {}", Kernel32.getLastErrorMessage());
                }
            }

            @Override
            public
            void setText(final MenuItem menuItem) {
                // no op.
            }

            @Override
            public
            void setShortcut(final MenuItem menuItem) {
                // no op
            }

            @Override
            public
            void remove() {
                hide();

                super.remove();

                User32.IMPL.PostMessage(WindowsEventDispatch.get(), WM_QUIT, new WPARAM(0), new LPARAM(0));
            }
        };

        // will wait until it's started up.
        WindowsEventDispatch.start();

        HWND hWnd = WindowsEventDispatch.get();
        if (hWnd == null) {
            throw new RuntimeException("The Windows System Tray is not supported! Please write an issue and include your OS type and " +
                                       "configuration");
        }

        showListener = new Listener() {
            @Override
            public
            void run(final HWND hWnd, final WPARAM wParam, final LPARAM lParam) {
                show();
            }
        };
        quitListener = new Listener() {
            @Override
            public
            void run(final HWND hWnd, final WPARAM wParam, final LPARAM lParam) {
                WindowsEventDispatch.stop();
                WindowsEventDispatch.removeListener(showListener);
                WindowsEventDispatch.removeListener(quitListener);
                WindowsEventDispatch.removeListener(menuListener);
            }
        };


        menuListener = new Listener() {
            final POINT mousePosition = new POINT();

            @Override
            public
            void run(final HWND hWnd, final WPARAM wParam, final LPARAM lParam) {
                int lp = lParam.intValue();
                switch (lp) {
                    case WM_LBUTTONUP:
                        if (User32.IMPL.GetCursorPos(mousePosition)) {
                            // this should be a swing menu, not a "native" menu, which will not be themed to match the OS style. It looks
                            // marginally better than AWT. This was verified via a completely native menu system (not the AWT menu system).
                            // The menu is currently AWT to allow this class to compile
                            // windowsMenu.showContextMenu(mousePosition);
                        }
                        break;
                    case WM_RBUTTONUP:
                        if (User32.IMPL.GetCursorPos(mousePosition)) {
                            // this should be a swing menu, not a "native" menu, which will not be themed to match the OS style. It looks
                            // marginally better than AWT. This was verified via a completely native menu system (not the AWT menu system).
                            // The menu is currently AWT to allow this class to compile
                            // windowsMenu.showContextMenu(mousePosition);
                        }
                        break;
                }
            }
        };

        WindowsEventDispatch.addListener(WM_TASKBARCREATED, showListener);
        WindowsEventDispatch.addListener(WM_QUIT, quitListener);
        WindowsEventDispatch.addListener(WM_SHELLNOTIFY, menuListener);

        show();

        bind(windowsMenu, null, systemTray);
    }

//    public synchronized void balloon (String title, String message, int millis) {
//        balloonNotifyIconData.hWnd = this.windowNotifyIconData.hWnd;
//        balloonNotifyIconData.uID = this.windowNotifyIconData.uID;
//        balloonNotifyIconData.setBalloon(title, message, millis, NIIF_NONE);
//        Shell_NotifyIcon(NIM_MODIFY, balloonNotifyIconData);
//    }


    private void hide() {
        if (imageIcon != null) {
            imageIcon.close();
            imageIcon = null;
        }

        NOTIFYICONDATA nid = new NOTIFYICONDATA();
        nid.hWnd = WindowsEventDispatch.get();

        if (!Shell32.Shell_NotifyIcon(NIM_DELETE, nid)) {
            SystemTray.logger.error("Error hiding tray. {}", Kernel32.getLastErrorMessage());
        }
        visible = false;
    }

    private void show() {
        if (imageIcon != null) {
            imageIcon.close();
        }
        imageIcon = convertImage(imageFile);

        NOTIFYICONDATA nid = new NOTIFYICONDATA();
        nid.hWnd = WindowsEventDispatch.get();
        nid.setTooltip(tooltipText);
        nid.setIcon(imageIcon);
        nid.setCallback(WM_SHELLNOTIFY);

        if (!Shell_NotifyIcon(NIM_ADD, nid)) {
            SystemTray.logger.error("Error showing tray. {}", Kernel32.getLastErrorMessage());
        }
        visible = true;
    }

    @Override
    protected
    void setTooltip_(final String tooltipText) {
        if (this.tooltipText.equals(tooltipText)){
            return;
        }
        this.tooltipText = tooltipText;

        NOTIFYICONDATA nid = new NOTIFYICONDATA();
        nid.hWnd = WindowsEventDispatch.get();
        nid.setTooltip(tooltipText);

        Shell_NotifyIcon(NIM_MODIFY, nid);
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }

    private static
    HICONWrap convertImage(final File imageFile) {
        if (imageFile != null) {
            ImageIcon imageIcon = new ImageIcon(imageFile.getAbsolutePath());
            // fully loads the image and returns when it's done loading the image
            imageIcon = new ImageIcon(imageIcon.getImage());

            HBITMAPWrap hbitmapTrayIcon = new HBITMAPWrap(ImageUtil.getBufferedImage(imageIcon));
            return new HICONWrap(hbitmapTrayIcon);
        }

        return null;
    }
}
