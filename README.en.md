# Spear Core

> English ｜ [简体中文](README.md)
>
> A set of **spears** for Minecraft: charge, stab, hit harder the faster you run — and mobs will chase you with one too.

> Targets **Minecraft 1.20.1 · Forge** (client and server both).
>
> This branch = 1.20.1 + Forge; 1.21.1 + NeoForge lives on `main`, feature parity.

---

## What this mod adds

- **Seven spears**: wood, stone, copper, iron, golden, diamond and netherite, each with its own stats.
- **Two ways to fight**: left-click to **stab**, hold right-click to **charge**.
- **Speed is damage**: the faster you run at a target, the harder it hits.
- **Enchantments work normally**: Lunge, Sharpness, Smite and the rest.
- **Mobs use spears too**: zombies, skeletons and vindicators may spawn holding one — and they really do charge.
- **Animations and sounds included**: first- and third-person spear poses, a charge-up animation, hit feedback.

---

## Installation

1. **Minecraft 1.20.1** with **Forge** (47.3.0 or newer).
2. Drop the jar into your `mods` folder.
3. **Install it on both the client and the server.** Server-only means other players see an empty hand.
4. Single-player needs nothing else.

---

## The seven spears

These are the values shown for a bare item (enchantments and attribute modifiers not included):

| Spear | Attack damage | Attack speed | Durability | Enchantability | Notes |
|---|---|---|---|---|---|
| Wooden spear | 1 | 1.5 | 59 | 15 | fastest to raise |
| Stone spear | 2 | 1.3 | 131 | 5 | |
| Copper spear | 2 | 1.2 | 195 | 13 | |
| Iron spear | 3 | 1.1 | 250 | 14 | balanced; the baseline feel |
| Golden spear | 1 | 1.1 | 59 | 22 | enchants well, fragile |
| Diamond spear | 4 | 1.0 | 1561 | 10 | |
| Netherite spear | 5 | 0.9 | 2031 | 15 | fireproof, survives lava |

**A lower attack speed number means slower attacks**: the wooden spear stabs most often, the netherite spear hits hardest but with the longest gap between hits.

---

## Crafting

All six ordinary spears are made at a **crafting table**, shaped as one diagonal line of material plus two sticks:

```
Wooden spear   = any planks x1   + stick x2
Stone spear    = cobblestone x1  + stick x2
Copper spear   = copper ingot x1 + stick x2
Iron spear     = iron ingot x1   + stick x2
Golden spear   = gold ingot x1   + stick x2
Diamond spear  = diamond x1      + stick x2
```

Layout (`a` is the material, `b` is a stick, leave the rest empty):

```
[ ][ ][a]
[ ][b][ ]
[b][ ][ ]
```

The **netherite spear** uses the vanilla route: a smithing table with a `netherite upgrade template + diamond spear + netherite ingot`.

With JEI installed, just search for "spear" in game to see every recipe.

---

## How to use it

### Left click: stab

**Left click** with a spear in hand (your normal attack key) performs a stab. Two conditions apply:

- **The attack cooldown bar must be full** — like an axe, you have to wait after each hit, and a partial bar will not stab.
- After a hit, the same mob cannot be stabbed again for a short while, so a single charge cannot deal a burst of damage.

Stab damage = **weapon attack damage + a bonus from your forward speed**, so standing still and poking is the weakest option.

> Note: MC 26.1.2 added spears to vanilla. Vanilla's stab is pure attribute damage and only the charge
> scales with speed, so "stab also scales with speed" is a deliberate difference from vanilla. The charge
> math itself is identical in both.

### Hold right click: charge

Holding **right click** starts charging, and the spear moves through several stages:

| Stage | What it enables |
|---|---|
| Ready | spear raised; no effect yet |
| Dismount window | **knock a rider off their mount** |
| Knockback window | **send the target flying** |
| Damage window | **deal damage** (the main damage window) |

Charging to its maximum (roughly 6–12 seconds depending on the material) **ends automatically**; you do not have to hold the button the whole time.

**Crucially, charging does not slow you down** the way eating does. You can walk normally and even hold sprint at full speed — that is the whole point. Charge into a crowd with right click held and keep running.

### Why "the faster you run, the harder you hit"

The system looks at **your speed along the direction you are facing**, and at the **relative speed between you and the target**. So:

- Standing still → very low damage, and the knockback/damage windows may not even trigger.
- Sprinting into the target → noticeably more damage.
- Meeting a mob head-on that is walking towards you → the highest relative speed, so the most damage.
- Chasing a mob that is running away in the same direction → low relative speed, low damage.

### Knockback and dismounting

- **Knockback**: sends the target flying; you need to be moving fast enough.
- **Dismount**: pulls an enemy straight off a horse or boat. It is the earliest effect you can trigger, which makes it ideal against pillager cavalry during a raid.

---

## The Lunge enchantment

A new **Lunge** enchantment, up to level 3, applicable only to spears:

- **A stab pushes you forward**, further at higher levels.
- Cost: **1 durability** per stab, plus **hunger** that scales with the level (level 3 is expensive).
- It does **not** trigger when your hunger is below 6 (the JAVA rule; with `behavior.lunge = MODDED` it is blocked at hunger ≤7).
- It does **not** trigger while riding, elytra flying, or in water.

Combined with the "faster means more damage" rule, Lunge lets you build up your own speed — it is the spear's signature enchantment.

---

## Mobs use spears too

Who carries a spear, how often, and how often it drops are all controlled by config options. The default is `JAVA`, i.e. vanilla behaviour:

| Mob | Carries | Chance |
|---|---|---|
| Zombie / husk / zombie villager | iron spear | ~5% |
| Piglin / zombified piglin | golden spear | ~5% |

Vanilla never published the exact chance, so 5% is the commonly cited approximation. Zombie horsemen and camel husk jockeys are ordinary zombies riding a mount, so their riders are covered too.

With `behavior.mobSpearEquipTable = MODDED` you get this mod's original table instead:

| Mob | Chance |
|---|---|
| Vindicator | 15% |
| Wither skeleton | 12% |
| Husk | 10% |
| Zombie / zombie villager / pillager | 8% |
| Drowned | 6% |
| Skeleton / stray | 5% |
| Piglin / piglin brute / zombified piglin | always, with a golden spear |

The drop chance of that spear is controlled by `behavior.mobSpearDropChance`: `JAVA` (default) = **8.5%**, plus 1% per Looting level (11.5% at Looting III); `MODDED` = fixed 15%.

So do not assume you are the only one who can charge — **in MODDED mode, a vindicator charging with a spear genuinely hurts**.

---

## Configuration

The config file is `config/spearcore-common.toml`.

### Master switches

```toml
# Whether the vanilla-material spears are enabled (wood, stone, iron, gold, diamond, netherite)
enableVanillaSpears = true

# Whether the copper spear is enabled
enableCopperSpear = true

# When Punchy is installed, drive its spear charge animation from this mod's per-material stages
punchyStageBridge = true
```

The first two switches (both **on by default**) control:

- whether these spears **appear in the creative inventory**;
- whether mobs **spawn carrying** them.

**Turning them off changes nothing else**: crafting, `/give` and every normal way of obtaining them still work. The items always exist — they are simply hidden from the creative tabs and never handed to mobs.

### Behaviour switches (`[behavior]`)

One option per detail, **all defaulting to `JAVA`** (= vanilla Java Edition behaviour). Each option only lists the versions whose behaviour actually differs; a version that matches Java is never offered as a duplicate.

```toml
[behavior]
jabDamage = "JAVA"
jabSpeedBonus = "JAVA"
jabCooldown = "JAVA"
chargeHitboxMargin = "JAVA"
chargeRepeatHit = "JAVA"
jabRepeatHit = "JAVA"
durability = "JAVA"
creativeMaxRange = "JAVA"
offhandCharging = "JAVA"
lunge = "JAVA"
hitSlowDown = "JAVA"
mobSpearEquipTable = "JAVA"
mobSpearDropChance = "JAVA"
minAttackRange = "JAVA"
chargeStrength = "JAVA"
jabToChargeDelay = "JAVA"
movingTargetAssist = "JAVA"
```

| Option | Values (first is default) | Meaning |
|---|---|---|
| `jabDamage` | `JAVA` / `BEDROCK` | Stab damage. JAVA: wood/gold 1, stone/copper 2, iron 3, diamond 4, netherite 5; BEDROCK: +1 per tier. |
| `jabSpeedBonus` | `JAVA` / `MODDED` | Whether a stab adds `floor(speed × multiplier)`. Vanilla (both editions) does not; MODDED does. |
| `jabCooldown` | `JAVA` / `BEDROCK` | JAVA: wait for the vanilla attack cooldown bar; BEDROCK: use the item's use-cooldown, **shared by every spear in the inventory**. |
| `chargeHitboxMargin` | `JAVA` / `MODDED` | Extra hitbox inflation while charging: JAVA 0.125, MODDED 0.25. |
| `chargeRepeatHit` | `JAVA` / `BEDROCK` | Re-hitting during a charge: JAVA once per target per charge; BEDROCK once per 10 ticks (0.5s) per target. |
| `jabRepeatHit` | `JAVA` / `MODDED` | Re-stabbing: JAVA no extra cooldown; MODDED skips a target for 10 ticks. |
| `durability` | `JAVA` / `BEDROCK` / `MODDED` | Durability. JAVA: 59/131/190/250/32/1561/2031; BEDROCK: +1 per tier; MODDED: this mod's old table (copper 195, gold 59). |
| `creativeMaxRange` | `JAVA` / `BEDROCK` | Creative attack range cap: JAVA 6.5, BEDROCK 7.5. |
| `offhandCharging` | `JAVA` / `BEDROCK` | Charging from the off hand. JAVA allows it; BEDROCK does not. |
| `lunge` | `JAVA` / `MODDED` | Lunge strength and hunger gate. JAVA: 0.458 / blocked below hunger 6; MODDED: 0.65 / blocked at hunger ≤7. |
| `hitSlowDown` | `JAVA` / `MODDED` | On hit, cut the attacker's own horizontal speed to 60% and cancel sprinting. JAVA has it; MODDED does not. |
| `mobSpearEquipTable` | `JAVA` / `MODDED` | Mob loadout: JAVA vanilla list (iron/golden spears), MODDED this mod's chances + random materials. |
| `mobSpearDropChance` | `JAVA` / `MODDED` | Mob spear drop chance: JAVA 8.5% (+1% per Looting level), MODDED fixed 15%. |
| `minAttackRange` | `JAVA` / `MODDED` | Minimum attack range: JAVA 2 blocks (halved for mobs); MODDED 0 — you can poke at point-blank range. |
| `chargeStrength` | `JAVA` / `BEDROCK` | Whether the charge baseline takes attack-damage modifiers (Strength/Weakness). JAVA does not ([MC-302778](https://bugs.mojang.com/browse/MC-302778)); BEDROCK does. |
| `jabToChargeDelay` | `JAVA` / `BEDROCK` | Delay before you can start another charge after a jab: JAVA 0 ticks; BEDROCK the spear's use-cooldown (wood 13, iron 19, netherite 23 ticks). |
| `movingTargetAssist` | `JAVA` / `MODDED` | Moving-target compensation ([MC-310857](https://bugs.mojang.com/browse/MC-310857)): JAVA judges at the server-side position; MODDED rewinds the hitbox 3 ticks to what the client sees. |

Two rules are **deliberately not configurable**:

- **The rider-dismount speed thresholds are hard-wired to the Java Edition data** (wood 14 / gold 13 / stone 13 / copper 12 / iron 11 / diamond 10 / netherite 9 blocks per second) — that is the only trustworthy vanilla set, and Bedrock was aligned to it.
- Every judgable version difference was turned into a switch where possible (e.g. `chargeRepeatHit` mirrors Bedrock's 0.5 s re-hit interval); **engine / rendering** differences are out of scope (Bedrock's Fire Aspect igniting candles/campfires/TNT, charge crit particles).

With `jabDamage = "BEDROCK"` the extra point goes onto the attack-damage attribute, so the **tooltip and the stab gain +1 together**; whether the charge gains it too depends on `chargeStrength` (with `BEDROCK` the charge baseline also takes modifiers).

## Deviations from vanilla

These are the places where this mod intentionally differs from Java Edition vanilla (each has a switch in the previous section); everything else defaults to vanilla behaviour:

- **Charge damage baseline**: vanilla uses a fixed baseline of 1 (bare-hand damage) plus the relative-speed multiplier; this mod always uses the weapon's attack damage as the baseline, and `chargeStrength` only decides whether modifiers such as Strength/Weakness count.
- **Stab speed bonus**: vanilla stab damage only uses the weapon's attack damage and never scales with movement speed; this mod does not add it by default (`jabSpeedBonus = "MODDED"` enables it).
- **Per-target contact cooldown**: vanilla resolves a charge against each target once and has no extra stab cooldown; this mod defaults to that, and the `MODDED` settings of `chargeRepeatHit` / `jabRepeatHit` add a 10-tick cooldown.
- **Zero minimum range**: vanilla cannot hit anything closer than 2 blocks; `minAttackRange = "MODDED"` removes that limit.
- **Jab recovery**: vanilla Java lets you charge again immediately after a jab; `jabToChargeDelay = "BEDROCK"` adds the spear's use-cooldown as recovery.
- **Moving-target assist**: vanilla judges at the server-side position (you must lead moving targets); `movingTargetAssist = "MODDED"` rewinds 3 ticks to the position the client sees.
- **Mob spears**: vanilla only has zombies/husks/zombie villagers/zombie horsemen with iron spears and piglins/zombified piglins with golden spears; this mod defaults to that list, and the chance table + random materials are the `MODDED` setting (`mobSpearEquipTable`, `mobSpearDropChance`).

---

## FAQ

**Q: Why is a spear weaker than a sword?**
Because most of its damage comes from the **speed bonus**. Standing still and swinging is indeed worse than a sword, but a sprinting charge can hit far harder than one.

**Q: Left click does nothing / it will not stab.**
The attack cooldown bar is not full (the bar under your crosshair), or the target was stabbed moments ago and is still immune.

**Q: I cannot find the spears in the creative inventory.**
They are disabled by default — see the Configuration section above. You can also search the item ID directly in the creative search box, or use `/give`.

**Q: Can I put Sharpness on it?**
Yes. Spears take enchantments like any other weapon. Lunge is the spear-specific one.

**Q: Does it support other versions?**
This branch is **1.20.1 + Forge**; **1.21.1 + NeoForge** lives on the `main` branch. Gameplay is identical.

**Q: Does it conflict with other mods?**
**JEI** (recipe viewing), **KubeJS** (add your own spears from scripts) and **Punchy** (first-person view animation) are all supported.

---

## License

MIT License

---

# For mod and datapack authors

Everything public you can hook into from another mod, a datapack, or a KubeJS script.

## Items and tags

| Thing | ID |
|---|---|
| Built-in spears | `spearcore:wooden_spear`, `stone_spear`, `copper_spear`, `iron_spear`, `golden_spear`, `diamond_spear`, `netherite_spear` |
| The six vanilla-material spears | `#spearcore:spears` |
| All seven spears | `#spearcore:spear` |
| Vanilla spear tag | `#minecraft:spears` |
| Melee weapon tag | `#minecraft:swords` (which also pulls them into `#minecraft:tools` and `#forge:tools`) |
| Piglin loved | `#minecraft:piglin_loved` (golden spear only) |
| Damage type | `spearcore:spear` |
| Lunge enchantment | `spearcore:lunge` (`supported_items` is `#spearcore:spear`, max level 3) |
| Player attributes | `spearcore:spear_stab_multiplier` (stab damage multiplier, default 1.0), `spearcore:spear_charge_multiplier` (charge damage multiplier, default 1.0) |

## Class hierarchy

```
net.minecraft.world.item.Item
└── SpearItem                 (abstract) declares every spear parameter (reach, phases, hitbox, sounds, ...)
    └── BaseSpearItem         (abstract) Stats-driven; the seven built-in spears are its inner subclasses
        └── ConfiguredSpearItem          generic concrete implementation; registers on construction
                                         (used by the KubeJS and data-driven paths)
```

- `SpearItem`: the abstract methods describe what the spear looks like and how it fights. Client mixins, network packets and mob AI all test `instanceof SpearItem`.
- `BaseSpearItem`: two constructors — `(SpearStats.Stats)` derives the item properties from the stats, while `(SpearStats.Stats, Item.Properties)` hands full control of the item properties to the caller.
- `ConfiguredSpearItem`: a finished class bound to no material; `new ConfiguredSpearItem(stats)` is enough. It references **no third-party mod classes**, so it is safe for optional integrations.

### Defining your own spear (Java)

```java
public class MySpearItem extends ConfiguredSpearItem {
    public MySpearItem() {
        super(SpearStats.Stats.of(
            /* durability */ 300, /* attackDuration (seconds) */ 0.9f, /* damageMultiplier */ 1.0f,
            /* attackDamageBonus */ 3.0f, /* enchantmentValue */ 15,
            /* rarity */ Rarity.UNCOMMON, /* repairIngredient */ Ingredient.of(Items.COPPER_INGOT),
            /* fireResistant */ false, /* materialName */ "my_spear",
            /* use/hit/attack sounds */ SpearSounds.ITEM_SPEAR_USE.get(), SpearSounds.ITEM_SPEAR_HIT.get(), SpearSounds.ITEM_SPEAR_ATTACK.get(),
            /* swingTimes */ 0.9f, /* hitboxMargin */ 0.25f, /* contactCooldownTicks */ 10,
            /* delayTicks */ 12,
            /* three phase conditions */ SpearCondition.ofAttackerSpeed(50, 0.3F),
                                         SpearCondition.ofAttackerSpeed(135, 5.1F),
                                         SpearCondition.ofRelativeSpeed(225, 4.6F),
            /* forwardMovement */ 0.38f, /* minRange */ 2.0f, /* maxRange */ 4.5f,
            /* creative ranges */ 2.0f, 6.5f,
            /* hitboxMargin2 */ 0.25f, /* mobFactor */ 0.5f,
            /* dealsKnockback */ true, /* dismounts */ true));
    }
}
```

If you cannot be bothered with the numbers, `SpearStats.spearOf(...)` is a simplified factory that derives the phase parameters from `attackDuration`, and the built-in presets are available as `SpearStats.WOOD / STONE / COPPER / IRON / GOLD / DIAMOND / NETHERITE`.

> Note: registration has to happen during the **item registry event**, because the constructor records the `Item → Stats` mapping into `SpearStats`. If you register through a deferred register, make sure the sound registry is populated before `SpearStats` initialises.

## Events

All of them are on Forge's `MinecraftForge.EVENT_BUS` (the game bus):

| Event | When | What you can change |
|---|---|---|
| `SpearDamageEvent` | Before each damage calculation (once for a stab, once for a charge) | `setMultiplier(`...`)` / `addMultiplier(`...`)` to adjust the final damage; `getAttackType()` tells `STAB` from `CHARGE` |
| `SpearHitEvent` | On a hit, after damage is applied | Read-only: lifesteal, ignite, particles, counters, advancements |
| `SpearChargePhaseEvent` | The instant a charge phase changes (fires once per change) | Read-only: `DELAY / DISMOUNT / KNOCKBACK / DAMAGE`, for custom animations, sounds or particles |
| `SpearMobEquipEvent` | When a mob is about to be given a spear on spawn | **Cancellable**; you can also swap the spear or change the drop chance |

## Extension points

- `UseSpearSpecialEntity`: lets **non-player entities** (your own mobs) hook into the spear speed checks by overriding `getJerotesSpearNeedSpeed()`, `getJerotesSpearDamageMultiple()`, `isJerotesSpearGetMotionLikePlayer()` and friends.
- `KnownMovementAccessor` / `SpearCooldownAccessor`: interfaces injected into entities via mixin, for reading and writing the "known movement" vector and the recently-stabbed immunity records.
- Custom sounds may reuse the entries in `SpearSounds`, or you can register your own `SoundEvent` and pass it to the stats.

## KubeJS (supported)

A script can register a real spear, with exactly the same syntax as on the 1.21.1 branch:

```js
StartupEvents.registry('item', event => {
    event.create('mypack:bronze_spear', 'spearcore:spear')
        .displayName('Bronze Spear')
        .durability(400)
        .attackDamageBonus(3)
        .texture('mypack:item/bronze_spear')
})
```

The full walkthrough (parameter tables, texture conventions, pitfalls; Chinese and English) is in **[kjs-guide.md](kjs-guide.md)**.

**Class-loading isolation**: `compat/kubejs` is the only package in the project that references `dev.latvian.mods.*`; the entry point is a class-name string in `kubejs.plugins.txt` plus `Class.forName`. Without KubeJS installed that package is never loaded (measured: a KubeJS-less boot shows zero ClassNotFound and zero exceptions).

**Matching version**: KubeJS **2001.6.5-build.26+forge** (1.20.1 Forge).

## Punchy (supported)

With Punchy installed, the spear charge animation **follows this mod's per-material timeline** —
raise (delay + dismount) → knockback → damage-only — instead of Punchy's fixed timeline.
Set `punchyStageBridge = false` to hand control back to Punchy.

Matching version: Punchy **2.8d** (1.20.1 Forge). Without Punchy the whole chain is skipped:
the bridge mixin config is `required=false` and `PunchyMixinPlugin` decides at startup whether to
apply it (measured: zero ClassNotFound and zero exceptions without Punchy).

## Porting notes (1.21.1 → 1.20.1)

These APIs do not line up between 1.21.1/NeoForge and 1.20.1/Forge (all already handled):

| 1.21.1 / NeoForge | 1.20.1 / Forge |
|---|---|
| `CustomPacketPayload` + `StreamCodec` | `SimpleImpl`'s `SimpleChannel` |
| `[[mixins]]` in `mods.toml` | `mixin { config ... }` in `build.gradle` → `MixinConfigs` in the MANIFEST |
| `Attributes.ENTITY_INTERACTION_RANGE` | `ForgeMod.ENTITY_REACH` |
| `getDefaultAttributeModifiers(ItemStack)` + `ItemAttributeModifiers` | `getDefaultAttributeModifiers(EquipmentSlot)` + `Multimap` |
| `Operation.ADD_VALUE`, modifier IDs as `ResourceLocation` | `Operation.ADDITION`, IDs as `UUID` |
| Enchantments are datapack JSON | Enchantments are registry objects: `LungeEnchantment` + `EnchantmentCategory.create` |
| `Item#postHurtEnemy` | `Item#hurtEnemy` (durability is deducted here on 1.20.1) |
| `ICancellableEvent` | `@Cancelable` |
| `ResourceLocation.fromNamespaceAndPath(ns, path)` | `new ResourceLocation(ns, path)` |

### Building and verifying

```bash
./gradlew build              # output: build/libs/spearcore-1.20.1-forge-3.2.0.1.jar
./gradlew runServer          # headless boot; the startup check prints "已注册 7 把长矛"
./gradlew runGameTestServer  # automated tests judged by the game itself
```

Read the verdict from the log: `All N required tests passed :)` or `N required tests failed :(`;
the exit code equals the number of failed required tests. The structure template lives in
`gameteststructures/` and Gradle stages it into `run/`.
