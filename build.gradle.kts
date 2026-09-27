import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.shadow) apply false
}

/**
 * Branch and short commit of the working copy, or null when there is no usable
 * git metadata.
 *
 * A source download has no `.git`, and a build inside some unrelated repository
 * must not claim to be a SealCore build, so both cases fall back to `nogit`
 * rather than putting a misleading name on an artifact.
 */
fun gitInfo(): Pair<String, String>? {
    fun git(vararg args: String): String? = runCatching {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        output.takeIf { process.exitValue() == 0 && it.isNotEmpty() }
    }.getOrNull()

    val branch = git("rev-parse", "--abbrev-ref", "HEAD")?.takeIf { it != "HEAD" } ?: return null
    val commit = git("rev-parse", "--short", "HEAD") ?: return null
    return branch.replace(Regex("[^A-Za-z0-9._-]"), "-") to commit
}

val git = gitInfo()
val gitBranch = git?.first ?: "nogit"
val gitCommit = git?.second ?: "unknown"

/** `main-a1b2c3d`, so a jar on a disk is traceable to a commit. */
val buildLabel = if (git == null) "nogit" else "$gitBranch-$gitCommit"

allprojects {
    group = "sealmc.swe3tie"
    version = providers.gradleProperty("sealcore.version").getOrElse("0.1.0-SNAPSHOT")
    extra["buildLabel"] = buildLabel
    extra["gitBranch"] = gitBranch
    extra["gitCommit"] = gitCommit
}
