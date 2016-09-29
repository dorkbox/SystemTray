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
package dorkbox.systemTray;

public
interface SystemTrayMenuAction {
    /**
     * This method will ALWAYS be called in the correct context, either in the swing EDT (if it's swing based), or in a separate thread
     * (GtkStatusIcon/AppIndicator based).
     *
     * @param systemTray this is the parent, system tray object
     * @param parent this is the parent menu of this menu entry
     * @param entry this is the menu entry that was clicked
     */
    void onClick(SystemTray systemTray, Menu parent, final MenuEntry entry);
}
