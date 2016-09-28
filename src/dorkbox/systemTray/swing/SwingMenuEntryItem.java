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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;

import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.util.SwingUtil;

public
class SwingMenuEntryItem extends SwingMenuEntry {
    private final ActionListener swingCallback;

    private volatile boolean hasLegitIcon = false;
    private volatile SystemTrayMenuAction callback;

    public
    // this is ALWAYS called on the EDT.
    SwingMenuEntryItem(final SystemTrayMenuAction callback, final SwingSystemTray systemTray) {
        super(new JMenuItem(), systemTray);

        this.callback = callback;
        swingCallback = new ActionListener() {
            @Override
            public
            void actionPerformed(ActionEvent e) {
                // we want it to run on the EDT
                handle();
            }
        };

        ((JMenuItem) menuItem).addActionListener(swingCallback);
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {
        this.callback = callback;
    }

    private
    void handle() {
        if (callback != null) {
            callback.onClick(systemTray, this);
        }
    }

    @Override
    public
    boolean hasImage() {
        return hasLegitIcon;
    }

    @Override
    void removePrivate() {
        ((JMenuItem) menuItem).removeActionListener(swingCallback);
    }

    // always called in the EDT
    @Override
    void renderText(final String text) {
        ((JMenuItem) menuItem).setText(text);
    }

    @Override
    void setImage_(final File imageFile) {
        hasLegitIcon = imageFile != null;

        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                if (imageFile != null) {
                    ImageIcon origIcon = new ImageIcon(imageFile.getAbsolutePath());
                    ((JMenuItem) menuItem).setIcon(origIcon);
                }
                else {
                    ((JMenuItem) menuItem).setIcon(null);
                }
            }
        });
    }
}
