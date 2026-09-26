# SealCore

All-in-one economy SMP plugin for Paper and Folia.

Phase 1 is the framework: no gameplay features yet. It gives feature modules
storage, currency access, a packet driven GUI, HUD widgets, placeholders and a
command tree, so later phases are wiring rather than infrastructure.

| | |
| --- | --- |
| **Minecraft** | 1.21.11, 26.1.2, 26.2 |
| **Server** | Paper and Folia, one jar for all of them |
| **Java** | 21 bytecode, runs on any supported server |
| **Main class** | `sealmc.swe3tie.sealcore.SealCore` |
| **Economy** | ExcellentEconomy, bound reflectively, not a build dependency |
| **Packets** | PacketEvents, shaded and relocated |
| **Storage** | SQLite by default, MySQL optional, JDBC over HikariCP |

> **Status: framework complete, no gameplay modules yet.**

## Build

The build itself needs JDK 25 because the 26.x Paper API is Java 25 bytecode.
The shipped jar still targets Java 21, so one artifact runs on every line.

```
./gradlew :sealcore:build
```

Artifacts are named after the commit they came from, so a jar on a disk is
always traceable to a branch and a commit:

```
sealcore/build/libs/SealCore-0.1.0-SNAPSHOT-main-a1b2c3d.jar
```

The plugin reports the same thing at runtime, so `/sealcore version` prints
`0.1.0-SNAPSHOT+main.a1b2c3d`. Outside a git checkout the build falls back to
`nogit` rather than inventing provenance. `./gradlew :sealcore:printVersion`
prints the label without building.

Database drivers and HikariCP are resolved by the server from the `libraries:`
block in `plugin.yml` instead of being shaded, which keeps the jar at ~7.7 MB.
`./gradlew :sealcore:build` also runs the cross version gate, see below.

## Installing

Drop the jar in `plugins/` and start the server. The plugin creates its own
configuration on first run:

```
plugins/SealCore/
├── config.yml          engine and shared settings
├── languages/en.yml    every player facing string
└── modules/            one file per module, created as modules arrive
```

**ExcellentEconomy is optional.** Without it the plugin still starts and
everything works except currency, which reports a clear "no provider" message
instead of failing. To use it, install ExcellentEconomy too; the mapping between
SealCore currency keys and its currency ids lives in `config.yml`.

**Do not install the standalone PacketEvents plugin as well.** SealCore embeds
its own relocated copy and two injectors on the same channels break connections.
SealCore logs a warning if it finds the standalone one.

## Layout

| Package | What lives there |
| --- | --- |
| `platform` | Server flavour detection, version ordering, Folia aware scheduling |
| `config` | Typed view over `config.yml` and `messages.yml` |
| `service` | Small KClass keyed registry feature modules resolve against |
| `storage` | Hikari pool, dialect aware upserts, migrations, JSON data store |
| `economy` | Currency keys, result type, provider interface, EE bridge |
| `gui` | Screens, sessions, elements, layouts, manager |
| `gui.packet` | The only code that imports PacketEvents |
| `hud` | Sidebar, boss bar, action bar and title widgets |
| `placeholder` | `%prefix_key%` engine and the PlaceholderAPI expansion |
| `command` | Node based dispatcher and `/sealcore` |
| `listener` | Join and quit handling |
| `text`, `util` | Component rendering, slot maths, caches, coroutine helpers |

## Configuration

Three files at three granularities, all in the plugin data folder:

```
plugins/SealCore/
├── config.yml          engine and shared settings
├── languages/
│   └── en.yml          every player facing string, one file per language
└── modules/
    ├── shop.yml        one file per module
    └── jobs-miner.yml
```

**The line between `config.yml` and a module file:** if exactly one module reads
a key it does not belong in `config.yml`. `config.yml` holds only what two or
more modules share plus the engine switches, which is why it stays small while
the module count grows.

**The id is the file name.** `modules/jobs-miner.yml` is the module
`jobs.miner`, and that one id is also its message namespace
(`jobs.miner.payout-received`) and its placeholder namespace
(`%jobs_miner_level%`). Nothing is named twice.

**Language fallback.** `lang: en.yml` in `config.yml` picks the file. A key
missing from the active language falls back to `languages/en.yml` before it
renders the key itself, so a partial translation shows English rather than
blanks. Language names resolve case insensitively, so `EN`, `en` and `en.yml`
all land on the same file.

**Messages never live in a module file.** Translating therefore never touches a
settings file, and one file per language is what a translator actually wants.

## Boot order

`onLoad` builds the embedded PacketEvents API and injects the channels, so the
injector exists before any player connects. `onEnable` starts the managers,
then wires config, storage, economy, interface, commands and tasks. `onDisable`
tears the same chain down in reverse.

## Design notes

**ExcellentEconomy is bound reflectively.** The plugin declares no dependency on
ExcellentEconomy, so it compiles and runs without it. At boot
`ExcellentEconomyBridge` resolves the API through the Bukkit services manager and
falls back to a no-op provider when the plugin is absent. The reflective
contract is pinned by test stand-ins declared in the test source set at the
upstream package and type names, so an upstream rename fails the build instead
of a live server. ExcellentEconomy 2.8.0 supports up to 26.1.2; 26.2 needs the
Phase 2 fork.

**Folia needs no separate dependency.** The regionised scheduler interfaces are
byte identical across 1.21 and 26.x, so `RegionizedTaskScheduler` compiles once
and calls the global, region and async schedulers directly. `BukkitScheduler` is
never touched, which is what Folia's `UnsupportedOperationException` guards.

**PacketEvents is shaded and relocated.** Both roots are relocated
(`com.github.retrooper.packetevents` and `io.github.retrooper.packetevents`).
Because a relocated copy has its own static state, the API is built with
`SpigotPacketEventsBuilder` instead of being picked up from a standalone
install. Do not run the standalone PacketEvents plugin alongside SealCore; the
plugin logs a warning if it finds one.

## Writing a module

Three steps, all driven by the framework. A module is a class and a file.

```kotlin
class JobsMinerModule : SealModule<JobsMinerModule.Spec> {

    override val id = "jobs.miner"
    override val schemaVersion = 2
    override val dependsOn = setOf("economy")

    /** Restart only keys, reported instead of silently applied. */
    override val restartKeys = setOf("reward-table")

    data class Spec(
        val payout: Double,
        val cooldownSeconds: Int,
    ) : ModuleSpec() {
        override fun describe() = "payout=$payout cooldown=${cooldownSeconds}s"
    }

    override fun parse(section: ModuleSection): Spec = Spec(
        // Every getter takes a default, so a partial file still loads, and a
        // wrong value is reported with the file and key instead of throwing.
        payout = section.double("payout", 12.0, min = 0.0),
        cooldownSeconds = section.int("cooldown-seconds", 30, min = 0, max = 3600),
    )

    override fun enable(spec: Spec) {
        // Idempotent: runs on boot and after every successful reload.
    }
}
```

Ship the default as `resources/modules/jobs-miner.yml` and register the class in
`SealCore.featureModules()`. The file is copied out on first run, the spec is
validated, and the module is enabled.

**Migrations, not version stamps.** When the file layout changes, bump
`schemaVersion` and add a migration. Unlike a `ConfigId` that overwrites the
file, a migration leaves keys an operator added by hand alone.

```kotlin
addMigration(ModuleMigration("jobs.miner", fromVersion = 1) { section ->
    section.set("cooldown-seconds", section.getInt("cooldown"))
})
```

**Reload is two phase and per module.** Every module is read, migrated, parsed
and validated before anything is applied. A file that fails leaves that module on
its last good spec while everything else reloads, and the report names the bad
key. `storage.type`, `gui.enabled` and similar are read but reported as needing
a restart, so a reload never looks like it worked when it did not.

**Dependencies are honoured, not assumed.** A module is enabled after whatever
it depends on, and skipped with a clear reason when that dependency is off. An
unknown dependency or a cycle is a boot failure, not a mystery at runtime.

## Cross version gate

`./gradlew :sealcore:compatCheck` recompiles the plugin sources against the
Paper API of every supported line. An API that disappeared in the 1.21 to 26
jump fails the build here instead of on a live server. `check` depends on it, so
plain `./gradlew build` runs it too.

## Testing

`./gradlew :sealcore:test` runs the unit suite against a real SQLite file, the
config parser, version ordering, slot and paging maths, placeholder token
resolution, the ExcellentEconomy bridge, and the whole module system: typed
config reading, last good retention on a failed reload, dependency skipping and
cycles, migrations, restart reporting and language fallback.

Booting a real server is a separate step; the packet path is only covered up to
packet construction, because rendering needs an actual Minecraft client.

## Verified

**Unit suite** covers the module system directly: typed config reading and its
error messages, last good retention on a failed reload, dependency skipping and
cycles, migrations, restart key reporting, and language fallback.

**Live boots.** The runtime paths were booted on all six targets with the
framework jar: Paper 1.21.11 (132), Paper 26.1.2 (74), Paper 26.2 (129), Folia
1.21.11 (14), Folia 26.1.2 (8), Folia 26.2 (7, BETA). Each enabled, answered
`/sealcore debug`, reloaded, and shut down cleanly, with no errors from SealCore
and no `UnsupportedOperationException` on Folia.

**The configuration refactor on top of that** was re-verified live on Paper
1.21.11, Paper 26.1.2 and Folia 1.21.11, confirming the new layout, the language
file and the new commands. The other three targets were not re-booted for it:
it is a config loading change, and the code it sits on, the packet bridge, the
region schedulers, storage and commands, is unchanged.

`build/smoke/harness.py` reruns the matrix. It needs network the first time to
download each server; afterwards it runs from the cached jars.

## Phases

1. Framework: this module, no gameplay features.
2. `excellenteconomy-fork`: a Java fork of ExcellentEconomy with 26.2 and Folia
   support. The `include` line in `settings.gradle.kts` is already in place,
   commented out, for when that module lands.
3. Feature modules: shops, jobs, auctions, claims, and whatever else the SMP
   needs, each consuming the framework through the service registry.

## Commands

`/sealcore version`, `/sealcore reload`, `/sealcore modules`, and
`/sealcore debug <platform|economy|storage|gui|hud|config>`.

`reload` and `modules` exist because "it reloaded" is not the same as "your
change took effect". Both report which modules applied, which kept their last
good spec, and which keys need a restart.
