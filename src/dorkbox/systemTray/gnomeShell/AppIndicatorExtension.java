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
package dorkbox.systemTray.gnomeShell;

/**
 * For the ability to show app-indicators on GNOME shell we use an ALREADY EXISTING extension, `(K)StatusNotifierItem/AppIndicator Support`, that is available from
 *      https://github.com/ubuntu/gnome-shell-extension-appindicator
 *      https://extensions.gnome.org/extension/615/appindicator-support
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public
class AppIndicatorExtension {

    private static final DownloadExtensionSupport extension = new DownloadExtensionSupport("appindicator", "appindicatorsupport@rgcjonas.gmail.com");

    /**
     * `(K)StatusNotifierItem/AppIndicator Support` will convert ALL app indicator icons to be at the top of the screen, so there is no reason to have both installed
     *
     * @return true if that extension is installed
     */
    public static
    boolean isInstalled() {
        return extension.isInstalled();
    }


    /**
     * Only install a version that specifically moves only our icon next to the clock
     */
    public static
    void install() {
        extension.install();
    }

    public static
    void unInstall() {
        extension.uninstall();
    }
}
