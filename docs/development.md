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
| 突进附魔 | `spearcore:lunge`（最高 3 级；放行判定是 `EnchantmentCategory.create("spearcore:spear", item -> item instanceof SpearItem)`，不读任何 tag —— `#spearcore:spear` 只是给数据包/第三方模组看的归类） |
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

## Tetra（已支持，自定义 archetype）

本模组不再让 Tetra 自带的工具变成矛，而是新增一个**模块化长矛**：数据包把七把贴图长矛在工作台里替换成
`tetra:dynamic_handheld`，并在 NBT 里写 `archetype=modular_spear`；判定只看这个 archetype
（`compat/tetra/TetraSpearProfile.isModularSpearArchetype`）。

数据文件（**必须放 `data/tetra/`**，Tetra 的 DataStore 只收 `tetra` 命名空间）：

| 文件 | 作用 |
|---|---|
| `data/tetra/archetypes/modular_spear.json` | 四个槽位：`spear/head`、`spear/handle`（major + required）、`spear/binding`、`spear/butt` |
| `data/tetra/modules/spear/{head,handle,binding,butt,socket}.json` | 五个基础矛部件模块（变体前缀 `spear_head/` / `spear_handle/` / `spear_binding/` / `spear_butt/` / `spear_socket/`；`models` 复用 Tetra 自带模块模型）。`spear/socket` 与绑定共用 `spear/binding` 槽，只吃 `tetra:socket/*` 材料（描述里叫「镶嵌」，不限于宝石） |
| `data/tetra/modules/spear/{shuo_head,three_point_blade}.json` | 两个**卷轴解锁**的矛头：槊头（变体 `spear_shuo_head/`，只吃金属，左键挥砍 + 自带横扫，慢速高伤）、三尖两刃刀（变体 `spear_three_point/`，全材料，左键挥砍 + 横扫，攻速/伤害/耐久/射程全面高于普通头） |
| `data/tetra/modules/spear/fuller.json` | 第五槽「强化附层」，数据抄 Tetra 剑的 `sword/reinforced_fuller`：材料 `tetra:metal/`、`tetra:wood/`、`tetra:bone/`、`tetra:misc/forged_beam`；攻速 -0.1、耐久 +100、`durabilityMultiplier` 1.1、extract 耐久 0.25/完整度 0.5；配方 `schematics/spear/fuller.json` 直接可见。枪纂变体另带 `generic.attack_damage 0.5` |
| `data/tetra/modules/spear/composite_shaft.json` | 积竹木柲唯一模块（只吃 `tetra:wood/`）：1/3 基础为正但很低（攻速 +0.02、伤害 +0.05、射程 +0.02、平铺耐久 6），三级改进后完成版约 +0.4/+1.0/+1.0、无负面，`integrity` 固定 2.5；后续加竹/缠藤/完成走 `improvements/spear/composite/stage.json` 三级原地改进，不换模块。普通 `handle` 的 `extract.integrity` 提到 1.3 |
| `data/tetra/schematics/spear/{head,handle,binding,butt,socket}.json` | 五个基础换部件配方（工作台只显示 `slots` 命中当前物品槽位的 schematic；socket 与 binding 同为 `spear/binding`，互相替换） |
| `data/tetra/schematics/spear/{shuo_head,three_point_blade}.json` | 两个卷轴解锁的换矛头配方，`requirement` 是 `{"type": "tetra:locked", "key": "tetra:spear/<part>"}` |
| `data/tetra/schematics/spear/composite_shaft_{1,2,3,_finish}.json` | 积竹木柲阶段链：1/3（木板 + `axe_dig` 3 级，locked key `tetra:spear/composite_shaft_1`）、2/3（64 竹子 + `sword_dig` 2 级）、3/3（64 藤 + `sword_dig` 2 级）、完成（3/3 + `spear_hone/stable` ≥5 → 最终形态） |
| `data/tetra/schematics/spear/head/hone_{damage,durability,speed}_{1..5}.json` | 矛头打磨配方（`hone: true`；改进复用 Tetra 的 `spearhead_hone/damage`、`head_hone/durability`、`head_hone/speed`，翻译复用 Tetra 的 `localizationKey`；requirement 只按改进等级锁，**不要**按模块 key 过滤，否则换上槊头/三尖两刃刀后整条线消失） |
| `data/tetra/schematics/spear/handle/hone_{speed,durability}_{1..5}.json` | 矛杆速度 / 耐久打磨（速度复用 `handle_hone/speed`；耐久是新改进 `handle_hone/durability`，数据在 `data/tetra/improvements/spear/handle/hone_durability.json`） |
| `data/tetra/schematics/spear/handle/hone_stable_{1..5}.json` | 积竹木柲 3/3 的「稳定」打磨（改进 `spear_hone/stable`，每级 +0.04 攻速；五级后出现「完成」） |
| `data/tetra/schematics/spear/head/hone_sharp.json` | 矛头任一条五级打磨后解锁的「尖锐」：蓄力伤害系数 +5%（改进 `spearhead_hone/sharp` 在 `improvements/spear/head/sharp.json`，Java 侧读 key） |
| `data/tetra/schematics/spear/head/{serrated,tempered}_{1..3}.json` | 矛头锯齿（流血）/ 回火（穿甲）升级，`displayType: improvement`；不再复用 Tetra 剑刃的 localizationKey，名称/描述/槽位标签用自己的矛头文案 |
| `data/tetra/improvements/spear/{head/sharp,handle/hone_durability,handle/stable}.json` | 本模组自己的改进：尖锐（蓄力系数）、矛杆耐久、稳定（攻速）；`improvements` 目录名写在对应模块里 |
| `assets/spearcore/models/item/tetra_spear{,_shuo,_three}.json` | 模块化矛外观：默认 / 槊头 / 三尖两刃刀。三套只换头部那一层贴图（`tetra_spear_head*`），杆贴图共用；新增矛头时复制一份 `tetra_spear_head*` 改画再补一组模型即可 |
| `data/tetra/replacements/spearcore_spears.json` | 七条替换规则：七把**本模组的矛** → `tetra:dynamic_handheld` + 四模块（矛头/枪纂按 oak…netherite，矛杆 `spear_handle/stick`，绑定 `spear_binding/string`） |

槽位、模块与配方的显示名走 Tetra 的 I18n key，全部写在本模组的 `assets/spearcore/lang/*.json` 里：`tetra.slot.spear/head`、`tetra.module.spear/head.name`、`tetra/schematic/spear/head.name`（**配方是斜杠前缀**）。卷轴解锁的部件还要两组：`tetra.unlock.tetra.spear/<part>.description`（摆在工作台旁/上时的解锁提示）与 `item.tetra.scroll.spear/<part>.{prefix,name}`（卷轴物品自身的显示名；`prefix` 照 Tetra 原版卷轴写「原理图」/「Schematic」，于是显示成「原理图：槊头」）。打磨配方是唯一的例外：它们把 `localizationKey` 指向 Tetra 自带的 `sword/shared_blade/hone_damage_N`、`double/shared_head/hone_durability_N`、`double/shared_head/hone_speed_N` 与 `single/handle/hone_speed_N`，直接沿用 Tetra 的「打磨：伤害/耐久/速度」翻译。

其它冒险模组的 **Tetra 联动**分两类：

- **材料联动**（静态扫描 `Niubiosis` 整合包的 jar：art_of_forging、tetracelium、irons_spellbooks、malum、rainbowcompound、Gobber2 加的都是这一类；该实例只用来读数据，功能验证在 `1.20.1-forge` 实例）只要材料落在本模组模块的 `materials` 前缀里就能直接当矛部件用，材质自带的 `improvements` 也会跟着生效。前缀覆盖面与 Tetra 自带的 spearhead / long_handle / binding 一致：矛头/枪尾 wood/stone/metal/gem/bone，矛杆 wood/metal/bone/rod，绑定 fibre/skin/fabric。`socket/*` 材料由本模组的 `spear/socket` 模块承接（装进 `spear/binding` 槽、替换绑定），材料自带的 `attributes` 会并入长矛，所以 `blood_rune_socket` / `fang_charm` / `creat_netherstarite` 都能装；Tetra 的 perk（`perk_socket_N`）没做，perk 类效果仍拿不到。`item_effects/`、`synergies/` 已通过 Java 钩子/反射接上；`crafting_effects/` 由通用模块门禁映射让第三方数据按槽位角色命中矛。
- **配方 / 模块联动**：`ModuleRequirementSpearMixin` 把第三方写死的 `sword/*` / `single/*` / `double/*` 模块要求按角色映射到矛槽位，所以 crafting effect 类数据（如 RevelationFix 的神灵金锭）直接生效；纯 schematic 类联动仍需本模组槽位补一份自己的 schematic，并让模块 `improvements` 接受对方目录（已含 `tetra:sword/`、`tetra:single/`、`tetra:double/`）。

**卷轴不写在数据包里，而是 Java 拼出来的 NBT**：`compat/tetra/TetraSpearScrolls` 定义三条卷轴（`SHUO_HEAD` ribbon `b58a4a`、`COMPOSITE_SHAFT` ribbon `8fa24a` 且解锁 `tetra:spear/composite_shaft_1`、`THREE_POINT_BLADE` ribbon `7a6fd0` 且 intricate），卷轴本体是 `tetra:scroll_rolled` + `BlockEntityTag.data[0]`（不写 `details`；`ribbon` 必须 16 进制字符串）。**获取方式**：`TetraSpearTrades` 给工具匠/武器匠 master 加卷轴交易；`TetraSpearLoot` 给 `minecraft:chests/{nether_bridge,bastion_treasure,stronghold_library,end_city_treasure}` 各加 4% 池产出三尖两刃刀卷轴（`SpearScrollLootFunction` 压 NBT）。工作台会收集附近方块（`ISchematicProviderBlock`）的 schematic 解锁 `tetra:locked`；intricate 卷轴必须正对工作台上方一格。

（Goety 2.5.60.6 的 jar 内没有 `data/tetra`，它没有 Tetra 联动数据；GoetyRevelation 2.3.6 有 Tetra 卷轴配方（`data/goety_revelation/recipes/schematics_aeglos.json`，用 `tetra:scroll` ingredient + `tetra:scroll_rolled` 的 NBT 产物），但它引用的 `tetra:sword/longinus`、`tetra:sword/aeglos` schematic 并不在 jar 里。）

打磨进度由 archetype 的 `honeable` / `honeBase` / `honeIntegrityMultiplier` 控制：本模组取 Tetra `config/tetra.toml` 里双手重武器档的 **140 / 75**（剑是 110 / 65）。矛头三线（伤害/耐久/速度）各 5 级，任一满级后解锁**尖锐**（蓄力系数 +5%）；矛杆有速度/耐久两线；积竹木柲 3/3 另有**稳定** 5 级，满级后出现「完成」。

- 判定与数值：`compat/tetra/TetraSpearProfile`（守卫 + 判定）→ `TetraSpearBridge`（读 `DynamicModularItem` 属性、矛头形态、尖锐改进）→ `TetraSpearStats.fit`（拟合）。
- 工作台维修：Tetra 的 Repair 需要物品类注册 `RepairSchematic` + 变体级 `RepairDefinition`。`TetraSpearRepairSetup` 为 `tetra:dynamic_handheld` 注册维修项，`ItemModuleRepairMixin`（`ItemModule#getRepairDefinitions` HEAD，remap=false）按当前变体的材料现算定义，所以任意材料都能修。
- 全息球条目：`assets/tetra/holosphere_entries/modular_spear.json`（item + archetype + icon + position），否则「适用于此原理图的材料」会回首页。
- 客户端：`TetraSpearModel` 注册默认/槊头/三尖两刃刀三套模型，`ItemRendererTetraModelMixin` 按矛头变体 key 选择；隐藏探针剑不再自带贴图（指向 `minecraft:item/barrier`）。
- 客户端（贴图模型 + 材料 tint）：
  - `client/TetraSpearModel` 注册 `spearcore:tetra_spear#inventory`（`forge:separate_transforms`：base = 手持 32×32 贴图，gui/fixed/ground = 16×16 图标）。
  - `compat/tetra/mixin/ItemRendererTetraModelMixin`（`ItemRenderer#getModel` HEAD，`require=0`）只对 `SpearStyle.MODULAR_3D` 的栈替换模型。
  - `client/TetraSpearColorsEvent` → `client/TetraColorRegistration` 给所有 `IModularItem` 注册物品色；`compat/tetra/TetraSpearColors` 读 Tetra 材料色（`glyph.tint` 优先，其次材质 `tints.texture`，按 `variant.key` 的最后一段匹配），tint 0 = 杆（`spear/handle`）、tint 1 = 矛头（`spear/head`）。
  - `ItemRenderPropertiesTetraMixin` 给 `DynamicModularItem` 挂 `SpearClientExtensions`（第一/第三人称姿态）。
- 物品名：`ItemStackTetraMixin.spearcore$modularSpearName` 注入 `ItemStack#getHoverName` HEAD，交给 `compat/tetra/TetraSpearNaming` 拼「材料 + 矛」：矛头材料 key 取自 `spear/head` 的 variant key 末段，名字用 `item.spearcore.modular_spear.named`（`%s矛` / `%s Spear` / `%sの槍`），材料名前缀用 Tetra 的 `tetra.material.<材料>.prefix`。铁砧改过名的不覆盖，材料读不到时回退成裸 `item.spearcore.modular_spear`。**不要**去 mixin Tetra 的 `ModularItem#getName`（发行版是 SRG 名 `m_7626_`），也不要调 `IModularItem#getItemName`（它走客户端 `I18n`，服务端会抛错）。
- 工作台姿态：Tetra 用 `BakedModel#isGui3d()` 决定平面物品要不要绕 X 轴转 90° 平躺。`forge:separate_transforms` 默认 `gui3d = true`（`BlockGeometryBakingContext` 字段初值），所以 `client/TetraSpearModel` 用 `BakedModelWrapper` 把 `isGui3d()` 固定成 `false`；删掉它矛就会立在工作台上。
- 工作台布局：archetype 的 `x`/`y` 是 `GuiModuleOffsets` 的像素偏移，照 Tetra 默认布局写——2 major `(4,0)`/`(4,18)`、2 minor `(-18,0)`/`(-18,18)`（↗矛头、↘矛杆、↖绑定、↙枪纂）；写大数会让模块框散开。
- GameTest 服务端不加载 Tetra，只能覆盖纯函数与数据文件；真正的手感必须在装了 Tetra 的实例里手工验。
- 对应版本：Tetra **1.20.1-6.17.0**（`compileOnly 'curse.maven:tetra-289712:8570756'`）。

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

构建前必须把 `JAVA_HOME` 指向 **JDK 17**：ForgeGradle 6 / Gradle 8.8 跑不了 JDK 21+，
用新 JDK 会报 `Unsupported class file major version 69`。`gradle.properties` 里刻意不写死
`org.gradle.java.installations.paths`（那是某台机器的绝对路径）。

```bash
export JAVA_HOME=/path/to/jdk-17    # 换成你自己机器上的 JDK 17 目录（Windows 也可以写 C:/... 风格）
./gradlew build              # 产物：build/libs/spear-core-forked-1.20.1-forge-3.4.0.jar
./gradlew runServer          # 无头启动；启动自检会打印"已注册 7 把长矛"
./gradlew runGameTestServer  # 由游戏本体判定的自动化测试
```

GameTest 的判定看日志：`All N required tests passed :)` 或 `N required tests failed :(`，
退出码等于失败的必需测试数。测试结构模板在 `gameteststructures/`，Gradle 会拷进 `run/`。
