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
import java.awt.event.KeyEvent;
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
class SystemTrayFixes {

    // oh my. Java likes to think that ALL windows tray icons are 16x16.... Lets fix that!
    // https://stackoverflow.com/questions/16378886/java-trayicon-right-click-disabled-on-mac-osx/35919788#35919788
    public static void fixWindows() {
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
                logger.debug("Error detecting if the Swing SystemTray is loaded", e);
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
         * What we are doing is modifying what is already present, post-distribution, and it is impossible to distribute what is modified
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
                                    "return new java.awt.Dimension(" + ImageUtils.TRAY_SIZE + ", " + ImageUtils.TRAY_SIZE + ");" +
                                    "}");

                trayBytes = trayClass.toBytecode();
            }

            {
                CtClass trayIconClass = pool.get("sun.awt.windows.WTrayIconPeer");
                CtMethod ctMethodCreate = trayIconClass.getDeclaredMethod("createNativeImage");
                CtMethod ctMethodUpdate = trayIconClass.getDeclaredMethod("updateNativeImage");

                int TRAY_MASK = (ImageUtils.TRAY_SIZE * ImageUtils.TRAY_SIZE) / 8;
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
                logger.debug("Successfully changed tray icon size to: {}", ImageUtils.TRAY_SIZE);
            }
        } catch (Exception e) {
            logger.error("Error setting tray icon size to: {}", ImageUtils.TRAY_SIZE, e);
        }
    }

    // MacOS AWT is hardcoded to respond only to "popup trigger" for menus, where it should be any mouse button
    // https://stackoverflow.com/questions/16378886/java-trayicon-right-click-disabled-on-mac-osx/35919788#35919788
    // https://bugs.openjdk.java.net/browse/JDK-7158615
    public static void fixMacOS() {
        if (!OS.isWindows()) {
            return;
        }

        String vendor = System.getProperty("java.vendor").toLowerCase(Locale.US);
        // spaces at the end to make sure we check for words
        if (!(vendor.contains("sun ") || vendor.contains("oracle "))) {
            // not fixing things that are not broken.
            return;
        }



        boolean isMacSwingTrayLoaded = false;

        try {
            // this is important to use reflection, because if JavaFX is not being used, calling getToolkit() will initialize it...
            java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            m.setAccessible(true);
            ClassLoader cl = ClassLoader.getSystemClassLoader();

            // if we are using swing (in windows only) the icon size is usually incorrect. We cannot fix that if it's already loaded.
            isMacSwingTrayLoaded = (null != m.invoke(cl, "sun.lwawt.macosx.CTrayIcon")) ||
                                       (null != m.invoke(cl, "java.awt.SystemTray"));
        } catch (Throwable e) {
            if (SystemTray.DEBUG) {
                logger.debug("Error detecting if the MacOS SystemTray is loaded", e);
            }
        }

        if (isMacSwingTrayLoaded) {
            throw new RuntimeException("Unable to initialize the swing tray in windows, it has already been created!");
        }
    }

    /**
     * Converts a key character into it's corresponding VK entry
     */
    public static
    int getVirtualKey(final char key) {
        switch (key) {
            case 0x08: return KeyEvent.VK_BACK_SPACE;
            case 0x09: return KeyEvent.VK_TAB;
            case 0x0a: return KeyEvent.VK_ENTER;
            case 0x1B: return KeyEvent.VK_ESCAPE;
            case 0x20AC: return KeyEvent.VK_EURO_SIGN;
            case 0x20: return KeyEvent.VK_SPACE;
            case 0x21: return KeyEvent.VK_EXCLAMATION_MARK;
            case 0x22: return KeyEvent.VK_QUOTEDBL;
            case 0x23: return KeyEvent.VK_NUMBER_SIGN;
            case 0x24: return KeyEvent.VK_DOLLAR;
            case 0x26: return KeyEvent.VK_AMPERSAND;
            case 0x27: return KeyEvent.VK_QUOTE;
            case 0x28: return KeyEvent.VK_LEFT_PARENTHESIS;
            case 0x29: return KeyEvent.VK_RIGHT_PARENTHESIS;
            case 0x2A: return KeyEvent.VK_ASTERISK;
            case 0x2B: return KeyEvent.VK_PLUS;
            case 0x2C: return KeyEvent.VK_COMMA;
            case 0x2D: return KeyEvent.VK_MINUS;
            case 0x2E: return KeyEvent.VK_PERIOD;
            case 0x2F: return KeyEvent.VK_SLASH;
            case 0x30: return KeyEvent.VK_0;
            case 0x31: return KeyEvent.VK_1;
            case 0x32: return KeyEvent.VK_2;
            case 0x33: return KeyEvent.VK_3;
            case 0x34: return KeyEvent.VK_4;
            case 0x35: return KeyEvent.VK_5;
            case 0x36: return KeyEvent.VK_6;
            case 0x37: return KeyEvent.VK_7;
            case 0x38: return KeyEvent.VK_8;
            case 0x39: return KeyEvent.VK_9;
            case 0x3A: return KeyEvent.VK_COLON;
            case 0x3B: return KeyEvent.VK_SEMICOLON;
            case 0x3C: return KeyEvent.VK_LESS;
            case 0x3D: return KeyEvent.VK_EQUALS;
            case 0x3E: return KeyEvent.VK_GREATER;
            case 0x40: return KeyEvent.VK_AT;
            case 0x41: return KeyEvent.VK_A;
            case 0x42: return KeyEvent.VK_B;
            case 0x43: return KeyEvent.VK_C;
            case 0x44: return KeyEvent.VK_D;
            case 0x45: return KeyEvent.VK_E;
            case 0x46: return KeyEvent.VK_F;
            case 0x47: return KeyEvent.VK_G;
            case 0x48: return KeyEvent.VK_H;
            case 0x49: return KeyEvent.VK_I;
            case 0x4A: return KeyEvent.VK_J;
            case 0x4B: return KeyEvent.VK_K;
            case 0x4C: return KeyEvent.VK_L;
            case 0x4D: return KeyEvent.VK_M;
            case 0x4E: return KeyEvent.VK_N;
            case 0x4F: return KeyEvent.VK_O;
            case 0x50: return KeyEvent.VK_P;
            case 0x51: return KeyEvent.VK_Q;
            case 0x52: return KeyEvent.VK_R;
            case 0x53: return KeyEvent.VK_S;
            case 0x54: return KeyEvent.VK_T;
            case 0x55: return KeyEvent.VK_U;
            case 0x56: return KeyEvent.VK_V;
            case 0x57: return KeyEvent.VK_W;
            case 0x58: return KeyEvent.VK_X;
            case 0x59: return KeyEvent.VK_Y;
            case 0x5A: return KeyEvent.VK_Z;
            case 0x5B: return KeyEvent.VK_OPEN_BRACKET;
            case 0x5C: return KeyEvent.VK_BACK_SLASH;
            case 0x5D: return KeyEvent.VK_CLOSE_BRACKET;
            case 0x5E: return KeyEvent.VK_CIRCUMFLEX;
            case 0x5F: return KeyEvent.VK_UNDERSCORE;
            case 0x60: return KeyEvent.VK_BACK_QUOTE;
            case 0x61: return KeyEvent.VK_A;
            case 0x62: return KeyEvent.VK_B;
            case 0x63: return KeyEvent.VK_C;
            case 0x64: return KeyEvent.VK_D;
            case 0x65: return KeyEvent.VK_E;
            case 0x66: return KeyEvent.VK_F;
            case 0x67: return KeyEvent.VK_G;
            case 0x68: return KeyEvent.VK_H;
            case 0x69: return KeyEvent.VK_I;
            case 0x6A: return KeyEvent.VK_J;
            case 0x6B: return KeyEvent.VK_K;
            case 0x6C: return KeyEvent.VK_L;
            case 0x6D: return KeyEvent.VK_M;
            case 0x6E: return KeyEvent.VK_N;
            case 0x6F: return KeyEvent.VK_O;
            case 0x70: return KeyEvent.VK_P;
            case 0x71: return KeyEvent.VK_Q;
            case 0x72: return KeyEvent.VK_R;
            case 0x73: return KeyEvent.VK_S;
            case 0x74: return KeyEvent.VK_T;
            case 0x75: return KeyEvent.VK_U;
            case 0x76: return KeyEvent.VK_V;
            case 0x77: return KeyEvent.VK_W;
            case 0x78: return KeyEvent.VK_X;
            case 0x79: return KeyEvent.VK_Y;
            case 0x7A: return KeyEvent.VK_Z;
            case 0x7B: return KeyEvent.VK_BRACELEFT;
            case 0x7D: return KeyEvent.VK_BRACERIGHT;
            case 0x7F: return KeyEvent.VK_DELETE;
            case 0xA1: return KeyEvent.VK_INVERTED_EXCLAMATION_MARK;
        }

        return 0;
    }
}
