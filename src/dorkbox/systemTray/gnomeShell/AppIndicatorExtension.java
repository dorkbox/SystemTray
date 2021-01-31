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
package dorkbox.systemTray.gnomeShell;

import static dorkbox.systemTray.SystemTray.logger;

import java.io.File;
import java.util.List;

import dorkbox.systemTray.SystemTray;


/**
 * For the ability to show app-indicators on GNOME shell we use an ALREADY EXISTING extension, `(K)StatusNotifierItem/AppIndicator Support`, that is available from
 *      https://github.com/ubuntu/gnome-shell-extension-appindicator
 *      https://extensions.gnome.org/extension/615/appindicator-support
 *
 * This extension is licensed GPLv2, and thus, for the extension - we include it as an "AGGREGATE" software package.
 *
 * There are several CRITICAL points to be observed if we are to not violate the GPLv2 license!
 *
 *  1) We do not execute this code. Gnome-shell executes it, and `libappindicator` calls it. We make API calls into `libappindicator` (with or without this extension)
 *       - The only thing we do is install it
 *
 *  2) As per the GPL FAQ itself (http://www.gnu.org/licenses/gpl-faq.html#MereAggregation), we can bundle AND install GPLv2 software without violating the GPLv2.
 *      - NOTE: GPLv3 is completely different, and you can no longer do this.
 *
 *  This is the relevant part of the GPLv2 FAQ:
 *       An “aggregate” consists of a number of separate programs, distributed together on the same CD-ROM or other media.
 *
 *       The GPL permits you to create and distribute an aggregate, even when the licenses of the other software are nonfree or GPL-incompatible. The only
 *       condition is that you cannot release the aggregate under a license that prohibits users from exercising rights that each program's individual
 *       license would grant them.
 *
 *  3) The installed software (this GPL extension) is free for modification (we do not do integrity checks, only manifest version checks)
 *
 *  4) The GPLv2 licensed code is not modified in any way.
 *
 *  5) GPLv2 licensed code does not have to be modifiable on the distribution medium (for example, a CD-ROM is read-only media and is legal for distribution)
 *
 *  We take the same approach as VMare.
 *       VMWare Server includes GPL code into their distribution and:
 *         - installs it alongside non-free software
 *         - packs it into an aggregate file for distribution and installation (an ISO image)
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public
class AppIndicatorExtension extends ExtensionSupport {
    private static final String UID = "appindicatorsupport@rgcjonas.gmail.com";

    /**
     * `(K)StatusNotifierItem/AppIndicator Support` will convert ALL app indicator icons to be at the top of the screen, so there is no reason to have both installed
     *
     * @return true if that extension is installed
     */
    public static
    boolean isInstalled() {
        List<String> enabledExtensions = getEnabledExtensions();
        return enabledExtensions.contains(UID);
    }


    /**
     * Only install a version that specifically moves only our icon next to the clock
     */
    public static
    void install() {
        if (SystemTray.DEBUG) {
            SystemTray.logger.debug("Installing the appindicator gnome-shell extension.");
        }

        boolean isInstalled;

        // should just be 3.14.1 or 3.20 or similar
        String gnomeVersion = ExtensionSupport.getGnomeVersion();
        if (gnomeVersion == null) {
            return;
        }

        List<String> enabledExtensions = getEnabledExtensions();
        isInstalled = enabledExtensions.contains(UID);

        // have to copy the extension over and enable it.
        String userHome = System.getProperty("user.home");

        // where the extension is saved
        final File directory = new File(userHome + "/.local/share/gnome-shell/extensions/" + UID);
        final File metaDatafile = new File(directory, "metadata.json");



        // have to create the metadata.json file (and make it so that it's **always** current).
        // we do this via getting the shell version

        // note: the appName is not configurable for the appindicator gnome-shell extension
        String metadata = ExtensionSupport.createMetadata(UID, SystemTray.getVersion(), "SystemTray", gnomeVersion);

        if (SystemTray.DEBUG) {
            logger.debug("Checking the appindicator gnome-shell extension");
        }

        if (isInstalled && !ExtensionSupport.needsUpgrade(metadata, metaDatafile)) {
            // this means that our version info, etc. is the same - there is no need to update anything
            return;
        }


        // we get here if we are NOT installed, or if we are installed and our metadata is NOT THE SAME.  (so we need to reinstall)
        if (SystemTray.DEBUG) {
            logger.debug("Installing appindicator gnome-shell extension");
        }


        boolean success = ExtensionSupport.writeFile(metadata, metaDatafile);
        if (success) {
            // copies our provided extension files to the correct location on disk
            boolean installedZip = ExtensionSupport.installZip("appindicator.zip", directory);
            if (!installedZip) {
                logger.error("Unable to install appindicator gnome-shell extension!");
            } else {
                if (SystemTray.DEBUG) {
                    logger.debug("Enabling appindicator gnome-shell extension");
                }
            }

            if (!enabledExtensions.contains(UID)) {
                enabledExtensions.add(UID);
            }
            setEnabledExtensions(enabledExtensions);

            // restartShell(SHELL_RESTART_COMMAND); // can't restart shell! must log in/out
        }
    }

    public static
    void unInstall() {
        unInstall(UID, null);
    }
}
