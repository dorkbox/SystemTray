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

import java.util.concurrent.atomic.AtomicInteger;

import dorkbox.systemTray.peer.EntryPeer;
import dorkbox.systemTray.util.ImageResizeUtil;

/**
 * This represents a common menu-entry, that is cross platform in nature
 */
@SuppressWarnings({"unused", "SameParameterValue"})
public
class Entry {
    private static final AtomicInteger MENU_ID_COUNTER = new AtomicInteger(0);
    private final int id = Entry.MENU_ID_COUNTER.getAndIncrement();

    private volatile Menu parent;

    protected volatile EntryPeer peer;
    protected volatile ImageResizeUtil imageResizeUtil;

    public
    Entry() {
    }

    // methods for hooking into the system tray, menu's, and entries.
    // called internally when an entry/menu is attached

    /**
     * @param peer the platform specific implementation for all actions for this type
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param imageResizeUtil the utility used to resize images. This can be Tray specific because of cache requirements
     */
    public
    void bind(final EntryPeer peer, final Menu parent, ImageResizeUtil imageResizeUtil) {
        this.parent = parent;
        this.peer = peer;
        this.imageResizeUtil = imageResizeUtil;
    }

    // END methods for hooking into the system tray, menu's, and entries.


    /**
     * @return true if this entry has a peer assigned to it.
     */
    public final
    boolean hasPeer() {
        return peer != null;
    }

    /**
     * @return the parent menu (of this entry or menu) or null if we are the root menu
     */
    public final
    Menu getParent() {
        return this.parent;
    }

    /**
     * @return the Image Resize Utility (with Tray specific cache) that this menu uses
     */
    public final
    ImageResizeUtil getImageResizeUtil() {
        return this.imageResizeUtil;
    }
    /**
     * Removes this menu entry from the menu and releases all system resources associated with this menu entry.
     */
    public
    void remove() {
        if (peer != null) {
            peer.remove();

            this.parent = null;
            peer = null;
        }
    }


    @Override
    public final
    int hashCode() {
        return id;
    }


    @Override
    public final
    boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        Entry other = (Entry) obj;
        return this.id == other.id;
    }
}
