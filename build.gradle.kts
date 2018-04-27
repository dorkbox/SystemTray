import org.gradle.api.internal.HasConvention
import org.gradle.internal.impldep.org.apache.http.client.methods.RequestBuilder.options
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.android.AndroidGradleWrapper.getTestVariants
import org.jetbrains.kotlin.gradle.plugin.android.AndroidGradleWrapper.srcDir
import java.time.Instant

plugins {
    java
    maven
    `maven-publish`
    kotlin("jvm") version "1.2.40"
}

apply {
    plugin("java")
    plugin("kotlin")
}

kotlin.experimental.coroutines = Coroutines.ENABLE


//    version = getWPILibVersion() ?: getVersionFromGitTag(fallback = "0.0.0") // fall back to git describe if no WPILib version is set
group = "com.dorkbox"
version = "3.13-SNAPSHOT"
description = """Dorkbox-SystemTray"""


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

// make working with sourceSets easier
val sourceSets = java.sourceSets
fun sourceSets(block: SourceSetContainer.() -> Unit) = sourceSets.apply(block)

val SourceSetContainer.main: SourceSet get() = maybeCreate("main")
fun SourceSetContainer.main(block: SourceSet.() -> Unit) = main.apply(block)

val SourceSetContainer.test: SourceSet get() = maybeCreate("test")
fun SourceSetContainer.test(block: SourceSet.() -> Unit) = test.apply(block)

val SourceSetContainer.utilities: SourceSet get() = maybeCreate("utilities")
fun SourceSetContainer.utilities(block: SourceSet.() -> Unit) = utilities.apply(block)

// testing jars
val SourceSetContainer.swingExample: SourceSet get() = maybeCreate("swingExample")
fun SourceSetContainer.swingExample(block: SourceSet.() -> Unit) = swingExample.apply(block)
val org.gradle.api.tasks.SourceSetContainer.javaFxExample: SourceSet get() = maybeCreate("javaFxExample")
fun SourceSetContainer.javaFxExample(block: SourceSet.() -> Unit) = javaFxExample.apply(block)
//val org.gradle.api.tasks.SourceSetContainer.testSwt: SourceSet get() = maybeCreate("testSwt")
//fun SourceSetContainer.testSwt(block: SourceSet.() -> Unit) = testSwt.apply(block)

//val SourceSet.kotlin: SourceDirectorySet
//    get() = (this as HasConvention).convention.getPlugin<KotlinSourceSet>().kotlin

var SourceDirectorySet.sourceDirs: Iterable<File>
    get() = srcDirs
    set(value) {
        setSrcDirs(value)
    }


fun javaFile(vararg fileNames: String): Iterable<String> {
    var fileList = ArrayList<String>()

    for (name in fileNames) {
        val fixed = name.replace('.', '/') + ".java"
        fileList.add(fixed)
    }

    return fileList
}

sourceSets {
    utilities {
        java {
            sourceDirs = files("../Utilities/src")
            include(javaFile("dorkbox.util.SwingUtil",
                             "dorkbox.util.OS",
                             "dorkbox.util.OSUtil",
                             "dorkbox.util.OSType",
                             "dorkbox.util.ImageResizeUtil",
                             "dorkbox.util.ImageUtil",
                             "dorkbox.util.CacheUtil",
                             "dorkbox.util.IO",
                             "dorkbox.util.JavaFX",
                             "dorkbox.util.Property",
                             "dorkbox.util.Swt",
                             "dorkbox.util.Keep",
                             "dorkbox.util.FontUtil",
                             "dorkbox.util.ScreenUtil",
                             "dorkbox.util.ClassLoaderUtil",
                             "dorkbox.util.NamedThreadFactory",
                             "dorkbox.util.ActionHandlerLong",
                             "dorkbox.util.FileUtil",
                             "dorkbox.util.MathUtil",
                             "dorkbox.util.LocationResolver",
                             "dorkbox.util.Desktop"))

            // entire packages/directories
            include("dorkbox/util/jna/**/*")
            include("dorkbox/util/windows/**/*")
            include("dorkbox/util/swing/**/*")
        }
    }

    main {
        java {
            sourceDirs = files("src")
            srcDir(sourceSets.utilities.java)

            resources {
                sourceDirs = files("src")
                include("dorkbox/systemTray/gnomeShell/extension.js", "dorkbox/systemTray/util/error_32.png")
            }
        }
    }

    test {
        java.sourceDirs = files("test")
    }

    swingExample {
        java {
            sourceDirs = files("test")
            include(javaFile("dorkbox.TestTray", "dorkbox.CustomSwingUI"))

            srcDir(sourceSets.main.allJava)
            compileClasspath = sourceSets.main.compileClasspath

            resources {
                sourceDirs = files("test")
                include("dorkbox/*.png")
            }
        }
    }

    javaFxExample {
        java {
            sourceDirs = files("test")
            include(javaFile("dorkbox.TestTray", "dorkbox.TestTrayJavaFX", "dorkbox.CustomSwingUI"))

            srcDir(sourceSets.main.allJava)
            compileClasspath = sourceSets.main.compileClasspath

            resources {
                sourceDirs = files("test")
                include("dorkbox/*.png")
            }
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

project(":Utilities") {
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

dependencies {
    compile(project(":Utilities")) {
        // don't include any of the project dependencies for anything
        isTransitive = false
    }
    compile(group = "com.dorkbox", name = "ShellExecutor", version = "1.1+")

    compile(group = "ch.qos.logback", name = "logback-classic", version = "1.1.6")
    compile(group = "org.javassist", name = "javassist", version = "3.21.0-GA")
    compile(group = "net.java.dev.jna", name = "jna", version = "4.3.0")
    compile(group = "net.java.dev.jna", name = "jna-platform", version = "4.3.0")
    compile(group = "org.slf4j", name = "slf4j-api", version = "1.7.25")

    testCompileOnly(group = "org.eclipse.swt", name = "org.eclipse.swt.gtk.linux.x86_64", version = "4.3")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
//        suppressWarnings = true
    // setup compile options. we specifically want to suppress usage of "Unsafe"
}

tasks.withType<Jar> {
    manifest {
        attributes["Implementation-Version"] = version
        attributes["Built-Date"] = Instant.now().toString()
    }
}

task<Jar>("_swingExampleJar") {
    dependsOn("compileSwingExampleJava")

    baseName = "SwingExample"

    from(sourceSets.swingExample.output.classesDirs)
    from(sourceSets.swingExample.output.resourcesDir)

    // makes it a fat-jar, we filter out the "Utilities" project (because we manually
    // This line of code recursively collects and copies all of a project's files and adds them to the JAR itself.
    // One can extend this task, to skip certain files or particular types at will
    from(configurations.runtime
                 .filter({ it.nameWithoutExtension != "Utilities" })
                 .map({ if (it.isDirectory) it else zipTree(it) }))
    with(tasks["jar"] as CopySpec)


    manifest {
        attributes["Main-Class"] = "dorkbox.TestTray"
    }
}

task<Jar>("_javaFxExampleJar") {
    dependsOn("compileJavaFxExampleJava")

    baseName = "JavaFxExample"

    from(sourceSets.javaFxExample.output.classesDirs)
    from(sourceSets.javaFxExample.output.resourcesDir)

    // makes it a fat-jar, we filter out the "Utilities" project (because we manually
    // This line of code recursively collects and copies all of a project's files and adds them to the JAR itself.
    // One can extend this task, to skip certain files or particular types at will
    from(configurations.runtime
                 .filter({ it.nameWithoutExtension != "Utilities" })
                 .map({ if (it.isDirectory) it else zipTree(it) }))
    with(tasks["jar"] as CopySpec)


    manifest {
        attributes["Main-Class"] = "dorkbox.TestTrayJavaFX"
    }
}















//task<JavaCompile>("Java") {
//    source = sourceSets.main.allSource
//    classpath = sourceSets.main.compileClasspath
//    destinationDir = sourceSets.main.java.outputDir
//}

//
//project(":Swing") {
//    sourceSets {
//        main {
//            java {
//                sourceDirs = files("test")
//                include(javaFile("dorkbox.TestTray", "dorkbox.CustomSwingUI"))
//
////                java.srcDir(sourceSets.main.java)
////                java.srcDir(sourceSets.utilities.java)
//
//                resources {
//                    sourceDirs = files("test")
//                    include("dorkbox/*.png")
//                }
//            }
//        }
//    }
//    dependencies {
////        compile(project (":"))
//    }
//}


// val compileKotlin: KotlinCompile by tasks
// compileKotlin.kotlinOptions.jvmTarget = "1.8"

/* -------------------------------------------------------------------------------------------
Tasks and publishing
   ------------------------------------------------------------------------------------------- */
//val sourceJar = task<Jar>("sourceJar") {
//    description = "Creates a JAR that contains the source code."
//    from(java.sourceSets["main"].allSource)
//    classifier = "sources"
//}
//val javaDocJar = task<Jar>("javaDocJar") {
//    dependsOn("javadoc")
//    description = "Creates a JAR that contains the javadocs."
//    from(java.docsDir)
//    classifier = "javadoc"
//}
//
//publishing {
//    (publications) {
//        "mavenJava"(MavenPublication::class) {
//            from(components["java"])
//            artifactId = "SystemTray"
//            artifact(javaDocJar)
//            artifact(sourceJar)
//        }
//    }
//}



//task<JavaCompile>("compileTestJavafx") {
//    dependsOn("compileJava")
//
//    source = sourceSets.testJavafx.allSource
//    classpath = sourceSets.main.compileClasspath
//    destinationDir = sourceSets.testJavafx.java.outputDir
//}

//
//task<Jar>("_testSwingJar") {
//    dependsOn(task<JavaCompile>("compileTestSwing") {
////        dependsOn("compileJava")
//
//        sourceCompatibility = "1.7"
//        targetCompatibility = "1.7"
//
//        source = sourceSets.testSwing.allJava
//        classpath = sourceSets.main.compileClasspath
//        destinationDir = sourceSets.testSwing.java.outputDir
//    })
//
//    baseName = "testSwing"
//
//
//
//    println("source")
//    sourceSets.main.compileClasspath.forEach({ it: File? -> println("  " + it) })
//    from(sourceSets.testSwing.output.classesDirs)
//
//    // makes it a fat-jar
//    // This line of code recursively collects and copies all of a project's files and adds them to the JAR itself.
//    // One can extend this task, to skip certain files or particular types at will
//    // specifically, we filter out the "Utilities" project
////    from(configurations.runtime
////                 .filter({ it.nameWithoutExtension != "Utilities" })
////                 .map({ if (it.isDirectory) it else zipTree(it) }))
//    with(tasks["jar"] as CopySpec)
//
//
//    manifest {
//        attributes["Main-Class"] = "dorkbox.TestTray"
//    }
//}
//



/*


project(":SwingTest") {
    sourceSets {

    }





//        task<JavaCompile>("compileTestSwt") {
//            dependsOn("compileJava")
//
//            source = sourceSets.testSwt.allSource
//            classpath = sourceSets.main.compileClasspath
//            destinationDir = sourceSets.testSwt.java.outputDir
//        }

//
//        task<Jar>("testJavaFXJar") {
//            dependsOn("compileTestJavaFX")
//
//            baseName = "testJavaFx"
//
//            from(sourceSets.testJavaFX.resources)
//
//            // makes it a fat-jar
//            // This line of code recursively collects and copies all of a project's files and adds them to the JAR itself.
//            // One can extend this task, to skip certain files or particular types at will
//            // specifically, we filter out the "Utilities" project
//            from(configurations.runtime.filter({ it.nameWithoutExtension != "Utilities" }).map({ if (it.isDirectory) it else zipTree(it) }))
//            with(tasks["jar"] as CopySpec)
//
//
//            manifest {
//                attributes["Main-Class"] = "dorkbox.TestTrayJavaFX"
//            }
//        }
//
//        task<Jar>("testSwtJar") {
//            dependsOn("compileTestSwt")
//
//            baseName = "testSwt"
//
//            from(sourceSets.testSwt.resources)
//
//            // makes it a fat-jar
//            // This line of code recursively collects and copies all of a project's files and adds them to the JAR itself.
//            // One can extend this task, to skip certain files or particular types at will
//            // specifically, we filter out the "Utilities" project
//            from(configurations.runtime.filter({ it.nameWithoutExtension != "Utilities" }).map({ if (it.isDirectory) it else zipTree(it) }))
//            with(tasks["jar"] as CopySpec)
//
//
//            manifest {
//                attributes["Main-Class"] = "dorkbox.TestTraySwt"
//            }
//        }

//

//

}

*/

//
//task("hello-src-set") {
//    var files: Set<File> = sourceSets.main.java.srcDirs
//    println(files)
//
//    println("Utilities")
//    println(sourceSets.utilities.java.filter.includes)
//
//    files = sourceSets.utilities.java.srcDirs
//    println(files)
//}


//shadowJar {
//    dependencies {
//        exclude(dependency('junit:junit:3.8.2'))
//    }
//}


//tasks.create('smokeTest', SmokeTest) {
//    SmokeTest task ->
//    group = "Verification"
//    description = "Runs Smoke tests"
//    testClassesDirs = sourceSets.smokeTest.output.classesDirs
//    classpath = sourceSets.smokeTest.runtimeClasspath
//    maxParallelForks = 1 // those tests are pretty expensive, we shouldn't execute them concurrently
//}


//task copyReport (type: Copy) {
//    from file ("${buildDir}/reports/my-report.pdf")
//    into file ("${buildDir}/toArchive")
//}

//task copyReport2 (type: Copy) {
//    from "${buildDir}/reports/my-report.pdf"
//    into "${buildDir}/toArchive"
//}

//task copyReport3 (type: Copy) {
//    from myReportTask . outputFile into archiveReportsTask . dirToArchive
//}
//task packageDistribution (type: Zip) {
//    archiveName = "my-distribution.zip"
//    destinationDir = file("${buildDir}/dist")
//
//    from "${buildDir}/toArchive"
//}
//task unpackFiles (type: Copy) {
//    from zipTree ("src/resources/thirdPartyResources.zip")
//    into "${buildDir}/resources"
//}
//task uberJar (type: Jar) {
//    appendix = 'uber'
//
//    from sourceSets . main . output from configurations . runtimeClasspath . files .
//    findAll { it.name.endsWith('jar') }.collect { zipTree(it) }
//}
//task moveReports {
//    doLast {
//        ant.move file : "${buildDir}/reports",
//        todir: "${buildDir}/toArchive"
//    }
//}
