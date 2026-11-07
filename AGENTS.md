# AGENTS.md

给在这个仓库里干活的 AI 代理（以及人类协作者）的约定。这个项目明显有 AI 参与，也拦不住后来人继续用 AI——所以这里不禁止 AI，而是把「怎样才能不把事情搞砸」写清楚。**动手前先读完本文件，动手后按第 6 节自检。**

## 1. 项目是什么

- **Spear Core**：给 Minecraft 1.20.1 Forge 加长矛的模组（七种材质：木/石/铜/铁/金/钻石/下界合金）。
- 本仓库是 `github.com/lyingice/Spear-Core` 的 fork，**只维护 `1.20.1-forge` 分支**。
  - `origin` = 自己的 fork（`wdlpiaoyi/Spear-Core`），`upstream` = 原作者。
- 展示名是 **Spear Core Forked**，但 `modId`、根包 `net.minecraft.spearcore`、注册命名空间 `spearcore`、物品 ID 与 tag **一律不改**——Avaritia 长矛等模组按 modId 写死兼容，动注册空间等于破坏兼容。
- 本仓库**只维护 1.20.1-forge**，没有 1.21.1 分支；`main` 是上游遗留代码（1.21.x / NeoForge 方向），不构建、不维护，不要改，也不要合并进 `1.20.1-forge`。
- 玩法还原目标：Java 版长矛行为（1.21.11 / 26.1.x 的数据），并通过配置项允许切到基岩版或「本模组自己的」行为。

## 2. 构建与测试（改完必须跑）

```bash
./gradlew compileJava          # 编译
./gradlew runGameTestServer    # 集成测试
```

- GameTest 的**通过标准**是日志出现 `All N required tests passed :)`（数量会随测试增加而变），失败则是 `N required tests failed :(`。退出码不总是可靠，**以日志为准**。
- 测试结构模板在 `gameteststructures/`，由 `stageGameTestStructures` 任务自动拷进 `run/`。
- Gradle 需要写 `~/.gradle` 缓存与锁文件；如果运行环境有文件沙箱，给它放行，否则会报 `Could not create parent directory for lock file`。
- 发布 jar 必须过 `reobfJar`（`jar` 已 `finalizedBy 'reobfJar'`）。别绕过它，否则 SRG 名不匹配，进真实游戏就 `NoSuchMethodError`。

## 3. 分支与提交

- 只在 `1.20.1-forge` 分支工作；提交前 `git status` 必须是干净的预期改动。
- **不要 force push**，不要改写已推送的历史。
- 提交信息用中文：一行摘要（可带 `修复：` / `清理：` / `兼容：` 之类前缀），正文用 `-` 列要点，说明「为什么」而不只是「改了什么」。
- 不要提交：`build/`、`run/`、`.gradle/`、`.idea/`、`.eclipse/`、`libs/`（`.gitignore` 已覆盖，别用 `git add -f`）。

## 4. 代码约定

- Java 17、Forge 1.20.1、official mappings、4 空格缩进、UTF-8、注释用中文。
- 不要升级 Forge / mappings / Gradle 版本，不要换构建插件；这套版本是验证过的。
- **不要引入 MCreator**：不加 `mcreator.gradle`、不加 `MCREATOR_*` 环境变量分支、不加 `will be REGENERATED on each build` 之类的注释。历史遗留已清理，见提交 `53e3126`。
- 新增/修改注入逻辑放 `mixin/` 包，mixin 配置写进 `spearcore.mixins.json`（Punchy 相关放 `spearcore.punchy.mixins.json`，Tetra 相关放 `spearcore.tetra.mixins.json`，两者都 `required=false` 且配各自的 `IMixinConfigPlugin`）。
- **不要内嵌 MixinExtras**（shading / jarJar 都不要）：类被摊进本模组 jar 后，本模组会变成导出 `com.llamalad7.mixinextras.*` 的模块，只要别的模组也带了 MixinExtras（如 justenoughbreeding），ModLauncher 就报 `Modules spearcore and MixinExtras export package ... to module ...` 启动失败。1.20.1 只用原版 Mixin 注入器（`@Inject` / `@Redirect` / `@ModifyConstant`）。
- 兼容层（KubeJS、Punchy）必须是**可选**依赖：`compileOnly` + 反射入口隔离，没装对应模组时零 ClassNotFound。

## 5. 行为配置体系（改玩法前必读）

- `config/SpearConfig.java`：Forge `COMMON` 配置。主开关（`enableVanillaSpears`、`enableCopperSpear`、`punchyStageBridge`、`resourcepackCompat`、`tetraCompat`、`tetraExtraReachScale`、`tetraDamageScale`）+ `[behavior]` 段的 17 个行为开关。
- `util/SpearRules.java`：17 个行为枚举的唯一读取入口（带异常兜底，配置文件没加载时回退 JAVA）。**任何行为判定都必须走 `SpearRules`，不要在业务代码里直接读配置或写死常量。**
- 约定：每个开关的默认值是 `JAVA`（= 原版 Java 行为）；**某版本取值和 Java 一样时，不要为它提供重复选项**（例如「戳刺速度加成」只有 `JAVA`/`MODDED` 两个值）。新增开关时同步改：`SpearConfig`（带中文注释）+ `SpearRules` + `README.md`（中文）与 `README.en.md`（英文）+ `kjs-guide.md`。
- 多部件实体（末影龙等 `PartEntity`）的射线命中：`SpearCombat.resolveLivingTarget` 先解析回母 `LivingEntity`，附魔加伤 / 同 tick 免疫清理 / 命中效果 / 击退 / 仇恨 / 重复命中记忆都作用在母实体上；伤害仍落在具体部件（保留原版龙头满伤、其他部件打折）。`getHitEntitiesAlong` 返回前用 `SpearCombat.dedupeByRoot` 去重，同一次出手对同一母实体只保留最近部件；蓄力/戳刺路径都不要再用 `instanceof LivingEntity` 过滤，改走 `resolveLivingTarget`。测试：`multipartTargetsResolveToLivingParent` / `multipartHitsCollapseToNearestPart`。
- 击落骑手的速度阈值**固定为 Java 数据**（木 14 / 金 13 / 石 13 / 铜 12 / 铁 11 / 钻 10 / 下界 9），见 `SpearStats.dismountSpeedFor`，不要再加开关。KubeJS 建造器的 `phases()` 只给窗口长度，阈值一律走 `SpearStats.dismountConditionFor(materialName, windowTicks)`，不要另写一个数（历史上写死过 0.3 格/秒，等于任何速度都能把人打下坐骑）。
- **蓄力伤害基线** = `ATTACK_DAMAGE` 的基础值 + 手持长矛自己的 `ADDITION` 攻击力（`SpearCombat.chargeBaseDamage` + `weaponAttackDamage`）；`chargeStrength=BEDROCK` 时取属性总值（玩家已含武器加成，怪物不含所以要补）。别退回「只取 `getAttributeBaseValue`」——那会让七种材质的蓄力基线全等于玩家的 1.0。
- 怪物 AI 判断出手距离必须和结算同一口径：`SpearItem.effectiveMinRange/effectiveMaxRange`（含创造射程、`mobFactor`、持有者 reach 超出 3.0 的部分），不要用 `getMaxRange()`。`SpearCollision.getHitEntitiesAlong` 与 `SpearItem#getHitEntitiesAlongInternal` 必须一致，否则怪物会在两倍于自己射程的距离上起手。
- 两条**不提供开关**的原版行为，别被旧说法带偏：蓄力（举矛）时**本来就能疾跑且不减速**（原版 `use_effects` 的 `speed_multiplier` 为 1.0、`can_sprint` 为 true）；命中目标时**攻击者自己会被减速**（`hitSlowDown` 的 `JAVA` 档才是原版手感）。
- 版本事实（改动对应开关前先确认）：Java 版蓄力伤害**不吃**力量/虚弱（原版 bug MC-302778，`chargeStrength` 的 `JAVA` 档就是复刻它）；基岩版戳刺后有一段后摇才能再蓄力（MCPE-233837，对应 `jabToChargeDelay` 的 `BEDROCK`）。
- `movingTargetAssist` 的 `MODDED` 档在**判断命中**时把目标判定箱按最近 3 tick 的位移往回挪（`SpearItem.compensatedTargetBox`），用来修 MC-310857「移动靶要预判」；只对玩家出手生效，怪物 AI 与伤害结算都不受影响，别顺手把「怪物的移动」也算进去。
- **攻击距离**：`BaseSpearItem` **不要**统一给长矛挂 `ForgeMod.ENTITY_REACH` 修饰符（历史上挂过 `+1.5`，已删，见提交「长矛攻击距离回到原版 4.5 格」）。本模组自带长矛的射程 = `SpearStats` 的 2 / 4.5（创造 6.5、怪物按 `mobFactor` 算成 2.25）**加上**持有者 reach 超出 3.0 的部分——后半句是给冒险整合包的攻击距离加成用的，别删（`SpearItem#effectiveMaxRange`）。
  - 第三方 / KubeJS 注册的长矛如果自己想要额外 reach，把属性修饰符写在该长矛自己的 `Item` 实现里，不要写进 `BaseSpearItem`，否则会和上面的换算叠算两遍（曾经变成 6.0 格）。
  - 戳刺判定箱用 `hitboxMargin2`（默认 0.25，对齐原版 `PIERCING_WEAPON`）；蓄力判定箱用 `hitboxMargin` + `SpearRules.hitboxMargin()`（`JAVA` 档 0.125，对齐 `KINETIC_WEAPON`）。这两个数不是「越大越还原」。

## 6. 数据与资源约定（踩过的坑）

- **1.20.1 的 tag 目录是 `data/<ns>/tags/items/`（复数）**，不是 1.21 的 `tags/item`。写错目录不会报错，tag 直接静默失效——原仓库就是这样，所有 tag 实际都没生效。
- 长矛必须挂进这些 tag（新增长矛时同步加，注意 `#spearcore:spears` 是「六把原版材质矛」、`#spearcore:spear` 才是「全部七把含铜矛」的上游约定）：
  - `#spearcore:spears`（六把原版材质矛，不含铜）、`#spearcore:spear`（全部七把，含铜矛；KubeJS 建造器的 `supported_items` 用它，突进附魔用的是 `EnchantmentCategory`，见下文）、`#spearcore:copper_spear`（仅铜矛）；
  - `#minecraft:spears`（前向兼容 1.21 的名字；KubeJS 新建的矛会自动加进来）；
  - `#minecraft:swords`（近战武器归类，顺带自动进入 `#minecraft:tools` 与 `#forge:tools`）；
  - `#minecraft:piglin_loved`（仅金矛）。
- `SpearGameTests.spearsCarryItemTags` 会验证上面这些 tag，tag 文件路径写错会直接测试失败。
- 新增物品要同时补齐：注册、配方（`data/spearcore/recipes/`）、模型（`assets/.../models/item/`）、贴图、语言文件 `en_us` / `zh_cn`。**只维护中英两份**：`ja_jp` 是上游日本贡献者在 3.3.x 时期提供的翻译，早已跟不上新 key、也没有人维护（新 key 只在日文里缺失，玩家看到的是中英混杂的原始 key），3.4.0 起从本 fork 删除，日文回退英文；不要重新引入这份文件。

### 资源包兼容层（1.21.9+ 长矛材质包）

- `resourcepack/` 下的类只在客户端加载（`Dist.CLIENT` 订阅者 + `AddPackFindersEvent`），专用服务器不得触碰资源栈。
- 只处理 `assets/minecraft/textures/item/*_spear(_in_hand).png`，**不要**顺手转换模型、语言文件或 `custom_model_data`。
- 一切补丁只在内存副本里，**绝不改写用户的资源包文件**；`pack_format` 只补成 15。

### Tetra 兼容层（自定义 archetype 的模块化长矛）

- **目标：不再影响 Tetra 自带的任何工具。** 本模组的模块化长矛 = `tetra:dynamic_handheld` + 物品栈 NBT 里的 `archetype=modular_spear`，由数据包 replacement 把七把贴图长矛转换得到（放进 Tetra 工作台即触发）。Tetra 自带的单头工具（矛头 + 长手柄）与普通模块工具一律按普通工具工作。
- **判定唯一入口**：`compat/tetra/TetraSpearProfile.isModularSpearArchetype(String)`（只看纯 path，命名空间恒为 `tetra`）。`TetraSpearProfile.of` 仍必须先 `ModList.isLoaded("tetra")` + `SpearConfig.TETRA_COMPAT` 守卫，任何异常只记一次日志并当作「不是长矛」；`TetraSpearBridge` 里先 `stack.getItem() instanceof DynamicModularItem`，再取 `DynamicModularItem.getArchetypeKey(stack)` 比对。不要再按模块 key（`single/spearhead` / `single/long_handle`）判定。
- **数据文件必须放 `data/tetra/`**：Tetra 的 DataStore 只收 `tetra` 命名空间（可以用 `spearcore` 的包，但目录必须是 `data/tetra/archetypes/`、`data/tetra/replacements/`）。
  - `data/tetra/archetypes/modular_spear.json`：四个槽位 `spear/head`（major + required）、`spear/handle`（major + required）、`spear/binding`、`spear/butt`；字段名是 record 组件名的 camelCase（`honeBase` / `honeIntegrityMultiplier`），漏字段会静默取默认值。方位对应设计稿 ↗矛头、↘矛杆、↖绑定/涂层、↙枪尾（配重）。**镶座（socket）与绑定共用 `spear/binding` 槽**：Tetra 剑本来就是把 socket 装在 guard 槽、替换掉护手，本模组照这个思路让镶嵌宝石替换绑定，不额外加槽位。
  - 部件模块是本模组自己的数据：`data/tetra/modules/spear/{head,handle,binding,butt,socket,shuo_head,composite_shaft,three_point_blade}.json`（模块 key 即路径 `spear/head` 等），变体前缀分别是 `spear_head/`、`spear_handle/`、`spear_binding/`、`spear_butt/`、`spear_socket/`、`spear_shuo_head/`、`spear_composite_shaft/`、`spear_three_point/`（三尖两刃刀的模块 key 是 `spear/three_point_blade`、变体前缀却是 `spear_three_point/`，两处不同名是刻意的，测试按 part→prefix 表校验）。后三个是**卷轴解锁的替换部件**：槊头与三尖两刃刀都占 `spear/head`，积竹木柲占 `spear/handle`。`spear/socket` 只吃 `tetra:socket/*` 材料，extract 抄 Tetra 的 `sword/socket`（durability +1 / integrity -1 / `models tetra:item/module/sword/guard/socket/`）；socket 材料自带的 `attributes` / `effects`（如 irons 的 `blood_rune_socket`、art_of_forging 的 `fang_charm`）会随变体直接合并进长矛，不需要额外 Java。**变体前缀不能复用 Tetra 的 `spearhead/` / `basic_blade/`**：Tetra 的变体 key 在模块间共享命名空间，撞名会和 Tetra 自带模块互相覆盖。`models` 直接引用 Tetra 自带的模块模型路径（贴图在 Tetra 包里，本模组不重复 ship）。
  - 换部件的配方在 `data/tetra/schematics/spear/{head,handle,binding,butt,socket}.json`（socket 的 `slots` 同样是 `spear/binding`，和绑定互斥）外加卷轴解锁的 `{shuo_head,composite_shaft,three_point_blade}.json`：工作台只显示 `slots` 命中当前物品槽位的 schematic，所以槽名改成 `spear/*` 后必须自带这四份（Tetra 剑的 `sword/*` schematic 不会再出现）。`outcomes` 的 `moduleKey` 写 `spear/head` 这种本模组模块 key，`moduleVariant` 写 `spear_head/` 这类前缀。
  - 打磨（hone）：`data/tetra/schematics/spear/head/hone_{damage,speed}_{1..5}.json`。改进 key **复用 Tetra 自带的两套**——`spearhead_hone/damage`（定义在 `data/tetra/improvements/single/spearhead/hone_damage.json`）与 `head_hone/speed`（`single/shared_head/hone_speed.json`），所以 `modules/spear/head.json` 的 `improvements` 必须继续包含 `tetra:single/spearhead/` 与 `tetra:single/shared_head/`（少一个，对应那条打磨线就整条消失）。`localizationKey` 也复用 Tetra 的 `sword/shared_blade/hone_damage_N` / `double/shared_head/hone_speed_N`，翻译跟着 Tetra 走，本模组不必再写十份语言文件。archetype 的 `honeable` 必须 `true`，`honeBase` / `honeIntegrityMultiplier` 取 Tetra `config/tetra.toml` 的**双手重武器**档 140 / 75（剑是 110 / 65）；写 0 会让打磨进度没有基数、面板进不去。
  - 翻译走 Tetra 的 I18n key，写在本模组 `assets/spearcore/lang/*.json`（MC 会合并所有命名空间的 lang）：槽位 `tetra.slot.spear/head`、模块 `tetra.module.spear/head.name`、配方 `tetra/schematic/spear/head.name`（**注意配方是斜杠前缀，不是点分**）。卷轴解锁的部件还要多两组：locked 需求的提示 `tetra.unlock.<ns>.<path>.description`（例 `tetra.unlock.tetra.spear/shuo_head.description`）与卷轴显示名 `item.tetra.scroll.spear/<part>.{prefix,name}`（`prefix` 照 Tetra 的原版卷轴写「原理图」/「Schematic」，有翻译时显示成「原理图：槊头」）。
  - **卷轴（解锁 locked 配方）**：`compat/tetra/TetraSpearScrolls` 定义三条卷轴（`SHUO_HEAD` / `COMPOSITE_SHAFT` / `THREE_POINT_BLADE`），`TetraSpearScrollsTab` 在 MOD 总线的 `BuildCreativeModeTabContentsEvent` 里把卷轴塞进 `tetra:default` 创造栏（**卷轴暂时没有获取方式**，用户明确要求先不加；以后接配方/战利品时把这一段删掉即可）。卷轴不是独立物品，而是 `tetra:scroll_rolled` 方块物品 + `BlockEntityTag.data[0]`，字段：`key` / `schematics`（字符串列表）/ `intricate` / `material` / `ribbon` / `glyphs`（**不写 `details`**：那是右键打开长卷的画面 id，原理图卷轴没有）；**`ribbon` 必须写成 16 进制字符串**（Tetra `ScrollData#readRibbonFast` 用 `Long.parseLong(s, 16)`，写 int 会静默变 0）。解锁链路：`ScrollBlock implements ISchematicProviderBlock` → 工作台把附近 provider 的 schematic 收进 `CraftingContext.unlocks` → 配方 `requirement: {"type": "tetra:locked", "key": "tetra:spear/xxx"}` 通过。**`intricate = true`（「复杂原理图」/深奥的记录）必须正对工作台上方一格**（`ScrollBlock#canUnlockSchematics` 判 `workbenchPos.above()`），普通卷轴摆在附近即可——三尖两刃刀是 intricate，其余两件不是。`TetraSpearScrolls` 不引用任何 `se.mickelus.tetra` 类，未装 Tetra 也能加载，`modularSpearScrollsUnlockTheirSchematics` 用它交叉校验「卷轴声明的 schematic ↔ 文件里的 locked key」。
  - 槽位的 `x` / `y` 是 Tetra `GuiModuleOffsets` 的像素偏移（`DynamicModularItem.getMajorGuiOffsets` 原样喂给 `GuiModuleOffsets`），数值要照 Tetra 默认布局量级：2 major 用 `(4,0)` / `(4,18)`，2 minor 用 `(-18,0)` / `(-18,18)`。写成 `0/40/80` 这种大数会让工作台里的模块框散开（`modularSpearArchetypeDataIsValid` 会校验这两组坐标）。
  - `data/tetra/replacements/spearcore_spears.json`：七条，`item=tetra:dynamic_handheld`、`archetype=modular_spear`、`modules` 写四个槽位的 `[模块 key, 变体 key]`。模块 key 会被 Tetra 强制加 `tetra:` 命名空间（本模组的数据放在 `data/tetra/` 下，所以 key 照样是 `spear/head` 这类纯 path）；变体材质：矛头/枪尾用 oak / stone / copper / iron / gold / diamond / netherite，矛杆固定 `spear_handle/stick`（`rod/stick` 材质），绑定固定 `spear_binding/string`（`fibre/string` 材质）。
- **第三方 mod 的 Tetra 联动分两类，处理方式完全不同**：
  - **材料联动（`data/tetra/materials/<类别>/<key>.json`，白送）**：模组只是往 Tetra 的材料库加材料，材料落在我们模块 `materials` 前缀里就能直接当矛部件用；材质自带的 `improvements` 也会授予（例如 malum 的 `soul_stained_steel` 自带 `malum.soul_strike`），前提是模块 `improvements` 接受它所在的目录——矛头/矛杆接受 `tetra:shared/`，第三方的共享改进几乎都放在 `data/tetra/improvements/shared/`，所以能拿到。前缀覆盖面和 Tetra 自带的 spearhead / long_handle / binding **完全一致**（矛头 wood/stone/metal/gem/bone、矛杆 wood/metal/bone/rod、绑定 fibre/skin/**fabric**，只多一个 `fabric/`）。
  - 实测基线是 Prism 实例 `Niubiosis`（568 个模组，Forge 47.4.28，2026-11；**只做 jar 数据静态扫描，不要拿它当测试实例，启动要半天**，功能验证一律在 `1.20.1-forge` 实例里跑）：`tetra 6.17.0` 本体 1413 条 `data/tetra`，其余 `art_of_forging 1.8.5` 427 条（metal/diabolium·endsteel_ingot·forged_steel_ingot·vobrivium_ingot、fibre/life_fiber·warped_muscle、misc/echo_shard·eerie_shard、reagent/*、socket/*）、`tetracelium 1.3.2` 168 条（metal/bronze·electrum·elementium·fiery_ingot·infused_iron·ironwood·knightmetal·lead·manasteel·nickel·osmium·silver·sky·steel·steeleaf·terrasteel·tin、wood/ancient_wood·baobab·dreamwood·livingwood·maple·treated_wood、gem/ruby·sapphire·agate·citrine·certus_quartz、bone/dragon_bone·wither_bone、fibre/hemp·mana_string·straw、fabric/canvas）、`irons_spellbooks 3.16.3` 25 条、`malum 1.6.7` 19 条、`rainbowcompound 1.3.5` 17 条（socket/creat_netherstarite）、`Gobber2 2.8.9` 7 条（metal/compat/gobber*）、`goetyawaken 1.3.9` 4 条（只有 `item_effects/`，condition 是 `tetra:fixed value:false`，等于没开）。该实例还装了 `AvaritiaSpear 1.5.0`，所以那边的 ABI 兼容同样要守住。
  - `socket/*` 材料由本模组的 `spear/socket` 模块承接（装在 `spear/binding` 槽，装了就替换掉绑定）：材料自带的 `attributes` 会随变体直接合并进长矛，所以 art_of_forging / irons_spellbooks / rainbowcompound 的 socket 材料（`blood_rune_socket`、`fang_charm`、`creat_netherstarite`）现在都能装。**perk 类效果（`perk_socket_N` 才有的那些）仍然拿不到**：Tetra 的 perk 需要槽位带 `perk_socket_N` 前缀 + schematic 的 `requirement` 判 `tetra:perk`，本模组只做了基础镶座，没有 perk 槽，需要时另外实现。
  - 另外三种 Tetra 数据机制对矛**目前不生效**，别指望：`item_effects/` 由 `DataEffectsHandler` 按 trigger/condition 全局触发（不看槽位）；`crafting_effects/` 是工作台合成的产物效果；`synergies/` 由各物品类在自己的 `commonInit` 里用 `SynergyStore.getOrdered("<类型>/")` 拉取，而 `tetra:dynamic_handheld` 没有这一步（`ModularItem` 里 `synergies` 只有空数组初值），所以第三方给 `sword/` / `single/` 写的 synergy 对模块化长矛一律无效。要在矛上支持 synergy，得自己写一份 `data/tetra/synergies/spear/` 并在 Java 侧把 `getAllSynergyData` 接上。
  - **配方 / 模块联动（不会自动生效）**：对方联动数据的 gate 是它们自己 schematic 的 `slots` / `requirement`（写死 `sword/blade`、`sword/hilt` 这类模块 key），我们改不了别人的数据包。接法是**数据包补一层**，不用改 Java：① 把对方改进目录加进 `data/tetra/modules/spear/head.json` 的 `improvements`（同路径 + `replace: true` 覆盖本模组那份，Tetra 的 `MergingDataStore` 按路径合并）；② 在本模组槽位（`spear/head` 等）补自己的 schematic，`outcomes.improvements` 授予对方的改进 key。
  - 另外核过：Goety 2.5.60.6 的 jar 里 `data/tetra` 为空，**它家没有 Tetra 联动**，所谓「unholy blood 涂层」不是 Tetra 的东西。
- **不要 mixin Tetra 的 `ItemModularHandheld`**：发行版 jar 里它的 override 是 SRG 名，Mixin 的 refmap 只覆盖 MC 类，映射不到模组类的 override；改为在 `ItemStack`（MC 类）上拦截 `use` / `getUseDuration` / `getUseAnimation` / `onUseTick` / `releaseUsing` / `finishUsingItem`。Tetra 的 `use()` 只有在它自己算出 `getUseDuration > 0` 时才会起手，所以 `use` 必须由本模组接管。另在 `Item` 上补 `canAttackBlock`（创造模式按原版剑返回 false），WieldYourPower 的强制破坏也看这个判定。配置放 `spearcore.tetra.mixins.json`（`required=false`，`TetraMixinPlugin`）。
- 行为逻辑统一在 `item/SpearLike` + `init/SpearCombat` + `init/SpearLikeSupport`，不要再写 `instanceof SpearItem`；AI goals / 怪物发矛 / 附魔分类保持 SpearItem-only（Tetra 怪没有 AI）。
- 数值：`compat/tetra/TetraSpearStats.fit` 从 Tetra 属性拟合，不复刻本模组材质预设，高数值区间必须放缓（`soft` 值）；改动公式要同步 `TetraCompatGameTests`。
- Tetra 自己贡献的额外攻击距离（`forge:entity_reach`）优先取 `IModularItem.getAttributeValue(stack, ForgeMod.ENTITY_REACH.get())`，取不到再退回遍历 `ItemStack` 的修饰符（`ADDITION` + `MULTIPLY_BASE` 折算）；只按配置 `tetraExtraReachScale`（0~1，默认 0.3，读取失败回落 0.3）计入射程，其他来源的 reach 不缩放。若来源完全统计不到，`SpearCombat.effectiveMaxRangeWithFallback` 会把全部额外距离当作 Tetra 来源，避免配置静默失效。纯函数在 `SpearCombat.weakenExtraReach`，测试是 `tetraExtraReachIsWeakened` / `tetraReachFallbackAppliesWhenBonusUnknown`。
- 模块化长矛固定 Java 语义：`usesJabCooldown()` 返回 false、`jabToChargeDelayTicks()` 返回 0，不受 `jabCooldown` / `jabToChargeDelay` 配置影响；文档要写明。
- Tetra 物品自身的 `ATTACK_DAMAGE`（`ADDITION` 部分）在 `ItemStackTetraMixin.getAttributeModifiers` 的 RETURN 处按 `SpearConfig.tetraDamageScale`（0~1，默认 0.5，读取失败回落 0.5）统一打折，实现在 `compat/tetra/TetraDamageScaling`；只缩放 `ADDITION`，`MULTIPLY_BASE`/`MULTIPLY_TOTAL`、玩家基础攻击力与其他来源都不动。测试是 `tetraAttackDamageIsScaled`。
- **属性查询的重入闸门别删**：`ItemStackTetraMixin.spearcore$tetraAttackDamage` 注入在 `ItemStack#getAttributeModifiers(EquipmentSlot)` 的 RETURN，而 `TetraSpearBridge` 取不到 `ENTITY_REACH` 时会回退去扫同一个方法的修饰符；`spearcore$resolvingAttributes` 这个 `ThreadLocal` 就是防自递归的。删掉会无限递归到 `StackOverflowError`，被兼容层 `catch (Throwable)` 吞掉后模块化长矛直接失去蓄力/戳刺全部行为。
- 客户端：`compat/tetra/mixin/ItemRenderPropertiesTetraMixin` 在 `Item#getRenderPropertiesInternal`（HEAD，`remap=false`）对 `DynamicModularItem` 惰性挂 `SpearClientExtensions`（渲染时 `ModList` 必已就绪）；**不要**改回 `Item#initializeClient`（物品构造期 `ModList` 可能还没就绪，会静默挂不上）。蓄力动画直接复用 `SpearAnimations.firstPersonUse` / `thirdPersonUseItem`；`getUseAnimation` 固定 `NONE`，第三人称蓄力手臂由 `HumanoidModelMixin` 调 `SpearAnimations.thirdPersonHandUse`。
- **外观用本模组自己的贴图模型，不交给 Tetra 拼**：`client/TetraSpearModel` 注册 `spearcore:tetra_spear#inventory`（`forge:separate_transforms`：base = 手持 32×32 贴图，gui/fixed/ground = 16×16 图标），`compat/tetra/mixin/ItemRendererTetraModelMixin`（`ItemRenderer#getModel` HEAD，`require=0`）只对 `SpearStyle.MODULAR_3D` 的栈替换模型。tint 由 `client/TetraColorRegistration` / `TetraSpearColorsEvent`（先 `ModList` 守卫）注册给所有 `IModularItem`，`compat/tetra/TetraSpearColors` 读 Tetra 材料色：**tint 0 = 杆（槽位 `spear/handle`）、tint 1 = 矛头（槽位 `spear/head`）**。`assets/spearcore/models/item/tetra_spear*.json` 与四张灰阶贴图是生效资产，别删、别改路径。
- **物品名「材料 + 矛」**：`ItemStackTetraMixin.spearcore$modularSpearName` 注入 `ItemStack#getHoverName` HEAD（MC 方法，refmap 可用），交给 `compat/tetra/TetraSpearNaming`：矛头槽位（`spear/head`）variant key 的末段是材料 key（`TetraMaterialKey.materialOf`，`spear_head/netherite` → `netherite`），拼 `item.spearcore.modular_spear.named` = `%s矛` / `%s Spear` / `%sの槍`，材料名用 Tetra 自己的 `tetra.material.<材料>.prefix`（铁 / 下界合金 / 木；缺翻译回退 `tetra.material.<材料>`）。铁砧改过名的栈不覆盖；材料读不到时回退成裸 `item.spearcore.modular_spear`（矛 / Spear / 槍）。**不要**去 mixin Tetra 的 `ModularItem#getName`（发行版是 SRG 名 `m_7626_`，注入不可靠），也不要直接调 `IModularItem#getItemName`——它内部走客户端专用的 `I18n`，而 `getHoverName` 在专用服务器上也会被调用（死亡消息、命令反馈），会直接抛错。
- **工作台上必须平躺**：Tetra 的 `WorkbenchTESR` 用 `BakedModel#isGui3d()` 选姿态——`true` 只平移 `(0.5, 1.125, 0.5)` 不旋转（平面贴图会竖着悬在工作台上方），`false` 才会绕 X 轴转 90° 平放。`forge:separate_transforms` 的几何上下文默认 `gui3d = true`（Forge `BlockGeometryBakingContext` 的字段初值，JSON 里没有能改它的键），所以 `client/TetraSpearModel` 烘焙后套一层 `BakedModelWrapper` 把 `isGui3d()` 固定成 `false`，**别删**。改成 3D 模型（`isGui3d = true`）之前先想清楚工作台姿态。
- 指向方块 / 挥空：`SpearCooldownAttackMixin` 在非 ENTITY 分支对 MODULAR_3D 补 `player.swing` + 客户端 `playLocalSound(ITEM_SPEAR_ATTACK)`；创造模式 `canAttackBlock=false` 会让原版 startAttack 空转，所以不能指望原版音效。
- 附魔：放行判定统一走 `SpearEnchantments.supportsSpearEnchantment(enchantment)` = 原版 8 项白名单 + 「模组添加、且能附在本模组隐藏探针剑 `spearcore:enchantment_probe` 上」的武器附魔。探针剑 `item/EnchantmentProbeItem` 继承 `SwordItem`，**不可获取**：不进创造栏、无配方、无掉落，只挂 `#minecraft:swords` 让人按标签判断武器的模组也认得它；不要给它加配方/战利品/本模组 tag。
- 放行点三处：`SpearItem#canApplyAtEnchantingTable`（内建矛，`supportsSpearEnchantment || super`）；`mixin/EnchantmentTetraCompatMixin`（`Enchantment#canApplyAtEnchantingTable` HEAD，`MODULAR_3D` + `supportsSpearEnchantment`）；`compat/tetra/mixin/ModularItemTetraEnchantMixin`（Tetra 的 `ModularItem#canApplyAtEnchantingTable` HEAD，`remap=false`；这个方法由 Forge 添加、名字不带 SRG，所以能安全注入，其它 SRG override 仍不要碰）。普通 Tetra 工具/未装 Tetra 不受影响。
- 突进的**运行时**门禁在 `SpearItem.isLungeApplicable(ItemStack)`（内部走 `SpearLikeSupport.of`），不要写回 `instanceof SpearItem`，否则模块化长矛能附上突进但触发不了冲刺。测试：`lungeAppliesToSpearLikeStacks` / `tetraWhitelistMatchesVanillaSpearWhitelist` / `lungeGateMatchesSpearLikeStacks` / `weaponProxyDetectsSwordCompatibleEnchantments` / `minecraftNamespaceEnchantsStayOnWhitelist` / `enchantmentProbeIsHiddenAndNotCraftable`。
- 玩家持矛的伤害类型固定用 `minecraft:player_attack`（`SpearDamageTypes` 里用 `PlayerSpearDamageSource` 保留 `death.attack.spear*` 文案），怪物持矛仍用 `spearcore:spear`；不要让长矛重新变成完全自定义的类型，否则 Slayer 等按原版玩家攻击判断的模组会整段跳过。测试：`playerSpearDamageUsesPlayerAttackType`。
- 命中后的原版附魔效果必须统一走 `SpearCombat.applyOnHitEnchantments`（火焰附加 + 攻击者侧 `doPostDamageEffects` 的 `doPostAttack` + 目标护甲侧 `doPostHurtEffects` 的 `doPostHurt`），两边 helper 的遍历对象不同、不能只调一个；不要在两条命中路径里各写一份。测试：`fireAspectIgnitesStabTarget` / `baneSlowsArthropodTarget`。
- 同一 tick 的「攻击前先打一段」类模组会留下受伤免疫；`SpearCombat.clearSameAttackerHurtImmunity` 只在 `invulnerableTime > 0 && getLastHurtByMob() == attacker` 时清一次，只服务长矛本体那一次 `hurt`，不要扩大成无条件清零。测试：`sameAttackerHurtImmunityIsCleared`。
- 模组附魔探针缓存用 `ConcurrentHashMap`，探针物品未注册时不缓存；`EnchantmentTetraCompatMixin` 必须先确认 Tetra 长矛栈再问附魔，避免非长矛物品查询也触发探针逻辑。
- 依赖：`compileOnly 'curse.maven:tetra-289712:8570756'`（Tetra 1.20.1-6.17.0）；未安装 Tetra 或 `tetraCompat=false` 时不得加载任何 Tetra 类。
- 测试：GameTest 服务端**不加载 Tetra**，所以只能覆盖纯函数与数据文件——`modularSpearArchetypeKeyMatching`、`modularSpearArchetypeDataIsValid`（archetype JSON 结构 + 槽位 `x`/`y` 坐标）、`modularSpearModulesAreDataDefined`（八个部件模块——含镶座与三件卷轴部件——的槽位/变体前缀/材料前缀）、`modularSpearPartSchematicsAreDataDefined`（八个换部件配方的槽位与 moduleKey）、`modularSpearScrollsUnlockTheirSchematics`（卷轴 NBT 结构、ribbon 的 16 进制、卷轴 schematic 与 locked key 一致、只有三尖两刃刀是 intricate）、`modularSpearHoneSchematicsAreDataDefined`（十条打磨配方 + 矛头模块确实接受那两个改进 key）、`modularSpearPartKeysTranslated`（中英两份的槽位/模块/配方 I18n key）、`spearReplacementsCoverAllSpears`（七条替换规则与材质映射）、`nonModularItemsAreNotClassified`（普通剑/三叉戟不误判）、`tetraMaterialKeySuffixMatches`（`TetraMaterialKey` 的 `/` 边界匹配，`ironwood` 不能被 `iron` 误匹配），以及原有的公式类用例（`fitDurationClampsAndGrowsWithSpeed` 等）。改动后必须手工在装了 Tetra 的实例里过一遍：工作台转换、换部件、蓄力/戳刺、附魔台、突进。

### 第三方兼容缝（AvaritiaSpear 1.4.0 等）

- AvaritiaSpear 1.4.0 是**上游作者**按上游 Spear Core 写的联动模组，按**字面描述符** mixin 上游的成员（`remap=false`）；本仓库为兼容保留了同样的签名，下面的名字 / 参数类型是稳定 ABI，**只能新增重载，不能删改**：
  - `SpearItem.jerotesLungeForwardMaybe(LivingEntity)`
  - `SpearItem.rushAttack(LivingEntity, float)`
  - `SpearItem.getHitEntitiesAlong(LivingEntity, SpearItem, float, Predicate<Entity>)`（旧的 `SpearItem` 参数版本；`SpearLike` 重载是给 Tetra 用的，两者必须共用同一实现）
  - `SpearAttackHandler.onPlayerAttack(AttackEntityEvent)`
  - `SpearSounds.ITEM_SPEAR_ATTACK/HIT/USE`、`ITEM_SPEAR_LUNGE_1/2/3`
  - `SpearCondition(int, float, float)`
- `gametest/CompatibilityGameTests` 用反射钉住上述签名；改这些类之前先跑它。
- 早期 3.3.x 曾把 `getHitEntitiesAlong` 的第二个参数从 `SpearItem` 改成 `SpearLike`，AvaritiaSpear 启动直接 `InvalidInjectionException` —— 这是必须避免的回归。
- `CompatPackResources.listResources` 是贴图集（TextureAtlas）唯一认的入口，别删；`FilePackResources` 在空 path 会拼出 `assets/<ns>//`，所以空 path 要用 `textures` 兜底。
- 本体必须保留 `src/main/resources/pack.mcmeta`（`pack_format: 15`），否则启动会弹 ResourcePackInfo 警告。
- 开关是 `SpearConfig.resourcepackCompat`（默认 `true`），由 `resourcepack/ResourcepackCompat.enabled()` 读取（配置未加载时回落 `true`）。该功能原先独立仓库 `Spear-Core-Resourcepack-Compat`，已并入本体，别在业务代码里再引 `spearrpcompat`。

## 7. 自检清单（每次提交前）

1. `./gradlew compileJava` 通过。
2. `./gradlew runGameTestServer` 日志出现 `All N required tests passed :)`。
3. 改了行为 → 有对应 GameTest；改了配置项 → `README.md` 与 `README.en.md` + `kjs-guide.md` 同步；改了资源包兼容层 → `ResourcepackCompatGameTests` 必须通过（meta 补丁、贴图识别、路径重映射、真实包端到端）；改了 Tetra 兼容层 → `TetraCompatGameTests` 必须通过（公式单调性/放缓/边界、archetype 键匹配、archetype / 部件模块 / 换部件配方 / 打磨配方 / 替换数据文件的结构、非长矛物品不误判），并手工在装了 Tetra 的实例里验一遍工作台转换。
4. `git status` 只含预期文件，没有构建产物、IDE 文件、编辑器备份。
5. 提交信息是中文、说清了动机。
6. 涉及原版数值（伤害、阈值、概率、时长）时，注明来源（Minecraft Wiki / 原版 jar 反编译），不要凭印象编。

## 8. 不要做的事

- 不要改 `main` 分支、不要动上游历史、不要 force push。
- 不要在业务代码里读 `SpearConfig`（走 `SpearRules`），不要把版本差异写死在多个地方。
- 不要为了「看起来兼容」往 `#minecraft:trident` 之类语义不符的 tag 里塞长矛；要加新归类先在本文件里说明理由。
- 不要删除或弱化 GameTest 来让构建变绿。
- 不要为了省事跳过 `reobfJar` / 关闭测试 / 注释掉报错代码。
- 不要提交密钥、token、本机绝对路径。