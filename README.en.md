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
- **Tetra compatibility**: drop one of this mod's spears into a Tetra workbench and it turns into a four-slot modular spear.
- **Resource-pack compatibility**: 1.21.9+ spear packs are detected and patched to 1.20.1 in memory.
- **Avaritia Spear compatibility**: upstream public method signatures are preserved, so `avaritia_spear` 1.4.0 works.
- **Multipart entities**: Forge multipart mobs such as the Ender Dragon take part-based damage (hitting the head hurts more), and a single swing hits each parent only once.

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
- The speed bonus on jabs is this mod's own `MODDED` mode (`jabSpeedBonus`); the default `JAVA` mode does not apply it, matching vanilla 26.1.2 piercing.

### Charge (hold right-click)

Hold right-click to walk through **wind-up -> dismount window -> knockback window -> damage window**.

- The charge auto-releases after the maximum duration (roughly 9-16 seconds depending on material: netherite 9.15s, iron 11.85s, wood 15.75s).
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

Values below are for the default config (`durability` = `JAVA`); with `MODDED` copper is 195 and golden 59.

| Spear | Attack damage | Attack speed | Durability | Enchantability | Notes |
|---|---|---|---|---|---|
| Wooden | 1 | 1.5 | 59 | 15 | fastest |
| Stone | 2 | 1.3 | 131 | 5 | |
| Copper | 2 | 1.2 | 190 | 13 | |
| Iron | 3 | 1.1 | 250 | 14 | baseline feel |
| Golden | 1 | 1.1 | 32 | 22 | good enchants, fragile |
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

# Whether this mod's Tetra modular spear should work like this mod's spears (does not affect the workbench replacement)
tetraCompat = true

# Scale Tetra's extra attack range into this mod's spear reach (0 = ignored, 1 = full, default 0.3)
tetraExtraReachScale = 0.3

# Scale the Tetra item's own attack damage into spear damage (0 = ignored, 1 = full, default 0.5)
tetraDamageScale = 0.5
```

The first two switches only control the **creative tab and mob equipment**; crafting, `/give` and other ways of obtaining spears still work.

There are 17 more per-behaviour switches under `[behavior]` (all default to `JAVA`, i.e. vanilla behaviour). Full tables for behaviour switches, resource-pack compatibility, Tetra and Avaritia compatibility: **[docs/configuration.md](docs/configuration.md)** (Chinese).

## Compatibility

### Tetra (WIP, still being validated)

This mod **no longer turns Tetra's own single-headed tools (spearhead + long handle) into spears**. Instead it adds a **new modular spear**: drop one of this mod's spears into a Tetra workbench and it is replaced by a `tetra:dynamic_handheld` modular item (archetype `modular_spear`); from then on it left-click jabs and right-click charges (instead of throwing), reusing the same poses, hit detection and animations.

- Requires **Tetra 1.20.1-6.17.0 or newer**.
- Its four slots use **sword parts for now**: blade `sword/blade`, hilt `sword/hilt`, guard `sword/guard` and pommel `sword/pommel`; the seven materials map to oak / stone / copper / iron / gold / diamond / netherite, and you can swap parts at the workbench later.
- Stats are fitted from Tetra's attack damage / speed with a soft cap; the Tetra item's own damage is first scaled by `tetraDamageScale`.
- Tetra's extra reach (`forge:entity_reach`) is scaled by `tetraExtraReachScale`.
- Vanilla enchantments supported by this mod and Lunge work on the modular spear too; anvil book enchanting is still blocked by Tetra itself (`isBookEnchantable=false`).
- On-hit enchantment effects fire as in vanilla: Fire Aspect ignites, and `doPostHurt` effects such as Bane of Arthropods / Frost Aspect run through `EnchantmentHelper`.
- Weapon enchantments added by other mods (e.g. Enigmatic Legacy's Slayer) also work on spears: the mod uses its own hidden probe sword to decide whether an enchantment applies to a standard weapon, so it does not depend on diamond swords being present. Player spear hits use the vanilla `player_attack` damage type (the spear death message is preserved), so bonuses gated on a real player attack are applied; non-whitelisted vanilla enchantments (e.g. Sweeping Edge) stay excluded.
- Mods that deal a pre-hit chunk of damage in the same tick (e.g. fantasy_ending's Void Collapsing) no longer swallow the spear's own damage.
- Fixed Java semantics: not affected by `jabCooldown` / `jabToChargeDelay`.
- The look is this mod's own texture-based spear model (reusing the four `tetra_spear*` textures) with handle and head tinted from Tetra materials; the item name follows Tetra's naming rule — material + "Spear" (e.g. Iron Spear, Netherite Spear) — and it lies flat on the Tetra workbench.
- The four workbench slots currently borrow Tetra's sword modules (blade / hilt / guard / pommel) as placeholders. **The item is not a sword**: combat, enchantments and damage are all spear behaviour.
- Turning `tetraCompat` off keeps the workbench replacement (that is datapack behaviour) but the converted item falls back to a plain Tetra tool.
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
Creative-tab visibility is **on** by default (`enableVanillaSpears` / `enableCopperSpear` are both `true`); if spears are missing, check those two switches or just use `/give`.

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
./gradlew build              # output: build/libs/spear-core-forked-1.20.1-forge-3.4.0.jar
./gradlew runServer          # headless launch; startup self-check prints the registered spears
./gradlew runGameTestServer  # automated tests; look for "All N required tests passed :)"
```

Release jars must go through `reobfJar` (`build` already includes it); do not bypass it.

## License

MIT License. Upstream code and art belong to Lyingice; changes and new assets in this fork belong to wdlpiaoyi and deepseek.
