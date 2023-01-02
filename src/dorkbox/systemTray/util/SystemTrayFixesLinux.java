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

import dorkbox.jna.ClassUtils;
import dorkbox.os.OS;
import dorkbox.systemTray.SystemTray;
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
class SystemTrayFixesLinux {


    /**
     * NOTE: ONLY IS FOR SWING TRAY TYPES!
     * <p>
     * Linux/Unix/Solaris use X11 + AWT to add an AWT window to a spot in the notification panel. UNFORTUNATELY, AWT
     * components are heavyweight, and DO NOT support transparency -- so one gets a "grey" box as the background of the icon.
     * <p>
     * Spectacularly enough, because this uses X11, it works on any X backend -- regardless of GtkStatusIcon or AppIndicator support. This
     * actually provides **more** support than GtkStatusIcons or AppIndicators, since this will ALWAYS work.
     * <p>
     * Additionally, the size of the tray is hard-coded to be 24.
     * <p>
     *
     * The down side, is that there is a "grey" box -- so hack around this issue by getting the color of a pixel in the notification area 1
     * off the corner, and setting that as the background.
     * <p>
     * It would be better to take a screenshot of the space BEHIND the tray icon, but we can't do that because there is no way to get
     * the info BEFORE the AWT is added to the notification area. See comments below for more details.
     * <p>
     * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6453521
     * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6267936
     * <p>
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/solaris/classes/sun/awt/X11/XTrayIconPeer.java
     * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/solaris/classes/sun/awt/X11/XSystemTrayPeer.java
     */
    public static
    void fix(int trayIconSize) {
        // linux/mac doesn't have transparent backgrounds for "swing" system tray icons

        // ONLY java <= 8
        if (OS.INSTANCE.getJavaVersion() > 8) {
            // there are problems with java 9+
            return;
        }

        if (SystemTrayFixes.isSwingTrayLoaded("sun.awt.X11.XTrayIconPeer")) {
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

                CtBehavior[] methodInfos = new CtBehavior[]{constructor, method1, method2};

                SystemTrayFixes.fixTraySize(methodInfos, 24, trayIconSize);

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
            ClassUtils.defineClass(runnableBytes);
            ClassUtils.defineClass(eFrameBytes);
            ClassUtils.defineClass(iconCanvasBytes);
            ClassUtils.defineClass(trayIconBytes);
            ClassUtils.defineClass(trayPeerBytes);

            if (SystemTray.DEBUG) {
                logger.debug("Successfully changed tray icon background color");
            }
        } catch (Exception e) {
            logger.error("Error setting tray icon background color", e);
        }
    }
}
