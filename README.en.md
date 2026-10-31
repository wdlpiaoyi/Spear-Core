# Spear Core Forked

[![](https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white)](https://github.com/deepseek-ai/deepseek-harness)

[简体中文](README.md) ｜ English（default）

> Adds seven spears to Minecraft 1.20.1 Forge: left-click to jab, hold right-click to charge, and the faster you run the harder you hit.

| | |
|---|---|
| Minecraft | 1.20.1 |
| Loader | Forge 47.3.0+ |
| Mod ID | `spearcore` |
| Sides | Client + server |
| Dependencies | None (Tetra / Punchy / KubeJS are optional) |
| License | MIT |

This repository is a fork of [wdlpiaoyi/Spear-Core](https://github.com/wdlpiaoyi/Spear-Core) (upstream [lyingice/Spear-Core](https://github.com/lyingice/Spear-Core)), maintained by wdlpiaoyi and deepseek, targeting 1.20.1 + Forge only. The display name is **Spear Core Forked**, while the `modId`, package and registry namespace stay `spearcore`, so mods that hard-code the mod ID (such as Avaritia Spear) keep working.

## Features

- **Seven spears**: wood, stone, copper, iron, gold, diamond, netherite, each with its own durability, attack speed and enchantability.
- **Two attacks**: left-click jab, hold right-click to charge; the charge phases unlock dismounting riders, knockback and damage in order.
- **Speed is damage**: damage scales with the relative speed towards the target - standing still is weakest, charging head-on is strongest.
- **Lunge enchantment**: `spearcore:lunge`, up to level 3, pushes you forward on a jab at the cost of durability and hunger.
- **Mobs use spears too**: zombies and piglins may spawn with spears (configurable) and will actually charge.
- **Poses and sounds**: first/third-person holding, charging and jabbing animations, arm animations and hit feedback.
- **Tetra compatibility**: a single-headed tool with both a long handle and a spearhead behaves like this mod's spear.
- **Resource-pack compatibility**: 1.21.9+ spear packs are detected and patched to 1.20.1 in memory.
- **Avaritia Spear compatibility**: upstream public method signatures are preserved, so `avaritia_spear` 1.4.0 works.

## Installation

1. Minecraft **1.20.1** with **Forge** (47.3.0 or newer).
2. Put the jar into `mods/`.
3. Install it on **both client and server**; installing on one side only leads to desync or empty hands.
4. For single player, just launch the game.

## How to use

### Jab (left-click)

When the attack cooldown bar is full:

- Jab damage = weapon attack damage + (optional) forward-speed bonus; standing still is the weakest.
- The same target cannot be jabbed repeatedly within a short window.
- Difference from vanilla: by default this mod lets jabs benefit from speed, while vanilla 26.1.2 piercing does not.

### Charge (hold right-click)

Hold right-click to walk through **wind-up -> dismount window -> knockback window -> damage window**.

- The charge auto-releases after the maximum duration (roughly 6-12 seconds depending on material).
- Charging does **not** slow you down like eating; you can walk and sprint normally.
- Knockback requires enough speed; dismounting riders is the earliest effect.

### Speed calculation

The mod looks at your speed along your facing direction and the relative speed to the target:

- Standing still: very low damage, may not even reach knockback or damage windows.
- Sprinting into the target: noticeably higher damage.
- Charging head-on into a target walking towards you: highest relative speed.
- Chasing a target running the same direction: low relative speed.

### Lunge

`spearcore:lunge`, up to level 3, spear-only: pushes you forward when you jab, further at higher levels; each trigger costs 1 durability and some hunger. It does not trigger with low hunger, while riding, while elytra flying, or in water.

## The seven spears

Displayed values with empty hands (enchantments and attribute bonuses excluded):

| Spear | Attack damage | Attack speed | Durability | Enchantability | Notes |
|---|---|---|---|---|---|
| Wooden | 1 | 1.5 | 59 | 15 | fastest |
| Stone | 2 | 1.3 | 131 | 5 | |
| Copper | 2 | 1.2 | 195 | 13 | |
| Iron | 3 | 1.1 | 250 | 14 | baseline feel |
| Golden | 1 | 1.1 | 59 | 22 | good enchants, fragile |
| Diamond | 4 | 1.0 | 1561 | 10 | |
| Netherite | 5 | 0.9 | 2031 | 15 | fire resistant, floats in lava |

Lower attack speed means slower: the wooden spear jabs most often, the netherite spear hits hardest per swing with the longest interval.

## Crafting

The six normal spears are crafted in a crafting table in a diagonal shape (one material + two sticks):

```
[ ][ ][a]
[ ][b][ ]
[b][ ][ ]
```

`a` is the material, `b` is a stick:

| Spear | Material |
|---|---|
| Wooden | any planks x1 + stick x2 |
| Stone | cobblestone x1 + stick x2 |
| Copper | copper ingot x1 + stick x2 |
| Iron | iron ingot x1 + stick x2 |
| Golden | gold ingot x1 + stick x2 |
| Diamond | diamond x1 + stick x2 |

**Netherite** uses the vanilla smithing table: `netherite upgrade template + diamond spear + netherite ingot`.

## Mobs

By default `behavior.mobSpearEquipTable = "JAVA"`: zombies / husks / zombie villagers get iron spears and piglins / zombified piglins get golden spears with roughly a 5% chance; the drop chance is 8.5% and +1% per Looting level.

Set it to `"MODDED"` for a wider list (vindicators 15%, wither skeletons 12%, etc.) and a flat 15% drop chance. See [docs/configuration.md](docs/configuration.md) (Chinese) for the full rules.

## Configuration

Config file: `config/spearcore-common.toml`.

```toml
# Whether the vanilla-material spears are enabled
enableVanillaSpears = true

# Whether the copper spear is enabled
enableCopperSpear = true

# When Punchy is installed, drive its charge animation from this mod's per-material stages
punchyStageBridge = true

# Whether to recognise 1.21.9+ spear resource packs and patch them to pack_format 15 in memory
resourcepackCompat = true

# Whether Tetra single-headed tools (long handle + spearhead) should work like this mod's spears
tetraCompat = true

# Scale Tetra's extra attack range into this mod's spear reach (0 = ignored, 1 = full, default 0.3)
tetraExtraReachScale = 0.3

# Scale Tetra's own weapon attack damage into spear damage (0 = ignored, 1 = full, default 0.5)
tetraDamageScale = 0.5
```

The first two switches only control the **creative tab and mob equipment**; crafting, `/give` and other ways of obtaining spears still work.

There are 17 more per-behaviour switches under `[behavior]` (all default to `JAVA`, i.e. vanilla behaviour). Full tables for behaviour switches, resource-pack compatibility, Tetra and Avaritia compatibility: **[docs/configuration.md](docs/configuration.md)** (Chinese).

## Compatibility

### Tetra (WIP, still being validated)

A Tetra single-headed tool with both **long handle** and **spearhead** behaves like this mod's spear: left-click jab, right-click charge (instead of throwing), reusing the same poses, hit detection and animations.

- Requires **Tetra 1.20.1-6.17.0 or newer**.
- Stats are fitted from Tetra's material attack damage / speed with a soft cap; Tetra weapon damage is first scaled by `tetraDamageScale`.
- Tetra's extra reach is scaled by `tetraExtraReachScale`.
- Fixed Java semantics: not affected by `jabCooldown` / `jabToChargeDelay`.
- The model uses four grayscale textures from this repository (16x16 icons + 32x32 in-hand), tinted by material in game.
- This compatibility is still being tuned; report pose/stat issues together with your Tetra version.

### Avaritia Spear

**Avaritia Spear 1.4.0** is an add-on made by the upstream author Lyingice; it compiles against the `spearcore` mod ID and upstream public methods. This fork keeps the same signatures (including the legacy `SpearItem` overload of `getHitEntitiesAlong`), and `CompatibilityGameTests` pins those compatibility seams.

### Others

- **JEI**: search "spear" for recipes.
- **KubeJS**: register your own spears from scripts - see **[kjs-guide.md](kjs-guide.md)**.
- **Punchy**: charge animation follows this mod's per-material stages; disable with `punchyStageBridge`.
- **Resource packs**: 1.21.9+ spear packs are patched in memory; files on disk are never modified.

## FAQ

**Why is spear attack damage lower than a sword?**
Most damage comes from speed; standing still is weaker than a sword, but a sprinting charge can far exceed it.

**Left-click does nothing?**
The attack cooldown bar is not full, or the target is still within its jab immunity window.

**Can't find spears in the creative tab?**
Creative-tab visibility is off by default; see `enableVanillaSpears` / `enableCopperSpear`, or just use `/give`.

**Does it support 1.21.1 / NeoForge?**
No. This repository targets 1.20.1 + Forge only; `main` is leftover upstream code that is neither built nor maintained.

**Will it conflict with other mods?**
JEI, KubeJS, Punchy, Tetra and Avaritia Spear are supported. For other conflicts, open an issue with `logs/latest.log`.

## Development

- Public items / tags / class hierarchy / events / extension points / KubeJS / Punchy / porting notes: **[docs/development.md](docs/development.md)** (Chinese)
- Full configuration reference: **[docs/configuration.md](docs/configuration.md)** (Chinese)
- KubeJS guide: **[kjs-guide.md](kjs-guide.md)**
- Repository conventions and self-checks: **[AGENTS.md](AGENTS.md)**

```bash
./gradlew build              # output: build/libs/spear-core-forked-1.20.1-forge-3.3.0.jar
./gradlew runServer          # headless launch; startup self-check prints the registered spears
./gradlew runGameTestServer  # automated tests; look for "All N required tests passed :)"
```

Release jars must go through `reobfJar` (`build` already includes it); do not bypass it.

## License

MIT License. Upstream code and art belong to Lyingice; changes and new assets in this fork belong to wdlpiaoyi and deepseek.
