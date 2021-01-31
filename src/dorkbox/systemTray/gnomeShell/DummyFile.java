/*
 * Copyright 2021 dorkbox, llc
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
import java.io.FileOutputStream;
import java.io.IOException;

import dorkbox.systemTray.SystemTray;

public
class DummyFile {

    private static final File file = new File(System.getProperty("user.home") + "/.local/.SystemTray");

    /**
     * Installs a dummy file to indicate we've restarted the shell. This will restart the shell if necessary
     */
    public static
    void install() {
        if (!file.exists()) {
            if (SystemTray.DEBUG) {
                SystemTray.logger.debug("Creating marker file");
            }

            try {
                new FileOutputStream(file).close();
            } catch (IOException e) {
                SystemTray.logger.error("Error creating file", e);
            }

            file.setLastModified(System.currentTimeMillis());

            // just restarting the shell is enough to get the system tray to work
            LegacyExtension.restartShell();
        }

    }
}
