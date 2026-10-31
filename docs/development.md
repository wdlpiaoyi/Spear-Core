# 开发与接入

把长矛接入其它模组、数据包或 KubeJS 时看这一页；仓库约定见 [AGENTS.md](../AGENTS.md)。


想在别的 mod、数据包或 KubeJS 脚本里接入长矛，下面是全部公开接口。

## 物品与标签

| 内容 | ID |
|---|---|
| 内建矛 | `spearcore:wooden_spear`、`stone_spear`、`copper_spear`、`iron_spear`、`golden_spear`、`diamond_spear`、`netherite_spear` |
| 六把原版材质矛 | `#spearcore:spears` |
| 全部七把矛 | `#spearcore:spear` |
| 原版矛标签 | `#minecraft:spears` |
| 近战武器归类 | `#minecraft:swords`（自动连带进入 `#minecraft:tools` 与 `#forge:tools`） |
| 猪灵喜爱 | `#minecraft:piglin_loved`（仅金矛） |
| 伤害类型 | `spearcore:spear` |
| 突进附魔 | `spearcore:lunge`（`supported_items` 即 `#spearcore:spear`，最高 3 级） |
| 玩家属性 | `spearcore:spear_stab_multiplier`（戳刺伤害倍率，默认 1.0）、`spearcore:spear_charge_multiplier`（冲锋伤害倍率，默认 1.0） |

## 类继承

```
net.minecraft.world.item.Item
└── SpearItem                 (abstract) 定义全部矛参数（攻击距离、阶段、判定箱、音效……）
    └── BaseSpearItem         (abstract) Stats 驱动，内建七把矛都是它的内部子类
        └── ConfiguredSpearItem          通用具体实现，构造即注册（KubeJS 与数据驱动路径使用）
```

- `SpearItem`：抽象方法全是"这个矛长什么样、怎么打"，客户端 mixin、网络包、怪物 AI 都按 `instanceof SpearItem` 判定。
- `BaseSpearItem`：两个构造 —— `(SpearStats.Stats)` 用 Stats 自动生成物品属性；`(SpearStats.Stats, Item.Properties)` 由调用方完全接管物品属性。
- `ConfiguredSpearItem`：不绑定任何材质的成品类，`new ConfiguredSpearItem(stats)` 即可；**不引用任何第三方 mod 的类**，可安全用于可选联动。

### 定义一把自己的矛（Java）

```java
public class MySpearItem extends ConfiguredSpearItem {
    public MySpearItem() {
        super(SpearStats.Stats.of(
            /* durability */ 300, /* attackDuration(秒) */ 0.9f, /* damageMultiplier */ 1.0f,
            /* attackDamageBonus */ 3.0f, /* enchantmentValue */ 15,
            /* rarity */ Rarity.UNCOMMON, /* repairIngredient */ Ingredient.of(Items.COPPER_INGOT),
            /* fireResistant */ false, /* materialName */ "my_spear",
            /* use/hit/attack 音效 */ SpearSounds.ITEM_SPEAR_USE.get(), SpearSounds.ITEM_SPEAR_HIT.get(), SpearSounds.ITEM_SPEAR_ATTACK.get(),
            /* swingTimes */ 0.9f, /* hitboxMargin */ 0.25f, /* contactCooldownTicks */ 10,
            /* delayTicks */ 12,
            /* 三个阶段条件 */ SpearCondition.ofAttackerSpeed(50, 0.3F),
                                  SpearCondition.ofAttackerSpeed(135, 5.1F),
                                  SpearCondition.ofRelativeSpeed(225, 4.6F),
            /* forwardMovement */ 0.38f, /* minRange */ 2.0f, /* maxRange */ 4.5f,
            /* 创造模式距离 */ 2.0f, 6.5f,
            /* hitboxMargin2 */ 0.25f, /* mobFactor */ 0.5f,
            /* dealsKnockback */ true, /* dismounts */ true));
    }
}
```

数值懒得填可以用 `SpearStats.spearOf(...)` 走简化工厂，它会按 `attackDuration` 自动算好各阶段参数；内建预设直接读 `SpearStats.WOOD / STONE / COPPER / IRON / GOLD / DIAMOND / NETHERITE`。

> 注意：注册必须发生在**物品注册事件**期间（构造时会把 `Item → Stats` 记进 `SpearStats` 的表）。要用延迟注册器的话，确保 `SpearStats` 初始化时音效注册表已就绪。

## 事件

全部在 Forge 的 `MinecraftForge.EVENT_BUS`（游戏总线）上：

| 事件 | 时机 | 能改什么 |
|---|---|---|
| `SpearDamageEvent` | 每次结算伤害前（戳刺与冲锋各一次） | `setMultiplier(`…`)` / `addMultiplier(`…`)` 调整最终伤害；`getAttackType()` 区分 `STAB` / `CHARGE` |
| `SpearHitEvent` | 命中且伤害已经打完 | 只读：吸血、点燃、粒子、计数、成就 |
| `SpearChargePhaseEvent` | 蓄力阶段切换的瞬间（只发一次） | 只读：`DELAY / DISMOUNT / KNOCKBACK / DAMAGE`，用于自定义动画/音效/粒子 |
| `SpearMobEquipEvent` | 怪物生成准备发矛时 | **可取消**；也能换矛、改掉落率 |

## 扩展点

- `UseSpearSpecialEntity`：让**非玩家实体**（自定义生物）接入矛的速度判定，可覆盖 `getJerotesSpearNeedSpeed()`、`getJerotesSpearDamageMultiple()`、`isJerotesSpearGetMotionLikePlayer()` 等。
- `KnownMovementAccessor` / `SpearCooldownAccessor`：通过 mixin 注入到实体上的接口，用于读写"已知移动向量"与"刚被谁戳过"的免疫记录。
- 自定义音效可以复用 `SpearSounds` 里的条目，也可以自己注册 `SoundEvent` 传给 Stats。

## KubeJS（已支持）

脚本里可以直接注册真正的矛，写法与上游 1.21.x 版本一致：

```js
StartupEvents.registry('item', event => {
    event.create('mypack:bronze_spear', 'spearcore:spear')
        .displayName('青铜矛')
        .durability(400)
        .attackDamageBonus(3)
        .texture('mypack:item/bronze_spear')
})
```

完整教程（参数表、贴图规范、常见坑，中英双语）：**[kjs-guide.md](kjs-guide.md)**。

**类加载隔离**：整个项目只有 `compat/kubejs` 包引用 `dev.latvian.mods.*`，入口是 `kubejs.plugins.txt` 里的类名字符串 + `Class.forName`。
没装 KubeJS 时那个包永远不会被加载（已实测：无 KubeJS 启动零 ClassNotFound、零 Exception）。

**对应版本**：KubeJS **2001.6.5-build.26+forge**（1.20.1 Forge）。

## Punchy（已支持）

装了 Punchy 时，矛的蓄力动画会**跟着本模组各材质的时间轴走** ——
举矛段(delay+dismount) → 击退段 → 仅伤害段，而不是用 Punchy 自带的固定时间轴。
不喜欢可以用配置项 `punchyStageBridge = false` 关掉，回到 Punchy 自己的节奏。

对应版本：Punchy **2.8d**（1.20.1 Forge）。没装 Punchy 时整条链路自动跳过：
桥接的 mixin 配置是 `required=false`，并额外由 `PunchyMixinPlugin` 在启动早期判定是否应用
（实测：无 Punchy 时零 ClassNotFound、零异常）。

## 1.20.1 移植说明

从 1.21.1 / NeoForge 移植到 1.20.1 / Forge 时，下面这些 API 是对不上的（都已按 1.20.1 改好）：

| 1.21.1 / NeoForge | 1.20.1 / Forge |
|---|---|
| `CustomPacketPayload` + `StreamCodec` | `SimpleImpl` 的 `SimpleChannel` |
| `mods.toml` 里的 `[[mixins]]` | `build.gradle` 的 `mixin { config ... }` → MANIFEST 的 `MixinConfigs` |
| `Attributes.ENTITY_INTERACTION_RANGE` | `ForgeMod.ENTITY_REACH` |
| `getDefaultAttributeModifiers(ItemStack)` + `ItemAttributeModifiers` | `getDefaultAttributeModifiers(EquipmentSlot)` + `Multimap` |
| `Operation.ADD_VALUE`，修饰符 ID 用 `ResourceLocation` | `Operation.ADDITION`，ID 用 `UUID` |
| 附魔是数据包 JSON | 附魔是注册表对象：`LungeEnchantment` + `EnchantmentCategory.create` |
| `Item#postHurtEnemy` | `Item#hurtEnemy`（1.20.1 的耐久扣减在这里） |
| `ICancellableEvent` | `@Cancelable` |
| `ResourceLocation.fromNamespaceAndPath(ns, path)` | `new ResourceLocation(ns, path)` |

### 自己构建与验证

```bash
./gradlew build              # 产物：build/libs/spear-core-forked-1.20.1-forge-3.3.0.jar
./gradlew runServer          # 无头启动；启动自检会打印"已注册 7 把长矛"
./gradlew runGameTestServer  # 由游戏本体判定的自动化测试
```

GameTest 的判定看日志：`All N required tests passed :)` 或 `N required tests failed :(`，
退出码等于失败的必需测试数。测试结构模板在 `gameteststructures/`，Gradle 会拷进 `run/`。
