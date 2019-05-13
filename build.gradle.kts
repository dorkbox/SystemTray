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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import net.sf.json.JSONObject
import java.net.URL
import java.time.Instant
import java.util.*
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
//////
////// TESTING : local maven repo <PUBLISHING - publishToMavenLocal>
//////
////// RELEASE : sonatype / maven central, <PUBLISHING - publish> then <RELEASE - closeAndReleaseRepository>
///////////////////////////////

println("\tGradle ${project.gradle.gradleVersion} on Java ${JavaVersion.current()}")

plugins {
    java
    signing
    `maven-publish`

    // close and release on sonatype
    id("io.codearte.nexus-staging") version "0.20.0"

    id("com.dorkbox.CrossCompile") version "1.0.1"
    id("com.dorkbox.Licensing") version "1.4"
    id("com.dorkbox.VersionUpdate") version "1.4.1"
    id("com.dorkbox.GradleUtils") version "1.0"

    kotlin("jvm") version "1.3.30"
}

val IS_COMPILING_JAVAFX = gradle.startParameter.taskNames.filterNot { it?.toLowerCase()?.contains("javafx") ?: false }.isEmpty()

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

    val JAVA_VERSION = JavaVersion.VERSION_1_6.toString()

    var sonatypeUserName = ""
    var sonatypePassword = ""
}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
description = Extras.description
group = Extras.group
version = Extras.version

val propsFile = File("$projectDir/../../gradle.properties").normalize()
if (propsFile.canRead()) {
    println("\tLoading custom property data from: [$propsFile]")

    val props = Properties()
    propsFile.inputStream().use {
        props.load(it)
    }

    val extraProperties = Extras::class.declaredMemberProperties.filterIsInstance<KMutableProperty<String>>()
    props.forEach { (k, v) -> run {
        val key = k as String
        val value = v as String

        val member = extraProperties.find { it.name == key }
        if (member != null) {
            member.setter.call(Extras::class.objectInstance, value)
        }
        else {
            project.extra.set(k, v)
        }
    }}
}


licensing {
    license(License.APACHE_2) {
        author(Extras.vendor)
        url(Extras.url)
        note(Extras.description)
    }

    license("Dorkbox Utils", License.APACHE_2) {
        author(Extras.vendor)
        url("https://git.dorkbox.com/dorkbox/Utilities")
    }

    license("JNA", License.APACHE_2) {
        copyright(2011)
        author("Timothy Wall")
        url("https://github.com/twall/jna")
    }

    license("Lantern", License.APACHE_2) {
        copyright(2010)
        author("Brave New Software Project, Inc.")
        url("https://github.com/getlantern/lantern")
    }

    license("QZTray", License.APACHE_2) {
        copyright (2016)
        author ("Tres Finocchiaro")
        author ("QZ Industries, LLC")
        url("https://github.com/tresf/tray/blob/dorkbox/src/qz/utils/ShellUtilities.java")
        note("Partial code released as Apache 2.0 for use in the SystemTray project by dorkbox, llc. Used with permission.")
    }

    license("SLF4J", License.MIT) {
        copyright(2008)
        author("QOS.ch")
        url("http://www.slf4j.org")
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
val org.gradle.api.tasks.SourceSetContainer.javaFxExample: SourceSet get() = maybeCreate("javaFxExample")
fun SourceSetContainer.javaFxExample(block: SourceSet.() -> Unit) = javaFxExample.apply(block)
val org.gradle.api.tasks.SourceSetContainer.swtExample: SourceSet get() = maybeCreate("swtExample")
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

    //  because the eclipse release of SWT is abandoned on maven, this MAVEN repo has newer version of SWT,
    maven("http://maven-eclipse.github.io/maven")

    jcenter()
}


///////////////////////////////
//////    Task defaults
///////////////////////////////
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    val current = JavaVersion.current()

    if (!IS_COMPILING_JAVAFX) {
        if (current > JavaVersion.VERSION_1_8) {
            throw GradleException("The WindowsXP compatibility layer is not compatible with Java9+")
        }
        sourceCompatibility = Extras.JAVA_VERSION
        targetCompatibility = Extras.JAVA_VERSION
    }
    else {
        if (current < JavaVersion.VERSION_1_7) {
            throw GradleException("This build must be run with Java 7+ to compile JavaFX example files")
        }
    }
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

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

tasks.compileJava.get().apply {
    println("\tCompiling classes to Java $sourceCompatibility")
}


dependencies {
    implementation("com.dorkbox:ShellExecutor:1.1+")
    implementation("org.javassist:javassist:3.24.1-GA")

    val jnaVersion = "5.2.0"

    implementation("net.java.dev.jna:jna:$jnaVersion")
    implementation("net.java.dev.jna:jna-platform:$jnaVersion")

    implementation("org.slf4j:slf4j-api:1.7.26")


    val log = runtime("ch.qos.logback:logback-classic:1.2.3")!!

    //  because the eclipse release of SWT is abandoned on maven, this repo has a newer version of SWT,
    //  http://maven-eclipse.github.io/maven
    // 4.4 is the oldest version that works with us. We use reflection to access SWT, so we can compile the project without needing SWT
    val swtDep = testCompileOnly("org.eclipse.swt:${getSwtMavenName()}:4.6.1")!!

    // https://stackoverflow.com/questions/52569724/javafx-11-create-a-jar-file-with-gradle
    // JavaFX isn't always added to the compile classpath....
    if (IS_COMPILING_JAVAFX) {
        val current = JavaVersion.current()
        // Java7/8 include JavaFX seperately. Newer versions of java bundle it (or, you can download/install it separately)
        if (current == JavaVersion.VERSION_1_7 || current == JavaVersion.VERSION_1_8) {
            val javaFxFile = "${System.getProperty("java.home", ".")}/lib/ext/jfxrt.jar"
            if (!File(javaFxFile).exists()) {
                throw GradleException("JavaFX file $javaFxFile cannot be found")
            }

            val javaFX = api(files(javaFxFile))

            javaFxExampleCompile.dependencies += listOf(javaFX, log)
        }
    }

    implementation("com.dorkbox:Utilities:1.1")

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


operator fun Regex.contains(text: CharSequence): Boolean = this.matches(text)
fun getSwtMavenName(): String {
    val currentOS = org.gradle.internal.os.OperatingSystem.current()
    val platform = when {
            currentOS.isWindows -> "win32"
            currentOS.isMacOsX  -> "macosx"
            else                -> "linux"
        }


    var arch = System.getProperty("os.arch")
    arch = when {
        arch.matches(".*64.*".toRegex()) -> "x86_64"
        else                             -> "x86"
    }


    //  because the eclipse release of SWT is abandoned on maven, this MAVEN repo has newer version of SWT,
    //  https://github.com/maven-eclipse/maven-eclipse.github.io   for the website about it
    //  http://maven-eclipse.github.io/maven  for the maven repo
    return "org.eclipse.swt.gtk.$platform.$arch"
}

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
//////
////// TESTING : local maven repo <PUBLISHING - publishToMavenLocal>
//////
////// RELEASE : sonatype / maven central, <PUBLISHING - publish> then <RELEASE - closeAndReleaseRepository>
///////////////////////////////
val sourceJar = task<Jar>("sourceJar") {
    description = "Creates a JAR that contains the source code."

    from(sourceSets["main"].java)

    archiveClassifier.set("sources")
}

val javaDocJar = task<Jar>("javaDocJar") {
    description = "Creates a JAR that contains the javadocs."

    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = Extras.group
            artifactId = Extras.id
            version = Extras.version

            from(components["java"])

            artifact(sourceJar)
            artifact(javaDocJar)

            pom {
                name.set(Extras.name)
                description.set(Extras.description)
                url.set(Extras.url)

                issueManagement {
                    url.set("${Extras.url}/issues")
                    system.set("Gitea Issues")
                }
                organization {
                    name.set(Extras.vendor)
                    url.set("https://dorkbox.com")
                }
                developers {
                    developer {
                        id.set("dorkbox")
                        name.set(Extras.vendor)
                        email.set("email@dorkbox.com")
                    }
                }
                scm {
                    url.set(Extras.url)
                    connection.set("scm:${Extras.url}.git")
                }
            }

        }
    }


    repositories {
        maven {
            setUrl("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            credentials {
                username = Extras.sonatypeUserName
                password = Extras.sonatypePassword
            }
        }
    }


    tasks.withType<PublishToMavenRepository> {
        onlyIf {
            publication == publishing.publications["maven"] && repository == publishing.repositories["maven"]
        }
    }

    tasks.withType<PublishToMavenLocal> {
        onlyIf {
            publication == publishing.publications["maven"]
        }
    }

    // output the release URL in the console
    tasks["releaseRepository"].doLast {
        val url = "https://oss.sonatype.org/content/repositories/releases/"
        val projectName = Extras.group.replace('.', '/')
        val name = Extras.name
        val version = Extras.version

        println("Maven URL: $url$projectName/$name/$version/")
    }
}

nexusStaging {
    username = Extras.sonatypeUserName
    password = Extras.sonatypePassword
}

signing {
    sign(publishing.publications["maven"])
}
