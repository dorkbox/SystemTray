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

import java.awt.AWTException;
import java.util.Locale;

import dorkbox.systemTray.SystemTray;
import dorkbox.util.BootStrapClassLoader;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;


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
 * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/141beb4d854d/src/macosx/classes/sun/lwawt/macosx/CTrayIcon.java#l216
 */
/**
 * Fixes issues with some java runtimes
 */
public
class SystemTrayFixes {

    // oh my. Java likes to think that ALL windows tray icons are 16x16.... Lets fix that!
    // https://stackoverflow.com/questions/16378886/java-trayicon-right-click-disabled-on-mac-osx/35919788#35919788
    public static void fixWindows() {
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
                    "java.awt.image.BufferedImage bufferedImage = $1;" +

                    "java.awt.image.Raster rasterImage = bufferedImage.getRaster();" +
                    "final byte[] mask = new byte[" + TRAY_MASK + "];" +
                    "final int pixels[] = ((java.awt.image.DataBufferInt)rasterImage.getDataBuffer()).getData();" +

                    "int numberOfPixels = pixels.length;" +
                    "int rasterImageWidth = rasterImage.getWidth();" +

                    "for (int i = 0; i < numberOfPixels; i++) {" +
                    "    int iByte = i / 8;" +
                    "    int augmentMask = 1 << (7 - (i % 8));" +
                    "    if ((pixels[i] & 0xFF000000) == 0) {" +
                    "        if (iByte < mask.length) {" +
                    "            mask[iByte] |= augmentMask;" +
                    "        }" +
                    "    }" +
                    "}" +

                    "if (rasterImage instanceof sun.awt.image.IntegerComponentRaster) {" +
                    "    rasterImageWidth = ((sun.awt.image.IntegerComponentRaster)rasterImage).getScanlineStride();" +
                    "}" +

                    "setNativeIcon(((java.awt.image.DataBufferInt)bufferedImage.getRaster().getDataBuffer()).getData(), " +
                                   "mask, rasterImageWidth, rasterImage.getWidth(), rasterImage.getHeight());" +
                "}");

                ctMethodUpdate.setBody("{" +
                    "java.awt.Image image = $1;" +

                    "if (isDisposed()) {" +
                    "   return;" +
                    "}" +

                    "int imageWidth = image.getWidth(observer);" +
                    "int imageHeight = image.getWidth(observer);" +

                    "java.awt.image.BufferedImage trayIcon = new java.awt.image.BufferedImage(imageWidth, imageHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);" +
                    "java.awt.Graphics2D g = trayIcon.createGraphics();" +

                    "if (g != null) {" +
                    "   try {" +
                    // this will render the image "nicely"
                    "      g.addRenderingHints(new java.awt.RenderingHints(java.awt.RenderingHints.KEY_RENDERING," +
                                                                          "java.awt.RenderingHints.VALUE_RENDER_QUALITY));" +
                    "      g.drawImage(image, 0, 0, imageWidth, imageHeight, observer);" +

                    "      createNativeImage(trayIcon);" +

                    "      updateNativeIcon(!firstUpdate);" +
                    "      if (firstUpdate) {" +
                    "          firstUpdate = false;" +
                    "      }" +
                    "   } finally {" +
                    "      g.dispose();" +
                    "   }" +
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

    // MacOS AWT is hardcoded to respond only to lef-click for menus, where it should be any mouse button
    // https://stackoverflow.com/questions/16378886/java-trayicon-right-click-disabled-on-mac-osx/35919788#35919788
    // https://bugs.openjdk.java.net/browse/JDK-7158615
    public static void fixMacOS() {
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

            // if we are using AWT (in MacOS only) the menu trigger is incomplete. We cannot fix that if it's already loaded.
            isMacSwingTrayLoaded = (null != m.invoke(cl, "sun.lwawt.macosx.CTrayIcon")) ||
                                       (null != m.invoke(cl, "java.awt.SystemTray"));
        } catch (Throwable e) {
            if (SystemTray.DEBUG) {
                logger.debug("Error detecting if the MacOS SystemTray is loaded", e);
            }
        }

        if (isMacSwingTrayLoaded) {
            throw new RuntimeException("Unable to initialize the AWT tray in MacOSx, it has already been created!");
        }

        try {
            java.awt.Robot robot = new java.awt.Robot();
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        } catch (AWTException e) {
            e.printStackTrace();
        }

        ClassPool pool = ClassPool.getDefault();
        byte[] mouseEventBytes;

        try {
            CtClass trayClass = pool.get("sun.lwawt.macosx.CTrayIcon");
            // now have to make a new "system tray" (that is null) in order to init/load this class completely
            // have to modify the SystemTray.getIconSize as well.
            trayClass.setModifiers(trayClass.getModifiers() & javassist.Modifier.PUBLIC);
            trayClass.getConstructors()[0].setModifiers(trayClass.getConstructors()[0].getModifiers() & javassist.Modifier.PUBLIC);

            CtField ctField = new CtField(CtClass.intType, "lastButton", trayClass);
            trayClass.addField(ctField);

            ctField = new CtField(CtClass.intType, "lastX", trayClass);
            trayClass.addField(ctField);

            ctField = new CtField(CtClass.intType, "lastY", trayClass);
            trayClass.addField(ctField);

            ctField = new CtField(pool.get("java.awt.Robot"), "robot", trayClass);
            trayClass.addField(ctField);

            CtMethod ctMethodGet = trayClass.getDeclaredMethod("handleMouseEvent");
            ctMethodGet.setBody("{" +
                "sun.lwawt.macosx.NSEvent event = $1;" +

                "sun.awt.SunToolkit toolKit = (sun.awt.SunToolkit)java.awt.Toolkit.getDefaultToolkit();" +
                "int button = event.getButtonNumber();" +
                "int mouseX = event.getAbsX();" +
                "int mouseY = event.getAbsY();" +

                // have to intercept to see if it was a button click redirect to preserve what button was used in the event
                "if (lastButton == 1 && mouseX == lastX && mouseY == lastY) {" +
                    "java.lang.System.err.println(\"Redefining button press to \" + lastButton);" +
                    "button = lastButton;" +
                    "lastButton = -1;" +
                    "lastX = 0;" +
                    "lastY = 0;" +
                "}" +

                "if ((button <= 2 || toolKit.areExtraMouseButtonsEnabled()) && button <= toolKit.getNumberOfButtons() - 1) {" +
                    "int eventType = sun.lwawt.macosx.NSEvent.nsToJavaEventType(event.getType());" +
                    "int jButton = 0;" +
                    "int jClickCount = 0;" +

                    "if (eventType != 503) {" +
                        "jButton = sun.lwawt.macosx.NSEvent.nsToJavaButton(button);" +
                        "jClickCount = event.getClickCount();" +
                    "}" +

                    "java.lang.System.err.println(\"Click \" + jButton + \" event: \" + eventType);" +

                    "int mouseMods = sun.lwawt.macosx.NSEvent.nsToJavaMouseModifiers(button, event.getModifierFlags());" +
                    // surprisingly, this is false when the popup is showing
                    "boolean popupTrigger = sun.lwawt.macosx.NSEvent.isPopupTrigger(mouseMods);" +

                    "int mouseMask = jButton > 0 ? java.awt.event.MouseEvent.getMaskForButton(jButton) : 0;" +
                    "long event0 = System.currentTimeMillis();" +

                    "if(eventType == 501) {" +
                        "mouseClickButtons |= mouseMask;" +
                    "} else if(eventType == 506) {" +
                        "mouseClickButtons = 0;" +
                    "}" +


                    // have to swallow + re-dispatch events in specific cases. (right click)
                    "if (eventType == 501 && popupTrigger && button == 1) {" +
                        "java.lang.System.err.println(\"Redispatching mouse press. Has popupTrigger \" + popupTrigger + \" event: \" + eventType);" +
                        // we use Robot to click where we clicked, in order to "fool" the native part to show the popup
                        // For what it's worth, this is the only way to get the native bits to behave.
                        "if (robot == null) {" +
                            "try {" +
                                "robot = new java.awt.Robot();" +
                            "} catch (java.awt.AWTException e) {" +
                                "e.printStackTrace();" +
                            "}" +
                        "}" +
                        "lastButton = 1;" +
                        "lastX = mouseX;" +
                        "lastY = mouseY;" +

                        "robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);" +
                        "return;" +
                    "}" +


                    "java.awt.event.MouseEvent mEvent = new java.awt.event.MouseEvent(this.dummyFrame, eventType, event0, mouseMods, mouseX, mouseY, mouseX, mouseY, jClickCount, popupTrigger, jButton);" +

                    "mEvent.setSource(this.target);" +
                    "this.postEvent(mEvent);" +

                    // mouse press
                    "if (eventType == 501) {" +
                        "if (popupTrigger) {" +
                            "String event5 = this.target.getActionCommand();" +
                            "java.awt.event.ActionEvent event6 = new java.awt.event.ActionEvent(this.target, 1001, event5);" +
                            "this.postEvent(event6);" +
                        "}" +
                    "}" +

                    // mouse release
                    "if (eventType == 502) {" +
                        "if ((mouseClickButtons & mouseMask) != 0) {" +
                            "java.awt.event.MouseEvent event7 = new java.awt.event.MouseEvent(this.dummyFrame, 500, event0, mouseMods, mouseX, mouseY, mouseX, mouseY, jClickCount, popupTrigger, jButton);" +

                        "event7.setSource(this.target);" +
                        "this.postEvent(event7);" +
                    "}" +

                    "mouseClickButtons &= ~mouseMask;" +
                "}" +
                "}" +
            "}");

            mouseEventBytes = trayClass.toBytecode();

            // whoosh, past the classloader and directly into memory.
            BootStrapClassLoader.defineClass(mouseEventBytes);

            if (SystemTray.DEBUG) {
                logger.debug("Successfully changed mouse trigger");
            }
        } catch (Exception e) {
            logger.error("Error changing SystemTray mouse trigger.", e);
        }
    }


}
