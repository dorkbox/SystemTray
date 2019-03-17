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

import java.nio.file.Paths
import java.time.Instant

buildscript {
    println("Gradle " + project.getGradle().getGradleVersion())

    // load properties from custom location
    def propsFile = Paths.get("${projectDir}/../../gradle.properties").normalize().toFile()
    if (propsFile.canRead()) {
        println("Loading custom property data from: ${propsFile}")

        def props = new Properties()
        propsFile.withInputStream {props.load(it)}
        props.each {key, val -> project.ext.set(key, val)}
    } else {
        ext.sonatypeUsername = ""
        ext.sonatypePassword = ""
    }


    // for plugin publishing and license sources
    repositories {
        maven {url "https://plugins.gradle.org/m2/"}
    }
    dependencies {
        // this is the only way to also get the source code for IDE auto-complete
        classpath "gradle.plugin.com.dorkbox:Licensing:1.2.2"
        classpath "gradle.plugin.com.dorkbox:Licensing:1.2.2:sources"
    }
}

plugins {
    id 'java'
    id 'maven-publish'
    id 'signing'
    id 'idea'

    // close and release on sonatype
    id 'io.codearte.nexus-staging' version '0.11.0'

    id "com.dorkbox.CrossCompile" version "1.0.1"
    id "com.dorkbox.VersionUpdate" version "1.2"

    // setup checking for the latest version of a plugin or dependency (and updating the gradle build)
    id "se.patrikerdes.use-latest-versions" version "0.2.3"
    id 'com.github.ben-manes.versions' version '0.16.0'
}

// this is the only way to also get the source code for IDE auto-complete
apply plugin: "com.dorkbox.Licensing"

// give us access to api/implementation differences for building java libraries
apply plugin: 'java-library'


// optionally let us specify which SWT to use. options are win32/mac32/linux32 and win64/mac64/linux64
// this is needed when building the SWT test example for a different OS combinations
//ext.swt = 'win64'
apply from: '../Utilities/scripts/gradle/swt.gradle'

// Utilities project shared configuration
apply from: '../Utilities/scripts/gradle/utilities.gradle'



project.description = 'Cross-platform SystemTray support for Swing/AWT, GtkStatusIcon, and AppIndicator on Java 6+'
project.group = 'com.dorkbox'
project.version = '3.17'

project.ext.name = 'SystemTray'
project.ext.url = 'https://git.dorkbox.com/dorkbox/SystemTray'

project.ext.javaVersion = JavaVersion.VERSION_1_6


sourceCompatibility = project.ext.javaVersion
targetCompatibility = project.ext.javaVersion


idea {
    module {
        downloadJavadoc = false
        downloadSources = true
    }
}


licensing {
    license(License.APACHE_2) {
        author 'dorkbox, llc'
        url project.ext.url
        note project.description
    }

    license('Dorkbox Utils', License.APACHE_2) {
        author 'dorkbox, llc'
        url 'https://git.dorkbox.com/dorkbox/Utilities'
    }

    license('JNA', License.APACHE_2) {
        copyright 2011
        author 'Timothy Wall'
        url 'https://github.com/twall/jna'
    }

    license('Lantern', License.APACHE_2) {
        copyright 2010
        author 'Brave New Software Project, Inc.'
        url 'https://github.com/getlantern/lantern'
    }

    license('QZTray', License.APACHE_2) {
        copyright 2016
        author 'Tres Finocchiaro, QZ Industries, LLC'
        url 'https://github.com/tresf/tray/blob/dorkbox/src/qz/utils/ShellUtilities.java'
        note 'Partial code released as Apache 2.0 for use in the SystemTray project by dorkbox, llc. Used with permission.'
    }

    license('SLF4J', License.MIT) {
        copyright 2008
        author 'QOS.ch'
        url 'http://www.slf4j.org'
    }
}

configurations {
    swtExampleJar
}

sourceSets {
    main {
        java {
            setSrcDirs Collections.singletonList('src')
        }

        resources {
            setSrcDirs Collections.singletonList('src')
            include 'dorkbox/systemTray/gnomeShell/extension.js',
                    'dorkbox/systemTray/gnomeShell/appindicator.zip',
                    'dorkbox/systemTray/util/error_32.png'
        }
    }

    test {
        java {
            setSrcDirs Collections.singletonList('test')

            // this is required because we reset the srcDirs to 'test' above, and 'main' must manually be added back
            srcDir main.java
        }

        resources {
            setSrcDirs Collections.singletonList('test')
            include 'dorkbox/*.png'
        }
    }

    example {
        java {
            setSrcDirs Collections.singletonList('test')
            include utilFiles('dorkbox.TestTray',
                              'dorkbox.CustomSwingUI')
            srcDir main.java
        }

        resources {
            setSrcDirs Collections.singletonList('test')
            include 'dorkbox/*.png'

            source sourceSets.main.resources
        }
    }

    javaFxExample {
        java {
            setSrcDirs Collections.singletonList('test')
            include utilFiles('dorkbox.TestTray',
                              'dorkbox.TestTrayJavaFX',
                              'dorkbox.CustomSwingUI')
            srcDir main.java
        }

        resources {
            setSrcDirs Collections.singletonList('test')
            include 'dorkbox/*.png'

            source sourceSets.main.resources
        }
    }

    swtExample {
        java {
            setSrcDirs Collections.singletonList('test')
            include utilFiles('dorkbox.TestTray',
                              'dorkbox.TestTraySwt',
                              'dorkbox.CustomSwingUI')
            srcDir main.java
        }

        resources {
            setSrcDirs Collections.singletonList('test')
            include 'dorkbox/*.png'

            source sourceSets.main.resources
        }
    }
}


repositories {
    mavenLocal() // this must be first!

    maven {
        //  because the eclipse release of SWT is abandoned on maven, this MAVEN repo has newer version of SWT,
        url 'http://maven-eclipse.github.io/maven'
    }
    jcenter()
}

dependencies {
    // utilities dependencies compile SPECIFICALLY so that java 9+ modules work. We have to remove this project jar from maven builds
    implementation(project('Utilities')) {
        // don't include any of the project dependencies for anything
        transitive = false
    }

    // our main dependencies are ALSO the same as the limited utilities (they are not automatically pulled in from other sourceSets)
    // needed by the utilities (custom since we don't want to include everything). IntelliJ includes everything, but our builds do not


    api 'com.dorkbox:ShellExecutor:1.1'

    api 'org.javassist:javassist:3.23.0-GA'
    api 'net.java.dev.jna:jna:4.5.2'
    api 'net.java.dev.jna:jna-platform:4.5.2'
    api 'org.slf4j:slf4j-api:1.7.25'


    def log = implementation 'ch.qos.logback:logback-classic:1.1.6'

    //  because the eclipse release of SWT is abandoned on maven, this repo has a newer version of SWT,
    //  http://maven-eclipse.github.io/maven
    // 4.4 is the oldest version that works with us. We use reflection to access SWT, so we can compile the project without needing SWT
    def swtDep = swtExampleJar "org.eclipse.swt:org.eclipse.swt.${swtWindowingLibrary}.${swtPlatform}.${swtArch}:4.4+"

    testCompileOnly swtDep

    // JavaFX isn't always added to the compile classpath....
    testImplementation files("${System.getProperty('java.home', '.')}/lib/ext/jfxrt.jar")

    // dependencies for our test examples
    exampleImplementation configurations.implementation, log
    javaFxExampleImplementation configurations.implementation, log
    swtExampleImplementation configurations.implementation, log, swtDep
}

///////////////////////////////
//////    Task defaults
///////////////////////////////
tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
}

tasks.withType(Jar) {
    duplicatesStrategy DuplicatesStrategy.FAIL

    manifest {
        attributes['Implementation-Version'] = version
        attributes['Build-Date'] = Instant.now().toString()
        attributes['Automatic-Module-Name'] = project.ext.name.toString()
    }
}

///////////////////////////////
//////    UTILITIES COMPILE (for inclusion into jars)
///////////////////////////////
static String[] utilFiles(String... fileNames) {
    def fileList = [] as ArrayList

    for (name in fileNames) {
        def fixed = name.replace('.', '/') + '.java'
        fileList.add(fixed)
    }

    return fileList
}

task compileUtils(type: JavaCompile) {
    // we don't want the default include of **/*.java
    getIncludes().clear()

    source = Collections.singletonList('../Utilities/src')

    include utilFiles('dorkbox.util.SwingUtil',
                      'dorkbox.util.OS',
                      'dorkbox.util.OSUtil',
                      'dorkbox.util.OSType',
                      'dorkbox.util.ImageResizeUtil',
                      'dorkbox.util.ImageUtil',
                      'dorkbox.util.CacheUtil',
                      'dorkbox.util.IO',
                      'dorkbox.util.JavaFX',
                      'dorkbox.util.Property',
                      'dorkbox.util.Keep',
                      'dorkbox.util.FontUtil',
                      'dorkbox.util.ScreenUtil',
                      'dorkbox.util.ClassLoaderUtil',
                      'dorkbox.util.Swt',
                      'dorkbox.util.NamedThreadFactory',
                      'dorkbox.util.ActionHandlerLong',
                      'dorkbox.util.FileUtil',
                      'dorkbox.util.MathUtil',
                      'dorkbox.util.LocationResolver',
                      'dorkbox.util.Desktop')

    // entire packages/directories
    include('dorkbox/util/jna/**/*.java')
    include('dorkbox/util/windows/**/*.java')
    include('dorkbox/util/swing/**/*.java')

    classpath = sourceSets.main.compileClasspath
    destinationDir = file("$rootDir/build/classes_utilities")
}

jar {
    dependsOn compileUtils

    // include applicable class files from subset of Utilities project
    from compileUtils.destinationDir
}

///////////////////////////////
//////    Tasks to launch examples from gradle
///////////////////////////////
task example(type: JavaExec) {
    classpath sourceSets.example.runtimeClasspath

    group = 'examples'
    main = 'dorkbox.TestTray'
    standardInput = System.in
}

task javaFxExample(type: JavaExec) {
    classpath sourceSets.javaFxExample.runtimeClasspath

    group = 'examples'
    main = 'dorkbox.TestTrayJavaFX'
    standardInput = System.in
}

task swtExample(type: JavaExec) {
    classpath sourceSets.swtExample.runtimeClasspath

    group = 'examples'
    main = 'dorkbox.TestTraySwt'
    standardInput = System.in
}

task jarExample(type: Jar) {
    dependsOn jar

    baseName = 'SystemTray-Example'
    group = BasePlugin.BUILD_GROUP
    description = 'Create an all-in-one example for testing, on a standard Java installation'

    from sourceSets.example.output.classesDirs
    from sourceSets.example.output.resourcesDir

    from compileUtils.destinationDir

    // add all of the main project jars as a fat-jar for all examples, exclude the Utilities.jar contents
    from configurations.runtimeClasspath
                       .findAll {it.name.endsWith('jar') && it.name != 'Utilities.jar'}
                       .collect { zipTree(it)}

    manifest {
        attributes['Main-Class'] = 'dorkbox.TestTray'
    }
}


task jarJavaFxExample(type: Jar) {
    dependsOn jar

    baseName = 'SystemTray-JavaFxExample'
    group = BasePlugin.BUILD_GROUP
    description = 'Create an all-in-one example for testing, using JavaFX'

    from sourceSets.javaFxExample.output.classesDirs
    from sourceSets.javaFxExample.output.resourcesDir

    from compileUtils.destinationDir

    // add all of the main project jars as a fat-jar for all examples, exclude the Utilities.jar contents
    from configurations.runtimeClasspath
                       .findAll {it.name.endsWith('jar') && it.name != 'Utilities.jar'}
                       .collect {zipTree(it)}

    manifest {
        attributes['Main-Class'] = 'dorkbox.TestTrayJavaFX'
        attributes['Class-Path'] = "${System.getProperty('java.home', '.')}/lib/ext/jfxrt.jar"
    }
}

task jarSwtExample(type: Jar) {
    dependsOn jar

    baseName = 'SystemTray-SwtExample'
    group = BasePlugin.BUILD_GROUP
    description = 'Create an all-in-one example for testing, using SWT'

    from sourceSets.swtExample.output.classesDirs
    from sourceSets.swtExample.output.resourcesDir

    from compileUtils.destinationDir

    // include SWT
    from configurations.swtExampleJar
                       .collect {zipTree(it)}

    // add all of the main project jars as a fat-jar for all examples, exclude the Utilities.jar contents
    from configurations.runtimeClasspath
                       .findAll {it.name.endsWith('jar') && it.name != 'Utilities.jar'}
                       .collect {zipTree(it)}

    manifest {
        attributes['Main-Class'] = 'dorkbox.TestTraySwt'
    }
}


task jarAllExamples {
    dependsOn jarExample
    dependsOn jarJavaFxExample
    dependsOn jarSwtExample

    group = BasePlugin.BUILD_GROUP
    description = 'Create all-in-one examples for testing, using Swing, JavaFX, and SWT'
}



/////////////////////////////
////    Maven Publishing + Release
/////////////////////////////
task sourceJar(type: Jar) {
    description = "Creates a JAR that contains the source code."

    from sourceSets.main.java

    classifier = "sources"
}

task javaDocJar(type: Jar) {
    description = "Creates a JAR that contains the javadocs."

    classifier = "javadoc"
}

// for testing, we don't publish to maven central, but only to local maven
publishing {
    publications {
        maven(MavenPublication) {
            from components.java

            artifact(javaDocJar)
            artifact(sourceJar)

            groupId project.group
            artifactId project.ext.name
            version project.version

            pom {
                withXml {
                    // eliminate logback and utilities (no need in maven POMs)
                    def root = asNode()

                    root.dependencies.'*'.findAll() {
                        it.artifactId.text() == "logback-classic" || it.artifactId.text() == "Utilities"
                    }.each() {
                        it.parent().remove(it)
                    }
                }

                name = project.ext.name
                url = project.ext.url
                description = project.description

                issueManagement {
                    url = "${project.ext.url}/issues".toString()
                    system = 'Gitea Issues'
                }

                organization {
                    name = 'dorkbox, llc'
                    url = 'https://dorkbox.com'
                }

                developers {
                    developer {
                        name = 'dorkbox, llc'
                        email = 'email@dorkbox.com'
                    }
                }

                scm {
                    url = project.ext.url
                    connection = "scm:${project.ext.url}.git".toString()
                }
            }
        }
    }

    repositories {
        maven {
            url "https://oss.sonatype.org/service/local/staging/deploy/maven2"
            credentials {
                username sonatypeUsername
                password sonatypePassword
            }
        }
    }
}

nexusStaging {
    username sonatypeUsername
    password sonatypePassword
}

signing {
    sign publishing.publications.maven
}

// output the release URL in the console
releaseRepository.doLast {
    def URL = 'https://oss.sonatype.org/content/repositories/releases/'
    def projectName = project.group.toString().replaceAll('\\.', '/')
    def name = project.ext.name
    def version = project.version

    println("Maven URL: ${URL}${projectName}/${name}/${version}/")
}

// we don't use maven with the plugin (it's uploaded separately to gradle plugins)
tasks.withType(PublishToMavenRepository) {
    onlyIf {
        repository == publishing.repositories.maven && publication == publishing.publications.maven
    }
}
tasks.withType(PublishToMavenLocal) {
    onlyIf {
        publication == publishing.publications.maven
    }
}

/////////////////////////////
////    Gradle Wrapper Configuration.
///  Run this task, then refresh the gradle project
/////////////////////////////
task updateWrapper(type: Wrapper) {
    gradleVersion = '4.10.2'
    distributionUrl = distributionUrl.replace("bin", "all")
    setDistributionType(Wrapper.DistributionType.ALL)
}