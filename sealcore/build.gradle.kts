import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

// Provenance, resolved once by the root build, so the jar name, the jar
// manifest and plugin.yml all report the same commit.
val buildLabel = rootProject.extra["buildLabel"] as String
val gitBranch = rootProject.extra["gitBranch"] as String
val gitCommit = rootProject.extra["gitCommit"] as String

java {
    // The build itself runs on JDK 25, because the 26.x Paper API is Java 25
    // bytecode, but the shipped jar targets Java 21 so one artifact runs on
    // 1.21.11 and both 26.x lines.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

// `release` rather than source/target compatibility: it also pins the API, so
// a method newer than 21 cannot slip in through the JDK 25 toolchain.
tasks.withType<JavaCompile>().configureEach {
    if (!name.startsWith("compileCompat")) options.release.set(21)
}

dependencies {
    // Compile against the oldest supported line so we never touch APIs that are
    // missing on 1.21.11. compileOnly: never shade the server API.
    compileOnly(libs.paperApi)
    compileOnly(libs.placeholderapi)

    // Loaded at runtime through the `libraries:` block in plugin.yml, so the jar
    // stays small (sqlite-jdbc alone is ~13 MB).
    compileOnly(libs.hikaricp)
    compileOnly(libs.sqliteJdbc)
    compileOnly(libs.mysqlConnectorJ)

    // Shaded and relocated: PacketEvents must not be bundled twice.
    implementation(libs.packeteventsSpigot)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testImplementation(libs.hikaricp)
    testImplementation(libs.sqliteJdbc)
    testImplementation(libs.paperApi)
    testImplementation(libs.packeteventsSpigot)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ---------------------------------------------------------------------------
// Cross-version compatibility gate.
//
// The same sources are recompiled against the two newer Paper API lines. If an
// API we call was removed or changed in the 1.21 -> 26 jump, this fails the
// build instead of failing on a live server. Each version gets its own source
// set, compiled with its own classpath.
// ---------------------------------------------------------------------------
val compatVersions = mapOf(
    "26.1.2" to libs.versions.paperV2612.get(),
    "26.2" to libs.versions.paperV262.get(),
)

val mainJavaDirs = sourceSets.main.get().java.srcDirs

val compatTasks = compatVersions.map { (mcVersion, paperVersion) ->
    val suffix = mcVersion.replace(".", "")
    // Only sources, never the main output: the point is to prove the code
    // compiles against a different API, not to produce a runnable jar.
    val set = sourceSets.create("compat$suffix")
    set.java.setSrcDirs(mainJavaDirs)

    dependencies {
        add("compat${suffix}CompileOnly", "io.papermc.paper:paper-api:$paperVersion")
        add("compat${suffix}CompileOnly", libs.placeholderapi)
        add("compat${suffix}CompileOnly", libs.hikaricp)
        add("compat${suffix}CompileOnly", libs.sqliteJdbc)
        add("compat${suffix}CompileOnly", libs.mysqlConnectorJ)
        add("compat${suffix}Implementation", libs.packeteventsSpigot)
    }

    // The 26.x API is Java 25 bytecode, so variant selection for this source
    // set has to ask for JVM 25 even though the shipped jar targets 21.
    configurations.named("compat${suffix}CompileClasspath") {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
        }
    }

    tasks.named<JavaCompile>("compileCompat${suffix}Java") {
        group = "verification"
        description = "Compiles the plugin against the Paper API of Minecraft $mcVersion."
        // The 26.x API is Java 25 bytecode, so the gate must run on 25.
        options.release.set(25)
    }
}

val compatCheck = tasks.register("compatCheck") {
    group = "verification"
    description = "Verifies the plugin compiles against every supported Paper API line."
    dependsOn(compatTasks)
}

tasks.check {
    dependsOn(compatCheck)
}

tasks.shadowJar {
    archiveBaseName.set("SealCore")
    archiveClassifier.set("")
    // Version and provenance in one glance:
    // SealCore-0.1.0-SNAPSHOT-main-a1b2c3d.jar
    archiveFileName.set("SealCore-${project.version}-$buildLabel.jar")
    manifest {
        attributes(
            "Implementation-Title" to "SealCore",
            "Implementation-Version" to project.version,
            "Git-Branch" to gitBranch,
            "Git-Commit" to gitCommit,
        )
    }
    // Both roots: the api artifact lives under com.github, the spigot
    // implementation under io.github.
    relocate("com.github.retrooper.packetevents", "sealmc.swe3tie.sealcore.libs.packetevents")
    relocate("io.github.retrooper.packetevents", "sealmc.swe3tie.sealcore.libs.packetevents")
    relocate("org.slf4j", "sealmc.swe3tie.sealcore.libs.slf4j")
    mergeServiceFiles()
    exclude("META-INF/maven/**")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    // The server reports this back through /sealcore version, so a jar and the
    // plugin it registered always agree.
    val pluginVersion = if (buildLabel == "nogit") project.version.toString() else "${project.version}+$gitBranch.$gitCommit"
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

/** Prints the provenance the artifacts are named after, for CI logs. */
tasks.register("printVersion") {
    val label = buildLabel
    val branch = gitBranch
    val commit = gitCommit
    val base = project.version.toString()
    doLast {
        logger.lifecycle("SealCore $base ($label) branch=$branch commit=$commit")
    }
}
