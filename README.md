# SealCore

All-in-one economy SMP plugin for Paper and Folia.

Phase 1 is the framework, and the first feature module on top of it: the
**economy** module with `/balance` and `/pay` on ExcellentEconomy. The framework
gives feature modules storage, currency access, a packet driven GUI, HUD widgets,
placeholders and a command tree, so later modules are wiring rather than
infrastructure.

| | |
| --- | --- |
| **Minecraft** | 1.21.11, 26.1.2, 26.2 |
| **Server** | Paper and Folia, one jar for all of them |
| **Java** | 21 bytecode, runs on any supported server |
| **Main class** | `sealmc.swe3tie.sealcore.SealCore` |
| **Economy** | ExcellentEconomy, bound reflectively, not a build dependency |
| **Packets** | PacketEvents, shaded and relocated |
| **Storage** | SQLite by default, MySQL optional, JDBC over HikariCP |

> **Status: framework plus the economy module (`/balance`, `/pay`). `/sell`
> and `/shop` are next, in the same module file.**

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
├── config.yml            engine and shared settings
├── languages/            en.yml and vi.yml, every player facing string
└── modules/economy.yml   one file per module
```

**ExcellentEconomy is optional.** Without it the plugin still starts and
everything works except currency, which reports a clear "no provider" message
instead of failing. To use it, install ExcellentEconomy too, plus NightCore; the
mapping between SealCore currency keys and its currency ids lives in
`config.yml`. On Folia, or on 26.2, install the fork instead of stock
ExcellentEconomy, see the design note below.

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
| `command` | Node based dispatcher, the Bukkit adapter, `/sealcore` |
| `modules` | Feature modules, one package each, starting with `economy` |
| `listener` | Join and quit handling |
| `text`, `util` | MiniMessage rendering and the `<accent>` tag, slot maths, caches |

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

**Language fallback.** `lang: vi.yml` in `config.yml` picks the file, with
`en.yml` shipped as the fallback for every other language. A key
missing from the active language falls back to `languages/en.yml` before it
renders the key itself, so a partial translation shows English rather than
blanks. Language names resolve case insensitively, so `EN`, `en` and `en.yml`
all land on the same file.

**Messages never live in a module file.** Translating therefore never touches a
settings file, and one file per language is what a translator actually wants.

**Strings are MiniMessage.** Every message, GUI title and HUD line is parsed by
MiniMessage, so `<red>`, `<gradient:...>`, `<bold>` and friends all work. On top
of the standard tags SealCore adds `<accent>`, which resolves to
`general.accent-color` in `config.yml` (`#9CC0D9` out of the box). Change that
one value and every language file follows; a value that is not a hex colour is
reported and the last good colour stays.

**Values go in braces, formatting goes in angle brackets.** Angle brackets
belong to MiniMessage, so a value written `<player>` used to be read as a tag
and could be swallowed whole by a colour name. Write `{player}` instead:

```
balance-other: '<accent>{player} <white>có <green>{money}'
```

A value is inserted unparsed, so a player called `white` prints as `white` and
cannot recolour the message, and a name containing `<red>` prints literally. A
language file still using the old `<player>` form keeps working, so a file
translated before the change does not blank out on upgrade.

**There is no prefix.** Messages are written to be read in place, the way a
payment receipt reads, and nothing is prepended to a player's chat. The dead
`prefix:` key is gone from every language file.

**Money is formatted by SealCore.** `modules/economy.yml` owns the symbol, the
decimals and the `K/M/B/T/Qa/Qi` suffixes, so `$89.89M` reads the same on every
server no matter how the currency is configured inside ExcellentEconomy.

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
of a live server. The amount parameter is declared as a primitive `double`
everywhere, and looking it up as `Double` throws `NoSuchMethodException` and
silently drops SealCore to its no-op provider, so the stand-ins box nothing.
The fork module has no test that reads the real `ExcellentEconomyAPI`, so a
re-boxing on the EE side is caught by the live check in `excellenteconomy-fork/FORK.md`,
not by the build.

**SealCore owns /balance and /pay, including their messages.** ExcellentEconomy
is a paper plugin, so it loads and enables before any Bukkit plugin and its
commands answer first, and a Bukkit plugin cannot take a name back afterwards:
the server builds its command tree as plugins load. The fork therefore declines
to register any command name that another installed plugin declares in its own
descriptor, and logs which plugin it yielded to. Because that is a declaration
rather than a claim, it does not depend on load order.

Withholding the command is not enough on its own, because ExcellentEconomy
also notifies from inside the API. Its default operation context tells the
player about every balance change, so one `/pay` printed ExcellentEconomy's
"$100 has been taken from your account!" directly above SealCore's own message.
SealCore builds its operation context with the two chat targets silenced and the
two loggers left on, so a payment is announced once and still recorded in
ExcellentEconomy's operation log.

**Upstream ExcellentEconomy is Paper 26.1.2 only.** For Folia, or for 26.2,
use the jar from `excellenteconomy-fork`, which is EE 2.8.0 with the Folia flag
in both plugin descriptors, NightCore bumped to the first line with a Folia
scheduler, and a fix for an upstream bug that crashed EE on any server without
Vault installed. It is verified booting on Folia 26.1.2 and 26.2. See
`excellenteconomy-fork/FORK.md`.

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

```java
public final class JobsMinerModule implements SealModule<JobsMinerModule.Spec> {

    @Override
    public String id() {
        return "jobs.miner";
    }

    @Override
    public int schemaVersion() {
        return 2;
    }

    @Override
    public Set<String> dependsOn() {
        return Set.of("economy");
    }

    /** Restart only keys, reported instead of silently applied. */
    @Override
    public Set<String> restartKeys() {
        return Set.of("reward-table");
    }

    public record Spec(double payout, int cooldownSeconds) implements ModuleSpec {

        @Override
        public String describe() {
            return "payout=" + payout + " cooldown=" + cooldownSeconds + "s";
        }
    }

    @Override
    public Spec parse(ModuleSection section) {
        // Every getter takes a default, so a partial file still loads, and a
        // wrong value is reported with the file and key instead of throwing.
        return new Spec(
            section.decimal("payout", 12.0, 0.0, Double.MAX_VALUE),
            section.integer("cooldown-seconds", 30, 0, 3600));
    }

    @Override
    public void enable(Spec spec) {
        // Idempotent: runs on boot and after every successful reload.
    }
}
```

Ship the default as `resources/modules/jobs-miner.yml` and register the class in
`SealCore.featureModules()`. The file is copied out on first run, the spec is
validated, and the module is enabled.

**Commands come from the module too.** Override `commands(context)` and return
one `SealCommand` per command; the framework binds the tree on boot and again on
every reload.

```java
@Override
public List<SealCommand> commands(ModuleContext context) {
    return List.of(
        new SealCommand(this, "balance", "Show a balance", "/balance [player]",
            ctx -> new BalanceCommand(ctx, this).node()));
}
```

The name must also exist under `commands:` in `plugin.yml`, because that is where
the server looks it up. A command whose module is switched off answers with
`core.module-disabled` instead of disappearing, so a player never gets silence
from a command that still exists.

**Migrations, not version stamps.** When the file layout changes, bump
`schemaVersion` and add a migration. Unlike a `ConfigId` that overwrites the
file, a migration leaves keys an operator added by hand alone.

```java
addMigration(new ModuleMigration("jobs.miner", 1,
    section -> section.set("cooldown-seconds", section.getInt("cooldown"))));
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

The economy module is tested through the command dispatcher with the real
Vietnamese language file, so the strings the tests assert on are the strings a
player sees: both `/balance` forms, both sides of `/pay` in chat and on the
actionbar, self pay, the amount range, insufficient funds, unknown players, the
cooldown, the bypass permission and a missing provider. A test also compares
`en.yml` and `vi.yml` key by key, and checks that every permission a module file
uses is declared in `plugin.yml`.

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

## Contributing

`docs/GIT.md` covers the repository layout, the commit identity and the remote.
Build with `./gradlew :sealcore:build`; it runs the test suite and the cross
version gate, so a change that breaks an API on any supported Minecraft line
fails before review.

## Phases

1. Framework, and the `economy` module: `/balance`, `/pay`, with `/sell` and
   `/shop` to come in `modules/economy.yml`.
2. Done: `excellenteconomy-fork`, a Java fork of ExcellentEconomy with 26.2 and
   Folia support. Build it with `./gradlew :excellenteconomy-fork:build`. It needs
   NightCore installed alongside it, which it does not bundle.
3. Feature modules: shops, jobs, auctions, claims, and whatever else the SMP
   needs, each consuming the framework through the service registry.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/balance [player]` (`/bal`) | `sealcore.economy.balance` | `Bạn có $89.89M`, or another player's balance |
| `/pay <player> <amount>` | `sealcore.economy.pay` | Sends money; both sides are told in chat and on the actionbar |
| `/sealcore version` | `sealcore.command.version` | Shows the loaded version and platform |
| `/sealcore reload` | `sealcore.command.reload` | Reloads config, modules and language |
| `/sealcore modules` | `sealcore.command.debug` | Lists module state |
| `/sealcore debug <...>` | `sealcore.command.debug` | Framework state |

`reload` and `modules` exist because "it reloaded" is not the same as "your
change took effect". Both report which modules applied, which kept their last
good spec, and which keys need a restart.

**Amounts.** `/pay Steve 2.5k` reads `k`, `m`, `b` and `t` suffixes, and
`1,250` is fine as typed. `modules/economy.yml` sets the range, whether a
player may pay themselves, and whether chat and the actionbar are used.

**Who can be paid.** Anyone online, plus anyone who has joined before, found by
name in the `sealcore_players` table. A name the server has never seen is not
looked up over the network, because that web call blocks a server thread. With
storage unavailable only online players resolve.

**Permissions.** `sealcore.economy.bypass` (default op) ignores the amount
range and the cooldown; `sealcore.economy.*` grants all three.
