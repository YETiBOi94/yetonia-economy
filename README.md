# Yetonia Economy 1.1.0 — Minecraft 26.2 / Java 25

A server-authoritative Fabric economy mod with a UUID-persistent ledger, player commands, a custom HUD, a Gem Banker NPC, a Material Quartermaster NPC, and a JSON shop configuration.

## Features

- Persistent balances keyed by player UUID using Minecraft Saved Data.
- `/balance` and `/balance <player>`.
- `/pay <player> <amount>`.
- Admin `/economy set|add|take|reload` and NPC spawn commands.
- `/shop list|buy|sell` for command-based material trading.
- The Gem Banker converts configured physical items such as diamonds and emeralds into coins.
- The Material Quartermaster buys and sells configured bulk bundles.
- Top-right HUD with `Yetonia Coins`, `Y <amount>`, and `coin_icon.png`.
- Server -> client balance packet; the client has no authoritative write path.

## Build requirements

Minecraft 26.2's current Fabric toolchain uses Java 25. This project targets Java 25, Minecraft 26.2, Fabric Loader 0.19.5, Loom 1.17.12, and Fabric API 0.161.0+26.2.

Install JDK 25 and Gradle 9.5.1. From the project root:

```text
gradle build
```

The JAR is written to `build/libs/`.

### GitHub Actions build

The included `.github/workflows/build.yml` is a cloud build. Push the project to GitHub and run the workflow from **Actions -> Build Yetonia Economy -> Run workflow**. It uses Java 25 and Gradle 9.5.1 and uploads the compiled JAR as a workflow artifact.

## Asset layout

The exact HUD texture path is:

```text
src/client/resources/assets/yetonia_economy/textures/gui/coin_icon.png
```

The supplied coin artwork is included there as a 16x16 PNG. Replace it with another PNG at the same path if desired.

The resource becomes:

```text
assets/yetonia_economy/textures/gui/coin_icon.png
```

inside the built JAR.

## Generated server config

On the first server start, the mod creates:

```text
config/yetonia_economy.json
```

Default content:

```json
{
  "startingBalance": 0,
  "bankerValues": {
    "minecraft:diamond": 100,
    "minecraft:emerald": 10
  },
  "shop": {
    "minecraft:oak_log": {
      "buyPrice": 100,
      "sellPrice": 100,
      "quantity": 100
    }
  }
}
```

Prices are Yetonia Coins per bundle. `quantity` is the number of physical items in one bundle. Example: 100 oak logs can be bought for 100 coins and sold for 100 coins.

After editing the file, run:

```text
/economy reload
```

## Commands

Player commands:

```text
/balance
/balance <player>
/pay <player> <amount>
/shop list
/shop buy <item_id> <bundles>
/shop sell <item_id> <bundles>
```

Admin commands require the admin permission level:

```text
/economy set <player> <amount>
/economy add <player> <amount>
/economy take <player> <amount>
/economy reload
/economy npc banker
/economy npc quartermaster
```

## NPCs

### Gem Banker

Spawn with:

```text
/economy npc banker
```

A player can right-click the banker while holding a configured `bankerValues` item. The whole held stack is removed and the corresponding number of Yetonia Coins is deposited. Creative-mode deposits are blocked.

### Material Quartermaster

Spawn with:

```text
/economy npc quartermaster
```

- Shift-right-click: cycle through configured shop entries.
- Right-click empty-handed: buy one bundle of the current selection.
- Right-click while holding a configured shop item: sell one bundle of that item.

## Server / client installation

Install the same JAR in both the dedicated server `mods/` directory and the client `mods/` directory. The server owns the ledger; the client only renders the synced balance.

## Source layout

```text
src/
├── client/
│   ├── java/com/yetonia/economy/YetoniaEconomyClient.java
│   └── resources/assets/yetonia_economy/textures/gui/coin_icon.png
└── main/
    ├── java/com/yetonia/economy/
    │   ├── YetoniaEconomy.java
    │   ├── YetoniaBalancePayload.java
    │   ├── YetoniaConfig.java
    │   └── YetoniaLedger.java
    └── resources/fabric.mod.json
```

## Notes about compiling

This chat workspace does not have a Java 25 JDK or a local Gradle installation, so the project is source-complete but has not been locally compiled here. The included GitHub Actions workflow is the reproducible Java 25 build path. The local `build-windows.bat` script expects Java 25 and Gradle 9.5.1 to be installed on Windows.
