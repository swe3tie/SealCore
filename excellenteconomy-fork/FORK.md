# ExcellentEconomy fork

ExcellentEconomy 2.8.0, forked so it runs on Folia and Minecraft 26.2.

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
| Version | `2.8.0` | `2.8.0-sealcore.1` |
| `maven-publish` | publishes to NightExpress's repo | removed, not published from here |
| `CurrencyFactory` | constructs `EconomyCurrency` inline | delegates to `VaultCurrencyFactory` |

### The two source edits

Everything else is dependency level, but two things in the source tree had to
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
without Vault. The rest of `src/` is upstream's, byte for byte.

## Installing

Needs **NightCore**, which this fork does not bundle:

- Paper 1.21.8 or newer, or **Folia**. `NightCore` 2.16.3 or newer on Folia.
- Install NightCore from <https://modrinth.com/plugin/nightcore>. Take the
  **2.16.6** build for Minecraft 26.2.
- Vault, PlaceholderAPI and PlayerPoints are optional, as upstream.

Drop `ExcellentEconomy-2.8.0-sealcore.1.jar` in `plugins/`. It replaces upstream
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

## Keeping in sync with upstream

```
git clone https://github.com/nulli0n/ExcellentEconomy
rsync -a --delete ExcellentEconomy/src/ excellenteconomy-fork/src/
./gradlew :excellenteconomy-fork:build
```

`rsync --delete` removes `VaultCurrencyFactory.java` along with the rest of
upstream's tree, because that file is ours. Re-apply the second source edit above
after a sync.

If upstream ever declares the amount parameter as a boxed `Double`, the reflective
contract in SealCore changes with it. `sealcore`'s
`ExcellentEconomyApiContractTest` checks the real interface's signatures when
this module is on the test classpath, so a rename or a re-box fails the build
rather than a live server.
