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
package dorkbox.systemTray.swingUI;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.util.ImageUtils;

class SwingEntryCheckbox extends SwingEntry implements Checkbox {

    private final ActionListener swingCallback;

    private volatile ActionListener callback;

    private static ImageIcon checkedIcon;
    private static ImageIcon uncheckedIcon;
    private volatile boolean isChecked = false;


    // this is ALWAYS called on the EDT.
    SwingEntryCheckbox(final SwingMenu parent, final ActionListener callback) {
        super(parent, new AdjustedJMenuItem());
        this.callback = callback;

        if (checkedIcon == null) {
            // from Brankic1979, public domain
            File checkedFile = ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, ImageUtils.class.getResource("checked_32.png"));
            checkedIcon = new ImageIcon(checkedFile.getAbsolutePath());

            File uncheckedFile = ImageUtils.getTransparentImage(ImageUtils.ENTRY_SIZE);
            uncheckedIcon = new ImageIcon(uncheckedFile.getAbsolutePath());
        }

        ((JMenuItem) _native).setIcon(uncheckedIcon);

        if (callback != null) {
            _native.setEnabled(true);
            swingCallback = new ActionListener() {
                @Override
                public
                void actionPerformed(ActionEvent e) {
                    // we want it to run on the EDT
                    if (isChecked) {
                        ((JMenuItem) _native).setIcon(uncheckedIcon);
                    } else {
                        ((JMenuItem) _native).setIcon(checkedIcon);
                    }
                    isChecked = !isChecked;

                    handle();
                }
            };

            ((JMenuItem) _native).addActionListener(swingCallback);
        } else {
            _native.setEnabled(false);
            swingCallback = null;
        }
    }

    /**
     * @return true if this checkbox is selected, false if not
     */
    public
    boolean getState() {
        return isChecked;
    }

    @Override
    public
    void setCallback(final ActionListener callback) {
        this.callback = callback;
    }

    private
    void handle() {
        ActionListener cb = this.callback;
        if (cb != null) {
            try {
                cb.actionPerformed(new ActionEvent((Checkbox)this, ActionEvent.ACTION_PERFORMED, ""));
            } catch (Throwable throwable) {
                SystemTray.logger.error("Error calling menu entry {} click event.", getText(), throwable);
            }
        }
    }

    // checkbox image is always present
    @Override
    public
    boolean hasImage() {
        return true;
    }

    @Override
    void removePrivate() {
        ((JMenuItem) _native).removeActionListener(swingCallback);
    }

    // always called in the EDT
    @Override
    void renderText(final String text) {
        ((JMenuItem) _native).setText(text);
    }

    @Override
    void setImage_(final File imageFile) {
    }
}
