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
package dorkbox.systemTray.util;

import static dorkbox.systemTray.SystemTray.logger;

import java.util.concurrent.atomic.AtomicBoolean;

import dorkbox.jna.ClassUtils;
import dorkbox.os.OS;
import dorkbox.systemTray.SystemTray;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;


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
 */


/**
 * Fixes issues with some java runtimes
 */
@SuppressWarnings("JavadocLinkAsPlainText")
public
class SystemTrayFixesWindows {
    private static AtomicBoolean loaded = new AtomicBoolean(false);

    /**
     * NOTE: Only for SWING
     * <p>
     * oh my. Java likes to think that ALL windows tray icons are 16x16.... Lets fix that!
     * <p>
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/windows/native/sun/windows/awt_TrayIcon.cpp
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/windows/classes/sun/awt/windows/WTrayIconPeer.java
     */
    public static
    void fix(int trayIconSize) {
        if (loaded.getAndSet(true)) {
            // already loaded, no need to fix again in the same JVM.
            return;
        }

        // ONLY java <= 8
        if (OS.INSTANCE.getJavaVersion() > 8) {
            // there are problems with java 9+
            return;
        }


        if (SystemTrayFixes.isSwingTrayLoaded("sun.awt.windows.WTrayIconPeer")) {
            // we have to throw a significant error.
            throw new RuntimeException("Unable to initialize the Swing System Tray, it has already been created!");
        }

        try {
            // necessary to initialize sun.awt.windows.WObjectPeer native initIDs()
            @SuppressWarnings("unused")
            java.awt.Robot robot = new java.awt.Robot();


            ClassPool pool = ClassPool.getDefault();
            byte[] trayBytes;
            byte[] trayIconBytes;

            {
                CtClass trayClass = pool.get("sun.awt.windows.WSystemTrayPeer");
                // now have to make a new "system tray" (that is null) in order to init/load this class completely
                // have to modify the SystemTray.getIconSize as well.
                trayClass.setModifiers(trayClass.getModifiers() & Modifier.PUBLIC);
                trayClass.getConstructors()[0].setModifiers(trayClass.getConstructors()[0].getModifiers() & Modifier.PUBLIC);


                CtMethod method = trayClass.getDeclaredMethod("getTrayIconSize");
                CtBehavior[] methodInfos = new CtBehavior[]{method};

                SystemTrayFixes.fixTraySize(methodInfos, 16, trayIconSize);

                // perform pre-verification for the modified method
                method.getMethodInfo().rebuildStackMapForME(trayClass.getClassPool());

                trayBytes = trayClass.toBytecode();
            }

            {
                CtClass trayIconClass = pool.get("sun.awt.windows.WTrayIconPeer");
                CtMethod ctMethodCreate = trayIconClass.getDeclaredMethod("createNativeImage");
                CtMethod ctMethodUpdate = trayIconClass.getDeclaredMethod("updateNativeImage");

                int TRAY_MASK = (trayIconSize * trayIconSize) / 8;
                ctMethodCreate.setBody("{" +
                    "java.awt.image.BufferedImage bufferedImage = $1;" +

                    "java.awt.image.Raster rasterImage = bufferedImage.getRaster();" +
                    "final byte[] mask = new byte[" + TRAY_MASK + "];" +
                    "final int pixels[] = ((java.awt.image.DataBufferInt)rasterImage.getDataBuffer()).getData();" +

                    "int numberOfPixels = pixels.length;" +
                    "int rasterImageWidth = rasterImage.getWidth();" +

                    "for (int i = 0; i < numberOfPixels; i++) {" +
                        "int iByte = i / 8;" +
                        "int augmentMask = 1 << (7 - (i % 8));" +
                        "if ((pixels[i] & 0xFF000000) == 0) {" +
                            "if (iByte < mask.length) {" +
                                "mask[iByte] |= augmentMask;" +
                            "}" +
                        "}" +
                    "}" +

                    "if (rasterImage instanceof sun.awt.image.IntegerComponentRaster) {" +
                        "rasterImageWidth = ((sun.awt.image.IntegerComponentRaster)rasterImage).getScanlineStride();" +
                    "}" +

                    "setNativeIcon(((java.awt.image.DataBufferInt)bufferedImage.getRaster().getDataBuffer()).getData(), " +
                                   "mask, rasterImageWidth, rasterImage.getWidth(), rasterImage.getHeight());" +
                "}");

                ctMethodUpdate.setBody("{" +
                    "java.awt.Image image = $1;" +

                    "if (isDisposed()) {" +
                        "return;" +
                    "}" +

                    "int imageWidth = image.getWidth(observer);" +
                    "int imageHeight = image.getWidth(observer);" +

                    "java.awt.image.BufferedImage trayIcon = new java.awt.image.BufferedImage(imageWidth, imageHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);" +
                    "java.awt.Graphics2D g = trayIcon.createGraphics();" +

                    "if (g != null) {" +
                        "try {" +
                            // this will render the image "nicely"
                            "g.addRenderingHints(new java.awt.RenderingHints(java.awt.RenderingHints.KEY_RENDERING," +
                                                                            "java.awt.RenderingHints.VALUE_RENDER_QUALITY));" +
                            "g.drawImage(image, 0, 0, imageWidth, imageHeight, observer);" +

                            "createNativeImage(trayIcon);" +

                            "updateNativeIcon(!firstUpdate);" +
                            "if (firstUpdate) {" +
                                "firstUpdate = false;" +
                            "}" +
                        "} finally {" +
                            "g.dispose();" +
                        "}" +
                    "}" +
                "}");

                // perform pre-verification for the modified method
                ctMethodCreate.getMethodInfo().rebuildStackMapForME(trayIconClass.getClassPool());
                ctMethodUpdate.getMethodInfo().rebuildStackMapForME(trayIconClass.getClassPool());

                trayIconBytes = trayIconClass.toBytecode();
            }

            // whoosh, past the classloader and directly into memory.
            ClassUtils.defineClass(trayBytes);
            ClassUtils.defineClass(trayIconBytes);

            if (SystemTray.DEBUG) {
                logger.debug("Successfully changed tray icon size to: {}", trayIconSize);
            }
        } catch (Exception e) {
            logger.error("Error setting tray icon size to: {}", trayIconSize, e);
        }
    }
}
