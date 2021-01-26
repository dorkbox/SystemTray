/*
 * Copyright 2018 dorkbox, llc
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

import java.time.Instant
import java.util.*

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
////// TESTING : (to local maven repo) <'publish and release' - 'publishToMavenLocal'>
////// RELEASE : (to sonatype/maven central), <'publish and release' - 'publishToSonatypeAndRelease'>
///////////////////////////////

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL   // always show the stacktrace!
gradle.startParameter.warningMode = WarningMode.All

plugins {
    java

    id("com.dorkbox.GradleUtils") version "1.12"
    id("com.dorkbox.Licensing") version "2.5"
    id("com.dorkbox.VersionUpdate") version "2.0"
    id("com.dorkbox.GradlePublish") version "1.10"
//    id("com.dorkbox.GradleModuleInfo") version "1.0"

    id("com.dorkbox.CrossCompile") version "1.0.1"

    kotlin("jvm") version "1.3.72"
}

object Extras {
    // set for the project
    const val description = "Cross-platform SystemTray support for Swing/AWT, GtkStatusIcon, and AppIndicator on Java 6+"
    const val group = "com.dorkbox"
    const val version = "3.17"

    // set as project.ext
    const val name = "SystemTray"
    const val id = "SystemTray"
    const val vendor = "Dorkbox LLC"
    const val url = "https://git.dorkbox.com/dorkbox/SystemTray"
    val buildDate = Instant.now().toString()

    val JAVA_VERSION = JavaVersion.VERSION_11.toString()
}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.fixIntellijPaths()
GradleUtils.defaultResolutionStrategy()
GradleUtils.compileConfiguration(JavaVersion.VERSION_1_8)


licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        url(Extras.url)
        author(Extras.vendor)

        extra("Lantern", License.APACHE_2) {
            it.copyright(2010)
            it.author("Brave New Software Project, Inc.")
            it.url("https://github.com/getlantern/lantern")
        }
        extra("QZTray", License.APACHE_2) {
            it.copyright(2016)
            it.author("Tres Finocchiaro")
            it.author("QZ Industries, LLC")
            it.url("https://github.com/tresf/tray/blob/dorkbox/src/qz/utils/ShellUtilities.java")
            it.note("Partial code released as Apache 2.0 for use in the SystemTray project by dorkbox, llc. Used with permission.")
        }
    }
}


fun javaFile(vararg fileNames: String): Iterable<String> {
    val fileList = ArrayList<String>(fileNames.size)
    fileNames.forEach { name ->
        fileList.add(name.replace('.', '/') + ".java")
    }
    return fileList
}

val exampleCompile : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }
val javaFxExampleCompile : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }
val swtExampleCompile : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }

val SourceSetContainer.example: SourceSet get() = maybeCreate("example")
fun SourceSetContainer.example(block: SourceSet.() -> Unit) = example.apply(block)
val SourceSetContainer.javaFxExample: SourceSet get() = maybeCreate("javaFxExample")
fun SourceSetContainer.javaFxExample(block: SourceSet.() -> Unit) = javaFxExample.apply(block)
val SourceSetContainer.swtExample: SourceSet get() = maybeCreate("swtExample")
fun SourceSetContainer.swtExample(block: SourceSet.() -> Unit) = swtExample.apply(block)

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))

            // want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")

            resources {
                setSrcDirs(listOf("src"))
                include("dorkbox/systemTray/gnomeShell/extension.js",
                    "dorkbox/systemTray/gnomeShell/appindicator.zip",
                    "dorkbox/systemTray/util/error_32.png")
            }
        }
    }

    test {
        java {
            setSrcDirs(listOf("test"))

            // only want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")

            // this is required because we reset the srcDirs to 'test' above, and 'main' must manually be added back
            srcDir(sourceSets["main"].allJava)


            resources {
                setSrcDirs(listOf("test"))
                include("dorkbox/*.png")
            }
        }
    }

    example {
        java {
            setSrcDirs(listOf("test"))
            include(javaFile("dorkbox.TestTray", "dorkbox.CustomSwingUI"))

            srcDir(sourceSets["main"].allJava)
        }

        resources {
            setSrcDirs(listOf("test"))
            include("dorkbox/*.png")

            srcDir(sourceSets["main"].resources)
        }
    }

    javaFxExample {
        java {
            setSrcDirs(listOf("test"))
            include(javaFile("dorkbox.TestTray", "dorkbox.TestTrayJavaFX", "dorkbox.CustomSwingUI"))

            srcDir(sourceSets["main"].allJava)
        }

        resources {
            setSrcDirs(listOf("test"))
            include("dorkbox/*.png")

            srcDir(sourceSets["main"].resources)
        }
    }

    swtExample {
        java {
            setSrcDirs(listOf("test"))
            include(javaFile("dorkbox.TestTray", "dorkbox.TestTraySwt", "dorkbox.CustomSwingUI"))

            srcDir(sourceSets["main"].allJava)
        }

        resources {
            setSrcDirs(listOf("test"))
            include("dorkbox/*.png")

            srcDir(sourceSets["main"].resources)
        }
    }
}

repositories {
    mavenLocal() // this must be first!

    jcenter()
}


///////////////////////////////
//////    Task defaults
///////////////////////////////
val jar: Jar by tasks
jar.apply {
    manifest {
        // https://docs.oracle.com/javase/tutorial/deployment/jar/packageman.html
        attributes["Name"] = Extras.name

        attributes["Specification-Title"] = Extras.name
        attributes["Specification-Version"] = Extras.version
        attributes["Specification-Vendor"] = Extras.vendor

        attributes["Implementation-Title"] = "${Extras.group}.${Extras.id}"
        attributes["Implementation-Version"] = Extras.buildDate
        attributes["Implementation-Vendor"] = Extras.vendor

        attributes["Automatic-Module-Name"] = Extras.id
    }
}

//fun getSwtMavenName(): String {
//    // SEE: https://repo1.maven.org/maven2/org/eclipse/platform/
//
//    // windows
//        // org.eclipse.swt.win32.win32.x86
//        // org.eclipse.swt.win32.win32.x86_64
//
//    // linux
//        // org.eclipse.swt.gtk.linux.x86
//        // org.eclipse.swt.gtk.linux.x86_64
//
//    // macoxs
//        // org.eclipse.swt.cocoa.macosx.x86_64
//
//    val currentOS = org.gradle.internal.os.OperatingSystem.current()
//    val windowingTk = when {
//        currentOS.isWindows -> "win32"
//        currentOS.isMacOsX  -> "cocoa"
//        else                -> "gtk"
//    }
//
//    val platform = when {
//        currentOS.isWindows -> "win32"
//        currentOS.isMacOsX  -> "macosx"
//        else                -> "linux"
//    }
//
//
//    var arch = System.getProperty("os.arch")
//    arch = when {
//        arch.matches(".*64.*".toRegex()) -> "x86_64"
//        else                             -> "x86"
//    }
//
//    return "$windowingTk.$platform.$arch"
//}
//
//configurations.all {
//    resolutionStrategy {
//        dependencySubstitution {
//            // The maven property ${osgi.platform} is not handled by Gradle for the SWT builds
//            // so we replace the dependency, using the osgi platform from the project settings
//            substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
//                    .with(module("org.eclipse.platform:org.eclipse.swt.gtk.${getSwtMavenName()}:3.110.0"))
//        }
//    }
//}


dependencies {
    implementation("com.dorkbox:Executor:2.1")
    implementation("com.dorkbox:Utilities:1.9")

    implementation("org.javassist:javassist:3.27.0-GA")

    val jnaVersion = "5.6.0"
    implementation("net.java.dev.jna:jna:$jnaVersion")
    implementation("net.java.dev.jna:jna-platform:$jnaVersion")

    implementation("org.slf4j:slf4j-api:1.7.30")
    val log = runtimeOnly("ch.qos.logback:logback-classic:1.2.3")!!



    // https://stackoverflow.com/questions/52569724/javafx-11-create-a-jar-file-with-gradle
    // JavaFX isn't always added to the compile classpath....
    val current = JavaVersion.current()

    // Java7/8 include JavaFX separately. Newer versions of java bundle it (or, you can download/install it separately)
    if (current == JavaVersion.VERSION_1_7 || current == JavaVersion.VERSION_1_8) {
        val javaFxFile = "${System.getProperty("java.home", ".")}/lib/ext/jfxrt.jar"
        if (File(javaFxFile).exists()) {
            val javaFX = api(files(javaFxFile))
            javaFxExampleCompile.dependencies += listOf(javaFX, log)
        }
    } else {
        // also see: https://stackoverflow.com/questions/52569724/javafx-11-create-a-jar-file-with-gradle
        val currentOS = org.gradle.internal.os.OperatingSystem.current()
        val platform = when {
            currentOS.isWindows -> { "win" }
            currentOS.isLinux -> { "linux" }
            currentOS.isMacOsX -> { "mac" }
            else -> { "unknown" }
        }

        val jfx1 = testImplementation("org.openjfx:javafx-base:11:${platform}")
        val jfx2 = testImplementation("org.openjfx:javafx-graphics:11:${platform}")
        val jfx3 = testImplementation("org.openjfx:javafx-controls:11:${platform}")

        javaFxExampleCompile.dependencies += listOf(jfx1, jfx2, jfx3, log)
    }


    // This is really SWT version 4.xx? no idea how the internal versions are tracked
    // 4.4 is the oldest version that works with us. We use reflection to access SWT, so we can compile the project without needing SWT
    //  because the eclipse release of SWT is sPecIaL!
    val swtName = GradleUtils.getSwtMavenId("3.114.100")
    val swtDep = testCompileOnly(swtName) {
        isTransitive = false
    }

    compileOnly(swtName) {
        isTransitive = false
    }

    exampleCompile.dependencies += log
    swtExampleCompile.dependencies += listOf(swtDep, log)
}

///////////////////////////////
//////    Tasks to launch examples from gradle
///////////////////////////////
task<JavaExec>("example") {
    classpath = sourceSets.example.runtimeClasspath
    main = "dorkbox.TestTray"
    standardInput = System.`in`
}

task<JavaExec>("javaFxExample") {
    classpath = sourceSets.javaFxExample.runtimeClasspath
    main = "dorkbox.TestTrayJavaFX"
    standardInput = System.`in`
}

task<JavaExec>("swtExample") {
    classpath = sourceSets.swtExample.runtimeClasspath
    main = "dorkbox.TestTraySwt"
    standardInput = System.`in`
}


/////////////////////////////
////    Jar Tasks
/////////////////////////////
task<Jar>("jarExample") {
    dependsOn(jar)

    archiveBaseName.set("SystemTray-Example")
    group = BasePlugin.BUILD_GROUP
    description = "Create an all-in-one example for testing, on a standard Java installation"

    from(sourceSets.example.output.classesDirs)
    from(sourceSets.example.output.resourcesDir)

    from(exampleCompile.map { if (it.isDirectory) it else zipTree(it) })

    manifest {
        attributes["Main-Class"] = "dorkbox.TestTray"
    }
}

task<Jar>("jarJavaFxExample") {
    dependsOn(jar)

    archiveBaseName.set("SystemTray-JavaFxExample")
    group = BasePlugin.BUILD_GROUP
    description = "Create an all-in-one example for testing, using JavaFX"

    from(sourceSets.javaFxExample.output.classesDirs)
    from(sourceSets.javaFxExample.output.resourcesDir)

    from(javaFxExampleCompile.map { if (it.isDirectory) it else zipTree(it) })


    manifest {
        attributes["Main-Class"] = "dorkbox.TestTrayJavaFX"
        attributes["Class-Path"] = System.getProperty("java.home", ".") + "/lib/ext/jfxrt.jar"
    }
}

task<Jar>("jarSwtExample") {
    dependsOn(jar)

    archiveBaseName.set("SystemTray-SwtExample")
    group = BasePlugin.BUILD_GROUP
    description = "Create an all-in-one example for testing, using SWT"

    from(sourceSets.swtExample.output.classesDirs)
    from(sourceSets.swtExample.output.resourcesDir)

    from(swtExampleCompile.map { if (it.isDirectory) it else zipTree(it) })

    manifest {
        attributes["Main-Class"] = "dorkbox.TestTraySwt"
    }
}


task("jarAllExamples") {
    dependsOn("jarExample")
    dependsOn("jarJavaFxExample")
    dependsOn("jarSwtExample")

    group = BasePlugin.BUILD_GROUP
    description = "Create all-in-one examples for testing, using Java only, JavaFX, and SWT"
}

