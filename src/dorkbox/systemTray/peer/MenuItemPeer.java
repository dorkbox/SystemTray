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
package dorkbox.systemTray.peer;

import dorkbox.systemTray.MenuItem;

/**
 * Internal component used to bind the API to the implementation
 */
public
interface MenuItemPeer extends EntryPeer {
    void setImage(MenuItem menuItem);

    void setEnabled(MenuItem menuItem);

    void setText(MenuItem menuItem);

    void setCallback(MenuItem menuItem);

    void setShortcut(MenuItem menuItem);

    void setTooltip(MenuItem menuItem);
}
