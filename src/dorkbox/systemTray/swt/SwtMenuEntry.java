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

package dorkbox.systemTray.swt;

import dorkbox.systemTray.ImageUtil;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import java.io.InputStream;
import java.net.URL;

class SwtMenuEntry implements MenuEntry {
    private final Menu parent;
    private final SystemTray systemTray;
    private final MenuItem menuItem;
    private final Listener selectionListener;

    private volatile String text;
    String imagePath;
    private Image image;
    volatile SystemTrayMenuAction callback;

    public
    SwtMenuEntry(final Menu parentMenu,
                 final String label,
                 final String imagePath,
                 final Image image,
                 final SystemTrayMenuAction callback,
                 final SystemTray systemTray) {

        this.parent = parentMenu;
        this.text = label;
        this.imagePath = imagePath;
        this.image = image;
        this.callback = callback;
        this.systemTray = systemTray;

        menuItem = new MenuItem(parentMenu, SWT.PUSH);
        menuItem.setText(label);

        selectionListener = new Listener() {
            public
            void handleEvent(Event event) {
                handle();
            }
        };
        menuItem.addListener(SWT.Selection, selectionListener);

        if (image != null && !image.isDisposed()) {
            menuItem.setImage(image);
        }
    }

    private
    void handle() {
        SystemTrayMenuAction cb = this.callback;
        if (cb != null) {
            cb.onClick(systemTray, this);
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
        menuItem.setText(newText);
    }

    private
    void setImage_(final String imagePath) {
        if (imagePath == null) {
            menuItem.setImage(null);
            image = null;
        }
        else {
            image = new Image(parent.getShell().getDisplay(), imagePath);
            menuItem.setImage(image);
        }
    }

    @Override
    public
    void setImage(final String imagePath) {
        if (imagePath == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(imagePath));
        }
    }

    @Override
    public
    void setImage(final URL imageUrl) {
        if (imageUrl == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(imageUrl));
        }
    }

    @Override
    public
    void setImage(final String cacheName, final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(cacheName, imageStream));
        }
    }

    @Override
    @Deprecated
    public
    void setImage(final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPathNoCache(imageStream));
        }
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {
        this.callback = callback;
    }

    @Override
    public
    void remove() {
        if (image != null) {
            image.dispose();
        }
        menuItem.dispose();
    }
}
