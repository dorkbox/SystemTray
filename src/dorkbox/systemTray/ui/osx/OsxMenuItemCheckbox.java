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
import dorkbox.jna.macos.cocoa.NSString;
import dorkbox.jna.macos.cocoa.OsxClickCallback;
import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.CheckboxPeer;
import dorkbox.systemTray.util.EventDispatch;

class OsxMenuItemCheckbox extends OsxBaseMenuItem implements CheckboxPeer, OsxClickCallback {

    // to prevent GC
    private final OsxClickAction clickAction;
    private NSString tooltip;
    private NSString title;
    private NSString keyEquivalent;


    // these have to be volatile, because they can be changed from any thread
    private volatile ActionListener callback;
    private volatile boolean isChecked = false;

    OsxMenuItemCheckbox(final OsxMenu parent) {
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

    @Override
    public
    void setEnabled(final Checkbox menuItem) {
        _native.setEnabled(menuItem.getEnabled());
    }

    @Override
    public
    void setText(final Checkbox menuItem) {
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
    void setCallback(final Checkbox menuItem) {
        callback = menuItem.getCallback();  // can be set to null

        if (callback != null) {
            callback = new ActionListener() {
                final ActionListener cb = menuItem.getCallback();

                @Override
                public
                void actionPerformed(ActionEvent e) {
                    // This can ALSO recursively call the callback
                    menuItem.setChecked(!isChecked);

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
        } else {
            callback = e->{
                // This can ALSO recursively call the callback
                menuItem.setChecked(!isChecked);
            };
        }
    }

    @Override
    public
    void setShortcut(final Checkbox menuItem) {
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
    void setChecked(final Checkbox menuItem) {
        boolean checked = menuItem.getChecked();

        // only dispatch if it's actually different
        if (checked != this.isChecked) {
            this.isChecked = checked;

            if (isChecked) {
                _native.setState(NSCellStateValue.NSOnState);
            }
            else {
                _native.setState(NSCellStateValue.NSOffState);
            }
        }
    }

    @Override
    public
    void setTooltip(final Checkbox menuItem) {
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
    }
}
