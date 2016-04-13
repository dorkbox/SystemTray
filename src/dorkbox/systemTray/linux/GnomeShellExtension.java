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
package dorkbox.systemTray.linux;

import dorkbox.util.Property;
import dorkbox.util.process.ShellProcessBuilder;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public
class GnomeShellExtension {
    private static final String UID = "SystemTray@dorkbox";

    @Property
    /** Permit the gnome-shell to be restarted when the extension is installed. */
    public static boolean ENABLE_SHELL_RESTART = true;

    @Property
    /** Default timeout to wait for the gnome-shell to completely restart. This is a best-guess estimate. */
    public static long SHELL_RESTART_TIMEOUT_MILLIS = 5000L;

    @Property
    /** Command to restart the gnome-shell. It is recommended to start it in the background (hence '&') */
    public static String SHELL_RESTART_COMMAND = "gnome-shell --replace &";

    public static void install(final Logger logger, final String shellVersionString) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8196);
        PrintStream outputStream = new PrintStream(byteArrayOutputStream);

        // gsettings get org.gnome.shell enabled-extensions
        final ShellProcessBuilder gsettings = new ShellProcessBuilder(outputStream);
        gsettings.setExecutable("gsettings");
        gsettings.addArgument("get");
        gsettings.addArgument("org.gnome.shell");
        gsettings.addArgument("enabled-extensions");
        gsettings.start();

        String output = ShellProcessBuilder.getOutput(byteArrayOutputStream);

        boolean hasTopIcons = output.contains("topIcons@adel.gadllah@gmail.com");
        boolean hasSystemTray = output.contains(UID);

        // topIcons will convert ALL icons to be at the top of the screen, so there is no reason to have both installed
        if (!hasTopIcons && !hasSystemTray) {
            // have to copy the extension over and enable it.
            String userHome = System.getProperty("user.home");

            final File file = new File(userHome + "/.local/share/gnome-shell/extensions/" + UID);
            if (!file.isDirectory()) {
                final boolean mkdirs = file.mkdirs();
                if (!mkdirs) {
                    final String msg = "Unable to create extension location: " + file;
                    logger.error(msg);
                    throw new RuntimeException(msg);
                }
            }

            InputStream reader = null;
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(new File(file, "extension.js"));
                reader = GnomeShellExtension.class.getResourceAsStream("extension.js");

                byte[] buffer = new byte[4096];
                int read;
                while ((read = reader.read(buffer)) > 0) {
                    fileOutputStream.write(buffer, 0, read);
                }
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {
                    }
                }
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            // have to create the metadata.json file (and make it so that it's **always** current).
            // we do this via getting the shell version


            // GNOME Shell 3.14.1
            String versionOutput = shellVersionString.replaceAll("[^\\d.]", ""); // should just be 3.14.1

            // now change to major version only (only if applicable)
            final int indexOf = versionOutput.indexOf('.');
            final int lastIndexOf = versionOutput.lastIndexOf('.');
            if (indexOf < lastIndexOf) {
                versionOutput = versionOutput.substring(0, indexOf);
            }

            String metadata = "{\n" +
                              "  \"description\": \"Shows a java tray icon on the top notification tray\",\n" +
                              "  \"name\": \"Dorkbox SystemTray\",\n" +
                              "  \"shell-version\": [\n" +
                              "    \"" + versionOutput + "\"\n" +
                              "  ],\n" +
                              "  \"url\": \"https://github.com/dorkbox/SystemTray\",\n" +
                              "  \"uuid\": \"" + UID + "\",\n" +
                              "  \"version\": 1\n" +
                              "}";


            BufferedWriter outputWriter = null;
            try {
                outputWriter = new BufferedWriter(new FileWriter(new File(file, "metadata.json"), false));
                // FileWriter always assumes default encoding is OK
                outputWriter.write(metadata);
                outputWriter.flush();
                outputWriter.close();
            } catch (Exception e) {
                if (outputWriter != null) {
                    try {
                        outputWriter.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            // now we have to enable us
            // gsettings get org.gnome.shell enabled-extensions   (['background-logo@fedorahosted.org']  on fedora 23) different on openSuse
            final StringBuilder stringBuilder = new StringBuilder(output);

            // strip off up to the leading  ['
            final int extensionIndex = output.indexOf("['");
            if (extensionIndex > 0) {
                stringBuilder.delete(0, extensionIndex);
            }

            // remove the last ]
            stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());

            // add our extension to the list
            if (stringBuilder.length() > 2) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("'")
                         .append(UID)
                         .append("'");


            stringBuilder.append("]");

            // gsettings set org.gnome.shell enabled-extensions "['SystemTray@dorkbox']"
            // gsettings set org.gnome.shell enabled-extensions "['xyz', 'SystemTray@dorkbox']"
            final ShellProcessBuilder setGsettings = new ShellProcessBuilder(outputStream);
            setGsettings.setExecutable("gsettings");
            setGsettings.addArgument("set");
            setGsettings.addArgument("org.gnome.shell");
            setGsettings.addArgument("enabled-extensions");
            setGsettings.addArgument(stringBuilder.toString());
            setGsettings.start();


            if (ENABLE_SHELL_RESTART) {
                logger.info("Restarting gnome-shell, so tray notification changes can be applied.");

                // now we have to restart the gnome shell via bash
                final ShellProcessBuilder restartShell = new ShellProcessBuilder();
                // restart shell in background process
                restartShell.addArgument(SHELL_RESTART_COMMAND);
                restartShell.start();

                // have to give the shell time to restart
                try {
                    Thread.sleep(SHELL_RESTART_TIMEOUT_MILLIS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                logger.info("Shell restarted.");
            }
        }
    }
}
