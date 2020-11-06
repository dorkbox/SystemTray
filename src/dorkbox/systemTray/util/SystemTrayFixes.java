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

import dorkbox.jna.JnaClassUtils;
import dorkbox.systemTray.SystemTray;
import dorkbox.util.OS;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.InstructionPrinter;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Mnemonic;


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
public
class SystemTrayFixes {
    private static
    boolean isSwingTrayLoaded() {
        String className;

        if (OS.isWindows()) {
            className = "sun.awt.windows.WTrayIconPeer";
        }
        else if (OS.isMacOsX()){
            className = "sun.lwawt.macosx.CTrayIcon";
        }
        else {
            className = "sun.awt.X11.XTrayIconPeer";
        }

        try {
            // this is important to use reflection, because if JavaFX is not being used, calling getToolkit() will initialize it...
            java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            m.setAccessible(true);
            ClassLoader cl = ClassLoader.getSystemClassLoader();

            // if we are using swing the classes are already created and we cannot fix that if it's already loaded.
            return (null != m.invoke(cl, className)) || (null != m.invoke(cl, "java.awt.SystemTray"));
        } catch (Throwable e) {
            if (SystemTray.DEBUG) {
                logger.debug("Error detecting if the Swing SystemTray is loaded, unexpected error.", e);
            }
        }

        return true;
    }

    private static
    boolean isOracleVM() {
        String vendor = System.getProperty("java.vendor")
                              .toLowerCase(Locale.US);

        // spaces at the end to make sure we check for words
        return vendor.contains("sun ") || vendor.contains("oracle ");
    }


    /**
     * oh my. Java likes to think that ALL windows tray icons are 16x16.... Lets fix that!
     *
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/windows/native/sun/windows/awt_TrayIcon.cpp
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/windows/classes/sun/awt/windows/WTrayIconPeer.java
     */
    public static
    void fixWindows(int trayIconSize) {
        if (isOracleVM()) {
            // not fixing things that are not broken.
            return;
        }

        // ONLY java <= 8
        if (OS.javaVersion > 8) {
            // there are problems with java 9+
            return;
        }


        if (isSwingTrayLoaded()) {
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
                trayClass.setModifiers(trayClass.getModifiers() & javassist.Modifier.PUBLIC);
                trayClass.getConstructors()[0].setModifiers(trayClass.getConstructors()[0].getModifiers() & javassist.Modifier.PUBLIC);


                CtMethod method = trayClass.getDeclaredMethod("getTrayIconSize");
                CtBehavior methodInfos[] = new CtBehavior[]{ method };

                fixTraySize(methodInfos, 16, trayIconSize);

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
            JnaClassUtils.defineClass(trayBytes);
            JnaClassUtils.defineClass(trayIconBytes);

            if (SystemTray.DEBUG) {
                logger.debug("Successfully changed tray icon size to: {}", trayIconSize);
            }
        } catch (Exception e) {
            logger.error("Error setting tray icon size to: {}", trayIconSize, e);
        }
    }

    /**
     * MacOS AWT is hardcoded to respond only to left-click for menus, where it should be any mouse button
     *
     * https://stackoverflow.com/questions/16378886/java-trayicon-right-click-disabled-on-mac-osx/35919788#35919788
     * https://bugs.openjdk.java.net/browse/JDK-7158615
     *
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/macosx/classes/sun/lwawt/macosx/CTrayIcon.java
     */
    public static
    void fixMacOS() {
        if (isOracleVM()) {
            // not fixing things that are not broken.
            return;
        }


        if (isSwingTrayLoaded()) {
            // we have to throw a significant error.
            throw new RuntimeException("Unable to initialize the AWT System Tray, it has already been created!");
        }

        try {
            java.awt.Robot robot = new java.awt.Robot();
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        } catch (AWTException e) {
            e.printStackTrace();
        }

        ClassPool pool = ClassPool.getDefault();
        byte[] mouseEventBytes;
        int mouseDelay = 75;

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

            String nsEventFQND;
            if (OS.javaVersion <= 7) {
                nsEventFQND = "sun.lwawt.macosx.event.NSEvent";
            }
            else {
                nsEventFQND = "sun.lwawt.macosx.NSEvent";
            }


            ctMethodGet.setBody("{" +
                nsEventFQND + " event = $1;" +

                "sun.awt.SunToolkit toolKit = (sun.awt.SunToolkit)java.awt.Toolkit.getDefaultToolkit();" +
                "int button = event.getButtonNumber();" +
                "int mouseX = event.getAbsX();" +
                "int mouseY = event.getAbsY();" +

                // have to intercept to see if it was a button click redirect to preserve what button was used in the event
                "if (lastButton == 1 && mouseX == lastX && mouseY == lastY) {" +
                    // "java.lang.System.err.println(\"Redefining button press to 1\");" +

                    "button = 1;" +
                    "lastButton = -1;" +
                    "lastX = 0;" +
                    "lastY = 0;" +
                "}" +

                "if ((button <= 2 || toolKit.areExtraMouseButtonsEnabled()) && button <= toolKit.getNumberOfButtons() - 1) {" +
                    "int eventType = " + nsEventFQND + ".nsToJavaEventType(event.getType());" +
                    "int jButton = 0;" +
                    "int jClickCount = 0;" +

                    "if (eventType != 503) {" +
                        "jButton = " + nsEventFQND + ".nsToJavaButton(button);" +
                        "jClickCount = event.getClickCount();" +
                    "}" +

                    // "java.lang.System.err.println(\"Click \" + jButton + \" event: \" + eventType);" +

                    "int mouseMods = " + nsEventFQND + ".nsToJavaMouseModifiers(button, event.getModifierFlags());" +
                    // surprisingly, this is false when the popup is showing
                    "boolean popupTrigger = " + nsEventFQND + ".isPopupTrigger(mouseMods);" +

                    "int mouseMask = jButton > 0 ? java.awt.event.MouseEvent.getMaskForButton(jButton) : 0;" +
                    "long event0 = System.currentTimeMillis();" +

                    "if(eventType == 501) {" +
                        "mouseClickButtons |= mouseMask;" +
                    "} else if(eventType == 506) {" +
                        "mouseClickButtons = 0;" +
                    "}" +


                    // have to swallow + re-dispatch events in specific cases. (right click)
                    "if (eventType == 501 && popupTrigger && button == 1) {" +
                        // "java.lang.System.err.println(\"Redispatching mouse press. Has popupTrigger \" + " + "popupTrigger + \" event: \" + " + "eventType);" +

                        // we use Robot to left click where we right clicked, in order to "fool" the native part to show the popup
                        // For what it's worth, this is the only way to get the native bits to behave.
                        "if (robot == null) {" +
                            "try {" +
                                "robot = new java.awt.Robot();" +
                                "robot.setAutoDelay(40);" +
                                "robot.setAutoWaitForIdle(true);" +
                            "} catch (java.awt.AWTException e) {" +
                                "e.printStackTrace();" +
                            "}" +
                        "}" +

                        "lastButton = 1;" +
                        "lastX = mouseX;" +
                        "lastY = mouseY;" +

                        // the delay is necessary for this to work correctly. Mouse release is not necessary.
                        // this simulates *just enough* of the default behavior so that right click behaves the same as left click.
                        "int maskButton1 = java.awt.event.InputEvent.getMaskForButton(java.awt.event.MouseEvent.BUTTON1);" +
                        "robot.mousePress(maskButton1);" +
                        "robot.delay(" + mouseDelay + ");" +

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

            // perform pre-verification for the modified method
            ctMethodGet.getMethodInfo().rebuildStackMapForME(trayClass.getClassPool());

            mouseEventBytes = trayClass.toBytecode();

            // whoosh, past the classloader and directly into memory.
            JnaClassUtils.defineClass(mouseEventBytes);

            if (SystemTray.DEBUG) {
                logger.debug("Successfully changed mouse trigger for MacOSX");
            }
        } catch (Exception e) {
            logger.error("Error changing SystemTray mouse trigger for MacOSX.", e);
        }
    }

    /**
     * Linux/Unix/Solaris use X11 + AWT to add an AWT window to a spot in the notification panel. UNFORTUNATELY, AWT
     * components are heavyweight, and DO NOT support transparency -- so one gets a "grey" box as the background of the icon.
     *
     * Spectacularly enough, because this uses X11, it works on any X backend -- regardless of GtkStatusIcon or AppIndicator support. This
     * actually provides **more** support than GtkStatusIcons or AppIndicators, since this will ALWAYS work.
     *
     * Additionally, the size of the tray is hard-coded to be 24.
     *
     *
     * The down side, is that there is a "grey" box -- so hack around this issue by getting the color of a pixel in the notification area 1
     * off the corner, and setting that as the background.
     *
     * It would be better to take a screenshot of the space BEHIND the tray icon, but we can't do that because there is no way to get
     * the info BEFORE the AWT is added to the notification area. See comments below for more details.
     *
     * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6453521
     * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6267936
     *
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/solaris/classes/sun/awt/X11/XTrayIconPeer.java
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/solaris/classes/sun/awt/X11/XSystemTrayPeer.java
     */
    public static
    void fixLinux(int trayIconSize) {
        // linux/mac doesn't have transparent backgrounds for "swing" system tray icons

        if (isOracleVM()) {
            // not fixing things that are not broken.
            return;
        }

        if (isSwingTrayLoaded()) {
            // we have to throw a significant error.
            throw new RuntimeException("Unable to initialize the Swing System Tray, it has already been created!");
        }

        try {
            ClassPool pool = ClassPool.getDefault();
            CtField ctField;

            String className = "sun.awt.X11.XTrayIconPeer";
            byte[] eFrameBytes;
            byte[] trayIconBytes;
            byte[] trayPeerBytes;
            byte[] runnableBytes;
            byte[] iconCanvasBytes;

            {
                CtClass trayIconClass = pool.get(className);
                CtClass eFrameClass = null;
                CtClass iconCanvasClass = null;
                CtClass trayPeerClass;

                CtClass[] nestedClasses = trayIconClass.getNestedClasses();
                String xEmbedFrameName = className + "$XTrayIconEmbeddedFrame";
                String iconCanvasName = className + "$IconCanvas";
                for (CtClass nestedClass : nestedClasses) {
                    String name = nestedClass.getName();

                    if (name.equals(xEmbedFrameName)) {
                        eFrameClass = nestedClass;
                    }

                    if (name.equals(iconCanvasName)) {
                        iconCanvasClass = nestedClass;
                    }
                }

                if (eFrameClass == null || iconCanvasClass == null) {
                    throw new RuntimeException("Unable to find required classes to fix. Unable to continue initialization.");
                }


                ctField = new CtField(pool.get("java.awt.Robot"), "robot", trayIconClass);
                ctField.setModifiers(Modifier.STATIC);
                trayIconClass.addField(ctField);

                ctField = new CtField(pool.get("java.awt.Color"), "color", trayIconClass);
                ctField.setModifiers(Modifier.STATIC);
                trayIconClass.addField(ctField);

                ctField = new CtField(pool.get("java.awt.image.BufferedImage"), "image", trayIconClass);
                ctField.setModifiers(Modifier.STATIC);
                trayIconClass.addField(ctField);

                ctField = new CtField(pool.get("java.awt.Rectangle"), "rectangle", trayIconClass);
                ctField.setModifiers(Modifier.STATIC);
                trayIconClass.addField(ctField);

                // fix other classes for icon size.
                trayPeerClass = pool.get("sun.awt.X11.XSystemTrayPeer");

                // now we have to replace ALL instances (in the constructor and other methods), where the icon size is set (default is 24).
                // Since, looking at the source code, there is NO other case where the number 24 is used (except for size), we just
                // bytecode replace 24 with our specified size.

                CtConstructor constructor = trayIconClass.getDeclaredConstructors()[0]; // only 1 constructor
                CtMethod method1 = trayIconClass.getDeclaredMethod("getBounds");
                CtMethod method2 = trayPeerClass.getDeclaredMethod("getTrayIconSize");

                CtBehavior methodInfos[] = new CtBehavior[]{constructor, method1, method2};

                fixTraySize(methodInfos, 24, trayIconSize);

                // perform pre-verification for the modified method
                constructor.getMethodInfo().rebuildStackMapForME(trayIconClass.getClassPool());
                method1.getMethodInfo().rebuildStackMapForME(trayIconClass.getClassPool());
                method2.getMethodInfo().rebuildStackMapForME(trayPeerClass.getClassPool());



                // The screenshot we capture, is just a 1-pixel wide strip, that we stretch to the correct size. This is so we can
                // have the correct background when there is a gradient panel (which happens on Ubuntu and possibly others).
                // NOTE: This method doesn't work for panel background images that are not a gradient, and there is no easy way to solve that problem.

                // make our custom runnable (cannot do anonymous inner classes via javassist.
                {
                    CtClass runnable = pool.makeClass("sun.awt.X11.RunnableImpl");
                    runnable.addInterface(pool.get("java.lang.Runnable"));

                    ctField = new CtField(pool.get(xEmbedFrameName), "frame", runnable);
                    ctField.setModifiers(Modifier.PROTECTED);
                    runnable.addField(ctField);

                    ctField = new CtField(pool.get("java.awt.Rectangle"), "size", runnable);
                    ctField.setModifiers(Modifier.PROTECTED);
                    runnable.addField(ctField);

                    ctField = new CtField(CtClass.intType, "attempts", runnable);
                    ctField.setModifiers(Modifier.PROTECTED);
                    runnable.addField(ctField);

                    CtMethod method = CtNewMethod.make("public void run() { " +
                        "java.awt.Point loc = frame.getLocationOnScreen();" +

                        "if (loc.x == 0 && loc.y == 0 && attempts < 10) {" +
                            // we still don't know! Reschedule.
                            "attempts++;" +
                            "java.awt.EventQueue.invokeLater(this);" +
                            // "System.err.println(\"Reschedule: \" + loc.x + \" : \" + loc.y);" +
                            "return;" +
                        "}" +

                        // "System.err.println(\"Actual location: \" + loc.x + \" : \" + loc.y);" +

                        // offset the pixel grabbing location, if possible. If we go negative, weird colors happen.
                        // which ever is the larger dimension (usually) is the orientation of the bar.
                        "java.awt.Rectangle rect;" +
                        "if (loc.x > loc.y) {" +
                            // horizontal panel.
                            "rect = new java.awt.Rectangle(loc.x-1, loc.y, 1, size.height);" +

                            // Sometimes the parent panel is LARGER than the icon, so we grab the color at the correct spot so there aren't any "weird" strips of color at the bottom of the icon.
                            "if (loc.y < 300) {" +
                                // panel is at the top of the screen (guessing...)
                                className + ".color = " + className + ".robot.getPixelColor(rect.x, rect.y + rect.height-1);" +
                            "} else {" +
                                // panel is at the bottom of the screen (guessing)
                                className + ".color = " + className + ".robot.getPixelColor(rect.x, rect.y);" +
                            "}" +
                        "} else {" +
                            // vertical panel (don't think this will happen much, but in case it does...)
                            "rect = new java.awt.Rectangle(loc.x, loc.y-1, size.width, 1);" +
                            className + ".color = " + className + ".robot.getPixelColor(rect.x, rect.y);" +
                        "}" +

                        // screen shot a strip, that we then modify the background of the tray icon
                        className + ".image = " + className + ".robot.createScreenCapture(rect);" +

                        // keeps track of the capture size, which can be DIFFERENT than the icon size
                        className + ".rectangle = rect;" +

                        // "System.err.println(\"capture location: \" + rect);" +

                        // this sets the background of the native component, NOT THE ICON (otherwise weird "grey" flashes occur)
                        "frame.setBackground(" + className + ".color);" +
                    "}", runnable);
                    runnable.addMethod(method);

                    runnableBytes = runnable.toBytecode();
                }


                {
                    // gets the pixel color just to the side of the icon. The CRITICAL thing to notice, is that this happens before the
                    // AWT window is positioned, so there can be a different system tray icon at this position (at this exact point in
                    // time). This means we cannot take a screen shot because before the window is placed, another icon is in this
                    // spot; and when the window is placed, it's too late to take a screenshot. The second best option is to take a sample of
                    // the pixel color, so at least we can fake transparency (this is what we do). This only works if the notification area
                    // is a solid color, and not an image or gradient.
                    CtMethod methodVisible = CtNewMethod.make("public void setVisible(boolean b) " +
                    "{ " +
                        "if (b) {" +
                            "if (" + className + ".robot == null) {" +
                                className + ".robot = new java.awt.Robot();" +

                                "sun.awt.X11.RunnableImpl r = new sun.awt.X11.RunnableImpl();" +
                                "r.frame = this;" +
                                "r.size = getBoundsPrivate();" +

                                // run our custom runnable on the event queue, so that we can get the location after it's been placed
                                "java.awt.EventQueue.invokeLater(r);" +
                            "}" +

                            // the problem here is that on SOME linux OSes, the location is invalid! So we check again on the EDT
                            "java.awt.Point loc = getPeer().getLocationOnScreen();" +

                            "int locX = loc.x;" +
                            "int locY = loc.y;" +

                            "if (!(locX == 0 && locY == 0)) {" +
                                // offset the pixel grabbing location, if possible. If we go negative, weird colors happen.
                                "if (locX > 0) locX -= 1;" +
                                "if (locY > 0) locY -= 1;" +

                                className + ".color = " + className + ".robot.getPixelColor(locX, locY);" +

                                // this sets the background of the native component, NOT THE ICON (otherwise weird "grey" flashes occur)
                              "setBackground(" + className + ".color);" +
                            "}" +
                        "}" +
                        "super.setVisible(b);" +
                    "}", eFrameClass);
                    eFrameClass.addMethod(methodVisible);
                    methodVisible.getMethodInfo()
                                 .rebuildStackMapForME(eFrameClass.getClassPool());

                    eFrameBytes = eFrameClass.toBytecode();
                }

                {
                    CtMethod ctMethodPaint = iconCanvasClass.getDeclaredMethod("paint");
                    String body = "{" + "java.awt.Graphics g = $1;" +
                        "if (g != null && curW > 0 && curH > 0) {" +
                            "java.awt.image.BufferedImage bufImage = new java.awt.image.BufferedImage(curW, curH, java.awt.image.BufferedImage.TYPE_INT_ARGB);" +
                            "java.awt.Graphics2D gr = bufImage.createGraphics();" +

                            "if (gr != null) {" +
                                "try {" +
                                    // this will render the image "nicely"
                                    "gr.addRenderingHints(new java.awt.RenderingHints(java.awt.RenderingHints.KEY_RENDERING," +
                                    "java.awt.RenderingHints.VALUE_RENDER_QUALITY));" +

                                    "gr.setColor(getBackground());" +
                                    "gr.fillRect(0, 0, curW, curH);" +

                                    "if (" + className + ".image != null) {" +
                                        "gr.drawImage(" + className + ".image, 0, 0, curW, curH, null);" +
                                    "}" +

                                    "gr.drawImage(image, 0, 0, curW, curH, observer);" +
                                    "gr.dispose();" +
                                    "g.drawImage(bufImage, 0, 0, curW, curH, null);" +
                                "} finally {" +
                                    "g.dispose();" +
                                "}" +
                            "}" +
                        "}" +
                    "}";
                    ctMethodPaint.setBody(body);

                    iconCanvasBytes = iconCanvasClass.toBytecode();
                }

                trayIconBytes = trayIconClass.toBytecode();
                trayPeerBytes = trayPeerClass.toBytecode();
            }

            // whoosh, past the classloader and directly into memory.
            JnaClassUtils.defineClass(runnableBytes);
            JnaClassUtils.defineClass(eFrameBytes);
            JnaClassUtils.defineClass(iconCanvasBytes);
            JnaClassUtils.defineClass(trayIconBytes);
            JnaClassUtils.defineClass(trayPeerBytes);

            if (SystemTray.DEBUG) {
                logger.debug("Successfully changed tray icon background color");
            }
        } catch (Exception e) {
            logger.error("Error setting tray icon background color", e);
        }
    }

    private static
    void fixTraySize(final CtBehavior[] behaviors, final int oldTraySize, final int newTraySize) {
        for (CtBehavior behavior : behaviors) {
            MethodInfo methodInfo = behavior.getMethodInfo();
            CodeIterator methodIterator = methodInfo.getCodeAttribute().iterator();

            while (methodIterator.hasNext()) {
                int index;
                try {
                    index = methodIterator.next();
                    int opcode = methodIterator.byteAt(index);

                    switch (opcode) {
                        case javassist.bytecode.Opcode.BIPUSH: {
                            int i = methodIterator.byteAt(index + 1);

                            if (i == oldTraySize) {
                                // re-write this to be our custom size.
                                methodIterator.writeByte((byte) newTraySize, index + 1);
                            }
                        }
                    }
                } catch (BadBytecode badBytecode) {
                    badBytecode.printStackTrace();
                }
            }
        }
    }

    private static
    void showMethodBytecode(final CtBehavior constructorOrMethod) throws BadBytecode {
        MethodInfo methodInfo = constructorOrMethod.getMethodInfo(); // only 1 constructor
        ConstPool pool2 = methodInfo.getConstPool();
        CodeIterator ci = methodInfo.getCodeAttribute().iterator();
        int lineNumber = -1;
        StringBuilder collector = new StringBuilder();
        int lastLine = -1;

        while (ci.hasNext()) {
            int index = ci.next();
            lineNumber = methodInfo.getLineNumber(index);
            int op = ci.byteAt(index);

            if (lastLine == -1) {
                lastLine = lineNumber;
            }

            if (lineNumber != lastLine) {
                if (collector.length() > 0) {
                    System.err.println(lastLine + " : " + collector);
                }
                lastLine = lineNumber;
                collector.delete(0, collector.length());
            }

            collector.append(Mnemonic.OPCODE[op])
                     .append(" ");

            System.out.println(lineNumber + " * " + Mnemonic.OPCODE[op] + "  ");
            System.out.println(lineNumber + " * " + InstructionPrinter.instructionString(ci, index, pool2));
        }

        if (collector.length() > 0) {
            System.err.println(lineNumber + " : " + collector);
        }
    }
}
