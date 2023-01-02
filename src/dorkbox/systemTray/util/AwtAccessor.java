/*
 * Copyright 2023 dorkbox, llc
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
package dorkbox.systemTray.util;

import java.awt.Component;
import java.awt.Image;
import java.awt.Menu;

public
class AwtAccessor {
    // the class methods here are rewritten using javassist.
    public static Object getPeer(java.awt.MenuComponent nativeComp) {
        return null;
    }

    public static void setImage(final Object peerObj, final Image img) {

    }

    public static void setToolTipText(final Object peerObj, final String text) {

    }

    public static void showPopup(final Component component, final Menu nativeComponent) {

    }

    public static java.awt.geom.Point2D getLocation(java.awt.TrayIcon trayIcon) {
        return null;
    }
}
