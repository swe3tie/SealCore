import java.io.ByteArrayOutputStream

plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

val paperApiVersion = property("paperApiVersion").toString()
val testPaperVersion = property("testPaperVersion").toString()
val testFoliaVersion = property("testFoliaVersion").toString()

group = "sealmc.swe3tie"
version = "0.1.0-SNAPSHOT"

// Libraries resolved by the server at boot through SealCoreLoader, so the jar stays small.
val runtimeLibraries = listOf(
    "com.zaxxer:HikariCP:7.1.0",
    "org.xerial:sqlite-jdbc:3.53.4.0",
    "com.mysql:mysql-connector-j:26.7.0",
)

java {
    // JDK 25 compiles, but the bytecode targets Java 21 so 1.21.11 servers can load it.
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.nightexpressdev.com/releases")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    mavenLocal() // forked ExcellentEconomy (2.8.0-sealcore.N) is published here
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    // Soft hooks. Never shaded; each is only touched when the plugin is present.
    compileOnly("su.nightexpress.excellenteconomy:ExcellentEconomy:2.8.0")
    compileOnly("su.nightexpress.nightcore:main:2.16.6")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")

    runtimeLibraries.forEach { compileOnly(it) }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
}

/** `<version>+<branch>.<commit>` so the server reports exactly which commit is running. */
fun gitOutput(vararg args: String): String? = try {
    val out = ByteArrayOutputStream()
    val proc = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
    proc.inputStream.copyTo(out)
    if (proc.waitFor() == 0) out.toString().trim().ifEmpty { null } else null
} catch (_: Exception) {
    null
}

val fullVersion: String = run {
    val branch = gitOutput("rev-parse", "--abbrev-ref", "HEAD")
    val commit = gitOutput("rev-parse", "--short", "HEAD")
    if (branch != null && commit != null) "${project.version}+$branch.$commit" else project.version.toString()
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    processResources {
        val props = mapOf(
            "version" to fullVersion,
            "libraries" to runtimeLibraries.joinToString("\n"),
        )
        inputs.properties(props)
        filesMatching(listOf("paper-plugin.yml", "sealcore-libraries.txt")) {
            expand(props)
        }
    }

    jar {
        archiveFileName = "SealCore-${fullVersion.replace('+', '-')}.jar"
    }

    test {
        useJUnitPlatform()
        systemProperty("java.io.tmpdir", System.getProperty("java.io.tmpdir"))
    }

    // Optional local plugin jars for smoke tests: -PtestPlugins=/path/to/plugins
    val extraPlugins = providers.gradleProperty("testPlugins").map { dir ->
        fileTree(dir) {
            include("ExcellentEconomy*.jar", "nightcore*.jar", "PlaceholderAPI*.jar", "packetevents*.jar")
        }
    }

    runServer {
        minecraftVersion(testPaperVersion)
        runDirectory = layout.projectDirectory.dir("run")
        if (extraPlugins.isPresent) pluginJars(extraPlugins.get())
        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
}

runPaper.folia.registerTask {
    minecraftVersion(testFoliaVersion)
    runDirectory = layout.projectDirectory.dir("run-folia")
    providers.gradleProperty("testPlugins").orNull?.let { dir ->
        pluginJars(fileTree(dir) {
            include("ExcellentEconomy*.jar", "nightcore*.jar", "PlaceholderAPI*.jar", "packetevents*.jar")
        })
    }
    jvmArgs("-Dcom.mojang.eula.agree=true")
}
