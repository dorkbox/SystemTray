import org.gradle.api.internal.HasConvention
import org.gradle.internal.impldep.org.apache.http.client.methods.RequestBuilder.options
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.android.AndroidGradleWrapper.getTestVariants
import org.jetbrains.kotlin.gradle.plugin.android.AndroidGradleWrapper.srcDir

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

group = "com.dorkbox"
version = "3.13-SNAPSHOT"
description = """Dorkbox-SystemTray"""


java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}


// make working with sourcesets easier
val sourceSets = java.sourceSets
fun sourceSets(block: SourceSetContainer.() -> Unit) = sourceSets.apply(block)

val SourceSetContainer.main: SourceSet get() = getByName("main")
fun SourceSetContainer.main(block: SourceSet.() -> Unit) = main.apply(block)

sourceSets.create("utilities")
val SourceSetContainer.utilities: SourceSet get() = getByName("utilities")
fun SourceSetContainer.utilities(block: SourceSet.() -> Unit) = utilities.apply(block)

val SourceSetContainer.test: SourceSet get() = getByName("test")
fun SourceSetContainer.test(block: SourceSet.() -> Unit) = test.apply(block)

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
            include(javaFile(
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
                    "dorkbox.util.Desktop"
//                    "",
                    ))
            // entire packages/directory
            include("dorkbox/util/jna/**/*")
            include("dorkbox/util/windows/**/*")
            include("dorkbox/util/swing/**/*")







//            include("jna/linux/GObject.java")
            filter.includes.clear()
//            filter.includes.add("OS.java")
//            filter.includes.add("ImageResizeUtil.java")
//            filter.includes.add("jna/linux/GObject.java")
        }
//        java.sourceDirs = files("../Utilities/src")
//        java.source(get("OS", "OSType"))
//        resources.sourceDirs = get("OS", "OSType")
    }
    main {
        java {
            sourceDirs = files("src")
            java.srcDir(sourceSets.utilities.java)
        }
    }
    test {
        java.sourceDirs = files("test")
//        resources.sourceDirs = files("test/res")
    }
}

// setup the sources jar to use all of the sources specified in our "main" source set
val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

publishing {
    (publications) {
        "mavenJava"(MavenPublication::class) {
            from(components["java"])
            artifactId = "SystemTray"
            artifact(sourcesJar)
        }
    }
}

repositories {
    mavenLocal()
    maven { setUrl("http://repo.maven.apache.org/maven2") }
    maven { setUrl("http://cfmlprojects.org/artifacts") }
}

dependencies {
//    compile(project(":Utilities")) {
//        isTransitive = false
//    }
    compile(group = "com.dorkbox", name = "ShellExecutor", version = "1.1+")

    compile(group = "ch.qos.logback", name = "logback-classic", version = "1.1.6")
    compile(group = "org.javassist", name = "javassist", version = "3.21.0-GA")
    compile(group = "net.java.dev.jna", name = "jna", version = "4.3.0")
    compile(group = "net.java.dev.jna", name = "jna-platform", version = "4.3.0")
    compile(group = "org.slf4j", name = "slf4j-api", version = "1.7.25")

    testCompileOnly(group = "org.eclipse.swt", name = "org.eclipse.swt.gtk.linux.x86_64", version = "4.3")
}

// val compileKotlin: KotlinCompile by tasks
// compileKotlin.kotlinOptions.jvmTarget = "1.8"


//test {
//    // listen to events in the test execution lifecycle
//    beforeTest { descriptor ->
//        logger.lifecycle("Running test: " + descriptor)
//    }
//}



task("hello-src-set") {
    var files: Set<File> = sourceSets.main.java.srcDirs
    println(files)

    println("Utilities")
    println(sourceSets.utilities.java.filter.includes)

    files = sourceSets.utilities.java.srcDirs
    println(files)
}

tasks.withType<JavaCompile> {
    println("Configuring $name in project ${project.name}...")
    // UTF-8 characters are used in menus
    options.encoding = "UTF-8"

//        suppressWarnings = true
//    options.compilerArgs.add(file("../Utilities/src/dorkbox/util/OS.java").absolutePath)
    // setup compile options. we specifically want to suppress usage of "Unsafe"
//    val compileJava: JavaCompile by tasks
//compileJava.
//    println("Compiler Args")
//    for (arg in options.compilerArgs) {
//        println(arg)
//    }
}

project(":Utilities") {
    tasks.withType<Test> {
        // want to remove utilities project from unit tests. It's unnecessary to run unit tests for the entire Utilities project
        exclude("**/*")
    }
}


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
