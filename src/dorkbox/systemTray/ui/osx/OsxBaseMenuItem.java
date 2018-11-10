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

import java.awt.image.BufferedImage;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.EntryPeer;
import dorkbox.util.ImageUtil;
import dorkbox.util.jna.macos.cocoa.NSImage;
import dorkbox.util.jna.macos.cocoa.NSInteger;
import dorkbox.util.jna.macos.cocoa.NSMenuItem;

abstract
class OsxBaseMenuItem implements EntryPeer {
    // these are necessary BECAUSE OSX menus look funky when there are some menu entries WITH icons and some WITHOUT
    static final NSImage transparentIcon;

    static {
        NSImage transparentIcon_;
        try {
            int menuImageSize = SystemTray.get()
                                          .getMenuImageSize();

            final BufferedImage image = ImageUtil.createImageAsBufferedImage(3, menuImageSize, null);
            transparentIcon_ = new NSImage(ImageUtil.toBytes(image));
        } catch (Exception e) {
            transparentIcon_ = null;
            SystemTray.logger.error("Error creating transparent image.", e);
        }

        transparentIcon = transparentIcon_;
    }

    // the native OSX components
    protected final OsxMenu parent;
    protected final NSMenuItem _native = new NSMenuItem();

    // to prevent GC
    @SuppressWarnings("FieldCanBeLocal")
    private final NSInteger indentationLevel = new NSInteger(1);

    OsxBaseMenuItem(final OsxMenu parent) {
        this.parent = parent;

        // this is to provide reasonable spacing for the menu item, otherwise it looks weird
        _native.setIndentationLevel(indentationLevel);
        _native.setImage(transparentIcon);

        parent.addItem(_native);
    }

    @Override
    public
    void remove() {
        _native.setImage(null);
        if (parent != null) {
            parent.removeItem(_native);
        }
    }
}
