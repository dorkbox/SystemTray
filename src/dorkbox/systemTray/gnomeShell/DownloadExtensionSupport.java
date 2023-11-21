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

import java.io.File;
import java.util.List;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.util.KotlinUtils;

@SuppressWarnings({"WeakerAccess"})
public
class DownloadExtensionSupport {
    // this can only be modified with a shell-restart (or, in our case to log out/in)
    private static final List<String> enabledExtensions = ExtensionSupport.getEnabledExtensions();

    private final String name;
    private final String UID;

    public
    DownloadExtensionSupport(final String name, final String UID) {
       this.UID = UID;
       this.name = name;
    }

    public
    void uninstall() {
        unInstall(UID, null);
    }

    public
    boolean isInstalled() {
        return enabledExtensions.contains(UID);
    }

    /**
     * NOTE: This way of installing the extension DOES NOT require us to logout/in for it to be enabled!
     *
     * @return true if the install succeeded
     */
    public
    boolean install() {
        if (SystemTray.DEBUG) {
            SystemTray.logger.debug("Installing the " + name + " gnome-shell extension.");
        }

        // should just be 3.14.1 or 3.20, or 40.1 or similar
        String gnomeVersion = ExtensionSupport.getGnomeVersion();
        if (gnomeVersion == null) {
            return false;
        }

        boolean isInstalled = enabledExtensions.contains(UID);

        // when the gnome version CHANGES, reinstall the extension!!
        if (isInstalled) {
            File TEMP_DIR = new File(System.getProperty("java.io.tmpdir", "temp")).getAbsoluteFile();
            File versionInfo = new File(TEMP_DIR, "saved_version.txt");

            if (versionInfo.canRead()) {
                String saved = KotlinUtils.INSTANCE.readText(versionInfo);
                if (!gnomeVersion.equals(saved)) {
                    // our version has changed, reinstall!.
                    isInstalled = false;
                }
            }
        }

        if (isInstalled) {
            return true;
        }


        // this will install the extension
        boolean isSuccess = false;
        int count = 5;
        while (count-- > 0) {
            String successString = KotlinUtils.INSTANCE.execute("gdbus", "call", "--session",
                                                                "--dest", "org.gnome.Shell.Extensions",
                                                                "--object-path", "/org/gnome/Shell/Extensions",
                                                                "--method", "org.gnome.Shell.Extensions.InstallRemoteExtension",
                                                                "\"" + UID + "\"");
            if (successString.contains("successful")) {
                isSuccess = true;
                break;
            }
        }

        if (!isSuccess) {
            SystemTray.logger.debug("Unable to install the extension for proper gnome-shell behavior, gnome could not install it!");
        }

        return isSuccess;
    }

    public static
    void unInstall(String UID, String restartCommand) {
        final boolean enabled = enabledExtensions.contains(UID);
        if (enabled) {
            enabledExtensions.remove(UID);
            ExtensionSupport.setEnabledExtensions(enabledExtensions);
        }

        // remove the extension from the drive
        String userHome = System.getProperty("user.home");
        final File directory = new File(userHome + "/.local/share/gnome-shell/extensions/" + UID);
        if (directory.canRead()) {
            KotlinUtils.INSTANCE.delete(directory);
        }

        if (enabled) {
            ExtensionSupport.restartShell(restartCommand);
        }
    }
}
