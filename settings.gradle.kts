pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.extendedclip.com/releases/")
    }
}

rootProject.name = "SealCore"

// Phase 2 module, added when the ExcellentEconomy 26.2 + Folia fork lands:
// include("excellenteconomy-fork")

include("sealcore")
