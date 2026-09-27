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

        // The ExcellentEconomy fork below needs NightCore, Vault and PlayerPoints.
        // PREFER_SETTINGS means a project's own repositories {} block is ignored, so
        // they have to be declared here.
        maven("https://repo.nightexpressdev.com/releases")
        maven("https://jitpack.io")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.rosewooddev.io/repository/public/")
    }
}

rootProject.name = "SealCore"

include("sealcore")

// Phase 2: ExcellentEconomy with Folia and Minecraft 26.2. See Fork.md there.
include("excellenteconomy-fork")
