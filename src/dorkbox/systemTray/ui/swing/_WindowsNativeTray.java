/*
 * Copyright 2021 dorkbox, llc.
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
import static dorkbox.jna.windows.Shell32.NIM_ADD;
import static dorkbox.jna.windows.Shell32.NIM_DELETE;
import static dorkbox.jna.windows.Shell32.NIM_MODIFY;
import static dorkbox.jna.windows.Shell32.Shell_NotifyIcon;
import static dorkbox.jna.windows.User32.WM_LBUTTONUP;
import static dorkbox.jna.windows.User32.WM_RBUTTONUP;
import static dorkbox.jna.windows.WindowsEventDispatch.WM_SHELLNOTIFY;
import static dorkbox.jna.windows.WindowsEventDispatch.WM_TASKBARCREATED;

import java.awt.Point;
import java.io.File;

import javax.swing.ImageIcon;

import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinDef.POINT;

import dorkbox.jna.windows.HBITMAPWrap;
import dorkbox.jna.windows.HICONWrap;
import dorkbox.jna.windows.Listener;
import dorkbox.jna.windows.Shell32;
import dorkbox.jna.windows.User32;
import dorkbox.jna.windows.WindowsEventDispatch;
import dorkbox.jna.windows.structs.NOTIFYICONDATA;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.systemTray.util.SizeAndScalingUtil;
import dorkbox.util.ImageUtil;
import dorkbox.util.SwingUtil;
import dorkbox.util.collections.ArrayMap;


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
    private volatile String tooltipText = "";

    private final WindowsEventDispatch edt;

    // The image resources are cached, so that if someone is trying to create an animation, the image resource is re-used instead of
    // constantly created/destroyed -- which over time leads to issues.
    // This cache isn't anything fancy, it just lets us reuse what we have. It's cleared on hide(), and it will auto-grow as necessary.
    // If someone uses a different file every time, then this will cause problems. An error log is added if a different image is created 100x
    private final ArrayMap<File, HICONWrap> imageCache = new ArrayMap<>(false, 10);

    @SuppressWarnings("unused")
    public
    _WindowsNativeTray(final String trayName, final ImageResizeUtil imageResizeUtil, final Runnable onRemoveEvent) {
        super(onRemoveEvent);

        // setup some swing menu bits...
        // This creates the transparent icon
        SwingMenuItem.createTransparentIcon(SizeAndScalingUtil.TRAY_MENU_SIZE, imageResizeUtil);
        SwingMenuItemCheckbox.createCheckedIcon(SizeAndScalingUtil.TRAY_MENU_SIZE);

        edt = WindowsEventDispatch.start();

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        final SwingMenu swingMenu = new SwingMenu() {
            @Override
            public
            void setImage(final MenuItem menuItem) {
                imageFile = menuItem.getImage();

                NOTIFYICONDATA nid = new NOTIFYICONDATA();
                nid.hWnd = edt.get();

                if (imageFile != null) {
                    HICONWrap imageIcon = convertImage(imageFile);
                    nid.setIcon(imageIcon);
                }

                if (!Shell32.Shell_NotifyIcon(NIM_MODIFY, nid)) {
                    SystemTray.logger.error("Error setting the image for the tray. {}", Kernel32Util.getLastErrorMessage());
                }

                // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                // want to make sure keep the tooltip text the same as before.
                setTooltip_(tooltipText);

                SwingUtil.invokeLater(()->{
                    if (popupMenu == null) {
                        TrayPopup popupMenu = (TrayPopup) _native;
                        popupMenu.pack();
                        popupMenu.setFocusable(true);
                        _WindowsNativeTray.this.popupMenu = popupMenu;
                    }

                    popupMenu.setTitleBarImage(imageFile);
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

                edt.stop();
            }
        };

        HWND hWnd = edt.get();
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
                edt.removeListener(showListener);
                edt.removeListener(quitListener);
                edt.removeListener(menuListener);

                edt.stop();
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
                            double scale = SizeAndScalingUtil.getWindowsDpiScaleForMouseClick(mousePosition.x, mousePosition.y);
                            Point point = new Point((int) (mousePosition.x * scale), (int) (mousePosition.y * scale));
                            popupMenu.doShow(point, 0);
                        }
                        break;

                    default:
                        break;
                }
            }
        };

        edt.addListener(WM_TASKBARCREATED, showListener);
        edt.addListener(WM_QUIT, quitListener);
        edt.addListener(WM_SHELLNOTIFY, menuListener);

        show();

        bind(swingMenu, null, imageResizeUtil);
    }

    private
    void setTooltip_(final String text) {
        if (tooltipText != null && tooltipText.equals(text)) {
            return;
        }

        tooltipText = text;

        NOTIFYICONDATA nid = new NOTIFYICONDATA();
        nid.hWnd = edt.get();

        if (text != null) {
            nid.setTooltip(text);
        }

        Shell_NotifyIcon(NIM_MODIFY, nid);
    }

    private
    void hide() {
        synchronized (imageCache) {
            for (final HICONWrap value : imageCache.values()) {
                value.close();
            }
            imageCache.clear();
        }

        if (visible) {
            NOTIFYICONDATA nid = new NOTIFYICONDATA();
            nid.hWnd = edt.get();

            if (!Shell32.Shell_NotifyIcon(NIM_DELETE, nid)) {
                SystemTray.logger.error("Error hiding tray. {}", Kernel32Util.getLastErrorMessage());
            }
            visible = false;
        }
    }

    private
    void show() {
        if (visible) {
            // for some reason, it is trying to show twice. This can happen when changing the display scale)
            hide();
        }

        NOTIFYICONDATA nid = new NOTIFYICONDATA();
        nid.hWnd = edt.get();
        nid.setTooltip(tooltipText);
        if (imageFile != null) {
            HICONWrap imageIcon = convertImage(imageFile);
            nid.setIcon(imageIcon);
        }
        nid.setCallback(WM_SHELLNOTIFY);

        if (!Shell_NotifyIcon(NIM_ADD, nid)) {
            SystemTray.logger.error("Error showing tray. {}, {}", Kernel32Util.getLastErrorMessage(), Thread.currentThread().getStackTrace());
        }
        visible = true;
    }

    private
    HICONWrap convertImage(final File imageFile) {
        synchronized (imageCache) {
            HICONWrap hiconWrap = imageCache.get(imageFile);
            if (hiconWrap == null) {
                ImageIcon imageIcon = new ImageIcon(imageFile.getAbsolutePath());
                // fully loads the image and returns when it's done loading the image
                imageIcon = new ImageIcon(imageIcon.getImage());

                HBITMAPWrap hbitmapTrayIcon = new HBITMAPWrap(ImageUtil.getBufferedImage(imageIcon));
                hiconWrap = new HICONWrap(hbitmapTrayIcon);

                imageCache.put(imageFile, hiconWrap);

                if (imageCache.size > 120) {
                    SystemTray.logger.error("More than 120 different images used for the SystemTray icon. This will lead to performance issues.");
                }
            }

            return hiconWrap;
        }
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
