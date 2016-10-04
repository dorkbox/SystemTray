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
package dorkbox.systemTray.swing;

import java.awt.Insets;

import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.border.EmptyBorder;

class AdjustedJMenu extends JMenu {

    // only necessary in linux
    private final SwingSystemTrayMenuPopup mainPopup;

    AdjustedJMenu(final SwingSystemTrayMenuPopup mainPopup) {
        this.mainPopup = mainPopup;
    }

    @Override
    public
    Insets getMargin() {
        Insets margin = super.getMargin();
        if (margin != null) {
            margin.set(2, -2, 2, 4);

            // makes sure the popup menu is created.
            JPopupMenu popupMenu = getPopupMenu();
            // now adjust the border of it
            popupMenu.setBorder(new EmptyBorder(1, 1, 1, 1));
        }

        return margin;
    }

    @Override
    public
    void setPopupMenuVisible(final boolean visible) {
        if (mainPopup != null) {
            mainPopup.track(getPopupMenu(), visible);
        }

        super.setPopupMenuVisible(visible);
    }

    @Override
    public
    void removeNotify() {
        if (mainPopup != null) {
            // have to make sure that when we are removed, we remove ourself from the tracker
            mainPopup.track(getPopupMenu(), false);
        }

        super.removeNotify();
    }
}
