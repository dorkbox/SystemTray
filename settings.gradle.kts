import java.util.*

include("Utilities")

rootProject.name = "SystemTray"


for (project in rootProject.children) {
    if (project.name == "Utilities") {
        // utilities are a DIFFERENT than all the other projects, in that they are never built directly, but are embedded into the project
        project.projectDir = file("../Utilities")
        project.buildFileName = "build.gradle.kts"
    }

    assert (project.projectDir.isDirectory)
    assert (project.buildFile.isFile)
}
