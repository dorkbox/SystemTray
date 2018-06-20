import org.gradle.api.internal.HasConvention
import org.gradle.internal.impldep.org.apache.http.client.methods.RequestBuilder.options
import org.jetbrains.kotlin.contracts.model.structure.UNKNOWN_COMPUTATION.type
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.android.AndroidGradleWrapper.getTestVariants
import org.jetbrains.kotlin.gradle.plugin.android.AndroidGradleWrapper.srcDir
import java.util.Collections
import java.time.Instant

plugins {
    java
    maven
    kotlin("jvm").version("1.2.41")
}

apply {
    plugin("java")
    plugin("kotlin")
}

kotlin.experimental.coroutines = Coroutines.ENABLE

group = "com.dorkbox"
version = "3.13-SNAPSHOT"
description = "Cross-platform SystemTray support for Swing/AWT, GtkStatusIcon, and AppIndicator on Java 6+"


java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

//configure<JavaPluginConvention> {
//    setSourceCompatibility(1.7)
//    setTargetCompatibility(1.7)
////    if (project.hasProperty("env")) {
////        var env = project.property("env");
////        sourceSets.getByName("main")
////                .resources.srcDirs("src/main/profile/" + env)
////    }
//}


// This creates the configurations usable inside dependencies and allowing us custom libs when building the example jars
val swingExampleCompile  = configurations.create("swingExampleCompile")
val javaFxExampleCompile = configurations.create("javaFxExampleCompile")
val swtExampleCompile    = configurations.create("swtExampleCompile")

// make working with sourceSets easier
val sourceSets = java.sourceSets
fun sourceSets(block: SourceSetContainer.() -> Unit) = sourceSets.apply(block)

val SourceSetContainer.main: SourceSet get() = maybeCreate("main")
fun SourceSetContainer.main(block: SourceSet.() -> Unit) = main.apply(block)

val SourceSetContainer.test: SourceSet get() = maybeCreate("test")
fun SourceSetContainer.test(block: SourceSet.() -> Unit) = test.apply(block)

// test/example jars
val SourceSetContainer.swingExample: SourceSet get() = maybeCreate("swingExample")
fun SourceSetContainer.swingExample(block: SourceSet.() -> Unit) = swingExample.apply(block)
val org.gradle.api.tasks.SourceSetContainer.javaFxExample: SourceSet get() = maybeCreate("javaFxExample")
fun SourceSetContainer.javaFxExample(block: SourceSet.() -> Unit) = javaFxExample.apply(block)
val org.gradle.api.tasks.SourceSetContainer.swtExample: SourceSet get() = maybeCreate("swtExample")
fun SourceSetContainer.swtExample(block: SourceSet.() -> Unit) = swtExample.apply(block)

var SourceDirectorySet.sourceDirs: Iterable<File>
    get() = srcDirs
    set(value) {
        setSrcDirs(value)
    }


fun javaFile(vararg fileNames: String): Iterable<String> {
    val fileList = ArrayList<String>()

    for (name in fileNames) {
        val fixed = name.replace('.', '/') + ".java"
        fileList.add(fixed)
    }

    return fileList
}

sourceSets {
    main {
        java {
            sourceDirs = files("src")

            // only want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")

            resources {
                sourceDirs = files("src")
                include("dorkbox/systemTray/gnomeShell/extension.js",
                        "dorkbox/systemTray/util/error_32.png")
            }
        }
    }

    test {
        java {
            sourceDirs = files("test")

            // only want to include java files for the source. 'setSrcDirs' resets includes...
            include("**/*.java")

            // this is required because we reset the srcDirs to 'test' above, and 'main' must manually be added back
            srcDir(sourceSets.main.allJava)
        }
    }

    swingExample {
        java {
            sourceDirs = files("test")
            include(javaFile("dorkbox.TestTray", "dorkbox.CustomSwingUI"))

            srcDir(sourceSets.main.allJava)
        }

        resources {
            sourceDirs = files("test")
            include("dorkbox/*.png")
        }
    }

    javaFxExample {
        java {
            sourceDirs = files("test")
            include(javaFile("dorkbox.TestTray", "dorkbox.TestTrayJavaFX", "dorkbox.CustomSwingUI"))

            srcDir(sourceSets.main.allJava)
        }

        resources {
            sourceDirs = files("test")
            include("dorkbox/*.png")
        }
    }

    swtExample {
        java {
            sourceDirs = files("test")
            include(javaFile("dorkbox.TestTray", "dorkbox.TestTraySwt", "dorkbox.CustomSwingUI"))

            srcDir(sourceSets.main.allJava)
        }

        resources {
            sourceDirs = files("test")
            include("dorkbox/*.png")
        }
    }
}

repositories {
    mavenLocal() // this must be first!

    mavenCentral()

    //  because the eclipse release of SWT is abandoned on maven, this MAVEN repo has newer version of SWT,
    maven(url = "http://maven-eclipse.github.io/maven")
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
    mavenCentral()
}


dependencies {
    compile(project("Utilities")) {
        // don't include any of the project dependencies for anything
        isTransitive = false
    }

    // our main dependencies are ALSO the same as the limited utilities (they are not automatically pulled in from other sourceSets)
    // needed by the utilities (custom since we don't want to include everything). IntelliJ includes everything, but our builds do not
    compile(group = "com.dorkbox", name = "ShellExecutor", version = "1.1+")
    compile(group = "org.javassist", name = "javassist", version = "3.21.0-GA")
    compile(group = "net.java.dev.jna", name = "jna", version = "4.3.0")
    compile(group = "net.java.dev.jna", name = "jna-platform", version = "4.3.0")
    compile(group = "org.slf4j", name = "slf4j-api", version = "1.7.25")


    val log = runtime(group = "ch.qos.logback", name = "logback-classic", version = "1.1.6")

    //  because the eclipse release of SWT is abandoned on maven, this repo has a newer version of SWT,
    //  http://maven-eclipse.github.io/maven
    // 4.4 is the oldest version that works with us. We use reflection to access SWT, so we can compile the project without needing SWT
    val swtDep = testCompileOnly(group = "org.eclipse.swt", name = getSwtMavenName(), version = "4.4+")

    // JavaFX isn't always added to the compile classpath....
    testCompile(files(System.getProperty("java.home", ".") + "/lib/ext/jfxrt.jar"))

    // dependencies for our test examples
    swingExampleCompile(configurations.compile)
    javaFxExampleCompile(configurations.compile)
    swtExampleCompile(configurations.compile)
    swtExampleCompile(swtDep)

    swingExampleCompile(log)
    javaFxExampleCompile(log)
    swtExampleCompile(log)
}



project("Utilities") {
    tasks.withType<Test> {
        // want to remove utilities project from unit tests. It's unnecessary to run unit tests for the entire Utilities project
        exclude("**/*")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.isIncremental = true
        options.isFork = true
        options.forkOptions.executable = "javac"

        // setup compile options. we specifically want to suppress usage of "Unsafe"
        options.compilerArgs = listOf("-XDignore.symbol.file", "-Xlint:deprecation")
    }
}



///////////////////////////////
//////    Task defaults
///////////////////////////////
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"

//    options.bootstrapClasspath = files("/jre/lib/rt.jar")
    sourceCompatibility = JavaVersion.VERSION_1_6.toString()
    targetCompatibility = JavaVersion.VERSION_1_6.toString()
}

tasks.withType<Jar> {
    setDuplicatesStrategy(DuplicatesStrategy.FAIL)

    manifest {
        attributes["Implementation-Version"] = version
        attributes["Built-Date"] = Instant.now().toString()
    }
}


///////////////////////////////
//////    UTILITIES COMPILE (for inclusion into jars)
///////////////////////////////
task<JavaCompile>("compileUtils") {
    // we don't want the default include of **/*.java
    includes.clear()

    source = fileTree("../Utilities/src")
    include(javaFile (
            "dorkbox.util.SwingUtil",
            "dorkbox.util.OS",
            "dorkbox.util.OSUtil",
            "dorkbox.util.OSType",
            "dorkbox.util.ImageResizeUtil",
            "dorkbox.util.ImageUtil",
            "dorkbox.util.CacheUtil",
            "dorkbox.util.IO",
            "dorkbox.util.JavaFX",
            "dorkbox.util.Property",
            "dorkbox.util.Keep",
            "dorkbox.util.FontUtil",
            "dorkbox.util.ScreenUtil",
            "dorkbox.util.ClassLoaderUtil",
            "dorkbox.util.Swt",
            "dorkbox.util.NamedThreadFactory",
            "dorkbox.util.ActionHandlerLong",
            "dorkbox.util.FileUtil",
            "dorkbox.util.MathUtil",
            "dorkbox.util.LocationResolver",
            "dorkbox.util.Desktop"))

    // entire packages/directories
    include("dorkbox/util/jna/**/*.java")
    include("dorkbox/util/windows/**/*.java")
    include("dorkbox/util/swing/**/*.java")

    classpath = sourceSets.main.compileClasspath
    destinationDir = file("$rootDir/build/classes_utilities")
}


///////////////////////////////
//////    Tasks to launch examples from gradle
///////////////////////////////
task<JavaExec>("swingExample") {
    classpath = sourceSets.swingExample.runtimeClasspath
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



///////////////////////////////
//////    Jar Tasks
///////////////////////////////
val jar: Jar by tasks
jar.apply {
    dependsOn("compileUtils")

    // include applicable class files from subset of Utilities project
    from((tasks["compileUtils"] as JavaCompile).destinationDir)
}

task<Jar>("jarSwingExample") {
    dependsOn("jar")

    baseName = "SystemTray-SwingExample"
    group = BasePlugin.BUILD_GROUP
    description = "Create an all-in-one example for testing, using Swing"

    from(sourceSets.swingExample.output.classesDirs)
    from(sourceSets.swingExample.output.resourcesDir)

    // add all of the main project jars as a fat-jar for all examples, exclude the Utilities.jar contents
    from(configurations.compile.filter { it.nameWithoutExtension != "Utilities"}
                               .map { if (it.isDirectory) it else zipTree(it) })

    // include applicable class files from subset of Utilities project
    from((tasks["compileUtils"] as JavaCompile).destinationDir)

    manifest {
        attributes["Main-Class"] = "dorkbox.TestTray"
    }
}


task<Jar>("jarJavaFxExample") {
    dependsOn("jar")

    baseName = "SystemTray-JavaFxExample"
    group = BasePlugin.BUILD_GROUP
    description = "Create an all-in-one example for testing, using JavaFX"

    from(sourceSets.javaFxExample.output.classesDirs)
    from(sourceSets.javaFxExample.output.resourcesDir)

    // add all of the main project jars as a fat-jar for all examples, exclude the Utilities.jar contents
    from(configurations.compile.filter { it.nameWithoutExtension != "Utilities" }
                               .map { if (it.isDirectory) it else zipTree(it) })

    // include applicable class files from subset of Utilities project
    from((tasks["compileUtils"] as JavaCompile).destinationDir)


    manifest {
        attributes["Main-Class"] = "dorkbox.TestTrayJavaFX"
        attributes["Class-Path"] = System.getProperty("java.home", ".") + "/lib/ext/jfxrt.jar"
    }
}

task<Jar>("jarSwtExample") {
    dependsOn("jar")

    baseName = "SystemTray-SwtExample"
    group = BasePlugin.BUILD_GROUP
    description = "Create an all-in-one example for testing, using SWT"

    from(sourceSets.swtExample.output.classesDirs)
    from(sourceSets.swtExample.output.resourcesDir)

    // add all of the main project jars as a fat-jar for all examples, exclude the Utilities.jar contents
    from(configurations.compile.filter { it.nameWithoutExtension != "Utilities" }
                               .map { if (it.isDirectory) it else zipTree(it) })

    // include applicable class files from subset of Utilities project
    from((tasks["compileUtils"] as JavaCompile).destinationDir)

    manifest {
        attributes["Main-Class"] = "dorkbox.TestTraySwt"
    }
}


task("jarAllExamples") {
    dependsOn("jarSwingExample")
    dependsOn("jarJavaFxExample")
    dependsOn("jarSwtExample")

    group = BasePlugin.BUILD_GROUP
    description = "Create all-in-one examples for testing, using Swing, JavaFX, and SWT"
}



operator fun Regex.contains(text: CharSequence): Boolean = this.matches(text)
fun getSwtMavenName(): String {
    var platform = System.getProperty("os.name")
    when (platform.replace("\\s".toRegex(), "").toLowerCase()) {
        in Regex(".*linux.*") -> platform = "linux"
        in Regex(".*darwin.*") -> platform = "macosx"
        in Regex(".*osx.*") -> platform = "macosx"
        in Regex(".*win.*") -> platform = "win32"
    }

    var arch = System.getProperty("os.arch")
    if (arch.matches(".*64.*".toRegex())) {
        arch = "x86_64"
    }
    else {
        arch = "x86"
    }


    //  because the eclipse release of SWT is abandoned on maven, this MAVEN repo has newer version of SWT,
    //  https://github.com/maven-eclipse/maven-eclipse.github.io   for the website about it
    //  http://maven-eclipse.github.io/maven  for the maven repo

    return "org.eclipse.swt.gtk.${platform}.${arch}"
}
