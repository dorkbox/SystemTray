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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBoxMenuItem;

import dorkbox.systemTray.peer.CheckboxPeer;
import dorkbox.util.SwingUtil;

/**
 * This represents a common menu-checkbox entry, that is cross platform in nature
 */
@SuppressWarnings("unused")
public
class Checkbox extends Entry {
    private volatile boolean isChecked = false;
    private volatile String text;
    private volatile ActionListener callback;

    private volatile boolean enabled = true;
    private volatile char mnemonicKey;
    private volatile String tooltip;

    public
    Checkbox() {
        this(null, null);
    }

    public
    Checkbox(final String text) {
        this(text, null);
    }

    public
    Checkbox(final JCheckBoxMenuItem jCheckBoxMenuItem) {
        final ActionListener[] actionListeners = jCheckBoxMenuItem.getActionListeners();
        //noinspection Duplicates
        if (actionListeners != null) {
            if (actionListeners.length == 1) {
                setCallback(actionListeners[0]);
            } else {
                ActionListener actionListener = new ActionListener() {
                    @Override
                    public
                    void actionPerformed(final ActionEvent e) {
                        for (ActionListener actionListener : actionListeners) {
                            actionListener.actionPerformed(e);
                        }
                    }
                };
                setCallback(actionListener);
            }
        }

        setEnabled(jCheckBoxMenuItem.isEnabled());
        setChecked(jCheckBoxMenuItem.getState());
        setShortcut(jCheckBoxMenuItem.getMnemonic());
        setText(jCheckBoxMenuItem.getText());
    }

    public
    Checkbox(final String text, final ActionListener callback) {
        this.text = text;
        this.callback = callback;
    }

    /**
     * @param peer the platform specific implementation for all actions for this type
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param systemTray the system tray (which is the object that sits in the system tray)
     */
    public
    void bind(final CheckboxPeer peer, final Menu parent, final SystemTray systemTray) {
        super.bind(peer, parent, systemTray);

        peer.setEnabled(this);
        peer.setText(this);
        peer.setCallback(this);
        peer.setShortcut(this);
        peer.setChecked(this);
    }

    /**
     * @return true if this checkbox is selected, false if not. A checkbox's state is set BEFORE the callback is triggered.
     */
    public
    boolean getChecked() {
        return isChecked;
    }

    /**
     * Sets the checked on/off status for this entry
     *
     * @param isChecked true to show the checkbox, false to hide it
     */
    public
    void setChecked(boolean isChecked) {
        this.isChecked = isChecked;

        if (peer != null) {
            ((CheckboxPeer) peer).setChecked(this);
        }
    }

    /**
     * Gets the callback assigned to this menu entry
     */
    public
    ActionListener getCallback() {
        return callback;
    }

    /**
     * Sets a callback for a menu entry. This is the action that occurs when one clicks the menu entry
     *
     * @param callback the callback to set. If null, the callback is safely removed.
     */
    public
    void setCallback(final ActionListener callback) {
        this.callback = callback;
        if (peer != null) {
            ((CheckboxPeer) peer).setCallback(this);
        }
    }

    /**
     * @return true if this item is enabled, or false if it is disabled.
     */
    public
    boolean getEnabled() {
        return this.enabled;
    }

    /**
     * Enables, or disables the entry.
     */
    public
    void setEnabled(final boolean enabled) {
        this.enabled = enabled;

        if (peer != null) {
            ((CheckboxPeer) peer).setEnabled(this);
        }
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
            ((CheckboxPeer) peer).setText(this);
        }
    }

    /**
     * Gets the shortcut key for this menu entry (Mnemonic) which is what menu entry uses to be "selected" via the keyboard while the
     * menu is displayed.
     * <p>
     * Mnemonics are case-insensitive, and if the character defined by the mnemonic is found within the text, the first occurrence
     * of it will be underlined.
     */
    public
    char getShortcut() {
        return this.mnemonicKey;
    }

    /**
     * Sets a menu entry shortcut key (Mnemonic) so that menu entry can be "selected" via the keyboard while the menu is displayed.
     * <p>
     * Mnemonics are case-insensitive, and if the character defined by the mnemonic is found within the text, the first occurrence
     * of it will be underlined.
     *
     * @param key this is the key to set as the mnemonic
     */
    public
    void setShortcut(final char key) {
        this.mnemonicKey = key;

        if (peer != null) {
            ((CheckboxPeer) peer).setShortcut(this);
        }
    }

    /**
     * Sets a menu entry shortcut key (Mnemonic) so that menu entry can be "selected" via the keyboard while the menu is displayed.
     *
     * Mnemonics are case-insensitive, and if the character defined by the mnemonic is found within the text, the first occurrence
     * of it will be underlined.
     *
     * @param key this is the VK key to set as the mnemonic
     */
    public
    void setShortcut(final int key) {
        this.mnemonicKey = SwingUtil.getFromVirtualKey(key);

        if (peer != null) {
            ((CheckboxPeer) peer).setShortcut(this);
        }
    }

    /**
     * Specifies the tooltip text, usually this is used to brand the SystemTray icon with your product's name, or to provide extra
     * information during mouse-over for menu entries.
     * <p>
     * NOTE: Maximum length is 64 characters long, and it is not supported on all Operating Systems and Desktop Environments.
     * <p>
     * For more details on Linux see https://bugs.launchpad.net/indicator-application/+bug/527458/comments/12.
     *
     * @param tooltipText the text to use as a mouse-over tooltip for the tray icon or menu entry, null to remove.
     */
    public
    void setTooltip(final String tooltipText) {
        if (tooltipText != null) {
            // this is a safety precaution, since the behavior of really long text is undefined.
            if (tooltipText.length() > 64) {
                throw new RuntimeException("Tooltip text cannot be longer than 64 characters.");
            }

            if (!MenuItem.alreadyEmittedTooltipWarning) {
                MenuItem.alreadyEmittedTooltipWarning = true;
                SystemTray.logger.warn("Please disable tooltips, as they are not consistent across all platforms and tray types.");
            }
        }

        this.tooltip = tooltipText;

        if (peer != null) {
            ((CheckboxPeer) peer).setTooltip(this);
        }
    }

    /**
     * Gets the mouse-over tooltip for the meme entry.
     *
     * NOTE: This is not consistent across all platforms and tray types.
     */
    public
    String getTooltip() {
        return this.tooltip;
    }


    /**
     * @return a copy of this Checkbox as a swing JCheckBoxMenuItem, with all elements converted to their respective swing elements. Modifications to the elements of the new JCheckBoxMenuItem will not affect anything, as they are all copies
     */
    public
    JCheckBoxMenuItem asSwingComponent() {
        JCheckBoxMenuItem jCheckBoxMenuItem = new JCheckBoxMenuItem();

        jCheckBoxMenuItem.setText(getText());
        jCheckBoxMenuItem.setToolTipText(getTooltip());
        jCheckBoxMenuItem.setEnabled(getEnabled());
        jCheckBoxMenuItem.setMnemonic(SwingUtil.getVirtualKey(getShortcut()));

        jCheckBoxMenuItem.setState(getChecked());
        jCheckBoxMenuItem.addActionListener(getCallback());

        return jCheckBoxMenuItem;
    }
}
