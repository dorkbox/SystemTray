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

package dorkbox.systemTray.swing;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import javax.swing.JComponent;

import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.util.ImageUtils;
import dorkbox.util.SwingUtil;

abstract
class SwingMenuEntry implements MenuEntry {
    private final int id = SwingSystemTray.MENU_ID_COUNTER.getAndIncrement();

    final SwingSystemTray systemTray;
    final JComponent menuItem;

    // this have to be volatile, because they can be changed from any thread
    private volatile String text;

    // this is ALWAYS called on the EDT.
    SwingMenuEntry(JComponent menuItem, final SwingSystemTray systemTray) {
        this.menuItem = menuItem;
        this.systemTray = systemTray;

        systemTray.getMenu().add(menuItem);
    }

    /**
     * must always be called in the GTK thread
     */
    abstract
    void renderText(final String text);

    abstract
    void setImage_(final File imageFile);


    @Override
    public
    String getText() {
        return text;
    }

    @Override
    public
    void setText(final String newText) {
        this.text = newText;

        SwingUtil.invokeLater(new Runnable() {
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
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                removePrivate();
                systemTray.getMenu().remove(menuItem);
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

        SwingMenuEntry other = (SwingMenuEntry) obj;
        return this.id == other.id;
    }
}
