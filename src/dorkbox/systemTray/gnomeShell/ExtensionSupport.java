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

import static dorkbox.systemTray.SystemTray.logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dorkbox.executor.Executor;
import dorkbox.os.OS;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.util.KotlinUtils;
import dorkbox.util.IO;

@SuppressWarnings({"WeakerAccess"})
public
class ExtensionSupport {
    // this can only be modified with a shell-restart (or, in our case to log out/in)
    static final List<String> enabledExtensions = getEnabledExtensions();

    private final String name;
    private final String UID;
    private final String zipFile;

    public
    ExtensionSupport(final String name, final String UID, final String zipFile) {
       this.UID = UID;
       this.name = name;
       this.zipFile = zipFile;
    }

    public
    void uninstall() {
        unInstall(UID, null);
    }

    public
    boolean isInstalled() {
        return enabledExtensions.contains(UID);
    }

    public
    void install() {
        if (SystemTray.DEBUG) {
            SystemTray.logger.debug("Installing the " + name + " gnome-shell extension.");
        }

        // should just be 3.14.1 or 3.20 or similar
        String gnomeVersion = ExtensionSupport.getGnomeVersion();
        if (gnomeVersion == null) {
            return;
        }

        boolean isInstalled = enabledExtensions.contains(UID);

        // have to copy the extension over and enable it.
        String userHome = System.getProperty("user.home");

        // where the extension is saved
        final File directory = new File(userHome + "/.local/share/gnome-shell/extensions/" + UID);
        final File metaDatafile = new File(directory, "metadata.json");



        // have to create the metadata.json file (and make it so that it's **always** current).
        // we do this via getting the shell version

        // note: the appName is not configurable for the gnome-shell extension
        String metadata = ExtensionSupport.createMetadata(UID, SystemTray.getVersion(), "SystemTray", gnomeVersion);

        if (SystemTray.DEBUG) {
            logger.debug("Checking the " + name + " gnome-shell extension");
        }

        if (isInstalled && !ExtensionSupport.needsUpgrade(metadata, metaDatafile)) {
            // this means that our version info, etc. is the same - there is no need to update anything
            return;
        }


        // we get here if we are NOT installed, or if we are installed and our metadata is NOT THE SAME.  (so we need to reinstall)
        if (SystemTray.DEBUG) {
            logger.debug("Installing " + name + " gnome-shell extension");
        }


        boolean success = ExtensionSupport.writeFile(metadata, metaDatafile);
        if (success) {
            // copies our provided extension files to the correct location on disk
            boolean installedZip = ExtensionSupport.installZip(zipFile, directory);
            if (!installedZip) {
                logger.error("Unable to install " + zipFile + " gnome-shell extension!");
            } else {
                if (SystemTray.DEBUG) {
                    logger.debug("Enabling " + zipFile + " gnome-shell extension");
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
    List<String> getEnabledExtensions() {
        String output;
        try {
            // gsettings get org.gnome.shell enabled-extensions
            output = KotlinUtils.INSTANCE.execute("/usr/bin/gsettings", "get", "org.gnome.shell", "enabled-extensions");
        } catch (Exception e) {
            logger.error("Unable to get gnome shell extensions!", e);
            output = "";
        }

        // now we have to enable us if we aren't already enabled

        // gsettings get org.gnome.shell enabled-extensions
        // defaults are:
        //  - fedora 23:   ['background-logo@fedorahosted.org']  on
        //  - openSuse:
        //  - Ubuntu Gnome 16.04:   @as []
        //  - fedora 39: ['--schemadir SCHEMADIR', 'appindicatorsupport@rgcjonas.gmail.com']

        final StringBuilder stringBuilder = new StringBuilder(output);

        // have to remove the end first, otherwise we would have to re-index the location of the ]

        // remove the last ]
        int extensionIndex = output.indexOf("]");
        if (extensionIndex > 0) {
            stringBuilder.delete(extensionIndex, stringBuilder.length());
        }

        // strip off UP-TO plus the leading  [
        extensionIndex = output.indexOf("[");
        if (extensionIndex >= 0) {
            stringBuilder.delete(0, extensionIndex+1);
        }

        // should be   'background-logo@fedorahosted.org', 'zyx', 'abs'
        // or nothing

        String installedExtensions = stringBuilder.toString();

        // now just split the extensions into a list so it is easier to manage

        String[] split = installedExtensions
                                 .split(", ");
        for (int i = 0; i < split.length; i++) {
            final String s = split[i];

            int i1 = s.indexOf("'");
            int i2 = s.lastIndexOf("'");

            if (i1 == 0 && i2 == s.length() - 1) {
                split[i] = s.substring(1, s.length() - 1);
            }
        }

        ArrayList<String> strings = new ArrayList<>(Arrays.asList(split));
        strings.removeIf(string->string.trim().isEmpty());

        if (SystemTray.DEBUG) {
            logger.debug("Installed extensions are: {}", strings);
        }

        return strings;
    }

    public static
    void setEnabledExtensions(List<String> extensions) {
        StringBuilder stringBuilder = new StringBuilder("[");

        for (int i = 0, extensionsSize = extensions.size(), limit = extensionsSize-1; i < extensionsSize; i++) {
            final String extension = extensions.get(i);
            if (extension.isEmpty()) {
                continue;
            }

            stringBuilder.append("'")
                         .append(extension)
                         .append("'");

            if (i < limit) {
                stringBuilder.append(",");
            }
        }
        stringBuilder.append("]");


        if (SystemTray.DEBUG) {
            logger.debug("Setting installed extensions to: {}", stringBuilder.toString());
        }

        // gsettings set org.gnome.shell enabled-extensions "['SystemTray@Dorkbox']"
        // gsettings set org.gnome.shell enabled-extensions "['background-logo@fedorahosted.org']"
        // gsettings set org.gnome.shell enabled-extensions "['background-logo@fedorahosted.org', 'SystemTray@Dorkbox']"
        try {
            KotlinUtils.INSTANCE.execute("/usr/bin/gsettings", "set", "org.gnome.shell", "enabled-extensions", stringBuilder.toString());
        } catch (Exception e) {
            logger.error("Unable to set gnome shell extensions!", e);
        }
    }

    public static
    void unInstall(String UID, String restartCommand) {
        final boolean enabled = enabledExtensions.contains(UID);
        if (enabled) {
            enabledExtensions.remove(UID);
            setEnabledExtensions(enabledExtensions);
        }

        // remove the extension from the drive
        String userHome = System.getProperty("user.home");
        final File directory = new File(userHome + "/.local/share/gnome-shell/extensions/" + UID);
        if (directory.canRead()) {
            KotlinUtils.INSTANCE.delete(directory);
        }

        if (enabled) {
            restartShell(restartCommand);
        }
    }

    public static
    void restartShell(String restartCommand) {
        if (restartCommand == null) {
            return;
        }

        // in some situations, you can no longer restart the shell in wayland. You must logout-login for shell modifications to apply
        // https://mail.gnome.org/archives/commits-list/2015-March/msg01019.html

        // HOWEVER, with wayland, shell-extensions that DO NO MODIFY THE GUI (we don't, we just add icons to it)
        // are enabled without a shell restart.

        // The app-indicator extension with wayland (on Fedora 27+) CANNOT restart the shell.


        logger.info("Restarting gnome-shell via '{}' so tray notification changes can be applied.", restartCommand);

        // now we have to restart the gnome shell via bash in a background process
        try {
            new Executor().command(restartCommand).startAsShellBlocking();
        } catch (Exception e) {
            logger.error("Unable to restart gnome shell!", e);
        }

        // We don't care when the shell restarts, since WHEN IT DOES restart, our extension will show our icon.
        // Until then however, there will be errors which can be ignored, because the shell-restart means everything works.
    }

    /**
     * @return true if successful, false if there was a failure
     */
    protected static
    boolean writeFile(final String metadata, final File metaDatafile) {
        File file = metaDatafile.getParentFile();

        // need to make the extension location
        if (!file.isDirectory()) {
            final boolean mkdirs = file.mkdirs();
            if (!mkdirs) {
                final String msg = "Unable to create extension location: " + file;
                logger.error(msg);
                return false;
            }
        }

        // write out the metadata
        BufferedWriter outputWriter = null;
        try {
            outputWriter = new BufferedWriter(new FileWriter(metaDatafile, false));
            // FileWriter always assumes default encoding is OK
            outputWriter.write(metadata);
            outputWriter.flush();
            outputWriter.close();
        } catch (IOException e) {
            logger.error("Error installing extension metadata file", e);
            return true;
        } finally {
            if (outputWriter != null) {
                try {
                    outputWriter.close();
                } catch (IOException e) {
                    logger.error("Error closing: {}", outputWriter, e);
                }
            }
        }


        return true;
    }



    /**
     * @return true if successful, false if there was a failure
     */
    @SuppressWarnings("SameParameterValue")
    protected static
    boolean installFile(final String resourceName, final File targetDirectory, final String appName) {
        final File outputFile = new File(targetDirectory, resourceName);

        try {
            InputStream reader = ExtensionSupport.class.getResourceAsStream(resourceName);
            // fileOutputStream = new FileOutputStream(outputFile);

            if (reader == null) {
                logger.error("The {} file resource cannot be found. Something is severely wrong.", resourceName);
                return false;
            }

            // instead of copying the file from one place to another, we have to read the file as a string, THEN replace the app name, THEN write it out.

            FileWriter fileWriter = new FileWriter(outputFile);
            InputStreamReader inputStreamReader = new InputStreamReader(reader);
            try {
                String lineSeparator = OS.INSTANCE.getLINE_SEPARATOR();
                BufferedReader bin = new BufferedReader(inputStreamReader);
                String line;

                while (( line = bin.readLine()) != null) {
                    if (line.startsWith("let APP_NAME = \"")) {
                        line = "let APP_NAME = \"" + appName + "\";";
                    }

                    fileWriter.write(line);
                    fileWriter.write(lineSeparator);
                }
            } finally {
                IO.closeQuietly(inputStreamReader);
                IO.closeQuietly(fileWriter);
            }

            return true;
        } catch (FileNotFoundException e) {
            logger.error("Cannot find gnome-shell extension", e);
        } catch (IOException e) {
            logger.error("Unable to get gnome-shell extension", e);
        }

        return false;
    }

    /**
     * @return true if successful, false if there was a failure
     */
    @SuppressWarnings("SameParameterValue")
    protected static
    boolean installZip(final String zipResourceName, final File targetDirectory) {
        ZipInputStream inputStream = null;
        FileOutputStream fileOutputStream = null;

        try {
            final InputStream resourceAsStream = ExtensionSupport.class.getResourceAsStream(zipResourceName);
            if (resourceAsStream == null) {
                SystemTray.logger.error("The appindicator extension resource cannot be found. This is unexpected!");
                return false;
            }

            inputStream = new ZipInputStream(resourceAsStream);

            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = entry.getName();

                    try {
                        File specificOutput = new File(targetDirectory, name);
                        File parentFile = specificOutput.getParentFile();

                        if (!parentFile.exists()) {
                            boolean mkdirs = parentFile.mkdirs();
                            if (!mkdirs) {
                                SystemTray.logger.error("Error creating target directory '{}' for Zip support.", parentFile);
                            }
                        }

                        fileOutputStream = new FileOutputStream(specificOutput);

                        IO.copyStream(inputStream, fileOutputStream);
                    } catch (IOException e) {
                        SystemTray.logger.error("Error extracting zip contents to {}", targetDirectory);
                    } finally {
                        if (fileOutputStream != null) {
                            IO.closeQuietly(fileOutputStream);
                            fileOutputStream = null;
                        }
                    }
                }
            }

            return true;
        } catch (FileNotFoundException e) {
            logger.error("Cannot find gnome-shell extension", e);
        } catch (IOException e) {
            logger.error("Unable to get gnome-shell extension", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.error("Error closing: {}", inputStream, e);
                }
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    logger.error("Error closing: {}", fileOutputStream, e);
                }
            }
        }

        return false;
    }


    protected static
    String createMetadata(final String uid, final String appVersion, final String appName, final String gnomeVersion) {
        return "{\n" +
               "  \"description\": \"Moves the java SystemTray icon from inside the notification drawer to alongside the clock.\",\n" +
               "  \"name\": \"Dorkbox SystemTray\",\n" +
               "  \"app-name-id\": \"" + appName + "\",\n" +
               "  \"shell-version\": [\n" +
               "    \"" + gnomeVersion + "\"\n" +
               "  ],\n" +
               "  \"url\": \"https://git.dorkbox.com/dorkbox/SystemTray\",\n" +
               "  \"uuid\": \"" + uid + "\",\n" +
               "  \"version\": " + appVersion + "\n" +
               "}\n";
    }

    protected static
    String getGnomeVersion() {
        String gnomeVersion = OS.DesktopEnv.INSTANCE.getGnomeVersion();
        if (gnomeVersion == null) {
            return null;
        }

        // We want "3.14" or "3.20" or whatever the latest version is (excluding the patch version info).
        final int indexOf = gnomeVersion.indexOf('.');
        final int nextIndexOf = gnomeVersion.indexOf('.', indexOf + 1);
        if (indexOf < nextIndexOf) {
            return gnomeVersion.substring(0, nextIndexOf);  // will be 3.14 (without the trailing '.1'), for example
        }

        return gnomeVersion;
    }

    /**
     * @return true if we need to upgrade/re-install the extension, false if we do not need to do anything
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected static
    boolean needsUpgrade(final String metadata, final File metaDatafile) {
        String existingMetadata = KotlinUtils.INSTANCE.readText(metaDatafile);

        if (SystemTray.DEBUG) {
            logger.debug("Extension already installed, checking for upgrade");
        }

        // have to check to see if the version is correct as well (otherwise we have to reinstall it)
        // compat for java 1.6

        // the metadata string we CHECK should equal the metadata string we PROVIDE
        if (metadata.equals(existingMetadata)) {
            // this means that our version info, etc. is the same - there is no need to update anything
            if (!SystemTray.DEBUG) {
                return false;
            } else {
                // if we are DEBUG, then we ALWAYS want to copy over our extension. We will have to manually restart the shell to see it
                logger.debug("Always upgrading extension in DEBUG mode");
            }
        }
        else {
            // this means that we need to reinstall our extension, since either GNOME or US have changed versions since
            // we last installed the extension.
            if (SystemTray.DEBUG) {
                logger.debug("Need to upgrade extension");
            }
        }

        return true;
    }
}
