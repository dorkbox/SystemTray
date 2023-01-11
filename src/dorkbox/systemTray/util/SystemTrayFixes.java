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
import dorkbox.systemTray.SystemTray;
import javassist.CtBehavior;
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
class SystemTrayFixes {
    protected static
    boolean isSwingTrayLoaded(String className) {
        try {
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            // if we are using swing, the classes are already created. We cannot fix things when it's already loaded.
            return ClassUtils.isClassLoaded(cl, className) || ClassUtils.isClassLoaded(cl, "java.awt.SystemTray");
        } catch (Throwable e) {
            if (SystemTray.DEBUG) {
                logger.debug("Error detecting if the Swing SystemTray is loaded, unexpected error.", e);
            }
        }

        return true;
    }


    protected static
    void fixTraySize(final CtBehavior[] behaviors, final int oldTraySize, final int newTraySize) {
        for (CtBehavior behavior : behaviors) {
            MethodInfo methodInfo = behavior.getMethodInfo();
            CodeIterator methodIterator = methodInfo.getCodeAttribute().iterator();

            while (methodIterator.hasNext()) {
                int index;
                try {
                    index = methodIterator.next();
                    int opcode = methodIterator.byteAt(index);
                    if (opcode == Opcode.BIPUSH) {
                        int i = methodIterator.byteAt(index + 1);

                        if (i == oldTraySize) {
                            // re-write this to be our custom size.
                            methodIterator.writeByte((byte) newTraySize, index + 1);
                        }
                    }
                } catch (BadBytecode badBytecode) {
                    badBytecode.printStackTrace();
                }
            }
        }
    }


    @SuppressWarnings("unused")
    protected static
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
