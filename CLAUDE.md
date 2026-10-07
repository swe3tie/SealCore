# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

SealCore: all-in-one Economy SMP plugin for **Paper and Folia** (single jar, `folia-supported: true`). Paper plugin (`paper-plugin.yml`, not `plugin.yml`), Java package `sealmc.swe3tie.sealcore`.

## Commands

```bash
./gradlew build                      # compile + test + jar (build/libs/SealCore-<version>-<branch>.<commit>.jar)
./gradlew test                       # JUnit 5
./gradlew test --tests 'sealmc.swe3tie.sealcore.core.module.ModuleGraphTest'          # one class
./gradlew test --tests 'sealmc.swe3tie.sealcore.core.module.ModuleGraphTest.someTest' # one method
./gradlew runServer                  # Paper smoke test in run/
./gradlew runFolia                   # Folia smoke test in run-folia/
./gradlew runServer -PtestPlugins=/path/to/plugins   # also load ExcellentEconomy/nightcore/PlaceholderAPI/packetevents jars from that dir
```

Toolchain JDK 25, but `options.release = 21`: do not use Java 22+ APIs/language features. Compiles against the lowest supported API (`paperApiVersion` in `gradle.properties`, 1.21.11) so one jar runs on 1.21.11 and 26.x. Unit tests only cover pure logic (no MockBukkit); keep testable logic free of server calls (e.g. `ModuleGraph`, `YamlFile.mergeDefaults`, `LangService.flatten`).

## Build specifics

- Version is `<version>+<branch>.<commit>`, injected into `paper-plugin.yml` via `processResources` expansion.
- Runtime libraries (HikariCP, sqlite-jdbc, mysql-connector-j) are **not shaded**. They are `compileOnly`, listed in `runtimeLibraries` in `build.gradle.kts`, written into `sealcore-libraries.txt`, and downloaded at boot by `SealCoreLoader` (Paper `PluginLoader` + `MavenLibraryResolver`). To add a runtime library, add it to `runtimeLibraries`.
- Soft-dependency plugins (ExcellentEconomy, nightcore, PlaceholderAPI, packetevents) are `compileOnly` and declared `required: false` in `paper-plugin.yml`. A forked ExcellentEconomy (`2.8.0-sealcore.N`) may be published to `mavenLocal()`.

## Architecture

`SealCore` (JavaPlugin) builds core services in fixed order in `onEnable`: `ConfigService` → `SealScheduler` → `LangService` → `StorageService` → `HookManager` → `CommandService` → `ModuleManager`. Failure before modules disables the plugin. `reload()` re-reads config + lang, re-attaches hooks, then `modules.sync()`; storage settings are restart-only. Services are reached through accessors on `SealCore` (`core.lang()`, `core.hooks()`, ...).

### Modules (`core/module`)
- Features live in `modules/<id>/` as `SealModule` subclasses, registered in `SealCore.onEnable` before `modules.freeze()`.
- Each module owns `modules/<id>.yml` (bundled default at `src/main/resources/modules/<id>.yml`) and is toggled by `modules.<id>` in `config.yml`.
- Declare `requiredHooks()` and `dependsOn()`; `ModuleGraph` orders enable via topo sort. Failing modules become `FAILED`/`MISSING_HOOK`/`MISSING_DEPENDENCY` without affecting others.
- Modules toggle at runtime, so register resources only through `listen()`, `track(SealTask)`, `registerPlaceholders()` (exposes `%sealcore_<moduleId>_<params>%`) so they release on disable.
- `SealSubmodule<P>`: feature inside a module, toggled by `submodules.<id>.enabled` in the parent yml; config via `settings()`; register resources through the parent.

### Commands (`core/command`)
Brigadier via Paper lifecycle `COMMANDS` event, which fires **once at startup**. Modules call `core.commands().register(this::isActive, node, desc, aliases, permission)` during enable; commands of disabled modules stay registered but hidden by the `requires` check. Command names/aliases changes need a restart.

### Scheduling (`core/scheduler`)
Always use `SealScheduler` (`global*`, `at*` for locations, `entity*` for entities/players, `async*` for I/O). Never use `BukkitScheduler` or branch on Paper vs Folia. Delays < 1 tick clamp to 1.

### Hooks (`core/hook`)
`HookManager` loads integrations only after confirming the plugin is enabled, so missing plugins never cause `NoClassDefFoundError`. Keep third-party types isolated in one class per integration (e.g. only `ExcellentCurrencyService` imports ExcellentEconomy). Modules use the `CurrencyService` interface with SealCore currency keys (`money`, `shards`, `coins`) mapped to backend ids in `config.yml`; `core.hooks().currency()` can be `null`.

### Storage (`core/storage`)
Shared Hikari pool (SQLite with pool size 1, or MySQL/MariaDB). `query`/`execute` return `CompletableFuture` on a dedicated executor; never block region/main threads on them. Use `table(name)` for prefixed names; version schemas per module with `migrate(owner, List<Migration>)` (blocking, call from module enable only).

### Config & language
- `YamlFile` creates files from jar defaults and on each load merges missing keys (with comments) without overwriting user values. "Free sections" (user-owned maps, e.g. `hooks.excellent-economy.currencies`) are only seeded when absent. Adding a key to a bundled yml is enough to roll it out.
- `CoreSettings` is an immutable record snapshot of `config.yml`; module-specific settings belong in `modules/<id>.yml`, not `config.yml`.
- All player-facing text lives in `languages/*.yml` (MiniMessage). `en.yml` is the fallback for missing keys; add new keys to both `en.yml` and `vi.yml` (new bundled languages also go in `LangService.BUNDLED`). Built-in tags: `<accent>`, `<prefix>`, `<accent_hex>`. Send with `core.lang().send(sender, "key", Placeholder.unparsed(...))`; use `unparsed` for user-provided values.
- Yml files carry `config-version` for future migrations; `[restart]` comments mark keys that only apply after restart.
