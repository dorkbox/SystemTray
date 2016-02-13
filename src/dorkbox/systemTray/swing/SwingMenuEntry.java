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

package dorkbox.systemTray.swing;

import dorkbox.systemTray.ImageUtil;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.util.SwingUtil;

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

class SwingMenuEntry implements MenuEntry {
    private final SwingSystemTrayMenuPopup parent;
    private final SystemTray systemTray;
    private final JMenuItem menuItem;
    private final ActionListener swingCallback;

    private volatile String text;
    private volatile SystemTrayMenuAction callback;

    SwingMenuEntry(final SwingSystemTrayMenuPopup parentMenu, final String label, final String imagePath, final SystemTrayMenuAction callback,
                   final SystemTray systemTray) {
        this.parent = parentMenu;
        this.text = label;
        this.callback = callback;
        this.systemTray = systemTray;

        swingCallback = new ActionListener() {
            @Override
            public
            void actionPerformed(ActionEvent e) {
                // we want it to run on the EDT
                handle();
            }
        };

        menuItem = new JMenuItem(label);
        menuItem.addActionListener(swingCallback);

        if (imagePath != null && !imagePath.isEmpty()) {
            menuItem.setIcon(new ImageIcon(imagePath));
        }

        parentMenu.add(menuItem);
    }

    private
    void handle() {
        SystemTrayMenuAction cb = this.callback;
        if (cb != null) {
            cb.onClick(systemTray, this);
        }
    }

    @Override
    public
    String getText() {
        return text;
    }

    @Override
    public
    void setText(final String newText) {
        this.text = newText;

        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                menuItem.setText(newText);
            }
        });
    }

    private
    void setImage_(final String imagePath) {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                if (imagePath != null && !imagePath.isEmpty()) {
                    menuItem.setIcon(new ImageIcon(imagePath));
                }
                else {
                    menuItem.setIcon(null);
                }
            }
        });
    }

    @Override
    public
    void setImage(final String imagePath) throws IOException {
        if (imagePath == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(imagePath));
        }
    }

    @Override
    public
    void setImage(final URL imageUrl) throws IOException {
        if (imageUrl == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(imageUrl));
        }
    }

    @Override
    public
    void setImage(final String cacheName, final InputStream imageStream) throws IOException {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(cacheName, imageStream));
        }
    }

    @Override
    @Deprecated
    public
    void setImage(final InputStream imageStream) throws IOException {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPathNoCache(imageStream));
        }
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {
        this.callback = callback;
    }

    @Override
    public
    void remove() {
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                menuItem.removeActionListener(swingCallback);
                parent.remove(menuItem);
            }
        });
    }
}
