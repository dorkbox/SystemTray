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
import dorkbox.util.OSUtil;

@SuppressWarnings({"DanglingJavadoc", "WeakerAccess"})
public
class LegacyExtension extends ExtensionSupport {
    private static final String UID = "SystemTray@Dorkbox";
    public static final String DEFAULT_NAME = "SystemTray";

    /** Command to restart the gnome-shell. It is recommended to start it in the background (hence '&') */
    private static final String SHELL_RESTART_COMMAND = "gnome-shell --replace &";

    /**
     * Only install a version that specifically moves only our icon next to the clock
     */
    public static
    void install() {
        if (OSUtil.DesktopEnv.isWayland()) {
            if (SystemTray.DEBUG) {
                SystemTray.logger.debug("Gnome-shell legacy extension not possible with wayland.");
            }

            return;
        }

        if (SystemTray.DEBUG) {
            SystemTray.logger.debug("Installing the legacy gnome-shell extension.");
        }

        boolean hasTopIcons;
        boolean hasSystemTray;

        // should just be 3.14.1 or 3.20 or similar
        String gnomeVersion = ExtensionSupport.getGnomeVersion();
        if (gnomeVersion == null) {
            return;
        }

        List<String> enabledExtensions = getEnabledExtensions();
        hasTopIcons = enabledExtensions.contains("topIcons@adel.gadllah@gmail.com");
        hasSystemTray = enabledExtensions.contains(UID);

        if (hasTopIcons) {
            // topIcons will convert ALL icons to be at the top of the screen, so there is no reason to have both installed
            return;
        }

        // have to copy the extension over and enable it.
        String userHome = System.getProperty("user.home");

        // where the extension is saved
        final File directory = new File(userHome + "/.local/share/gnome-shell/extensions/" + UID);
        final File metaDatafile = new File(directory, "metadata.json");


        // have to create the metadata.json file (and make it so that it's **always** current).
        // we do this via getting the shell version

        String metadata = ExtensionSupport.createMetadata(UID, SystemTray.getVersion(), gnomeVersion);

        if (SystemTray.DEBUG) {
            logger.debug("Checking the legacy gnome-shell extension");
        }

        if (hasSystemTray && !ExtensionSupport.needsUpgrade(metadata, metaDatafile)) {
            // this means that our version info, etc. is the same - there is no need to update anything
            return;
        }


        // we get here if we are NOT installed, or if we are installed and our metadata is NOT THE SAME.  (so we need to reinstall)
        if (SystemTray.DEBUG) {
            logger.debug("Installing legacy gnome-shell extension");
        }


        boolean success = ExtensionSupport.writeFile(metadata, metaDatafile);

        if (success && !hasSystemTray) {
            // copies our provided extension files to the correct location on disk
            ExtensionSupport.installFile("extension.js", directory);

            if (SystemTray.DEBUG) {
                logger.debug("Enabling legacy gnome-shell extension");
            }

            if (!enabledExtensions.contains(UID)) {
                enabledExtensions.add(UID);
            }
            setEnabledExtensions(enabledExtensions);

            restartShell(SHELL_RESTART_COMMAND);
        }
    }

    public static
    void unInstall() {
        if (OSUtil.DesktopEnv.isWayland()) {
            if (OSUtil.Linux.isUbuntu() && OSUtil.Linux.getUbuntuVersion()[0] == 17) {
                // ubuntu 17.04 is NOT WAYLAND (it's MIR) and ubuntu 17.10 is WAYLAND (and doesn't support this)
                return;
            }
            else if (OSUtil.Linux.isFedora()) {
                // fedora doesn't support this
                return;

            }
            else {
                if (SystemTray.DEBUG) {
                    logger.warn("Trying to restart the shell with an unknown version of wayland. Please create an issue with OS and debug information.");
                }
                else {
                    logger.warn("Trying to restart the shell with an unknown version of wayland. Please set `SystemTray.DEBUG=true;` then create an issue " +
                                "with OS and debug information.");
                }
            }
        }

        unInstall(UID, SHELL_RESTART_COMMAND);
    }

    public static
    void restartShell() {
        if (SystemTray.DEBUG) {
            logger.debug("DEBUG mode enabled. You need to log-out/in or manually restart the shell via '{}' to apply the changes.", SHELL_RESTART_COMMAND);
            return;
        }

        restartShell(SHELL_RESTART_COMMAND);
    }
}
