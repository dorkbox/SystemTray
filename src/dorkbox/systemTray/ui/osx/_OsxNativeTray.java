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

import java.io.File;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.util.jna.macos.cocoa.NSImage;
import dorkbox.util.jna.macos.cocoa.NSStatusBar;
import dorkbox.util.jna.macos.cocoa.NSStatusItem;
import dorkbox.util.jna.macos.cocoa.NSString;

/**
 *
 */
public
class _OsxNativeTray extends Tray {

    // is the system tray visible or not.
    private volatile boolean visible = false;
    private volatile File imageFile;
    private volatile String tooltipText = "";

    private final Object keepAliveLock = new Object[0];

    @SuppressWarnings("FieldCanBeLocal")
    private final Thread keepAliveThread;

    // references are ALSO to prevent GC
    private final NSStatusBar statusBar;
    private volatile NSStatusItem statusItem;
    private volatile NSString statusItemTooltip;
    private volatile NSImage statusItemImage;

    public
    _OsxNativeTray(final SystemTray systemTray) {
        super();

        // THIS WILL NOT keep the app running, so we use a "keep-alive" thread so this behavior is THE SAME across
        // all platforms.
        keepAliveThread = new Thread(new Runnable() {
            @Override
            public
            void run() {
                synchronized (keepAliveLock) {
                    keepAliveLock.notifyAll();

                    try {
                        keepAliveLock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }, "TrayKeepAliveThread");
        keepAliveThread.start();


        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        final OsxMenu osxMenu = new OsxMenu(null) {

            @Override
            public
            void setEnabled(final MenuItem menuItem) {
                if (statusItem == null) {
                    statusItem = statusBar.newStatusItem();
                    statusItem.setHighlightMode(true);
                    statusItem.setMenu(this._nativeMenu);
                }

                boolean enabled = menuItem.getEnabled();

                if (visible && !enabled) {
                    statusBar.removeStatusItem(statusItem);
                    visible = false;
                }
                else if (!visible && enabled) {
                    visible = true;



                    // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                    // want to make sure keep the tooltip text the same as before.
                    statusItem.setToolTip(statusItemTooltip);
                    statusItem.setImage(statusItemImage);
                }
            }

            @Override
            public
            void setImage(final MenuItem menuItem) {
                imageFile = menuItem.getImage();

                if (statusItem == null) {
                    statusItem = statusBar.newStatusItem();
                    statusItem.setHighlightMode(true);
                    statusItem.setMenu(this._nativeMenu);
                }


                if (imageFile == null) {
                    statusItemImage = null;
                }
                else {
                    statusItemImage = new NSImage(imageFile);
                }

                statusItem.setImage(statusItemImage);

                // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                // want to make sure keep the tooltip text the same as before.
                statusItem.setToolTip(statusItemTooltip);
            }

            @Override
            public
            void setText(final MenuItem menuItem) {
                // no op.
            }

            @Override
            public
            void setShortcut(final MenuItem menuItem) {
                // no op
            }

            @Override
            public
            void setTooltip(final MenuItem menuItem) {
                if (statusItem == null) {
                    statusItem = statusBar.newStatusItem();
                    statusItem.setHighlightMode(true);
                    statusItem.setMenu(this._nativeMenu);
                }

                final String text = menuItem.getTooltip();

                if (tooltipText != null && tooltipText.equals(text) ||
                    tooltipText == null && text != null) {
                    return;
                }

                if (text == null) {
                    tooltipText = "";
                }
                else {
                    tooltipText = text;
                }

                statusItemTooltip = new NSString(tooltipText);

                // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                // want to make sure keep the tooltip text the same as before.
                statusItem.setImage(statusItemImage);
                statusItem.setToolTip(statusItemTooltip);
            }

            @Override
            public
            void remove() {
                if (statusItem != null) {
                    statusBar.removeStatusItem(statusItem);
                }
                statusItem = null;
                statusItemTooltip = null;
                statusItemImage = null;

                // make sure this thread doesn't keep the JVM alive anymore
                synchronized (keepAliveLock) {
                    keepAliveLock.notifyAll();
                }

                super.remove();
            }
        };

        statusBar = NSStatusBar.systemStatusBar();

        bind(osxMenu, null, systemTray);
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
