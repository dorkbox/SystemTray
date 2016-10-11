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
package dorkbox.systemTray.nativeUI;

/**
 * Represents a System Tray or menu, that will have it's menu rendered via the native subsystem.
 * <p>
 * This is does not have as many features as the swing-based UI, however the trade off is that this will always have the native L&F of
 * the system (with the exception of Windows, whose native menu looks absolutely terrible).
 * <p>
 * Noticeable differences that are limitations for the NativeUI only:
 *  - AppIndicator Status entries must be plain text (they are not bold as they are everywhere else).
 *  - MacOS cannot have images in their menu or sub-menu's -- only plain text is possible
 */
public
interface NativeUI
{}
