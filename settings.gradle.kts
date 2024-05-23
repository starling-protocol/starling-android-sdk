pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        /*flatDir {
            dirs("./protocol")
        }*/
    }
}

rootProject.name = "Starling"
include(":starling")
include(":protocol")

