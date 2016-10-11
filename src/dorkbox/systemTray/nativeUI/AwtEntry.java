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
package dorkbox.systemTray.nativeUI;

import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.PopupMenu;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.swingUI.SwingUI;
import dorkbox.systemTray.util.ImageUtils;
import dorkbox.systemTray.util.MenuBase;
import dorkbox.systemTray.util.SystemTrayFixes;

abstract
class AwtEntry implements Entry, SwingUI  {
    private final int id = MenuBase.MENU_ID_COUNTER.getAndIncrement();

    private final AwtMenu parent;
    final MenuItem _native;

    // this have to be volatile, because they can be changed from any thread
    private volatile String text;

    // this is ALWAYS called on the EDT.
    AwtEntry(final AwtMenu parent, final MenuItem menuItem) {
        this.parent = parent;
        this._native = menuItem;

        parent._native.add(menuItem);
    }

    @Override
    public
    Menu getParent() {
        return parent;
    }

    /**
     * must always be called in the EDT thread
     */
    abstract
    void renderText(final String text);

    /**
     * Not always called on the EDT thread
     */
    abstract
    void setImage_(final File imageFile);

    /**
     * Enables, or disables the sub-menu entry.
     */
    @Override
    public
    void setEnabled(final boolean enabled) {
        _native.setEnabled(enabled);
    }

    @Override
    public
    void setShortcut(final char key) {
        if (!(_native instanceof PopupMenu)) {
            // yikes...
            final int vKey = SystemTrayFixes.getVirtualKey(key);

            parent.dispatch(new Runnable() {
                @Override
                public
                void run() {
                    _native.setShortcut(new MenuShortcut(vKey));
                }
            });
        }
    }

    @Override
    public
    String getText() {
        return text;
    }

    @Override
    public
    void setText(final String newText) {
        this.text = newText;

        parent.dispatch(new Runnable() {
            @Override
            public
            void run() {
                renderText(newText);
            }
        });
    }

    @Override
    public
    void setImage(final File imageFile) {
        if (imageFile == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageFile));
        }
    }

    @Override
    public final
    void setImage(final String imagePath) {
        if (imagePath == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath));
        }
    }

    @Override
    public final
    void setImage(final URL imageUrl) {
        if (imageUrl == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl));
        }
    }

    @Override
    public final
    void setImage(final String cacheName, final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream));
        }
    }

    @Override
    public final
    void setImage(final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream));
        }
    }

    @Override
    public final
    void remove() {
        parent.dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                removePrivate();
                parent._native.remove(_native);
            }
        });
    }

    // called when this item is removed. Necessary to cleanup/remove itself
    abstract
    void removePrivate();

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

        AwtEntry other = (AwtEntry) obj;
        return this.id == other.id;
    }
}
