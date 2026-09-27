# ExcellentEconomy fork

ExcellentEconomy 2.8.0, forked so it runs on Folia and Minecraft 26.2, and
so it hands `/balance` and `/pay` to the plugin that declares them.

Upstream is <https://github.com/nulli0n/ExcellentEconomy>, GPL-3.0, and this
fork stays GPL-3.0. `LICENSE` is upstream's, unmodified. `UPSTREAM-README.md` is
upstream's readme, kept for reference.

## Why a fork was needed

Upstream 2.8.0 does not run on a Folia server on 26.2, for two separate reasons,
and neither one is in EE's own source.

**Folia.** EE never calls `BukkitScheduler` itself. Its only three scheduling
call sites go through NightCore's `AbstractPlugin` (`runTask`, `runTaskAsync`),
and NightCore picks the implementation at runtime:
`Version.isFolia() ? new FoliaScheduler(plugin) : new PaperScheduler(plugin)`.
That `FoliaScheduler` arrived in NightCore **2.16.0**. EE 2.8.0 pins NightCore
**2.15.1**, which predates it, so on Folia the calls fell through to the Bukkit
scheduler and threw. Bumping the dependency is the entire fix.

**26.2.** Upstream compiles against `paper-api:26.1.2.build.+`. EE's source
compiles against the 26.2 API with no changes, so this is a target bump.

## What changed

| | Upstream 2.8.0 | This fork |
| --- | --- | --- |
| `paper-api` | `26.1.2.build.+` (dynamic) | `26.2.build.129-stable` (pinned) |
| NightCore | `2.15.1` | `2.16.4` |
| `plugin.yml` | no `folia-supported` | `folia-supported: true` |
| `paper-plugin.yml` | no `folia-supported` | `folia-supported: true` |
| Version | `2.8.0` | `2.8.0-sealcore.2` |
| `maven-publish` | publishes to NightExpress's repo | removed, not published from here |
| `CurrencyFactory` | constructs `EconomyCurrency` inline | delegates to `VaultCurrencyFactory` |
| `CommandManager` | overwrites any command name already taken | yields a name another plugin declares |

### The three source edits

Everything else is dependency level, but three things in the source tree had to
change as well.

**1. `paper-plugin.yml` needs the Folia flag too.** Upstream ships both
`plugin.yml` and `paper-plugin.yml`, and Paper/Folia read whichever applies. A
fork that only added `folia-supported: true` to `plugin.yml` still gets refused
by Folia, because Folia reads `paper-plugin.yml`. Both descriptors now carry the
flag, and `processResources` filters both. Upstream's maven build filters
`plugin.yml` only, so a straight port also ships a literal `${version}` in
`paper-plugin.yml` and Folia reports the plugin as `v${version}`.

**2. `CurrencyFactory` must not load `EconomyCurrency` on a Vault-less server.**
This is a real upstream bug, not a 26.2 regression. `CurrencyFactory` is linked
on the first currency load, and linking verifies every method in it, which forces
`EconomyCurrency` to resolve. `EconomyCurrency implements net.milkbowl.vault.economy.Economy`,
so EE threw `NoClassDefFoundError: net/milkbowl/vault/economy/Economy` while
enabling on any server without Vault installed, even though Vault is declared
optional in both descriptors. Reproduced on 26.1.2 and 26.2 with the unpatched
sources.

The fix is the smallest one that keeps the verifier happy: the `new
EconomyCurrency(...)` moved into `VaultCurrencyFactory`, which nothing links
until `Plugins.isInstalled(Vault)` has already returned true. `CurrencyFactory`
keeps its public shape, so nothing else in EE had to move.

The other Vault reference, `MigratorFactory`, is already safe: its only call site
sits behind `Plugins.isInstalled(HookPlugin.VAULT)`, so it is never linked
without Vault.

**3. A command name another plugin declares is left alone.** ExcellentEconomy is
a paper plugin, so it loads and enables before any Bukkit plugin and its
`/balance` and `/pay` answer before SealCore ever gets a chance. That is not
recoverable from the other side: the server builds its command tree as plugins
load, so a Bukkit plugin cannot take a name back once it has been taken, and
rewriting the command map later changes nothing. Upstream `CommandManager`
registers straight into that map, which silently overwrites the other owner.

So the fork leaves the name alone instead of taking it. `claimable(...)`
compares the name and every alias against what other **installed** plugins
declare in their own `plugin.yml`, read from `PluginDescriptionFile`, and
refuses to register on a match:

```
[ExcellentEconomy] Leaving 'balance' to SealCore and 'bal' to SealCore. That plugin
is installed and declares the name, so this plugin's own version of that command
is not registered. Move money through the other plugin instead.
```

The comparison is against declarations, not against who currently holds the
name, and that is what makes it independent of load order: a plugin that lists
`pay` in its descriptor meant it whether or not it has registered yet. Reading
declarations rather than registrations is also why a Bukkit plugin works here at
all — a Bukkit plugin is loaded before this paper plugin enables, so its
`plugin.yml` is readable at this point. Note that Bukkit's `Command` takes the
first label as the *name* and the rest as aliases, so `/pay` declared alone has
an empty alias list; the check looks at the name as well as the aliases, or it
would not see `/pay` at all.

The old top level `commandAliases` key is not read, because no Paper API this
fork targets exposes a getter for it. A command's own `aliases:` list, which is
the usual way to write one, is read. Declaring the name under `commands:` and
listing aliases there is what SealCore does.

Nothing here is specific to SealCore. Any plugin that declares a conflicting
name gets it kept.

## Installing

Needs **NightCore**, which this fork does not bundle:

- Paper 1.21.8 or newer, or **Folia**. `NightCore` 2.16.3 or newer on Folia.
- Install NightCore from <https://modrinth.com/plugin/nightcore>. Take the
  **2.16.6** build for Minecraft 26.2.
- Vault, PlaceholderAPI and PlayerPoints are optional, as upstream.

Drop `ExcellentEconomy-2.8.0-sealcore.2.jar` in `plugins/`. It replaces upstream
2.8.0 outright; do not run both.

## Verified

Built and booted on a real server, not just compiled:

| Server | Result |
| --- | --- |
| Folia 26.2 build 7 | EE enables, registers `money` + `coins`, SealCore binds it |
| Folia 26.1.2 build 8 | same, no regression against the older line |

`/sealcore debug economy` on the 26.2 server reports
`Provider: ExcellentEconomy`, `Available: true`, `Currencies: money, coins`,
which also exercises SealCore's reflective bridge against the real API.

Command ownership, on Folia 26.1.2 build 8 with both plugins installed and the
jars from this repo:

| Command | Answers | Evidence |
| --- | --- | --- |
| `/bal`, `/balance` | SealCore | EE logs it is leaving `balance` and `bal`; both forms print SealCore's message |
| `/balance <player>` | SealCore | prints the other player's balance in SealCore's format |
| `/pay` | SealCore | EE logs it is leaving `pay`; a payment is announced once, by SealCore only |
| `/pay` over balance | SealCore | refused with SealCore's message and no money moved |
| `/money` and EE's other names | ExcellentEconomy | untouched; only declared conflicts are yielded |

A single `/pay` used to produce two lines, because ExcellentEconomy notifies the
player from inside `CurrencyManager.remove` on top of SealCore's own message. That
is a SealCore side fix, not a fork change: the bridge builds its operation
context with `NotificationTarget.USER` and `NotificationTarget.EXECUTOR` silenced
and the two loggers left on. This fork only had to stop taking the command name.

## Keeping in sync with upstream

```
git clone https://github.com/nulli0n/ExcellentEconomy
rsync -a --delete ExcellentEconomy/src/ excellenteconomy-fork/src/
./gradlew :excellenteconomy-fork:build
```

`rsync --delete` removes `VaultCurrencyFactory.java` along with the rest of
upstream's tree, because that file is ours. Re-apply the second and third source
edits above after a sync.

There is no test in this module that reads the real `ExcellentEconomyAPI`. If
upstream renames a method, re-boxes the amount parameter from `double` to
`Double`, or changes what `OperationContext.of` does, the build stays green and
only a live server shows it. The reflective contract is pinned on the SealCore
side by stand-ins declared in `sealcore/src/test` at the upstream package and type
names, which catch a mismatch against *that* copy but not against a new upstream
release. After a sync, re-run the live check in **Verified** above.
