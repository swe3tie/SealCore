plugins {
    `java-library`
}

/**
 * ExcellentEconomy, forked.
 *
 * Upstream 2.8.0 is Paper only and pins paper-api 26.1.2, so a Folia server on
 * 26.2 cannot run it. Both problems are dependency level, not source level:
 *
 *  - Folia support lives in NightCore. EE never touches BukkitScheduler itself, it
 *    goes through NightCore's AbstractPlugin, and NightCore has shipped a
 *    FoliaScheduler since 2.16.0. EE 2.8.0 pins 2.15.1, which predates it, so the
 *    bump to 2.16.4 is the whole fix.
 *  - 26.2 is a compile target bump. EE's own source compiles against the 26.2 API
 *    unchanged.
 *
 * See FORK.md for the exact diff against upstream.
 */

// Distinct from upstream 2.8.0 so an operator can never mistake a jar of this
// fork for the original on a server that already has one.
group = "sealmc.swe3tie"
version = "2.8.0-sealcore.2"

base {
    archivesName.set("ExcellentEconomy")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.nightexpressdev.com/releases")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.rosewooddev.io/repository/public/")
}

dependencies {
    // Pinned, unlike upstream's `26.1.2.build.+` dynamic version, so a build
    // cannot silently start compiling against a different API line.
    compileOnly(libs.paperApiV262)

    // Upstream pins 2.15.1; 2.16.4 is the newest release on the NightCore maven
    // and the first line whose artifacts carry the Folia scheduler.
    compileOnly(libs.nightCore)

    // VaultAPI 1.7 drags in org.bukkit:bukkit 1.13.1, whose capability clashes
    // with the paper-api we compile against. Upstream excludes it too.
    compileOnly(libs.vaultApi) {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly(libs.placeholderapi)
    compileOnly(libs.playerPoints)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
    // Loading ExcellentEconomyAPI drags in its NightCore supertypes and its Bukkit
    // parameter types, so the compileOnly set has to be on the test runtime classpath
    // too, not just the test compile classpath.
    testImplementation(libs.paperApiV262)
    testImplementation(libs.nightCore)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Upstream compiles against these APIs without complaint, so the 58
    // [removal] warnings it prints all come from NightCore marking its config
    // helpers for removal. None of them are ours to fix, and leaving them on
    // would bury a real warning, so both categories are silenced.
    options.compilerArgs.add("-Xlint:-deprecation")
    options.compilerArgs.add("-Xlint:-removal")
}

tasks.processResources {
    // Replicates maven's <filtering>true</filtering>: the jar's plugin.yml reports
    // this build's version.
    inputs.property("version", project.version.toString())
    // Both descriptors ship in the jar and Paper/Folia pick whichever is relevant,
    // so both have to be filtered. Leaving paper-plugin.yml raw is how the first
    // build shipped a literal `${version}` that Folia then refused to load.
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand("version" to project.version)
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "ExcellentEconomy (SealCore fork)",
            "Implementation-Version" to project.version,
        )
    }
}
