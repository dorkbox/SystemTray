/*
 * Copyright 2015 dorkbox, llc
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
package dorkbox.systemTray;

import javax.swing.JMenuItem;

import dorkbox.systemTray.peer.StatusPeer;
import dorkbox.systemTray.util.ImageResizeUtil;

/**
 * This represents a common menu-status entry, that is cross platform in nature
 */
public
class Status extends Entry {
    private volatile String text;

    public
    Status() {
    }

    /**
     * @param peer the platform specific implementation for all actions for this type
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param imageResizeUtil the utility used to resize images. This can be Tray specific because of cache requirements
     */
    public
    void bind(final StatusPeer peer, final Menu parent, ImageResizeUtil imageResizeUtil) {
        super.bind(peer, parent, imageResizeUtil);

        peer.setText(this);
    }

    /**
     * @return the text label that the menu entry has assigned
     */
    public
    String getText() {
        return text;
    }

    /**
     * Specifies the new text to set for a menu entry
     *
     * @param text the new text to set
     */
    public
    void setText(final String text) {
        this.text = text;

        if (peer != null) {
            ((StatusPeer) peer).setText(this);
        }
    }

    /**
     * @return a copy of this Status as a swing JMenuItem, with all elements converted to their respective swing elements.
     */
    public
    JMenuItem asSwingComponent() {
        JMenuItem jMenuItem = new JMenuItem();

        jMenuItem.setText(getText());
        jMenuItem.setEnabled(false);

        return jMenuItem;
    }
}
