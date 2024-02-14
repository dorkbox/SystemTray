/*
 * Copyright 2024 dorkbox, llc
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
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dorkbox.gradle.SwtType
import dorkbox.os.OS

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
////// TESTING : (to local maven repo) <'publish and release' - 'publishToMavenLocal'>
////// RELEASE : (to sonatype/maven central), <'publish and release' - 'publishToSonatypeAndRelease'>
///////////////////////////////

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL   // always show the stacktrace!

plugins {
    id("com.dorkbox.GradleUtils") version "3.18"
    id("com.dorkbox.Licensing") version "2.28"
    id("com.dorkbox.VersionUpdate") version "2.8"
    id("com.dorkbox.GradlePublish") version "1.22"

    id("com.github.johnrengelman.shadow") version "8.1.1"

    kotlin("jvm") version "1.9.0"
}

// TODO: check if there are any images. ONLY if there are images, then we set all menu entries to have image offsets.
//     otherwise, menus will be left-aligned.


object Extras {
    // set for the project
    const val description = "Cross-platform SystemTray support for Swing/AWT, GtkStatusIcon, and AppIndicator on Java 8+"
    const val group = "com.dorkbox"
    const val version = "4.5"

    // set as project.ext
    const val name = "SystemTray"
    const val id = "SystemTray"
    const val vendor = "Dorkbox LLC"
    const val vendorUrl = "https://dorkbox.com"
    const val url = "https://git.dorkbox.com/dorkbox/SystemTray"
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

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

// this is incredibly verbose and repetitive, but the gradle dev team cannot figure out HOW they want gradle to behave, and is constantly
// changing -- so this is required to prevent compile warnings/errors.
val normalExampleCompile = configurations.create("normalExampleCompile").run { extendsFrom(configurations.compileClasspath.get()) }
val normalExampleConfig = configurations.create("normalExampleConfig").run { extendsFrom(normalExampleCompile) }

val javaFxExampleCompile = configurations.create("javaFxExampleCompile").run { extendsFrom(configurations.compileClasspath.get()) }
val javaFxExampleConfig = configurations.create("javaFxExampleConfig").run { extendsFrom(javaFxExampleCompile) }

val swtExampleCompile = configurations.create("swtExampleCompile").run { extendsFrom(configurations.compileClasspath.get()) }
val swtExampleConfig = configurations.create("swtExampleConfig").run { extendsFrom(swtExampleCompile) }

val linux64SwtDeps : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }
val mac64SwtDeps : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }
val macArm64SwtDeps : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }
val win64SwtDeps : Configuration by configurations.creating { extendsFrom(configurations.implementation.get()) }

val normalExampleSet = sourceSets.create("normalExample")
val javaFxExampleSet = sourceSets.create("javaFxExample")
val swtExampleSet = sourceSets.create("swtExample")

fun SourceSetContainer.normalExample(block: SourceSet.() -> Unit) = normalExampleSet.apply(block)
fun SourceSetContainer.javaFxExample(block: SourceSet.() -> Unit) = javaFxExampleSet.apply(block)
fun SourceSetContainer.swtExample(block: SourceSet.() -> Unit) = swtExampleSet.apply(block)

sourceSets {
    main {
        java {
            resources {
                setSrcDirs(listOf("src"))
                include("dorkbox/systemTray/gnomeShell/extension.js",
                        "dorkbox/systemTray/util/error_32.png")
            }
        }

        kotlin {
            setSrcDirs(listOf("src"))
        }
    }

    normalExample {
        java {
            setSrcDirs(listOf("test-normal"))
            // only want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")
        }

        kotlin {
            setSrcDirs(listOf("test-normal"))
            // only want to include kt files for the source. 'setSrcDirs' resets includes...
            include("**/*.kt")
        }

        resources {
            setSrcDirs(listOf("test-resources"))
            include("dorkbox/*.png")
        }

        compileClasspath += sourceSets["main"].compileClasspath
        runtimeClasspath += sourceSets["main"].runtimeClasspath
    }

    javaFxExample {
        java {
            setSrcDirs(listOf("test-javaFx"))
            // only want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")
        }

        kotlin {
            setSrcDirs(listOf("test-javaFx"))
            // only want to include kt files for the source. 'setSrcDirs' resets includes...
            include("**/*.kt")
        }

        resources {
            setSrcDirs(listOf("test-resources"))
            include("dorkbox/*.png")
        }

        compileClasspath += sourceSets["main"].compileClasspath
        runtimeClasspath += sourceSets["main"].runtimeClasspath
    }

    swtExample {
        java {
            setSrcDirs(listOf("test-swt"))
            // only want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")
        }

        kotlin {
            setSrcDirs(listOf("test-swt"))
            // only want to include kt files for the source. 'setSrcDirs' resets includes...
            include("**/*.kt")
        }

        resources {
            setSrcDirs(listOf("test-resources"))
            include("dorkbox/*.png")
        }

        compileClasspath += sourceSets["main"].compileClasspath
        runtimeClasspath += sourceSets["main"].runtimeClasspath
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
        attributes["Implementation-Version"] = GradleUtils.now()
        attributes["Implementation-Vendor"] = Extras.vendor
    }
}

dependencies {
    val jnaVersion = "5.14.0"

    // This is really SWT version 4.xx? no idea how the internal versions are tracked
    // 4.4 is the oldest version that works with us, and the release of SWT is sPecIaL!
    // 3.108.0 is the MOST RECENT version supported by x86. All newer version no longer support x86
    // 3.1116.100 is the MOST RECENT version supported by macos ARM.
    val swtVersion = "3.122.0"

    api("com.dorkbox:Collections:2.8")
    api("com.dorkbox:Executor:3.14")
    api("com.dorkbox:Desktop:1.1")
    api("com.dorkbox:JNA:1.4")
    api("com.dorkbox:OS:1.11")
    api("com.dorkbox:Updates:1.1")
    api("com.dorkbox:Utilities:1.48")

    api("org.javassist:javassist:3.30.1-GA")

    api("net.java.dev.jna:jna-jpms:${jnaVersion}")
    api("net.java.dev.jna:jna-platform-jpms:${jnaVersion}")

    // note: this has support for JPMS, so it's required
    api("org.slf4j:slf4j-api:1.8.0-beta4")  // java 8
//    api("org.slf4j:slf4j-api:2.0.9") // java 11 only

    val logbackVer = "1.3.5" // java 8
//    val logbackVer = "1.4.5" // java 11 only


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
        println("\tJava ${JavaVersion.current()}, JavaFX: $version")

        javaFxExampleCompile("org.openjfx:javafx-base:$version:${platform}")
        javaFxExampleCompile("org.openjfx:javafx-graphics:$version:${platform}")
        javaFxExampleCompile("org.openjfx:javafx-controls:$version:${platform}")
    }



    normalExampleCompile(sourceSets.main.get().output)
    javaFxExampleCompile(sourceSets.main.get().output)
    swtExampleCompile(sourceSets.main.get().output)



    val logback = "ch.qos.logback:logback-classic:$logbackVer"
    normalExampleCompile(logback)
    javaFxExampleCompile(logback)
    swtExampleCompile(logback)
    linux64SwtDeps(logback)
    mac64SwtDeps(logback)
    macArm64SwtDeps(logback)
    win64SwtDeps(logback)





    // SEE: https://repo1.maven.org/maven2/org/eclipse/platform/
    swtExampleCompile(GradleUtils.getSwtMavenId(swtVersion)) { isTransitive = false }

    // add all SWT dependencies for all supported OS configurations to a "mega" jar
    linux64SwtDeps(SwtType.LINUX_64.fullId(swtVersion)) { isTransitive = false }
    mac64SwtDeps(SwtType.MAC_64.fullId(swtVersion)) { isTransitive = false }
    macArm64SwtDeps(SwtType.MAC_AARCH64.fullId(swtVersion)) { isTransitive = false }
    win64SwtDeps(SwtType.WIN_64.fullId(swtVersion)) { isTransitive = false }


    linux64SwtDeps.resolutionStrategy {
        dependencySubstitution {
            substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
                .using(module(SwtType.LINUX_64.fullId(swtVersion)))
        }
    }
    mac64SwtDeps.resolutionStrategy {
        dependencySubstitution {
            substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
                .using(module(SwtType.MAC_64.fullId(swtVersion)))
        }
    }

    macArm64SwtDeps.resolutionStrategy {
        dependencySubstitution {
            substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
                .using(module(SwtType.MAC_AARCH64.fullId(swtVersion)))
                .because("SWT resolution is broken by default")
        }
    }
    mac64SwtDeps.resolutionStrategy {
        dependencySubstitution {
            substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
                .using(module(SwtType.WIN_64.fullId(swtVersion)))
        }
    }
}

/////////////////////////////
////    Tasks to launch examples from gradle
/////////////////////////////

task<JavaExec>("SystemTray_normal") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = normalExampleSet.runtimeClasspath
    mainClass.set("dorkbox.TestTray")
    standardInput = System.`in`
}

task<JavaExec>("SystemTray_javaFx") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = javaFxExampleSet.runtimeClasspath
    mainClass.set("dorkbox.TestTrayJavaFX")
    standardInput = System.`in`
}

task<JavaExec>("SystemTray_swt") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = swtExampleSet.runtimeClasspath
    mainClass.set("dorkbox.TestTraySwt")
    standardInput = System.`in`
    jvmArgs = listOf("-XstartOnFirstThread")
}


///////////////////////////
//    Jar Tasks
///////////////////////////
tasks.register<ShadowJar>("normalShadowJar") {
    manifest.inheritFrom(tasks.jar.get().manifest)
    group = "shadow"

    manifest {
        attributes["Main-Class"] = "dorkbox.TestTray"
    }

    mergeServiceFiles()

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(normalExampleSet.output)
    configurations = listOf(normalExampleConfig)

    archiveBaseName.set(project.name + "-Example")
}

tasks.register<ShadowJar>("javaFxShadowJar") {
    manifest.inheritFrom(tasks.jar.get().manifest)
    group = "shadow"

    manifest {
        attributes["Main-Class"] = "dorkbox.TestTrayJavaFX"

        if (JavaVersion.current() == JavaVersion.VERSION_1_8) {
            // necessary for java FX 8 on Java8, for our limited use - the api in JavaFx11 is compatible, so we can compile with any JDK
            attributes["Class-Path"] = System.getProperty("java.home", ".") + "/lib/ext/jfxrt.jar"
        }
    }

    mergeServiceFiles()

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(javaFxExampleSet.output)
    configurations = listOf(javaFxExampleConfig)

    archiveBaseName.set(project.name + "-JavaFxExample")
}

tasks.register<ShadowJar>("swtShadowJar") {
    manifest.inheritFrom(tasks.jar.get().manifest)
    group = "shadow"

    manifest {
        attributes["Main-Class"] = "dorkbox.TestTray"
    }

    mergeServiceFiles()

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE


    // include ALL versions of SWT (so a single jar can run on all OS,
////    from(linux64SwtDeps.map { if (it.isDirectory) it else zipTree(it) }) {
////        exclude("META-INF/*.DSA", "META-INF/*.SF")
////    }
//    from(mac64SwtDeps.map { if (it.isDirectory) it else zipTree(it) }) {
//        exclude("META-INF/*.DSA", "META-INF/*.SF")
//    }
//    from(macArm64SwtDeps.map { if (it.isDirectory) it else zipTree(it) }) {
//        exclude("META-INF/*.DSA", "META-INF/*.SF")
//    }
////    from(win64SwtDeps.map { if (it.isDirectory) it else zipTree(it) }) {
////        exclude("META-INF/*.DSA", "META-INF/*.SF")
////    }

    from(swtExampleSet.output)
    configurations = listOf(swtExampleConfig)

    exclude("META-INF/*.RSA", "META-INF/*.SF","META-INF/*.DSA")

    archiveBaseName.set(project.name + "-SwtExample")
}


task("jarAllExamples") {
    dependsOn("normalShadowJar")
    dependsOn("javaFxShadowJar")
    dependsOn("swtShadowJar")

    group = "shadow"
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
