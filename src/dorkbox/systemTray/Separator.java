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

/**
 * This represents a common menu-spacer entry, that is cross platform in nature.
 * <p>
 * When menu entries are removed, any menu spacer that ends up at the top/bottom of the menu will also be removed.
 * <p>
 * For example:
 * <pre> {@code
 * Original     Entry3 deleted     Result
 *
 * <Status>         <Status>       <Status>
 * Entry1           Entry1         Entry1
 * Entry2      ->   Entry2    ->   Entry2
 * <Spacer>         (deleted)
 * Entry3           (deleted)
 *
 * }</pre>
 */
public
class Separator extends Entry {

    public
    Separator() {
        super();
    }
}
