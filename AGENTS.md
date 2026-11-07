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
- 新增物品要同时补齐：注册、配方（`data/spearcore/recipes/`）、模型（`assets/.../models/item/`）、贴图、三种语言文件（`en_us` / `zh_cn` / `ja_jp`）。

### 资源包兼容层（1.21.9+ 长矛材质包）

- `resourcepack/` 下的类只在客户端加载（`Dist.CLIENT` 订阅者 + `AddPackFindersEvent`），专用服务器不得触碰资源栈。
- 只处理 `assets/minecraft/textures/item/*_spear(_in_hand).png`，**不要**顺手转换模型、语言文件或 `custom_model_data`。
- 一切补丁只在内存副本里，**绝不改写用户的资源包文件**；`pack_format` 只补成 15。

### Tetra 兼容层（自定义 archetype 的模块化长矛）

- **目标：不再影响 Tetra 自带的任何工具。** 本模组的模块化长矛 = `tetra:dynamic_handheld` + 物品栈 NBT 里的 `archetype=modular_spear`，由数据包 replacement 把七把贴图长矛转换得到（放进 Tetra 工作台即触发）。Tetra 自带的单头工具（矛头 + 长手柄）与普通模块工具一律按普通工具工作。
- **判定唯一入口**：`compat/tetra/TetraSpearProfile.isModularSpearArchetype(String)`（只看纯 path，命名空间恒为 `tetra`）。`TetraSpearProfile.of` 仍必须先 `ModList.isLoaded("tetra")` + `SpearConfig.TETRA_COMPAT` 守卫，任何异常只记一次日志并当作「不是长矛」；`TetraSpearBridge` 里先 `stack.getItem() instanceof DynamicModularItem`，再取 `DynamicModularItem.getArchetypeKey(stack)` 比对。不要再按模块 key（`single/spearhead` / `single/long_handle`）判定。
- **数据文件必须放 `data/tetra/`**：Tetra 的 DataStore 只收 `tetra` 命名空间（可以用 `spearcore` 的包，但目录必须是 `data/tetra/archetypes/`、`data/tetra/replacements/`）。
  - `data/tetra/archetypes/modular_spear.json`：四个槽位 `sword/blade`（major + required）、`sword/hilt`（major + required）、`sword/guard`、`sword/pommel`；字段名是 record 组件名的 camelCase（`honeBase` / `honeIntegrityMultiplier`），漏字段会静默取默认值。
  - `data/tetra/replacements/spearcore_spears.json`：七条，`item=tetra:dynamic_handheld`、`archetype=modular_spear`、`modules` 写四个槽位的 `[模块 key, 变体 key]`（`sword/basic_blade` + `basic_blade/oak` 之类）。模块 key 会被 Tetra 强制加 `tetra:` 命名空间，**只能用 Tetra 自带模块**；变体材质 key 是 oak / stone / copper / iron / gold / diamond / netherite。
- **不要 mixin Tetra 的 `ItemModularHandheld`**：发行版 jar 里它的 override 是 SRG 名，Mixin 的 refmap 只覆盖 MC 类，映射不到模组类的 override；改为在 `ItemStack`（MC 类）上拦截 `use` / `getUseDuration` / `getUseAnimation` / `onUseTick` / `releaseUsing` / `finishUsingItem`。Tetra 的 `use()` 只有在它自己算出 `getUseDuration > 0` 时才会起手，所以 `use` 必须由本模组接管。另在 `Item` 上补 `canAttackBlock`（创造模式按原版剑返回 false），WieldYourPower 的强制破坏也看这个判定。配置放 `spearcore.tetra.mixins.json`（`required=false`，`TetraMixinPlugin`）。
- 行为逻辑统一在 `item/SpearLike` + `init/SpearCombat` + `init/SpearLikeSupport`，不要再写 `instanceof SpearItem`；AI goals / 怪物发矛 / 附魔分类保持 SpearItem-only（Tetra 怪没有 AI）。
- 数值：`compat/tetra/TetraSpearStats.fit` 从 Tetra 属性拟合，不复刻本模组材质预设，高数值区间必须放缓（`soft` 值）；改动公式要同步 `TetraCompatGameTests`。
- Tetra 自己贡献的额外攻击距离（`forge:entity_reach`）优先取 `IModularItem.getAttributeValue(stack, ForgeMod.ENTITY_REACH.get())`，取不到再退回遍历 `ItemStack` 的修饰符（`ADDITION` + `MULTIPLY_BASE` 折算）；只按配置 `tetraExtraReachScale`（0~1，默认 0.3，读取失败回落 0.3）计入射程，其他来源的 reach 不缩放。若来源完全统计不到，`SpearCombat.effectiveMaxRangeWithFallback` 会把全部额外距离当作 Tetra 来源，避免配置静默失效。纯函数在 `SpearCombat.weakenExtraReach`，测试是 `tetraExtraReachIsWeakened` / `tetraReachFallbackAppliesWhenBonusUnknown`。
- 模块化长矛固定 Java 语义：`usesJabCooldown()` 返回 false、`jabToChargeDelayTicks()` 返回 0，不受 `jabCooldown` / `jabToChargeDelay` 配置影响；文档要写明。
- Tetra 物品自身的 `ATTACK_DAMAGE`（`ADDITION` 部分）在 `ItemStackTetraMixin.getAttributeModifiers` 的 RETURN 处按 `SpearConfig.tetraDamageScale`（0~1，默认 0.5，读取失败回落 0.5）统一打折，实现在 `compat/tetra/TetraDamageScaling`；只缩放 `ADDITION`，`MULTIPLY_BASE`/`MULTIPLY_TOTAL`、玩家基础攻击力与其他来源都不动。测试是 `tetraAttackDamageIsScaled`。
- **属性查询的重入闸门别删**：`ItemStackTetraMixin.spearcore$tetraAttackDamage` 注入在 `ItemStack#getAttributeModifiers(EquipmentSlot)` 的 RETURN，而 `TetraSpearBridge` 取不到 `ENTITY_REACH` 时会回退去扫同一个方法的修饰符；`spearcore$resolvingAttributes` 这个 `ThreadLocal` 就是防自递归的。删掉会无限递归到 `StackOverflowError`，被兼容层 `catch (Throwable)` 吞掉后模块化长矛直接失去蓄力/戳刺全部行为。
- 客户端：`compat/tetra/mixin/ItemRenderPropertiesTetraMixin` 在 `Item#getRenderPropertiesInternal`（HEAD，`remap=false`）对 `DynamicModularItem` 惰性挂 `SpearClientExtensions`（渲染时 `ModList` 必已就绪）；**不要**改回 `Item#initializeClient`（物品构造期 `ModList` 可能还没就绪，会静默挂不上）。蓄力动画直接复用 `SpearAnimations.firstPersonUse` / `thirdPersonUseItem`；`getUseAnimation` 固定 `NONE`，第三人称蓄力手臂由 `HumanoidModelMixin` 调 `SpearAnimations.thirdPersonHandUse`。
- **模型交给 Tetra**：模块化长矛用 Tetra 自带的 `assets/tetra/models/item/dynamic_handheld.json`（`tetra:modular_loader`）按部件拼装，材料 tint 也由 Tetra 处理。**不要再替换 Tetra 的渲染**：旧的 `client/TetraSpearModel` + `compat/tetra/mixin/ItemRendererTetraModelMixin` + `TetraSpearColors` / `TetraMaterialKey` / `TetraColorRegistration` / `TetraSpearColorsEvent` / `TetraClientCompat` 已删除。仓库里保留的 `assets/spearcore/models/item/tetra_spear*.json` 与四张灰阶贴图只是以后的素材，**当前没有代码引用，别当生效路径改**。
- 指向方块 / 挥空：`SpearCooldownAttackMixin` 在非 ENTITY 分支对 MODULAR_3D 补 `player.swing` + 客户端 `playLocalSound(ITEM_SPEAR_ATTACK)`；创造模式 `canAttackBlock=false` 会让原版 startAttack 空转，所以不能指望原版音效。
- 附魔：放行判定统一走 `SpearEnchantments.supportsSpearEnchantment(enchantment)` = 原版 8 项白名单 + 「模组添加、且能附在本模组隐藏探针剑 `spearcore:enchantment_probe` 上」的武器附魔。探针剑 `item/EnchantmentProbeItem` 继承 `SwordItem`，**不可获取**：不进创造栏、无配方、无掉落，只挂 `#minecraft:swords` 让人按标签判断武器的模组也认得它；不要给它加配方/战利品/本模组 tag。
- 放行点三处：`SpearItem#canApplyAtEnchantingTable`（内建矛，`supportsSpearEnchantment || super`）；`mixin/EnchantmentTetraCompatMixin`（`Enchantment#canApplyAtEnchantingTable` HEAD，`MODULAR_3D` + `supportsSpearEnchantment`）；`compat/tetra/mixin/ModularItemTetraEnchantMixin`（Tetra 的 `ModularItem#canApplyAtEnchantingTable` HEAD，`remap=false`；这个方法由 Forge 添加、名字不带 SRG，所以能安全注入，其它 SRG override 仍不要碰）。普通 Tetra 工具/未装 Tetra 不受影响。
- 突进的**运行时**门禁在 `SpearItem.isLungeApplicable(ItemStack)`（内部走 `SpearLikeSupport.of`），不要写回 `instanceof SpearItem`，否则模块化长矛能附上突进但触发不了冲刺。测试：`lungeAppliesToSpearLikeStacks` / `tetraWhitelistMatchesVanillaSpearWhitelist` / `lungeGateMatchesSpearLikeStacks` / `weaponProxyDetectsSwordCompatibleEnchantments` / `minecraftNamespaceEnchantsStayOnWhitelist` / `enchantmentProbeIsHiddenAndNotCraftable`。
- 玩家持矛的伤害类型固定用 `minecraft:player_attack`（`SpearDamageTypes` 里用 `PlayerSpearDamageSource` 保留 `death.attack.spear*` 文案），怪物持矛仍用 `spearcore:spear`；不要让长矛重新变成完全自定义的类型，否则 Slayer 等按原版玩家攻击判断的模组会整段跳过。测试：`playerSpearDamageUsesPlayerAttackType`。
- 命中后的原版附魔效果必须统一走 `SpearCombat.applyOnHitEnchantments`（火焰附加 + 攻击者侧 `doPostDamageEffects` 的 `doPostAttack` + 目标护甲侧 `doPostHurtEffects` 的 `doPostHurt`），两边 helper 的遍历对象不同、不能只调一个；不要在两条命中路径里各写一份。测试：`fireAspectIgnitesStabTarget` / `baneSlowsArthropodTarget`。
- 同一 tick 的「攻击前先打一段」类模组会留下受伤免疫；`SpearCombat.clearSameAttackerHurtImmunity` 只在 `invulnerableTime > 0 && getLastHurtByMob() == attacker` 时清一次，只服务长矛本体那一次 `hurt`，不要扩大成无条件清零。测试：`sameAttackerHurtImmunityIsCleared`。
- 模组附魔探针缓存用 `ConcurrentHashMap`，探针物品未注册时不缓存；`EnchantmentTetraCompatMixin` 必须先确认 Tetra 长矛栈再问附魔，避免非长矛物品查询也触发探针逻辑。
- 依赖：`compileOnly 'curse.maven:tetra-289712:8570756'`（Tetra 1.20.1-6.17.0）；未安装 Tetra 或 `tetraCompat=false` 时不得加载任何 Tetra 类。
- 测试：GameTest 服务端**不加载 Tetra**，所以只能覆盖纯函数与数据文件——`modularSpearArchetypeKeyMatching`、`modularSpearArchetypeDataIsValid`（archetype JSON 结构）、`spearReplacementsCoverAllSpears`（七条替换规则与材质映射）、`nonModularItemsAreNotClassified`（普通剑/三叉戟不误判），以及原有的公式类用例（`fitDurationClampsAndGrowsWithSpeed` 等）。改动后必须手工在装了 Tetra 的实例里过一遍：工作台转换、换部件、蓄力/戳刺、附魔台、突进。

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
3. 改了行为 → 有对应 GameTest；改了配置项 → `README.md` 与 `README.en.md` + `kjs-guide.md` 同步；改了资源包兼容层 → `ResourcepackCompatGameTests` 必须通过（meta 补丁、贴图识别、路径重映射、真实包端到端）；改了 Tetra 兼容层 → `TetraCompatGameTests` 必须通过（公式单调性/放缓/边界、archetype 键匹配、archetype 与替换数据文件的结构、非长矛物品不误判），并手工在装了 Tetra 的实例里验一遍工作台转换。
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