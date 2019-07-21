/*
 * Copyright 2018 dorkbox, llc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package dorkbox.systemTray.ui.swing;

import static com.sun.jna.platform.win32.WinDef.HWND;
import static com.sun.jna.platform.win32.WinDef.LPARAM;
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

import java.awt.Point;
import java.io.File;

import javax.swing.ImageIcon;

import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinDef.POINT;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.util.ImageUtil;
import dorkbox.util.SwingUtil;
import dorkbox.util.jna.windows.HBITMAPWrap;
import dorkbox.util.jna.windows.HICONWrap;
import dorkbox.util.jna.windows.Listener;
import dorkbox.util.jna.windows.Shell32;
import dorkbox.util.jna.windows.User32;
import dorkbox.util.jna.windows.WindowsEventDispatch;
import dorkbox.util.jna.windows.structs.NOTIFYICONDATA;


/**
 * Native implementation of a System tray on Windows with a Swing menu (the native menu is terrible)
 */
public
class _WindowsNativeTray extends Tray {
    private final Listener quitListener;
    private final Listener menuListener;
    private final Listener showListener;
    private volatile TrayPopup popupMenu;
    // is the system tray visible or not.
    private volatile boolean visible = true;
    private volatile File imageFile;
    private volatile HICONWrap imageIcon;
    private volatile String tooltipText = "";

    public
    _WindowsNativeTray(final SystemTray systemTray) {
        super(systemTray);

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        final SwingMenu swingMenu = new SwingMenu(systemTray) {
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
                if (imageIcon != null) {
                    nid.setIcon(imageIcon);
                }

                if (!Shell32.Shell_NotifyIcon(NIM_MODIFY, nid)) {
                    SystemTray.logger.error("Error setting the image for the tray. {}", Kernel32Util.getLastErrorMessage());
                }

                // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                // want to make sure keep the tooltip text the same as before.
                setTooltip_(tooltipText);

                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        if (popupMenu == null) {
                            TrayPopup popupMenu = (TrayPopup) _native;
                            popupMenu.pack();
                            popupMenu.setFocusable(true);
                            _WindowsNativeTray.this.popupMenu = popupMenu;
                        }

                        popupMenu.setTitleBarImage(imageFile);
                    }
                });
            }

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
            void setTooltip(final MenuItem menuItem) {
                final String text = menuItem.getTooltip();

                setTooltip_(text);
            }

            @Override
            public
            void remove() {
                hide();

                super.remove();

                User32.User32.PostMessage(WindowsEventDispatch.get(), WM_QUIT, new WPARAM(0), new LPARAM(0));
            }
        };

        // will wait until it's started up.
        WindowsEventDispatch.start();

        HWND hWnd = WindowsEventDispatch.get();
        if (hWnd == null) {
            throw new RuntimeException("The Windows System Tray is not supported! Please write an issue and include your OS type and configuration");
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
                    case WM_RBUTTONUP:
                        if (popupMenu != null && User32.User32.GetCursorPos(mousePosition)) {
                            Point point = new Point(mousePosition.x, mousePosition.y);
                            popupMenu.doShow(point, 0);
                        }
                        break;

                    default:
                        break;
                }
            }
        };

        WindowsEventDispatch.addListener(WM_TASKBARCREATED, showListener);
        WindowsEventDispatch.addListener(WM_QUIT, quitListener);
        WindowsEventDispatch.addListener(WM_SHELLNOTIFY, menuListener);

        show();

        bind(swingMenu, null, systemTray);
    }

    private
    void setTooltip_(final String text) {
        if (tooltipText != null && tooltipText.equals(text)) {
            return;
        }

        tooltipText = text;

        NOTIFYICONDATA nid = new NOTIFYICONDATA();
        nid.hWnd = WindowsEventDispatch.get();

        if (text != null) {
            nid.setTooltip(text);
        }

        Shell_NotifyIcon(NIM_MODIFY, nid);
    }

    private
    void hide() {
        if (imageIcon != null) {
            imageIcon.close();
            imageIcon = null;
        }

        if (visible) {
            NOTIFYICONDATA nid = new NOTIFYICONDATA();
            nid.hWnd = WindowsEventDispatch.get();

            if (!Shell32.Shell_NotifyIcon(NIM_DELETE, nid)) {
                SystemTray.logger.error("Error hiding tray. {}", Kernel32Util.getLastErrorMessage());
            }
            visible = false;
        }
    }

    private
    void show() {
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
            SystemTray.logger.error("Error showing tray. {}", Kernel32Util.getLastErrorMessage());
        }
        visible = true;
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

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
