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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dorkbox.executor.ShellAsyncExecutor;
import dorkbox.executor.ShellExecutor;
import dorkbox.systemTray.SystemTray;
import dorkbox.util.IO;
import dorkbox.util.OSUtil;

@SuppressWarnings({"DanglingJavadoc", "WeakerAccess"})
public
class ExtensionSupport {
    public static
    List<String> getEnabledExtensions() {
        // gsettings get org.gnome.shell enabled-extensions
        final ShellExecutor gsettings = new ShellExecutor();
        gsettings.setExecutable("gsettings");
        gsettings.addArgument("get");
        gsettings.addArgument("org.gnome.shell");
        gsettings.addArgument("enabled-extensions");
        gsettings.start();

        String output = gsettings.getOutput();

        // now we have to enable us if we aren't already enabled

        // gsettings get org.gnome.shell enabled-extensions
        // defaults are:
        //  - fedora 23:   ['background-logo@fedorahosted.org']  on
        //  - openSuse:
        //  - Ubuntu Gnome 16.04:   @as []

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

        ArrayList<String> strings = new ArrayList<String>(Arrays.asList(split));
        for (Iterator<String> iterator = strings.iterator(); iterator.hasNext(); ) {
            final String string = iterator.next();
            if (string.trim()
                      .isEmpty()) {
                iterator.remove();
            }
        }

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
        final ShellExecutor setGsettings = new ShellExecutor();
        setGsettings.setExecutable("gsettings");
        setGsettings.addArgument("set");
        setGsettings.addArgument("org.gnome.shell");
        setGsettings.addArgument("enabled-extensions");
        setGsettings.addArgument(stringBuilder.toString());
        setGsettings.start();
    }

    public static
    void unInstall(String UID, String restartCommand) {
        List<String> enabledExtensions = getEnabledExtensions();
        if (enabledExtensions.contains(UID)) {
            enabledExtensions.remove(UID);

            setEnabledExtensions(enabledExtensions);

            restartShell(restartCommand);
        }
    }

    public static
    void restartShell(String restartCommand) {
        // in some situations, you can no longer restart the shell in wayland. You must logout-login for shell modifications to apply
        // https://mail.gnome.org/archives/commits-list/2015-March/msg01019.html

        // HOWEVER, with wayland, shell-extensions that DO NO MODIFY THE GUI (we don't, we just add icons to it)
        // are enabled without a shell restart.

        // The app-indicator extension with wayland (on Fedora 27+) CANNOT restart the shell.


        logger.info("Restarting gnome-shell via '{}' so tray notification changes can be applied.", restartCommand);

        // now we have to restart the gnome shell via bash in a background process
        ShellAsyncExecutor.runShell(restartCommand);

        // We don't care when the shell restarts, since WHEN IT DOES restart, our extension will show our icon.
        // Until then however, there will be errors which can be ignored, because the shell-restart means everything works.
    }

    protected static
    String readFile(final File metaDatafile) {
        StringBuilder builder = new StringBuilder(256);
        BufferedReader bin = null;
        try {
            bin = new BufferedReader(new FileReader(metaDatafile));
            String line;
            while ((line = bin.readLine()) != null) {
                builder.append(line)
                       .append("\n");
            }
        } catch (Exception ignored) {
        } finally {
            if (bin != null) {
                try {
                    bin.close();
                } catch (IOException e) {
                    logger.error("Error closing: {}", bin, e);
                }
            }
        }

        return builder.toString();
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
    protected static
    boolean installFile(final String resourceName, final File targetDirectory) {
        final File outputFile = new File(targetDirectory, resourceName);

        InputStream reader = null;
        FileOutputStream fileOutputStream = null;
        try {
            reader = ExtensionSupport.class.getResourceAsStream(resourceName);
            fileOutputStream = new FileOutputStream(outputFile);

            if (reader == null) {
                logger.error("The {} file cannot be found. Something is severely wrong.", resourceName);
                return false;
            }

            IO.copyStream(reader, fileOutputStream);

            return true;
        } catch (FileNotFoundException e) {
            logger.error("Cannot find gnome-shell extension", e);
        } catch (IOException e) {
            logger.error("Unable to get gnome-shell extension", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.error("Error closing: {}", reader, e);
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

    /**
     * @return true if successful, false if there was a failure
     */
    protected static
    boolean installZip(final String zipResourceName, final File targetDirectory) {
        ZipInputStream inputStream = null;
        FileOutputStream fileOutputStream = null;

        try {
            inputStream = new ZipInputStream(ExtensionSupport.class.getResourceAsStream(zipResourceName));

            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = entry.getName();

                    try {
                        if (!entry.isDirectory()) {
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
                        }
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
    String createMetadata(final String uid, final String appVersion, final String gnomeVersion) {
        return "{\n" +
                "  \"description\": \"Moves the java SystemTray icon from inside the notification drawer to alongside the clock.\",\n" +
                "  \"name\": \"Dorkbox SystemTray\",\n" +
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
        String gnomeVersion = OSUtil.DesktopEnv.getGnomeVersion();
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
    protected static
    boolean needsUpgrade(final String metadata, final File metaDatafile) {
        String existingMetadata = ExtensionSupport.readFile(metaDatafile);

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
