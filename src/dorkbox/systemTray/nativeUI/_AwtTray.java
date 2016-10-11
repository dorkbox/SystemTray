/*
 * Copyright 2014 dorkbox, llc
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

import java.awt.AWTException;
import java.awt.Image;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.File;

import javax.swing.ImageIcon;

/**
 * Class for handling all system tray interaction, via AWT.
 *
 * It doesn't work well on linux. See bugs:
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6267936
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6453521
 * https://stackoverflow.com/questions/331407/java-trayicon-using-image-with-transparent-background/3882028#3882028
 */
@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "WeakerAccess"})
public
class _AwtTray extends AwtMenu {
    private volatile SystemTray tray;
    private volatile TrayIcon trayIcon;

    // is the system tray visible or not.
    private volatile boolean visible = true;

    // Called in the EDT
    public
    _AwtTray(final dorkbox.systemTray.SystemTray systemTray) {
        super(systemTray, null, new PopupMenu());

        if (!SystemTray.isSupported()) {
            throw new RuntimeException("System Tray is not supported in this configuration! Please write an issue and include your OS " +
                                       "type and configuration");
        }

        _AwtTray.this.tray = SystemTray.getSystemTray();
    }

    public
    void shutdown() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                removeAll();
                remove();

                tray.remove(trayIcon);
            }
        });
    }

    public
    void setImage_(final File iconFile) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                // stupid java won't scale it right away, so we have to do this twice to get the correct size
                final Image trayImage = new ImageIcon(iconFile.getAbsolutePath()).getImage();
                trayImage.flush();

                if (trayIcon == null) {
                    // here we init. everything
                    trayIcon = new TrayIcon(trayImage);

                    // appindicators DO NOT support anything other than PLAIN gtk-menus (which we hack to support swing menus)
                    //   they ALSO do not support tooltips, so we cater to the lowest common denominator
                    // trayIcon.setToolTip("app name");

                    trayIcon.setPopupMenu((PopupMenu) _native);

                    try {
                        tray.add(trayIcon);
                    } catch (AWTException e) {
                        dorkbox.systemTray.SystemTray.logger.error("TrayIcon could not be added.", e);
                    }
                } else {
                    trayIcon.setImage(trayImage);
                }
            }
        });
    }

    @SuppressWarnings("Duplicates")
    public
    void setEnabled(final boolean setEnabled) {
        visible = !setEnabled;

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (visible && !setEnabled) {
                    tray.remove(trayIcon);
                }
                else if (!visible && setEnabled) {
                    try {
                        tray.add(trayIcon);
                    } catch (AWTException e) {
                        dorkbox.systemTray.SystemTray.logger.error("Error adding the icon back to the tray");
                    }
                }
            }
        });
    }
}
