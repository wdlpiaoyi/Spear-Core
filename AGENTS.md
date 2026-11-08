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

- `config/SpearConfig.java`：Forge `COMMON` 配置。主开关（`enableVanillaSpears`、`enableCopperSpear`、`punchyStageBridge`、`resourcepackCompat`、`tetraExtraReachScale`、`tetraDamageScale`；Tetra 兼容强制开启，3.4.0 起删除了 `tetraCompat`）+ `[behavior]` 段的 17 个行为开关。
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
- **判定唯一入口**：`compat/tetra/TetraSpearProfile.isModularSpearArchetype(String)`（只看纯 path，命名空间恒为 `tetra`）。`TetraSpearProfile.of` 只先做 `ModList.isLoaded("tetra")` 守卫（3.4.0 起强制开启、不再有 `tetraCompat`），任何异常只记一次日志并当作「不是长矛」；`TetraSpearBridge` 里先 `stack.getItem() instanceof DynamicModularItem`，再取 `DynamicModularItem.getArchetypeKey(stack)` 比对。不要再按模块 key（`single/spearhead` / `single/long_handle`）判定。
- **数据文件必须放 `data/tetra/`**：Tetra 的 DataStore 只收 `tetra` 命名空间（可以用 `spearcore` 的包，但目录必须是 `data/tetra/archetypes/`、`data/tetra/replacements/`）。
  - `data/tetra/archetypes/modular_spear.json`：五个槽位 `spear/head`（major + required）、`spear/handle`（major + required）、`spear/binding`、`spear/butt`（变体带 `generic.attack_damage 0.5`，略微提升攻击力）、`spear/fuller`（强化附层，minor + 可选，**左上交错 (-12,-1)**）；字段名是 record 组件名的 camelCase（`honeBase` / `honeIntegrityMultiplier`），漏字段会静默取默认值。方位对应设计稿 ↗矛头、↘矛杆、↖绑定/涂层、↙枪尾（配重）。**镶座（socket）与绑定共用 `spear/binding` 槽**：Tetra 剑本来就是把 socket 装在 guard 槽、替换掉护手，本模组照这个思路让镶嵌宝石替换绑定，不额外加槽位。
  - 部件模块是本模组自己的数据：`data/tetra/modules/spear/{head,handle,binding,butt,socket,fuller,shuo_head,composite_shaft,three_point_blade}.json`（9 个，含强化附层 `fuller`（数据抄 Tetra 剑的 `sword/reinforced_fuller`）：材料 metal/wood/bone/misc-forged_beam（与 Tetra 剑的 `sword/reinforced_fuller` 完全一致；第三方 fuller 材料不写进材料表，由运行期导入的专用 outcome 处理），攻速 -0.1、耐久 +100、`durabilityMultiplier` 1.1、extract 耐久 0.25/完整度 0.5；普通 `handle` 的 `extract.integrity` 为 **1.3**；模块 key 即路径 `spear/head` 等），变体前缀分别是 `spear_head/`、`spear_handle/`、`spear_binding/`、`spear_butt/`、`spear_socket/`、`spear_shuo_head/`、`spear_fuller/`、`spear_composite_shaft/`、`spear_three_point/`（三尖两刃刀的模块 key 是 `spear/three_point_blade`、变体前缀却是 `spear_three_point/`，两处不同名是刻意的，测试按 part→prefix 表校验）。积竹木柲是**单模块 + 三级原地改进**：只有 `composite_shaft`（只吃 `tetra:wood/`，1/3 **攻速 -1.5、攻击力 -0.3、射程 0**，平铺耐久 6，`integrity` 固定 2.5），后续加竹/缠藤/完成都用 `data/tetra/improvements/spear/handle/stage.json` 的 `spear_composite/stage`（逐级补回，完成版相对普通杆净 +0.4 攻速 / +1.0 伤害 / +1.0 射程，无负面），不改模块。**1/3 必须比普通杆（-1.1 / 0 / +0.5）更慢、伤害更低、射程更短**；负值可以写，只要总攻速不会被原版属性下限夹到 0。材料集：普通矛头 wood/stone/metal/gem/bone；**槊头只吃 metal**；**三尖两刃刀全五类**；积竹木柲只吃木板。数值：三尖两刃刀攻速比普通头 +0.25（`attributes.attack_speed` 更小负数）、伤害 2.8、耐久/完整度不低于普通头；槊头伤害 2.1、攻速比普通头更快（-0.45 vs -0.9）。三个矛头模块的 `improvements` 都带 `tetra:spear/head/` 与 `tetra:sword/`、`tetra:single/`、`tetra:double/`（后者用于通用门禁/第三方 crafting effect，见下）。`spear/socket` 只吃 `tetra:socket/*` 材料，extract 抄 Tetra 的 `sword/socket`；socket 材料自带的 `attributes` / `effects` 会随变体直接合并进长矛。**变体前缀不能复用 Tetra 的 `spearhead/` / `basic_blade/`**：Tetra 的变体 key 在模块间共享命名空间，撞名会和 Tetra 自带模块互相覆盖。`models` 直接引用 Tetra 自带的模块模型路径（贴图在 Tetra 包里，本模组不重复 ship）。
  - 换部件/阶段配方在 `data/tetra/schematics/spear/{head,handle,binding,butt,socket}.json`（socket 的 `slots` 同样是 `spear/binding`，和绑定互斥）外加卷轴解锁与阶段链：`shuo_head.json`、`three_point_blade.json`、`composite_shaft_1.json`（木板 + `axe_dig` 3 级，locked key `tetra:spear/composite_shaft_1`，创建 `spear/composite_shaft` 模块）、`composite_shaft_2.json`（`displayType: improvement`，要求模块 + 没有 stage 改进，竹子 → stage 1，切具 `cut`）、`composite_shaft_3.json`（stage 1 → stage 2，藤 → stage 2，切具 `cut`）、`composite_shaft_finish.json`（stage 2 + `spear_hone/stable` ≥5 → stage 3，不写 moduleKey/moduleVariant）。工作台只显示 `slots` 命中槽位的 schematic；`outcomes` 的 `moduleKey` 写 `spear/head` 这种模块 key，`moduleVariant` 写 `spear_head/` 这类前缀，固定材料用 `material: {items, count}`。
  - 打磨（hone）：矛头 `data/tetra/schematics/spear/head/hone_{damage,durability,speed}_{1..5}.json`（15 条）、矛杆 `data/tetra/schematics/spear/handle/hone_{speed,durability}_{1..5}.json`（10 条）、积竹木柲 3/3 的 `handle/hone_stable_{1..5}.json`（5 条），共 30 条。改进 key：矛头复用 Tetra 的 `spearhead_hone/damage` / `head_hone/durability` / `head_hone/speed`；矛杆速度复用 `handle_hone/speed`；矛杆耐久与稳定是本模组自己的 `data/tetra/improvements/spear/{handle/hone_durability,handle/stable}.json`（`handle_hone/durability`、`spear_hone/stable`）。`localizationKey` 尽量复用 Tetra 的 `sword/shared_blade/hone_damage_N` / `double/shared_head/hone_durability_N` / `double/shared_head/hone_speed_N` / `single/handle/hone_speed_N`。**requirement 不要写 `tetra:module`**（除稳定线要限定 3/3 矛杆），`ModuleRequirement` 比对模块 key 而不是槽位，否则换矛头后整条线消失；1 级 `tetra:not`，2~5 级 `level: N-1`。
  - 尖锐：`schematics/spear/head/hone_sharp.json`（hone=true），requirement 是 `spearhead_hone/damage` / `head_hone/durability` / `head_hone/speed` 任一 `level.min=5`；改进 `spearhead_hone/sharp` 由 `data/tetra/improvements/spear/head/sharp.json` 提供，Java 侧 `TetraSpearBridge` 读到它就给蓄力系数 ×1.05（`TetraSpearStats.fit` 的新重载）。
  - 升级（improvement）：锯齿 / 回火沿用 Tetra 的 `spearhead/serrated` / `spearhead/tempered` 改进，但 **不再复用 `sword/shared_blade/...` 的 localizationKey**（那份描述写「剑刃」）：6 份 schematic 各用自己的 `tetra/schematic/spear/head/{serrated,tempered}_N.{name,description,slot1}`，中英都写矛头文案。其 requirement 同样不按模块 key 过滤；与神灵金锭的通用门禁（见下）都依赖模块接受 `tetra:sword/` 等目录。
  - 翻译走 Tetra 的 I18n key，写在本模组 `assets/spearcore/lang/*.json`（MC 会合并所有命名空间的 lang）：槽位 `tetra.slot.spear/head`、模块 `tetra.module.spear/head.name`、配方 `tetra/schematic/spear/head.name`（**注意配方是斜杠前缀，不是点分**）。卷轴解锁的部件还要多两组：locked 需求的提示 `tetra.unlock.<ns>.<path>.description`（例 `tetra.unlock.tetra.spear/shuo_head.description`）与卷轴显示名 `item.tetra.scroll.spear/<part>.{prefix,name}`（`prefix` 照 Tetra 的原版卷轴写「原理图」/「Schematic」，有翻译时显示成「原理图：槊头」）。
  - **卷轴（解锁 locked 配方）**：`compat/tetra/TetraSpearScrolls` 定义三条卷轴（`SHUO_HEAD` ribbon `b58a4a`、`COMPOSITE_SHAFT` ribbon `8fa24a` 且 schematics 指向 `tetra:spear/composite_shaft_1`、`THREE_POINT_BLADE` ribbon `7a6fd0` 且 intricate）。卷轴本体是 `tetra:scroll_rolled` + `BlockEntityTag.data[0]`（`key`/`schematics`/`intricate`/`material`/`ribbon`/`glyphs`；不写 `details`；`ribbon` 必须 16 进制字符串）。**获取方式**：`TetraSpearTrades` 在 `VillagerTradesEvent` 里给工具匠 master 加积竹木柲卷轴（16 绿宝石）、武器匠 master 加槊头卷轴（24 绿宝石）；`TetraSpearScrollsTab` 另外把三条卷轴放进 Tetra 创造栏作为验收入口（不是唯一来源）；1/3 配方的 locked 需求是 `tetra:or(新 key, 旧 key tetra:spear/composite_shaft)`，兼容旧版发出去的卷轴。`TetraSpearLoot` 在 `LootTableLoadEvent` 给 `minecraft:chests/{nether_bridge,bastion_treasure,stronghold_library,end_city_treasure}` 各加 4% 池产出三尖两刃刀卷轴（`SpearScrollLootFunction` 压 NBT）。解锁链路不变：`ScrollBlock` → `CraftingContext.unlocks` → `tetra:locked`；intricate 卷轴必须正对工作台上一格。
  - 槽位的 `x` / `y` 是 Tetra `GuiModuleOffsets` 的像素偏移（`DynamicModularItem.getMajorGuiOffsets` 原样喂给 `GuiModuleOffsets`），数值要照 Tetra 默认布局量级：2 major 用 `(4,0)` / `(4,18)`，3 minor 照 Tetra 剑的默认交错布局 `(-12,-1)` / `(-21,12)` / `(-12,25)`（顺序：附层、绑定、枪纂）。写成 `0/40/80` 这种大数会让工作台里的模块框散开（`modularSpearArchetypeDataIsValid` 会校验这两组坐标）。
  - `data/tetra/replacements/spearcore_spears.json`：七条，`item=tetra:dynamic_handheld`、`archetype=modular_spear`、`modules` 写四个必需槽位的 `[模块 key, 变体 key]`（`spear/fuller` 可选，初始留空）。模块 key 会被 Tetra 强制加 `tetra:` 命名空间（本模组的数据放在 `data/tetra/` 下，所以 key 照样是 `spear/head` 这类纯 path）；变体材质：矛头/枪尾用 oak / stone / copper / iron / gold / diamond / netherite，矛杆固定 `spear_handle/stick`（`rod/stick` 材质），绑定固定 `spear_binding/string`（`fibre/string` 材质）。
- **第三方 mod 的 Tetra 联动分两类，处理方式完全不同**：
  - **材料联动（`data/tetra/materials/<类别>/<key>.json`，白送）**：模组只是往 Tetra 的材料库加材料，材料落在我们模块 `materials` 前缀里就能直接当矛部件用；材质自带的 `improvements` 也会授予（例如 malum 的 `soul_stained_steel` 自带 `malum.soul_strike`），前提是模块 `improvements` 接受它所在的目录——矛头/矛杆接受 `tetra:shared/`，第三方的共享改进几乎都放在 `data/tetra/improvements/shared/`，所以能拿到。前缀覆盖面和 Tetra 自带的 spearhead / long_handle / binding **完全一致**（矛头 wood/stone/metal/gem/bone、矛杆 wood/metal/bone/rod、绑定 fibre/skin/**fabric**，只多一个 `fabric/`）。
  - 实测基线是 Prism 实例 `Niubiosis`（568 个模组，Forge 47.4.28，2026-11；**只做 jar 数据静态扫描，不要拿它当测试实例，启动要半天**，功能验证一律在 `1.20.1-forge` 实例里跑）：`tetra 6.17.0` 本体 1413 条 `data/tetra`，其余 `art_of_forging 1.8.5` 427 条（metal/diabolium·endsteel_ingot·forged_steel_ingot·vobrivium_ingot、fibre/life_fiber·warped_muscle、misc/echo_shard·eerie_shard、reagent/*、socket/*）、`tetracelium 1.3.2` 168 条（metal/bronze·electrum·elementium·fiery_ingot·infused_iron·ironwood·knightmetal·lead·manasteel·nickel·osmium·silver·sky·steel·steeleaf·terrasteel·tin、wood/ancient_wood·baobab·dreamwood·livingwood·maple·treated_wood、gem/ruby·sapphire·agate·citrine·certus_quartz、bone/dragon_bone·wither_bone、fibre/hemp·mana_string·straw、fabric/canvas）、`irons_spellbooks 3.16.3` 25 条、`malum 1.6.7` 19 条、`rainbowcompound 1.3.5` 17 条（socket/creat_netherstarite）、`Gobber2 2.8.9` 7 条（metal/compat/gobber*）、`goetyawaken 1.3.9` 4 条（只有 `item_effects/`，condition 是 `tetra:fixed value:false`，等于没开）。该实例还装了 `AvaritiaSpear 1.5.0`，所以那边的 ABI 兼容同样要守住。
  - `socket/*` 材料由本模组的 `spear/socket` 模块承接（装在 `spear/binding` 槽，装了就替换掉绑定）：材料自带的 `attributes` 会随变体直接合并进长矛，所以 art_of_forging / irons_spellbooks / rainbowcompound 的 socket 材料（`blood_rune_socket`、`fang_charm`、`creat_netherstarite`）现在都能装。**perk 类效果（`perk_socket_N` 才有的那些）仍然拿不到**：Tetra 的 perk 需要槽位带 `perk_socket_N` 前缀 + schematic 的 `requirement` 判 `tetra:perk`，本模组只做了基础镶座，没有 perk 槽，需要时另外实现。
  - 三种 Tetra 数据机制已接上（3.4.0）：`item_effects/` 由 `SpearCombat` 在起手/命中调 `DataEffectsHandler.applyOnUseEffects/applyOnHitEffects`；`synergies/` 由 `ModularItemSynergyMixin` 对矛返回 `SynergyStore.getOrdered("spear/")`（数据放 `data/tetra/synergies/spear/`）；`crafting_effects/` 由通用模块门禁映射（见下）让第三方数据按槽位角色命中矛。
  - **配方 / 模块联动**：对方数据的 gate 写死 `sword/blade`、`sword/hilt` 这类模块 key；`ModuleRequirementSpearMixin` 会按槽位角色把它映射到矛（见「通用模块门禁映射」）。如果对方是 schematic（有 `slots`）而不是 crafting effect，仍需在本模组槽位补一份自己的 schematic（数据包补层），并让模块 `improvements` 接受对方目录（head/handle 已含 `tetra:sword/`、`tetra:single/`、`tetra:double/`）。
  - **工作台维修**：Tetra 的 Repair 动作要两样东西同时成立：(1) 物品类初始化时注册了 `RepairSchematic`（`ModularBladedItem` 等在 `commonInit` 里做；`DynamicModularItem` 没有做）；(2) 当前模块变体能在 `RepairRegistry` 里查到 `RepairDefinition`（`data/tetra/repairs/` 按**精确变体 key** 匹配，或 `repair:true` schematic 注入；Tetra 只给 bolt/forged_beam/sockets 等特殊模块配了数据，普通剑刃/手柄没有）。本模组两样都补：`TetraSpearRepairSetup` 在 `FMLCommonSetupEvent` 为 `tetra:dynamic_handheld` 注册 `RepairSchematic(..., "modular_spear")`；`ItemModuleRepairMixin`（`ItemModule#getRepairDefinitions` HEAD，remap=false）对 `spear/` 模块按当前变体的材料现算 RepairDefinition（材料 = 该材料自己的物品），任意材料（含第三方）都能修。
  - 另外核过：Goety 2.5.60.6 的 jar 里 `data/tetra` 为空，**它家没有 Tetra 联动**，所谓「unholy blood 涂层」不是 Tetra 的东西。
- **不要 mixin Tetra 的 `ItemModularHandheld`**：发行版 jar 里它的 override 是 SRG 名，Mixin 的 refmap 只覆盖 MC 类，映射不到模组类的 override；改为在 `ItemStack`（MC 类）上拦截 `use` / `getUseDuration` / `getUseAnimation` / `onUseTick` / `releaseUsing` / `finishUsingItem`。Tetra 的 `use()` 只有在它自己算出 `getUseDuration > 0` 时才会起手，所以 `use` 必须由本模组接管。另在 `Item` 上补 `canAttackBlock`（创造模式按原版剑返回 false），WieldYourPower 的强制破坏也看这个判定。配置放 `spearcore.tetra.mixins.json`（`required=false`，`TetraMixinPlugin`）。
- 行为逻辑统一在 `item/SpearLike` + `init/SpearCombat` + `init/SpearLikeSupport`，不要再写 `instanceof SpearItem`；AI goals / 怪物发矛 / 附魔分类保持 SpearItem-only（Tetra 怪没有 AI）。
- 数值：`compat/tetra/TetraSpearStats.fit` 从 Tetra 属性拟合，不复刻本模组材质预设，高数值区间必须放缓（`soft` 值）；改动公式要同步 `TetraCompatGameTests`。
- Tetra 自己贡献的额外攻击距离（`forge:entity_reach`）优先取 `IModularItem.getAttributeValue(stack, ForgeMod.ENTITY_REACH.get())`，取不到再退回遍历 `ItemStack` 的修饰符（`ADDITION` + `MULTIPLY_BASE` 折算）；只按配置 `tetraExtraReachScale`（0~1，默认 1.0，读取失败回落 1.0）计入射程，其他来源的 reach 不缩放。若来源完全统计不到，`SpearCombat.effectiveMaxRangeWithFallback` 会把全部额外距离当作 Tetra 来源，避免配置静默失效。纯函数在 `SpearCombat.weakenExtraReach`，测试是 `tetraExtraReachIsWeakened` / `tetraReachFallbackAppliesWhenBonusUnknown`。
- 模块化长矛固定 Java 语义：`usesJabCooldown()` 返回 false、`jabToChargeDelayTicks()` 返回 0，不受 `jabCooldown` / `jabToChargeDelay` 配置影响；文档要写明。
- Tetra 物品自身的 `ATTACK_DAMAGE`（`ADDITION` 部分）在 `ItemStackTetraMixin.getAttributeModifiers` 的 RETURN 处按 `SpearConfig.tetraDamageScale`（0~1，默认 1.0，读取失败回落 1.0）统一打折，实现在 `compat/tetra/TetraDamageScaling`；只缩放 `ADDITION`，`MULTIPLY_BASE`/`MULTIPLY_TOTAL`、玩家基础攻击力与其他来源都不动。测试是 `tetraAttackDamageIsScaled`。
- **属性查询的重入闸门别删**：`ItemStackTetraMixin.spearcore$tetraAttackDamage` 注入在 `ItemStack#getAttributeModifiers(EquipmentSlot)` 的 RETURN，而 `TetraSpearBridge` 取不到 `ENTITY_REACH` 时会回退去扫同一个方法的修饰符；`spearcore$resolvingAttributes` 这个 `ThreadLocal` 就是防自递归的。删掉会无限递归到 `StackOverflowError`，被兼容层 `catch (Throwable)` 吞掉后模块化长矛直接失去蓄力/戳刺全部行为。
- 客户端：`compat/tetra/mixin/ItemRenderPropertiesTetraMixin` 在 `Item#getRenderPropertiesInternal`（HEAD，`remap=false`）对 `DynamicModularItem` 惰性挂 `SpearClientExtensions`（渲染时 `ModList` 必已就绪）；**不要**改回 `Item#initializeClient`（物品构造期 `ModList` 可能还没就绪，会静默挂不上）。蓄力动画直接复用 `SpearAnimations.firstPersonUse` / `thirdPersonUseItem`；`getUseAnimation` 固定 `NONE`，第三人称蓄力手臂由 `HumanoidModelMixin` 调 `SpearAnimations.thirdPersonHandUse`。
- **外观用本模组自己的贴图模型，不交给 Tetra 拼**：`client/TetraSpearModel` 注册 `spearcore:tetra_spear` / `spearcore:tetra_spear_shuo` / `spearcore:tetra_spear_three` 三套模型（`forge:separate_transforms`：base = 32×32 手持贴图，gui/fixed/ground = 16×16 图标），`compat/tetra/mixin/ItemRendererTetraModelMixin`（`ItemRenderer#getModel` HEAD，`require=0`）按矛头槽变体 key 选模型。三套只换头部那一层，杆贴图共用 `tetra_spear_handle*`；头部贴图盖住杆，所以换头不需要重画杆；杆部件（积竹木柲）不提供外观，只影响 tint 0。tint 由 `client/TetraColorRegistration` / `TetraSpearColorsEvent` 注册：tint 0 = 杆、tint 1 = 矛头。**全息球**：`assets/tetra/holosphere_entries/modular_spear.json`（item `tetra:dynamic_handheld`、archetype `modular_spear`、icon 指 `spearcore:textures/item/tetra_spear.png`）让「适用于此原理图的材料」按钮能跳到模块页而不是首页。隐藏探针剑不再自带贴图（model 指向 `minecraft:item/barrier`）。材质色缓存会在客户端资源重载时清空；启动后第一次取色用材料 `iron` 自检反射读取，失败直接 warn。
- **物品名「材料 + 矛 / 槊 / 刃」**：`ItemStackTetraMixin.spearcore$modularSpearName` 注入 `ItemStack#getHoverName` HEAD，交给 `compat/tetra/TetraSpearNaming`：`TetraSpearHead` 按矛头变体前缀选 key（`item.spearcore.modular_spear.named` = `%s矛`、`named_shuo` = `%s槊`、`named_blade` = `%s刃`），材料名用 Tetra 的 `tetra.material.<材料>.prefix`，缺翻译回退 `tetra.material.<材料>`。铁砧改过名不覆盖；材料读不到回退裸 `item.spearcore.modular_spear`（矛 / Spear）。**不要** mixin Tetra 的 `ModularItem#getName`（SRG 名），也不要直接调 `IModularItem#getItemName`（客户端 I18n 在服务器会抛错）。
- **工作台上必须平躺**：Tetra 的 `WorkbenchTESR` 用 `BakedModel#isGui3d()` 选姿态——`true` 只平移 `(0.5, 1.125, 0.5)` 不旋转（平面贴图会竖着悬在工作台上方），`false` 才会绕 X 轴转 90° 平放。`forge:separate_transforms` 的几何上下文默认 `gui3d = true`（Forge `BlockGeometryBakingContext` 的字段初值，JSON 里没有能改它的键），所以 `client/TetraSpearModel` 烘焙后套一层 `BakedModelWrapper` 把 `isGui3d()` 固定成 `false`，**别删**。改成 3D 模型（`isGui3d = true`）之前先想清楚工作台姿态。
- 指向方块 / 挥空：`SpearCooldownAttackMixin` 在非 ENTITY 分支对 MODULAR_3D 补 `player.swing` + 客户端 `playLocalSound(ITEM_SPEAR_ATTACK)`；创造模式 `canAttackBlock=false` 会让原版 startAttack 空转，所以不能指望原版音效。
- 附魔：放行判定统一走 `SpearEnchantments.supportsSpearEnchantment(enchantment)` = 原版 8 项白名单 + 「模组添加、且能附在本模组隐藏探针剑 `spearcore:enchantment_probe` 上」的武器附魔。探针剑 `item/EnchantmentProbeItem` 继承 `SwordItem`，**不可获取**：不进创造栏、无配方、无掉落，只挂 `#minecraft:swords` 让人按标签判断武器的模组也认得它；不要给它加配方/战利品/本模组 tag。
- 放行点三处：`SpearItem#canApplyAtEnchantingTable`（内建矛，`supportsSpearEnchantment || super`）；`mixin/EnchantmentTetraCompatMixin`（`Enchantment#canApplyAtEnchantingTable` HEAD，`MODULAR_3D` + `supportsSpearEnchantment`）；`compat/tetra/mixin/ModularItemTetraEnchantMixin`（Tetra 的 `ModularItem#canApplyAtEnchantingTable` HEAD，`remap=false`；这个方法由 Forge 添加、名字不带 SRG，所以能安全注入，其它 SRG override 仍不要碰）；同一 mixin 里的 `isBookEnchantable` HEAD 放行——Tetra 的 `ModularItem#isBookEnchantable` 硬编码 `false`，不放行模块化长矛就永远吃不了附魔书（书上的附魔必须**全部**通过 `supportsSpearEnchantment`，否则保持 Tetra 原行为）。普通 Tetra 工具/未装 Tetra 不受影响。
- 突进的**运行时**门禁在 `SpearItem.isLungeApplicable(ItemStack)`（内部走 `SpearLikeSupport.of`），不要写回 `instanceof SpearItem`，否则模块化长矛能附上突进但触发不了冲刺。测试：`lungeAppliesToSpearLikeStacks` / `tetraWhitelistMatchesVanillaSpearWhitelist` / `lungeGateMatchesSpearLikeStacks` / `weaponProxyDetectsSwordCompatibleEnchantments` / `minecraftNamespaceEnchantsStayOnWhitelist` / `enchantmentProbeIsHiddenAndNotCraftable`。
- 玩家持矛的伤害类型固定用 `minecraft:player_attack`（`SpearDamageTypes` 里用 `PlayerSpearDamageSource` 保留 `death.attack.spear*` 文案），怪物持矛仍用 `spearcore:spear`；不要让长矛重新变成完全自定义的类型，否则 Slayer 等按原版玩家攻击判断的模组会整段跳过。测试：`playerSpearDamageUsesPlayerAttackType`。
- 命中后的原版附魔效果必须统一走 `SpearCombat.applyOnHitEnchantments`（火焰附加 + 攻击者侧 `doPostDamageEffects` 的 `doPostAttack` + 目标护甲侧 `doPostHurtEffects` 的 `doPostHurt`），两边 helper 的遍历对象不同、不能只调一个；不要在两条命中路径里各写一份。测试：`fireAspectIgnitesStabTarget` / `baneSlowsArthropodTarget`。
- 同一 tick 的「攻击前先打一段」类模组会留下受伤免疫；`SpearCombat.clearSameAttackerHurtImmunity` 只在 `invulnerableTime > 0 && getLastHurtByMob() == attacker` 时清一次，只服务长矛本体那一次 `hurt`，不要扩大成无条件清零。测试：`sameAttackerHurtImmunityIsCleared`。
- 模组附魔探针缓存用 `ConcurrentHashMap`，探针物品未注册时不缓存；`EnchantmentTetraCompatMixin` 必须先确认 Tetra 长矛栈再问附魔，避免非长矛物品查询也触发探针逻辑。
- 依赖：`compileOnly 'curse.maven:tetra-289712:8570756'`（Tetra 1.20.1-6.17.0）；未安装 Tetra 时不得加载任何 Tetra 类（兼容强制开启，不再有 `tetraCompat`）。
- 测试：GameTest 服务端**不加载 Tetra**，所以只能覆盖纯函数与数据文件——`modularSpearArchetypeKeyMatching`、`modularSpearArchetypeDataIsValid`（5 槽 + 强化附层坐标）、`modularSpearModulesAreDataDefined`（9 个模块：含镶座、强化附层、单模块积竹木柲与三件卷轴部件；普通矛杆 integrity 1.3）、`modularSpearPartSchematicsAreDataDefined`（换部件/阶段配方的槽位、moduleKey、materials 一致）、`modularSpearScrollsUnlockTheirSchematics`（卷轴 NBT / locked key / intricate）、`modularSpearHoneSchematicsAreDataDefined`（30 条 hone + 尖锐）、`modularSpearHeadUpgradesAreDataDefined`（锯齿/回火、不再复用剑刃 localizationKey）、`modularSpearHeadTexturesAreWired`（三套模型接线 + 头部贴图存在）、`modularSpearHeadBalanceIsDataDefined`（三尖两刃刀全面强于普通头、槊金属限定）、`modularSpearCompositeStagesAreDataDefined`（单模块 + stage 1..3 原地改进 / 稳定 / 完成；1/3 降攻速/伤害/射程，工具用 `cut`）、`modularSpearHolosphereEntryExists`、`modularSpearFullerIsDataDefined`（材料与 Tetra 剑完全一致，第三方材料靠运行期导入）、`spearHandleIntegrityRaised`、`spearButtAddsAttackDamage`、`spearScrollsTabIsRegistered`、`tetraTuningDefaultsAreFull`、`spearHeadSlashingFlags`、`tetraModuleGateMappingMatchesSpearSlots`、`tetraCraftingEffectModuleMappingMatchesSpearSlots`、`spearScrollLootNbtIsComplete`、`modularSpearPartKeysTranslated`、`spearReplacementsCoverAllSpears`、`nonModularItemsAreNotClassified`、`tetraMaterialKeySuffixMatches`，以及公式类用例。改动后仍必须手工进游戏过一遍（工作台转换、换部件/砍/蓄力、打磨/升级、卷轴、附魔书、维修、全息球）。
- **挥砍 = 原版剑左键**：`SpearLike#slashing()` 由 `TetraSpearHead` 判定（先模块 key、再变体前缀）。客户端 `SpearCooldownAttackMixin` 对 slashing 栈**直接 return**，不取消、不发自定义包，完全交给原版 `startAttack`；`SpearClientExtensions` / `ItemInHandLayerMixin` 对 slashing 的挥手动画也不再套长矛前刺，交给原版物品动画。服务端 `SpearAttackHandler` 的 slashing 分支**不取消** Forge `AttackEntityEvent`，也不补自定义横扫：横扫若按攻击力全额结算会绕开原版蓄力/冷却缩放（0 蓄力也能打出高伤），所以完全交给原版。原版横扫的开关是 `ItemStack#canPerformAction(ToolActions.SWORD_SWEEP)`（Forge 替换了 `instanceof SwordItem`），Tetra 的 `DynamicModularItem` 默认返回 false，所以 `ItemStackTetraMixin` 对 slashing 矛头放行这个 action，满蓄力时就会走原版横扫粒子/音效/伤害；普通矛头仍只戳刺。`SpearStabAttackPacket` 与 `performStabAttack` 对 slashing 栈直接拒绝，普通矛头仍走原穿刺。**不要再引入自定义挥砍包/动画**：曾经用 `SpearSlashAttackPacket` 取消原版 startAttack，实机表现为「没有伤害、动画也怪」。
- **重装动画**：模块化矛的模块耐久存在 NBT，命中后 `hurtEnemy`/`hurtAndBreak` 会让 Forge `ForgeHooksClient.shouldCauseReequipAnimation` 每次重播「掏出」动画。`ModularSpearReequipMixin`（client，`remap=false`）对同一个模块化矛栈返回 false；换物品/空手不受影响。
- **虚拟原版攻击（通用攻击钩子）**：蓄力命中后 `SpearCombat.virtualAttack` 置 ThreadLocal 再调 `player.attack(target)`；`SpearAttackHandler` 见到该标志只取消事件、不重复结算。这样任何挂在 `Player#attack` / Forge AttackEntityEvent 的 mod（fantasy_ending 的 Void Collapsing 等）都能看到蓄力命中。
- **通用模块门禁映射（两处，缺一不可）**：① `mixin/ModuleRequirementSpearMixin` 管 **schematic requirement**（`schematic.requirement.ModuleRequirement`）；② `compat/tetra/mixin/ModuleConditionSpearMixin` 管 **crafting effect**（`craftingeffect.condition.ModuleCondition`，RevelationFix 的神灵金锭走这个）。两者都用纯函数 `TetraModuleGateMapping` 把第三方写死的 `sword/*` / `single/*` / `double/*` 按角色映射到矛槽位（刃↔矛头、柄↔矛杆、护手↔绑定、配重↔枪纂），`anySlot` 用 `matchesAnySpearSlot`。只修 ① 的话神灵金锭的亡灵杀手10/破妄10 仍不生效（3.4.0 实机踩过）。
- **synergies / item_effects**：`ModularItemSynergyMixin` 对 `modular_spear` 栈返回 `SynergyStore.getOrdered("spear/")`（反射，mutil 非编译期依赖），数据放 `data/tetra/synergies/spear/`；`SpearCombat` 在起手/命中调 `DataEffectsHandler.applyOnUseEffects/applyOnHitEffects`，第三方 `item_effects` 开始对矛生效。`crafting_effects` 通过上面的通用门禁走第三方数据。
- **通用第三方 fuller 变体导入**：`SchematicRegistryTetraMixin` 在 Tetra 的 `setupSchematics` 返回后调用 `TetraFullerImportImpl`（这是唯一可靠的时机；`TagsUpdatedEvent` 早于 Tetra DataStore 加载，已实机踩坑）。导入扫描所有以 `/fuller` 结尾槽位的第三方 schematic，把它们的 module 变体和 outcome 复用进 `spear/fuller` 模块/配方；不认 modid、不写死材料，所以 RevelationFix 的 `goety/unholy_blood` 与 `fuller/shadow_essence_fuller` 这类「特效在模块变体里、不在材料里」的附层都能拿到完整 effects。`spear/fuller` 的基础材料表保持与 Tetra 剑一致（metal/wood/bone/forged_beam），不再用 `tetra:fuller/` 前缀兜底，避免通用 outcome 先命中一个没有特效的默认变体。日志会打印已导入数量。
- **第三方剑刃改进配方（longinus / aeglos 等）**：`ConfigSchematicSpearMixin` 只对「improvement-only、不写 moduleKey/moduleVariant、且来自非 tetra 命名空间」的配方按角色放行 `spear/head` 等槽位；`isRelevant` / `isApplicableForSlot` 两处都放行，保证工作台能列出并应用到矛头。会替换模块的剑刃配方仍然只作用于剑，避免把 `sword/basic_blade` 塞进矛头槽。
- **P4 结论**：镶座 perk 不实现——Tetra 的 `tetra:perk` 依赖 `mutil.Perks`（赞助者 support/contribute/community/moderate），且只在客户端判定；材料自带属性/效果已覆盖 socket 强化。

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