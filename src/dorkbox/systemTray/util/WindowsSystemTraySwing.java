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
package dorkbox.systemTray.util;

import static dorkbox.systemTray.SystemTray.logger;

import java.awt.Robot;
import java.util.Locale;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.BootStrapClassLoader;
import dorkbox.util.OS;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * Fixes issues with some java runtimes
 */
public
class WindowsSystemTraySwing {

    // oh my. Java likes to think that ALL windows tray icons are 16x16.... Lets fix that!
    public static void fix() {
        // if we are using swing (in windows only) the icon size is usually incorrect. Here we have to fix that.
        if (!OS.isWindows()) {
            return;
        }

        String vendor = System.getProperty("java.vendor").toLowerCase(Locale.US);
        // spaces at the end to make sure we check for words
        if (!(vendor.contains("sun ") || vendor.contains("oracle "))) {
            // not fixing things that are not broken.
            return;
        }


        boolean isWindowsSwingTrayLoaded = false;

        try {
            // this is important to use reflection, because if JavaFX is not being used, calling getToolkit() will initialize it...
            java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            m.setAccessible(true);
            ClassLoader cl = ClassLoader.getSystemClassLoader();

            // if we are using swing (in windows only) the icon size is usually incorrect. We cannot fix that if it's already loaded.
            isWindowsSwingTrayLoaded = (null != m.invoke(cl, "sun.awt.windows.WTrayIconPeer")) ||
                                       (null != m.invoke(cl, "java.awt.SystemTray"));
        } catch (Throwable e) {
            if (SystemTray.DEBUG) {
                logger.debug("Error detecting javaFX/SWT mode", e);
            }
        }

        if (isWindowsSwingTrayLoaded) {
            throw new RuntimeException("Unable to initialize the swing tray in windows, it has already been created!");
        }

        /*
         * When DISTRIBUTING the JRE/JDK by Sun/Oracle, the license agreement states that we cannot create/modify specific files.
         *
         ************* (when DISTRIBUTING the JRE/JDK...)
         * C. Java Technology Restrictions. You may not create, modify, or change the behavior of, or authorize your licensees to create, modify,
         * or change the behavior of, classes, interfaces, or subpackages that are in any way identified as "java", "javax", "sun" or similar
         * convention as specified by Oracle in any naming convention designation.
         *************
         *
         * Since we are not distributing a modified file, it does not apply to us.
         *
         * Again, just to be ABSOLUTELY CLEAR. This is for DISTRIBUTING the runtime.
         *
         * ************************************
         * To follow the license for DISTRIBUTION, these files themselves CANNOT BE MODIFIED in any way,
         * and if they are modified THEY CANNOT BE DISTRIBUTED.
         * ************************************
         *
         * Important distinction: We are not DISTRIBUTING java, nor modifying the distribution class files.
         *
         * What we are doing is modifying what is already present, post-distribution, and it is impossible to distribute is modified
         *
         * To see what files we need to fix...
         * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/windows/native/sun/windows/awt_TrayIcon.cpp
         * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/windows/classes/sun/awt/windows/WTrayIconPeer.java
         */

        try {
            // necessary to initialize sun.awt.windows.WObjectPeer native initIDs()
            @SuppressWarnings("unused")
            Robot robot = new Robot();


            ClassPool pool = ClassPool.getDefault();
            byte[] trayBytes;
            byte[] trayIconBytes;

            {
                CtClass trayClass = pool.get("sun.awt.windows.WSystemTrayPeer");
                // now have to make a new "system tray" (that is null) in order to init/load this class completely
                // have to modify the SystemTray.getIconSize as well.
                trayClass.setModifiers(trayClass.getModifiers() & javassist.Modifier.PUBLIC);
                trayClass.getConstructors()[0].setModifiers(trayClass.getConstructors()[0].getModifiers() & javassist.Modifier.PUBLIC);
                CtMethod ctMethodGet = trayClass.getDeclaredMethod("getTrayIconSize");
                ctMethodGet.setBody("{" +
                                    "return new java.awt.Dimension(" + ImageUtils.SIZE + ", " + ImageUtils.SIZE + ");" +
                                    "}");

                trayBytes = trayClass.toBytecode();
            }

            {
                CtClass trayIconClass = pool.get("sun.awt.windows.WTrayIconPeer");
                CtMethod ctMethodCreate = trayIconClass.getDeclaredMethod("createNativeImage");
                CtMethod ctMethodUpdate = trayIconClass.getDeclaredMethod("updateNativeImage");

                int TRAY_MASK = (ImageUtils.SIZE * ImageUtils.SIZE) / 8;
                ctMethodCreate.setBody("{" +
                    "java.awt.image.BufferedImage bufferedImage = $1;\n" +

                    "java.awt.image.Raster rasterImage = bufferedImage.getRaster();\n" +
                    "final byte[] mask = new byte[" + TRAY_MASK + "];\n" +
                    "final int pixels[] = ((java.awt.image.DataBufferInt)rasterImage.getDataBuffer()).getData();\n" +

                    "int numberOfPixels = pixels.length;\n" +
                    "int rasterImageWidth = rasterImage.getWidth();\n" +

                    "for (int i = 0; i < numberOfPixels; i++) {\n" +
                    "    int iByte = i / 8;\n" +
                    "    int augmentMask = 1 << (7 - (i % 8));\n" +
                    "    if ((pixels[i] & 0xFF000000) == 0) {\n" +
                    "        if (iByte < mask.length) {\n" +
                    "            mask[iByte] |= augmentMask;\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n" +

                    "if (rasterImage instanceof sun.awt.image.IntegerComponentRaster) {\n" +
                    "    rasterImageWidth = ((sun.awt.image.IntegerComponentRaster)rasterImage).getScanlineStride();\n" +
                    "}\n" +

                    "setNativeIcon(((java.awt.image.DataBufferInt)bufferedImage.getRaster().getDataBuffer()).getData(), " +
                                   "mask, rasterImageWidth, rasterImage.getWidth(), rasterImage.getHeight());\n" +
                "}");

                ctMethodUpdate.setBody("{" +
                    "java.awt.Image image = $1;\n" +

                    "if (isDisposed()) {\n" +
                    "   return;\n" +
                    "}\n" +

                    "int imageWidth = image.getWidth(observer);\n" +
                    "int imageHeight = image.getWidth(observer);\n" +

                    "java.awt.image.BufferedImage trayIcon = new java.awt.image.BufferedImage(imageWidth, imageHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);\n" +
                    "java.awt.Graphics2D g = trayIcon.createGraphics();\n" +

                    "if (g != null) {\n" +
                    "   try {\n" +
                    // this will render the image "nicely"
                    "      g.addRenderingHints(new java.awt.RenderingHints(java.awt.RenderingHints.KEY_RENDERING," +
                                                                          "java.awt.RenderingHints.VALUE_RENDER_QUALITY));\n" +
                    "      g.drawImage(image, 0, 0, imageWidth, imageHeight, observer);\n" +

                    "      createNativeImage(trayIcon);\n" +

                    "      updateNativeIcon(!firstUpdate);\n" +
                    "      if (firstUpdate) {" +
                    "          firstUpdate = false;\n" +
                    "      }\n" +
                    "   } finally {\n" +
                    "      g.dispose();\n" +
                    "   }\n" +
                    "}" +
                "}");

                trayIconBytes = trayIconClass.toBytecode();
            }

            // whoosh, past the classloader and directly into memory.
            BootStrapClassLoader.defineClass(trayBytes);
            BootStrapClassLoader.defineClass(trayIconBytes);

            if (SystemTray.DEBUG) {
                logger.info("Successfully changed tray icon size to: {}", ImageUtils.SIZE);
            }
        } catch (Exception e) {
            logger.error("Error setting tray icon size to: {}", ImageUtils.SIZE, e);
        }
    }
}
