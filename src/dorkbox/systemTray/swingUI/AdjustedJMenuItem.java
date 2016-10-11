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
package dorkbox.systemTray.swingUI;

import java.awt.Insets;

import javax.swing.JMenuItem;

class AdjustedJMenuItem extends JMenuItem {
    AdjustedJMenuItem() {
        super();
    }

    @Override
    public
    Insets getMargin() {
        Insets margin = super.getMargin();
        if (margin != null) {
            margin.set(2, -2, 2, 4);
        }
        return margin;
    }
}
