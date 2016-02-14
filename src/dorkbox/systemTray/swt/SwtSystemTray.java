/*
 * Copyright 2016 dorkbox, llc
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
package dorkbox.systemTray.swt;

import dorkbox.systemTray.ImageUtil;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTrayMenuAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TrayItem;

import java.io.InputStream;
import java.net.URL;

/**
 * Class for handling all system tray interaction, via SWING
 */
public
class SwtSystemTray extends dorkbox.systemTray.SystemTray {
//    volatile SwingSystemTrayMenuPopup menu;
    volatile MenuItem connectionStatusItem;
//
//    volatile SystemTray tray;
//    volatile TrayIcon trayIcon;

    private final Display display;
    private final Shell shell;
    private final TrayItem tray;

    volatile boolean isActive = false;
    private final Menu menu;

    /**
     * Creates a new system tray handler class.
     */
    public
    SwtSystemTray() {
        super();

        display = Display.getCurrent();
        shell = display.getShells()[0];

        tray = new TrayItem(display.getSystemTray(), SWT.NONE);
        menu = new Menu(shell, SWT.POP_UP);

        tray.addListener(SWT.MenuDetect, new Listener() {
            public void handleEvent(Event event) {
                menu.setVisible(true);
            }
        });
        tray.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                menu.setVisible(true);
            }
        });
    }


    @Override
    public
    void shutdown() {
        synchronized (this) {
            for (MenuEntry menuEntry : menuEntries) {
                menuEntry.remove();
            }
            menuEntries.clear();

            connectionStatusItem = null;

            tray.dispose();
        }
    }

    @Override
    public
    void setStatus(final String infoString) {
        synchronized (this) {
            if (connectionStatusItem == null && infoString != null && !infoString.isEmpty()) {
                deleteMenu();

                connectionStatusItem = new MenuItem(menu, SWT.PUSH);
                connectionStatusItem.setText(infoString);
                connectionStatusItem.setEnabled(false);

                createMenu();
            }
            else {
                if (infoString == null || infoString.isEmpty()) {
                    // deletes the status entry only
                    connectionStatusItem.dispose();
                    connectionStatusItem = null;
                }
                else {
                    connectionStatusItem.setText(infoString);
                }
            }
        }
    }

    /**
     * Deletes the contents of the menu, and unreferences everything in it.
     */
    private void deleteMenu() {
        // have to remove status from menu
        if (connectionStatusItem != null) {
            connectionStatusItem.dispose();
        }

        synchronized (menuEntries) {
            // have to remove all other menu entries
            for (int i = 0; i < menuEntries.size(); i++) {
                SwtMenuEntry menuEntry__ = (SwtMenuEntry) menuEntries.get(i);
                menuEntry__.remove();
            }
        }
    }

    /**
     * recreates the menu items
     */
    private void createMenu() {
        synchronized (menuEntries) {
            // now add back other menu entries
            final SwtMenuEntry[] swtMenuEntries = menuEntries.toArray(new SwtMenuEntry[0]);
            menuEntries.clear();

            for (int i = 0; i < swtMenuEntries.length; i++) {
                SwtMenuEntry menuEntry__ = swtMenuEntries[i];
                addMenuEntry_(menuEntry__.getText(), menuEntry__.imagePath, menuEntry__.callback);
            }
        }
    }

    @Override
    protected
    void setIcon_(final String iconPath) {
        tray.setImage(new Image(display, iconPath));
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    private
    void addMenuEntry_(final String menuText, final String imagePath, final SystemTrayMenuAction callback) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        synchronized (this) {
            synchronized (menuEntries) {
                MenuEntry menuEntry = getMenuEntry(menuText);

                if (menuEntry != null) {
                    throw new IllegalArgumentException("Menu entry already exists for given label '" + menuText + "'");
                }
                else {
                    final Image image;
                    if (imagePath != null) {
                        image = new Image(display, imagePath);
                    } else {
                        image = null;
                    }

                    menuEntry = new SwtMenuEntry(menu, menuText, imagePath, image, callback, this);
                    menuEntries.add(menuEntry);
                }
            }
        }
    }

    @Override
    public
    void addMenuEntry(String menuText, final String imagePath, final SystemTrayMenuAction callback) {
        if (imagePath == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPath(imagePath), callback);
        }
    }

    @Override
    public
    void addMenuEntry(final String menuText, final URL imageUrl, final SystemTrayMenuAction callback) {
        if (imageUrl == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPath(imageUrl), callback);
        }
    }

    @Override
    public
    void addMenuEntry(final String menuText, final String cacheName, final InputStream imageStream, final SystemTrayMenuAction callback) {
        if (imageStream == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPath(cacheName, imageStream), callback);
        }
    }

    @Override
    @Deprecated
    public
    void addMenuEntry(final String menuText, final InputStream imageStream, final SystemTrayMenuAction callback) {
        if (imageStream == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPathNoCache(imageStream), callback);
        }
    }
}
