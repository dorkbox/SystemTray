/*
 * Copyright 2018 dorkbox, llc
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
package dorkbox.systemTray.ui.osx;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import dorkbox.jna.macos.cocoa.NSCellStateValue;
import dorkbox.jna.macos.cocoa.NSImage;
import dorkbox.jna.macos.cocoa.NSString;
import dorkbox.jna.macos.cocoa.OsxClickCallback;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.MenuItemPeer;
import dorkbox.systemTray.util.EventDispatch;

class OsxMenuItem extends OsxBaseMenuItem implements MenuItemPeer, OsxClickCallback {

    // these have to be volatile, because they can be changed from any thread
    private volatile ActionListener callback;

    // to prevent GC
    private final OsxClickAction clickAction;
    private volatile NSImage image;
    private NSString tooltip;
    private NSString title;
    private NSString keyEquivalent;


    OsxMenuItem(final OsxMenu parent) {
        super(parent);

        clickAction = new OsxClickAction(this);
        _native.setTarget(clickAction);
        _native.setAction(OsxClickAction.action);
    }

    @Override
    public
    void click() {
        ActionListener callback = this.callback;
        if (callback != null) {
            callback.actionPerformed(null);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setImage(final MenuItem menuItem) {
        if (menuItem.getImage() != null) {
            _native.setState(NSCellStateValue.NSOnState);

            image = new NSImage(menuItem.getImage());
            _native.setOnStateImage(image);

        }
        else {
            _native.setState(NSCellStateValue.NSOffState);
            _native.setOnStateImage(null);
        }
    }

    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        _native.setEnabled(menuItem.getEnabled());
    }

    @Override
    public
    void setText(final MenuItem menuItem) {
        String text = menuItem.getText();
        if (text == null || text.isEmpty()) {
            title = null;
        }
        else {
            title = new NSString(text);
        }

        _native.setTitle(title);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setCallback(final MenuItem menuItem) {
        callback = menuItem.getCallback();  // can be set to null

        if (callback != null) {
            callback = new ActionListener() {
                final ActionListener cb = menuItem.getCallback();

                @Override
                public
                void actionPerformed(ActionEvent e) {
                    // we want it to run on our own with our own action event info (so it is consistent across all platforms)
                    EventDispatch.runLater(()->{
                        try {
                            cb.actionPerformed(new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, ""));
                        } catch (Throwable throwable) {
                            SystemTray.logger.error("Error calling menu entry {} click event.", menuItem.getText(), throwable);
                        }
                    });
                }
            };
        }
    }

    /**
     * NOTE:
     * OSX can have upper + lower case shortcuts, so we force lowercase because Linux/windows do not have uppercase.
     * Additionally, we cater to the lowest common denominator (as much as is reasonable), so lower-case for everyone.
     */
    @Override
    public
    void setShortcut(final MenuItem menuItem) {
        char shortcut = menuItem.getShortcut();

        if (shortcut != 0) {
            keyEquivalent = new NSString(Character.toString(shortcut).toLowerCase());
        } else {
            keyEquivalent = new NSString("");
        }

        _native.setKeyEquivalent(keyEquivalent);
    }

    @Override
    public
    void setTooltip(final MenuItem menuItem) {
        String tooltip = menuItem.getTooltip();
        if (tooltip == null || tooltip.isEmpty()) {
            this.tooltip = null;
        }
        else {
            this.tooltip = new NSString(tooltip);
        }

        _native.setToolTip(this.tooltip);
    }

    @Override
    public
    void remove() {
        super.remove();

        title = null;
        tooltip = null;
        keyEquivalent = null;
        callback = null;

        _native.setTarget(null);
        _native.setAction(null);

        clickAction.remove();
        image = null;
    }
}
