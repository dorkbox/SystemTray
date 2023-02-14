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
import dorkbox.os.OS
import java.time.Instant

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
////// TESTING : (to local maven repo) <'publish and release' - 'publishToMavenLocal'>
////// RELEASE : (to sonatype/maven central), <'publish and release' - 'publishToSonatypeAndRelease'>
///////////////////////////////

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL   // always show the stacktrace!

plugins {
    id("com.dorkbox.GradleUtils") version "3.8"
    id("com.dorkbox.Licensing") version "2.19.1"
    id("com.dorkbox.VersionUpdate") version "2.5"
    id("com.dorkbox.GradlePublish") version "1.17"

    kotlin("jvm") version "1.7.22"
}

// TODO: check if there are any images. ONLY if there are images, then we set all menu entries to have image offsets.
//     otherwise, menus will be left-aligned.


object Extras {
    // set for the project
    const val description = "Cross-platform SystemTray support for Swing/AWT, GtkStatusIcon, and AppIndicator on Java 8+"
    const val group = "com.dorkbox"
    const val version = "4.2.2"

    // set as project.ext
    const val name = "SystemTray"
    const val id = "SystemTray"
    const val vendor = "Dorkbox LLC"
    const val vendorUrl = "https://dorkbox.com"
    const val url = "https://git.dorkbox.com/dorkbox/SystemTray"

    val buildDate = Instant.now().toString()
}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_1_8)
GradleUtils.jpms(JavaVersion.VERSION_1_9)

licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        url(Extras.url)
        author(Extras.vendor)

        extra("Lantern", License.APACHE_2) {
            copyright(2010)
            author("Brave New Software Project, Inc.")
            url("https://github.com/getlantern/lantern")
        }
        extra("QZTray", License.APACHE_2) {
            copyright(2016)
            author("Tres Finocchiaro")
            author("QZ Industries, LLC")
            url("https://github.com/tresf/tray/blob/dorkbox/src/qz/utils/ShellUtilities.java")
            note("Partial code released as Apache 2.0 for use in the SystemTray project by dorkbox, llc. Used with permission.")
        }
    }
}

val exampleCompile by configurations.creating { extendsFrom(configurations.implementation.get()) }
val javaFxExampleCompile : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }
val swtExampleCompile : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }

//val javaFxDeps : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }
//val linux64SwtDeps : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }
//val mac64SwtDeps : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }
//val win64SwtDeps : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }

val SourceSetContainer.javaFxExample: SourceSet get() = maybeCreate("javaFxExample")
fun SourceSetContainer.javaFxExample(block: SourceSet.() -> Unit) = javaFxExample.apply(block)
val SourceSetContainer.swtExample: SourceSet get() = maybeCreate("swtExample")
fun SourceSetContainer.swtExample(block: SourceSet.() -> Unit) = swtExample.apply(block)

sourceSets {
    main {
        java {
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
            srcDir(sourceSets["main"].allJava)
        }

        resources {
            setSrcDirs(listOf("test"))
            include("dorkbox/*.png")

            srcDir(sourceSets["main"].resources)
        }

        compileClasspath += sourceSets.main.get().runtimeClasspath
    }

    javaFxExample {
        java {
            setSrcDirs(listOf("test-javaFx"))
            // only want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")

            // this is required because we reset the srcDirs to 'test' above, and 'main' must manually be added back
            srcDir(sourceSets["main"].allJava)
        }

        resources {
            setSrcDirs(listOf("test"))
            include("dorkbox/*.png")

            srcDir(sourceSets["main"].resources)
        }

        compileClasspath += sourceSets.test.get().runtimeClasspath
    }

    swtExample {
        java {
            setSrcDirs(listOf("test-swt"))
            // only want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")

            srcDir(sourceSets["main"].allJava)
        }

        resources {
            setSrcDirs(listOf("test"))
            include("dorkbox/*.png")

            srcDir(sourceSets["main"].resources)
        }

        compileClasspath += sourceSets.test.get().runtimeClasspath
    }
}


///////////////////////////////
//////    Task defaults
///////////////////////////////
tasks.jar.get().apply {
    manifest {
        // https://docs.oracle.com/javase/tutorial/deployment/jar/packageman.html
        attributes["Name"] = Extras.name

        attributes["Specification-Title"] = Extras.name
        attributes["Specification-Version"] = Extras.version
        attributes["Specification-Vendor"] = Extras.vendor

        attributes["Implementation-Title"] = "${Extras.group}.${Extras.id}"
        attributes["Implementation-Version"] = Extras.buildDate
        attributes["Implementation-Vendor"] = Extras.vendor
    }
}

dependencies {
    val jnaVersion = "5.12.1"

    // This is really SWT version 4.xx? no idea how the internal versions are tracked
    // 4.4 is the oldest version that works with us, and the release of SWT is sPecIaL!
    // 3.108.0 is the MOST RECENT version supported by x86. All newer version no longer support x86
    // 3.1116.100 is the MOST RECENT version supported by macos ARM.
    val swtVersion = "3.122.0"

    api("com.dorkbox:Executor:3.11")
    api("com.dorkbox:Desktop:1.0")
    api("com.dorkbox:JNA:1.0")
    api("com.dorkbox:OS:1.6")
    api("com.dorkbox:Updates:1.1")
    api("com.dorkbox:Utilities:1.40")

    api("org.javassist:javassist:3.29.2-GA")

    api("net.java.dev.jna:jna-jpms:${jnaVersion}")
    api("net.java.dev.jna:jna-platform-jpms:${jnaVersion}")

    // note: this has support for JPMS, so it's required
    api("org.slf4j:slf4j-api:1.8.0-beta4")  // java 8
//    api("org.slf4j:slf4j-api:2.0.6") // java 11 only


    val logbackVer = "1.3.5" // java 8
//    val logbackVer = "1.4.5" // java 11 only
//    api("ch.qos.logback:logback-classic:$logbackVer")






    // NOTE: we must have GRADLE ITSELF using the Oracle 1.8 JDK (which includes JavaFX).
    //       OR we will manually include JavaFx11 (JavaFx8, for what we use, is compatible)

    // https://stackoverflow.com/questions/52569724/javafx-11-create-a-jar-file-with-gradle
    // JavaFX isn't always added to the compile classpath....
    // Java 8 includes JavaFX separately, Java11+ must use openjfx
    if (JavaVersion.current() == JavaVersion.VERSION_1_8) {
        // Paths for the various executables in the Java 'bin' directory
        val javaFxFile = File("${System.getProperty("java.home", ".")}/lib/ext/jfxrt.jar")
        println("\tJava 8, JavaFX: $javaFxFile")

        if (javaFxFile.exists()) {
            javaFxExampleCompile(files(javaFxFile))
        } else {
            println("\tJavaFX not found, unable to add JavaFX 8 dependency!")
        }
    } else {
        // also see: https://stackoverflow.com/questions/52569724/javafx-11-create-a-jar-file-with-gradle
        val currentOS = org.gradle.internal.os.OperatingSystem.current()
        val platform = when {
            currentOS.isWindows -> { "win" }
            currentOS.isLinux -> { "linux" }
            currentOS.isMacOsX -> {
                if (OS.isArm) {
                    "mac-aarch64"
                } else {
                    "mac"
                }
            }
            else -> { "unknown" }
        }

        val version = "17.0.2"
        javaFxExampleCompile("org.openjfx:javafx-base:$version:${platform}")
        javaFxExampleCompile("org.openjfx:javafx-graphics:$version:${platform}")
        javaFxExampleCompile("org.openjfx:javafx-controls:$version:${platform}")

//        // include all distro for jars
//        listOf("win", "linux", "mac").forEach {
//            javaFxDeps("org.openjfx:javafx-base:11:${it}")
//            javaFxDeps("org.openjfx:javafx-graphics:11:${it}")
//            javaFxDeps("org.openjfx:javafx-controls:11:${it}")
//        }
    }

    // SEE: https://repo1.maven.org/maven2/org/eclipse/platform/
    swtExampleCompile(GradleUtils.getSwtMavenId(swtVersion)) {
        isTransitive = false
    }


    val log = testImplementation("ch.qos.logback:logback-classic:$logbackVer")!!

     exampleCompile.dependencies += log
     javaFxExampleCompile.dependencies += log
     swtExampleCompile.dependencies += log

//    // add all SWT dependencies for all supported OS configurations to a "mega" jar
//    linux64SwtDeps(SwtType.LINUX_64.fullId(Extras.swtVersion)) { isTransitive = false }
//    mac64SwtDeps(SwtType.MAC_64.fullId(Extras.swtVersion)) { isTransitive = false }
//    win64SwtDeps(SwtType.WIN_64.fullId(Extras.swtVersion)) { isTransitive = false }
//
//    linux64SwtDeps.dependencies += log
//    mac64SwtDeps.dependencies += log
//    win64SwtDeps.dependencies += log
//
//    linux64SwtDeps.resolutionStrategy {
//        dependencySubstitution {
//            substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
//                .with(module(SwtType.LINUX_64.fullId(Extras.swtVersion)))
//        }
//    }
//    mac64SwtDeps.resolutionStrategy {
//        dependencySubstitution {
//            substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
//                .with(module(SwtType.MAC_64.fullId(Extras.swtVersion)))
//        }
//    }
//    mac64SwtDeps.resolutionStrategy {
//        dependencySubstitution {
//            substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
//                .with(module(SwtType.WIN_64.fullId(Extras.swtVersion)))
//        }
//    }
}

/////////////////////////////
////    Tasks to launch examples from gradle
/////////////////////////////

task<JavaExec>("SystemTray_default") {
    group = BasePlugin.BUILD_GROUP
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dorkbox.TestTray")
    standardInput = System.`in`
}

task<JavaExec>("SystemTray_javaFx") {
    group = BasePlugin.BUILD_GROUP
    classpath = sourceSets.javaFxExample.runtimeClasspath
    mainClass.set("dorkbox.TestTrayJavaFX")
    standardInput = System.`in`
}

task<JavaExec>("SystemTray_swt") {
    group = BasePlugin.BUILD_GROUP
    classpath = sourceSets.swtExample.runtimeClasspath
    mainClass.set("dorkbox.TestTraySwt")
    standardInput = System.`in`
    jvmArgs = listOf("-XstartOnFirstThread")
}


///////////////////////////
//    Jar Tasks
///////////////////////////
task<Jar>("jarExample") {
    archiveBaseName.set("SystemTray-Example")
    group = BasePlugin.BUILD_GROUP
    description = "Create an all-in-one example for testing, on a standard Java installation"

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.test.get().output.classesDirs)
    from(sourceSets.test.get().output.resourcesDir)

    from(exampleCompile.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.DSA", "META-INF/*.SF")
    }

    manifest {
        attributes["Main-Class"] = "dorkbox.TestTray"
    }
}

task<Jar>("jarJavaFxExample") {
    archiveBaseName.set("SystemTray-JavaFxExample")
    group = BasePlugin.BUILD_GROUP
    description = "Create an all-in-one example for testing, using JavaFX"

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.javaFxExample.output.classesDirs)
    from(sourceSets.javaFxExample.output.resourcesDir)

//    from(javaFxExampleCompile.map { if (it.isDirectory) it else zipTree(it) }) {
//        exclude("META-INF/*.DSA", "META-INF/*.SF")
//    }

//    from(javaFxDeps.map { if (it.isDirectory) it else zipTree(it) }) {
//        exclude("META-INF/*.DSA", "META-INF/*.SF")
//    }

    manifest {
        attributes["Main-Class"] = "dorkbox.TestTrayJavaFX"
        // necessary for java FX 8 on Java8, for our limited use - the api in JavaFx11 is compatible, so we can compile with any JDK
        //  attributes["Class-Path"] = System.getProperty("java.home", ".") + "/lib/ext/jfxrt.jar"
    }
}

task<Jar>("jarSwtExample") {
    archiveBaseName.set("SystemTray-SwtExample")
    group = BasePlugin.BUILD_GROUP
    description = "Create an all-in-one example for testing, using SWT"

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.swtExample.output.classesDirs)
    from(sourceSets.swtExample.output.resourcesDir)

//    from(swtExampleCompile.map { if (it.isDirectory) it else zipTree(it) }) {
//        exclude("META-INF/*.DSA", "META-INF/*.SF")
//    }

    // include ALL versions of SWT (so a single jar can run on all OS,
//    from(linux64SwtDeps.map { if (it.isDirectory) it else zipTree(it) }) {
//        exclude("META-INF/*.DSA", "META-INF/*.SF")
//    }
//    from(mac64SwtDeps.map { if (it.isDirectory) it else zipTree(it) }) {
//        exclude("META-INF/*.DSA", "META-INF/*.SF")
//    }
//    from(win64SwtDeps.map { if (it.isDirectory) it else zipTree(it) }) {
//        exclude("META-INF/*.DSA", "META-INF/*.SF")
//    }

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


publishToSonatype {
    groupId = Extras.group
    artifactId = Extras.id
    version = Extras.version

    name = Extras.name
    description = Extras.description
    url = Extras.url

    vendor = Extras.vendor
    vendorUrl = Extras.vendorUrl

    issueManagement {
        url = "${Extras.url}/issues"
        nickname = "Gitea Issues"
    }

    developer {
        id = "dorkbox"
        name = Extras.vendor
        email = "email@dorkbox.com"
    }
}
