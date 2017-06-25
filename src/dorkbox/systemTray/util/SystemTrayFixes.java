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
import dorkbox.util.OS;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;


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
        return !(vendor.contains("sun ") || vendor.contains("oracle "));
    }


    /**
     * oh my. Java likes to think that ALL windows tray icons are 16x16.... Lets fix that!
     *
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/windows/native/sun/windows/awt_TrayIcon.cpp
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/windows/classes/sun/awt/windows/WTrayIconPeer.java
     */
    public static void fixWindows(int trayIconSize) {
        if (isOracleVM()) {
            // not fixing things that are not broken.
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
                CtMethod ctMethodGet = trayClass.getDeclaredMethod("getTrayIconSize");
                ctMethodGet.setBody("{" +
                                    "return new java.awt.Dimension(" + trayIconSize + ", " + trayIconSize + ");" +
                                    "}");

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
                logger.debug("Successfully changed tray icon size to: {}", trayIconSize);
            }
        } catch (Exception e) {
            logger.error("Error setting tray icon size to: {}", trayIconSize, e);
        }
    }

    /**
     * MacOS AWT is hardcoded to respond only to lef-click for menus, where it should be any mouse button
     *
     * https://stackoverflow.com/questions/16378886/java-trayicon-right-click-disabled-on-mac-osx/35919788#35919788
     * https://bugs.openjdk.java.net/browse/JDK-7158615
     *
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/macosx/classes/sun/lwawt/macosx/CTrayIcon.java
     */
    public static void fixMacOS() {
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

            mouseEventBytes = trayClass.toBytecode();

            // whoosh, past the classloader and directly into memory.
            BootStrapClassLoader.defineClass(mouseEventBytes);

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
     * Additionally, the size of the tray is hard-coded to be 24 -- so we want to fix that as well.
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
    void fixLinux() {
        // linux/mac doesn't have transparent backgrounds for "swing" system tray icons
        // TODO Fix the tray icon size to something other than (hardcoded) 24. This will be a lot of work, since this value is in the
        // constructor, and there is a significant amount of code and anonymous classes there - and javassist does not support non-staic
        // anonymous classes.

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

            {
                CtClass trayIconClass = pool.get(className);

                ctField = new CtField(pool.get("java.awt.Robot"), "robot", trayIconClass);
                ctField.setModifiers(Modifier.STATIC);
                trayIconClass.addField(ctField);

                ctField = new CtField(pool.get("java.awt.Color"), "color", trayIconClass);
                ctField.setModifiers(Modifier.STATIC);
                trayIconClass.addField(ctField);

                trayIconBytes = trayIconClass.toBytecode();


                CtClass eFrameClass = null;
                CtClass[] nestedClasses = trayIconClass.getNestedClasses();
                for (CtClass nestedClass : nestedClasses) {
                    String name = nestedClass.getName();

                    if (name.equals(className + "$XTrayIconEmbeddedFrame")) {
                        eFrameClass = nestedClass;
                    }
                }

                if (eFrameClass == null) {
                    throw new RuntimeException("Unable to find required classes to fix. Unable to continue initialization.");
                }

                // gets the pixel color just to the side of the icon. The CRITICAL thing to notice, is that this happens before the
                // AWT window is positioned, so there can be a different system tray icon at this position (at this exact point in
                // time. This means we cannot take a screen shot, because before the window is placed, another icon is in this spot,
                // and when the window is placed, it's too late to take a screenshot. The second best option is to take a sample of
                // the pixel color, so at least we can fake transparency. This only works if the notification area is a solid color,
                // and not an image or gradient.
                CtMethod methodVisible = CtNewMethod.make(
                "public void setVisible(boolean b) " +
                "{ " +
                    "if (b) {" +
                        "if (" + className + ".robot == null) {" +
                            className + ".robot = new java.awt.Robot();" +
                        "}" +

                        "java.awt.Point loc = getPeer().getLocationOnScreen();" +
                        className + ".color = " + className + ".robot.getPixelColor(loc.x-1, loc.y-1);" +
                        // this sets the background of the native component, NOT THE ICON (otherwise weird "grey" flashes occur
                        "setBackground(" + className + ".color);" +
                    "}" +

                    "super.setVisible(b);" +
                " }",
                                eFrameClass);
                eFrameClass.addMethod(methodVisible);

                eFrameBytes = eFrameClass.toBytecode();


//                CtMethod ctMethodRepaintImage = iconCanvasClass.getDeclaredMethod("repaintImage");


//                MethodInfo methodInfo = ctMethodRepaintImage.getMethodInfo();
//                ConstPool pool2 = methodInfo.getConstPool();
//                CodeIterator ci = methodInfo.getCodeAttribute().iterator();
//                int lineNumber = -1;
//
//                while (ci.hasNext()) {
//                    int index = ci.next();
//                    lineNumber = methodInfo.getLineNumber(index);
//                    int opcode = ci.byteAt(index);
//
//                    String op = Mnemonic.OPCODE[opcode];
//                    switch (opcode) {
//                        case INVOKEVIRTUAL: {
//                            int i = ci.u16bitAt(index + 1);
//
//                            // specifically inject the pixel grabbing code JUST before update happens.
//                            if (pool2.getMethodrefClassName(i)
//                                     .equals("sun.awt.X11.XTrayIconPeer$IconCanvas") &&
//
//                                pool2.getMethodrefName(i)
//                                     .equals("update") &&
//
//                                pool2.getMethodrefType(i)
//                                     .equals("(Ljava/awt/Graphics;)V")) {
//
//
////                                "           if (robot == null) {" +
////                                "               robot = new java.awt.Robot();" +
////                                "           }" +
////                                "       if (doClear) {" +

////                                "              java.awt.Point loc = getLocationOnScreen();" +
////                                "              color = robot.getPixelColor(loc.x-1, loc.y-1);" +
//
//
//                                System.out.println(op + " " + "#" + i + " = Method " + pool2.getMethodrefClassName(i) + "." +
//                                                   pool2.getMethodrefName(i) + "(" + pool2.getMethodrefType(i) + ")");
//                            }
//                        }
//                    }
//
////                    System.out.println(lineNumber + " * " + Mnemonic.OPCODE[op] + "  ");
//                    System.out.println(lineNumber + " * " + InstructionPrinter.instructionString(ci, index, pool2));
//                }

//new Canvas().update();

//                CtMethod methodUpdate = iconCanvasClass.getMethod("update", "(Ljava/awt/Graphics;)V");


//                for(CtMethod method:iconCanvasClass.getDeclaredMethods()){
//                    System.err.println("MEthod: " + method.getName());
//                    method.insertBefore("System.out.println(\"Before every method call....\");");
//                }
//
//                for (CtMethod method : iconCanvasClass.getMethods()) {
////                    System.err.println("Method: " + method.getName());
//                    if (method.getName()
//                              .equals("getBackground")) {
//
//                        System.err.println("found " + method.getName());
////                        method.insertBefore("System.err.println(\"Before every method call....\");");
//                        method.setBody("return color;");
//                    }
//                }


//                StringBuilder collector = new StringBuilder();
//                int lastLine = -1;
//
//                while (ci.hasNext()) {
//                    int index = ci.next();
//                    lineNumber = methodInfo.getLineNumber(index);
//                    int op = ci.byteAt(index);
//
//                    if (lastLine == -1) {
//                        lastLine = lineNumber;
//                    }
//
//                    if (lineNumber != lastLine) {
//                        if (collector.length() > 0) {
//                            System.out.println(lastLine + " : " + collector);
//                        }
//                        lastLine = lineNumber;
//                        collector.delete(0, collector.length());
//                    }
//
//                    collector.append(Mnemonic.OPCODE[op])
//                             .append(" ");
//
////                    System.out.println(lineNumber + " * " + Mnemonic.OPCODE[op] + "  ");
//                    System.out.println(lineNumber + " * " + InstructionPrinter.instructionString(ci, index, pool2));
//                }
//
//                if (collector.length() > 0) {
//                    System.out.println(lineNumber + " : " + collector);
//                }








//                String body = "{" +
//                    "boolean doClear = $1;" +
//
//                    "java.lang.System.err.println(\"update image: \" + doClear);" +
//
//                    "java.awt.Graphics g = getGraphics();" +
//                    "if (g != null) {" +
//                    "   try {" +
//                    "       if (isVisible()) {" +
//                    "           if (robot == null) {" +
//                    "               robot = new java.awt.Robot();" +
//                    "           }" +
//                    "       if (doClear) {" +
//                    // gets the pixel color just to the side of the icon. The CRITICAL thing to notice, is that this happens before the
//                    // AWT window is positioned, so there can be a different system tray icon at this position (at this exact point in
//                    // time. This means we cannot take a screen shot, because before the window is placed, another icon is in this spot,
//                    // and when the window is placed, it's too late to take a screenshot. The second best option is to take a sample of
//                    // the pixel color, so at least we can fake transparency. This only works if the notification area is a solid color,
//                    // and not an image or gradient.
//                    "              java.awt.Point loc = getLocationOnScreen();" +
//                    "              color = robot.getPixelColor(loc.x-1, loc.y-1);" +
//                    "              update(g);" +
//                    "          } else {" +
//                    "              paint(g);" +
//                    "          }" +
//                    "      }" +
//                    "  } finally {" +
//                    "      g.dispose();" +
//                    "  }" +
//                    "}" +
//                "}";
//                ctMethodRepaintImage.setBody(body);

//
//                CtMethod ctMethodPaint = iconCanvasClass.getDeclaredMethod("paint");
//                body = "{" +
//                    "java.awt.Graphics g = $1;" +
//
//                    "if (g != null && curW > 0 && curH > 0) {" +
//                    "     java.awt.image.BufferedImage bufImage = new java.awt.image.BufferedImage(curW, curH, java.awt.image.BufferedImage.TYPE_INT_ARGB);" +
//                    "     java.awt.Graphics2D gr = bufImage.createGraphics();" +
//                    "     if (gr != null) {" +
//                    "         try {" +
//
//                    // this will render the image "nicely"
//                    "             gr.addRenderingHints(new java.awt.RenderingHints(java.awt.RenderingHints.KEY_RENDERING," +
//                    "                                                              java.awt.RenderingHints.VALUE_RENDER_QUALITY));" +
//
//                    // Have to replace the color with the correct pixel color to simulate transparency
//                    "             gr.setColor(color);" +
//                    "             gr.fillRect(0, 0, curW, curH);" +
//                    "             gr.drawImage(image, 0, 0, curW, curH, observer);" +
//                    "             gr.dispose();" +
//                    "             g.drawImage(bufImage, 0, 0, curW, curH, null);" +
//                    "          } finally {" +
//                    "             g.dispose();" +
//                    "          }" +
//                    "     }" +
//                    "}" +
//                "}";
//                ctMethodPaint.setBody(body);


//                iconCanvasClass.writeFile("/tmp/modifiedClassesFolder");




            }

            // whoosh, past the classloader and directly into memory.
            BootStrapClassLoader.defineClass(trayIconBytes);
            BootStrapClassLoader.defineClass(eFrameBytes);

            if (SystemTray.DEBUG) {
                logger.debug("Successfully changed tray icon background color");
            }
        } catch (Exception e) {
            logger.error("Error setting tray icon background color", e);
        }
    }
}
