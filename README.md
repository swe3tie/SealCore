# SealCore

All-in-one economy SMP plugin for Paper and Folia. SealCore ships as one plugin jar and supports Folia region scheduling.

## Features

- `/balance` and `/bal` show player balances.
- `/pay <player> <amount>` transfers money between players, with cooldown and amount limits.
- `/sell` opens a configurable sell GUI and pays for configured item prices when the inventory closes.
- Economy commands accept compact amounts such as `10k`, `1.5m`, `2b`, and `5t`.
- English and Vietnamese language files with MiniMessage formatting.
- SQLite or MySQL/MariaDB storage through a shared connection pool.
- Optional ExcellentEconomy, PlaceholderAPI, and PacketEvents integrations.
- Runtime module enable/disable/reload support through `/sealcore` admin commands.

## Requirements

- Paper or Folia server compatible with the API declared in [`paper-plugin.yml`](src/main/resources/paper-plugin.yml).
- Java 25 to build the project. The plugin targets Java 21 bytecode.
- ExcellentEconomy for economy-backed features such as balances, payments, and selling.

Optional integrations are detected when installed. They are not bundled into SealCore.

## Installation

1. Download or build the SealCore jar.
2. Put the jar in the server's `plugins/` directory.
3. Install and configure ExcellentEconomy when using economy features.
4. Start the server once, then edit generated files under `plugins/SealCore/`.
5. Use `/sealcore reload` after changing reloadable configuration.

Command names and aliases are registered during server startup. Restart the server after changing them.

## Configuration

| Path | Purpose |
| --- | --- |
| `plugins/SealCore/config.yml` | Language, storage, hooks, and module toggles. |
| `plugins/SealCore/languages/en.yml` | English fallback messages. |
| `plugins/SealCore/languages/vi.yml` | Vietnamese messages. |
| `plugins/SealCore/modules/economy/config.yml` | Economy command names, permissions, limits, and cooldown. |
| `plugins/SealCore/modules/economy/prices.yml` | Per-item sell prices, including potion and enchanted-book keys. |
| `plugins/SealCore/modules/economy/gui/sell-gui.yml` | `/sell` title, rows, input slots, and category buttons. |
| `plugins/SealCore/modules/economy/gui/sell-multiplier.yml` | Category sell progression GUI, page slots, navigation, and state panes. |

Missing default keys are merged into existing files without replacing user values. Keep module files inside their module directory when adding module-specific configuration.

### `/sell` GUI

`modules/economy/gui/sell-gui.yml` controls inventory size and which slots accept items. Items placed in configured input slots are priced using `modules/economy/prices.yml` when the GUI closes. Items without a price return to the player. The title can use `{TOTAL}`, `{TOTAL_FORMATTED}`, `{MONEY}`, and `{MONEY_FORMATTED}` placeholders. Category progression details are configured in `modules/economy/gui/sell-multiplier.yml`.

### Permissions

- `sealcore.economy.balance` — view own balance.
- `sealcore.economy.balance.others` — view another player's balance.
- `sealcore.economy.pay` — use `/pay`.
- `sealcore.economy.pay.bypass` — bypass `/pay` cooldown and limits.
- `sealcore.economy.sell` — use `/sell`.
- `sealcore.admin` — administrator permissions.

## Building

Use the Gradle wrapper from project root:

```bash
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew build
```

Run unit tests only:

```bash
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew test
```

Run local Paper or Folia smoke tests:

```bash
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew runServer
```

```bash
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew runFolia
```

Use `-PtestPlugins=/path/to/plugins` with a run task to load local ExcellentEconomy, Nightcore, PlaceholderAPI, or PacketEvents jars.

## Development

- Java sources live under `src/main/java/sealmc/swe3tie/sealcore`.
- Bundled defaults live under `src/main/resources`.
- Keep server-facing logic on `SealScheduler` so the same jar works on Paper and Folia.
- Keep pure logic free of server calls so it remains unit-testable.
- Run `./gradlew test` before submitting changes.

## License

No license has been declared in this repository yet.
