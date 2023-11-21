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
 * For the ability to show gtk menu items, we have to use the https://github.com/MartinPL/Tray-Icons-Reloaded extension for gnome-shell
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public
class TrayIconsReloadedIndicatorExtension {

    private static final DownloadExtensionSupport extension = new DownloadExtensionSupport("gtkstatusicon",
                                                                                           "trayIconsReloaded@selfmade.pl");


    /**
     * @return true if that extension is installed
     *
     *
     * sed 's/[\"]*:[ ]*{[\"]*pk[\"]*:/\n/g' <<< $(curl -s https://extensions.gnome.org/extension/2890/tray-icons-reloaded/) | sed '$ d' | sed 's/^.*\"//g' | sort -rV
     *
     *
     * sed 's/&nbsp;/ /g; s/&amp;/\&/g; s/&lt;/\</g; s/&gt;/\>/g; s/&quot;/\"/g; s/#&#39;/\'"'"'/g; s/&ldquo;/\"/g; s/&rdquo;/\"/g;'
     *
     *
     *curl -s https://extensions.gnome.org/extension/2890/tray-icons-reloaded/ | grep data-versions | sed 's/&nbsp;/ /g; s/&amp;/\&/g; s/&lt;/\</g; s/&gt;/\>/g; s/&quot;/\"/g; s/#&#39;/\'"'"'/g; s/&ldquo;/\"/g; s/&rdquo;/\"/g;'
     *
     *
     *  "version": "27"}}, "45": {"45397": {"pk": 45397, "version": "29"}}}"
     *
     *
     * gdbus call --session --dest org.gnome.Shell.Extensions --object-path /org/gnome/Shell/Extensions --method org.gnome.Shell.Extensions.InstallRemoteExtension "trayIconsReloaded@selfmade.pl"
     *
     * gdbus call --session --dest org.gnome.Shell.Extensions --object-path /org/gnome/Shell/Extensions --method org.gnome.Shell.Extensions.InstallRemoteExtension "appindicatorsupport@rgcjonas.gmail.com"
     *https://extensions.gnome.org/extension/615/appindicator-support/
     *
     * oteExtension user@fedora:/media/psf/libs$ gdbus call --session --dest org.gnome.Shell.Extensions --object-path /org/gnome/Shell/Extensions --method org.gnome.Shell.Extensions.InstallRemoteExtension "appindicatorsupport@rgcjonas.gmail.com"
     * Error: GDBus.Error:org.freedesktop.DBus.Error.NoReply: Remote peer disconnected
     * user@fedora:/media/psf/libs$ gdbus call --session --dest org.gnome.Shell.Extensions --object-path /org/gnome/Shell/Extensions --method org.gnome.Shell.Extensions.InstallRemoteExtension "appindicatorsupport@rgcjonas.gmail.com"
     * ('successful',)
     * user@fedora:/media/psf/libs$
     *
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
