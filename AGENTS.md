# AGENTS.md

本文件是仓库维护约定。改动前先读，提交前按末尾清单自检。

## 1. 范围与兼容约束

- 只维护 `1.20.1-forge` 分支；不构建、不修改 `main` 或其它上游遗留分支，也不要把它们合并进来。
- 保持 `modId`、根包、注册命名空间、物品 ID 与 tag 不变。第三方模组按这些标识写死兼容，改动会直接破坏兼容。
- 保持 Avaritia Spear 等第三方依赖的公开方法签名；只能新增重载，不能删除或修改已有签名。
- 目标平台版本、Forge 版本、Gradle 版本、依赖版本一律以构建脚本为准，不在本文件重复。

## 2. 构建与验证

```bash
./gradlew compileJava
./gradlew runGameTestServer
```

- GameTest 的通过标准是日志出现 `All N required tests passed :)`，失败是 `N required tests failed :(`。不要只看退出码。
- 测试结构由 `gameteststructures/` 提供，Gradle 任务负责复制到运行目录。
- 发布 jar 必须经过 `reobfJar`；不要绕过它，否则 SRG 名不匹配，进游戏会报 `NoSuchMethodError`。
- 构建需要写 Gradle 缓存和锁文件；沙箱环境要允许这些路径。
- 测试服务端不加载 Tetra；Tetra 相关纯函数与数据可以自动化，真正的工具行为必须进装了 Tetra 的实例手工验。

## 3. 分支与提交

- 只在 `1.20.1-forge` 分支工作。
- 不 force push，不改写已推送历史。
- 提交信息用中文：一行摘要，正文用 `-` 列要点，说明动机和影响，而不只是罗列改动。
- 不提交 `build/`、`run/`、`.gradle/`、IDE 文件、`libs/` 或编辑器备份；不要用 `git add -f` 绕过忽略规则。
- 提交前 `git status` 只能包含预期文件。

## 4. 代码与 Mixin

- 使用构建脚本固定的 Java 版本、Forge 版本、official mappings 和现有构建插件；不要升级或替换。
- 缩进与文件编码遵循仓库既有格式；代码注释使用中文。
- 不引入 MCreator 生成文件、`MCREATOR_*` 分支或“每次构建会重新生成”的标记。
- 新增注入放 `mixin/` 包，并写入对应的 mixin 配置：
  - 通用：`spearcore.mixins.json`
  - Punchy：`spearcore.punchy.mixins.json`
  - Tetra：`spearcore.tetra.mixins.json`
  - 后两者保持可选加载，并配各自的 `IMixinConfigPlugin`。
- 不内嵌 MixinExtras（不 shade、不 jarJar）。它会把本模组变成 MixinExtras 包的导出模块，与其它携带 MixinExtras 的模组冲突。只用原版 Mixin 注入器。
- KubeJS、Punchy、Tetra 等兼容层必须可选：`compileOnly` + 反射入口隔离，未安装时不能触发对应类加载。
- 新增或修改行为逻辑优先放进 `item/SpearLike`、`init/SpearCombat`、`init/SpearLikeSupport`，不要在业务代码里散落 `instanceof SpearItem`。AI、怪物装备、附魔分类等确实只服务内建矛的逻辑可以保持 `SpearItem` 专用。

## 5. 行为配置

- `config/SpearConfig.java` 是 Forge 配置入口；`util/SpearRules.java` 是行为判定的唯一读取入口。
- 业务代码不得直接读 `SpearConfig`，也不得把版本差异写死；所有行为判定走 `SpearRules`。
- 配置默认值遵循原版 Java 行为。某档行为与 Java 相同时，不要新增重复选项。
- 新增开关时同步更新：`SpearConfig` 注释、`SpearRules`、`README`、`README.en`、`kjs-guide`。
- 蓄力重复命中分两套配置：
  - `chargeRepeatHit` 只用于 `SpearLike#speedScalesChargeRepeat()` 为 true 的内建/KubeJS 矛与 Tetra 矛；
  - 第三方 `SpearItem` 走独立的 `chargeRepeatHitThirdParty`。
  - 不要为了省事让第三方默认跟随内建矛开关，否则会改变第三方矛的右键节奏。
- 攻速类间隔必须使用持有者的实际 `ATTACK_SPEED`，包含 haste、装备和其它修饰符；武器自身时长只作为属性不可用时的回退。

## 6. 战斗与命中

- 多部件实体必须通过 `SpearCombat.resolveLivingTarget` 解析母实体；命中效果、附魔、击退、仇恨、免疫记录作用在母实体上，伤害仍落到具体部件。
- `getHitEntitiesAlong` 返回前用 `dedupeByRoot` 去重；同一次出手对同一母实体只应结算一次。
- 怪物 AI 与结算使用同一套 `effectiveMinRange` / `effectiveMaxRange`，不要用裸 `getMaxRange`。`SpearCollision` 与 `SpearItem#getHitEntitiesAlongInternal` 必须保持一致。
- 不要统一给 `BaseSpearItem` 挂 `ENTITY_REACH` 修饰符。射程由长矛自身数值加上持有者 reach 超出原版的部分组成；第三方长矛需要额外 reach 时写在自己的物品实现里，避免重复叠加。
- 击落骑手阈值统一走 `SpearStats.dismountSpeedFor` / `SpearStats.dismountConditionFor`；KubeJS 建造器不得另写一套数值。
- 蓄力伤害基线走 `SpearCombat.chargeBaseDamage`。JAVA 语义只取基础攻击力加本矛自己的 `ADDITION`；BEDROCK 语义取完整属性，并正确处理副手蓄力。
- 蓄力判定箱与戳刺判定箱分别走 `hitboxMargin` / `hitboxMargin2` 及相关配置；不要在业务代码写死。
- 移动靶补偿只对玩家命中生效，使用 `SpearItem.compensatedTargetBox`；不要把怪物 AI 或伤害结算纳入补偿。
- 矛盾共持只处理“主手长矛 + 副手盾”。右键让盾、自动举盾、起身收盾的职责分别放在 `SpearCombat` 与 `event/SpearShieldStanceHandler`；盾牌减伤、击退、音效交给原版。
- 玩家持矛的伤害类型保持 `minecraft:player_attack`，怪物持矛使用本模组类型；不要改成完全自定义的玩家伤害类型。
- 命中后的原版附魔效果统一走 `SpearCombat.applyOnHitEnchantments`，同时覆盖攻击者侧和受击者护甲侧。
- 同 tick 免疫清理只在“目标当前免疫且上次伤害来自同一攻击者”时执行，不要扩大为无条件清零。
- `SpearCombat.attack` 与 `SpearCombat.performStabAttack` 目前是两条实现；改一条时必须对照另一条并补测试。不要把这条已知债务误当成已合并。
- 三尖两刃刀的特殊伤害由 `event/ThreePointBladeHandler` 统一处理：按最终伤害拆分普通/虚空/魔法/直接扣血，并对敌对生物削减最大生命。不要让额外伤害再次进入拆分逻辑。
- 玩家挥砍仍由原版左键与 `SpearAttackHandler` 处理；不要重新引入自定义挥砍包或自定义横扫结算。槊/刃的空中横扫由 `SpearLike#sweepsWhileAirborne` 控制，地面判定不能写死。

## 7. 数据与资源

- 当前 MC 版本的物品 tag 目录是 `data/<namespace>/tags/items/`（复数）。路径写错不会报错，但 tag 会静默失效。
- 长矛必须维护这些归类：
  - `#spearcore:spears`：内建普通材质矛（不含铜矛）
  - `#spearcore:spear`：全部内建矛（含铜矛）
  - `#spearcore:copper_spear`：铜矛
  - `#minecraft:spears`
  - `#minecraft:swords`
  - 金矛额外进入 `#minecraft:piglin_loved`
- 新增长矛必须同时补齐：注册、配方、物品模型、贴图、语言文件。
- 只维护 `en_us` 与 `zh_cn`；不要重新引入无人维护的日文文件，缺失时回退英文。
- 资源包兼容层只在客户端加载。只处理长矛贴图，所有补丁仅在内存副本中，绝不改写用户资源包文件；不改模型、语言或 `custom_model_data`。
- `pack.mcmeta` 必须随资源发布，否则启动会出现资源包警告。

## 8. Tetra 兼容层

### 识别与边界

- 模块化长矛 = `tetra:dynamic_handheld` + `archetype=modular_spear`。判定唯一入口是 `compat/tetra/TetraSpearProfile`。
- Tetra 自带的单头工具和普通模块工具不能被当成长矛。
- `TetraSpearProfile.of` 先检查 Tetra 是否加载；任何异常只记一次日志并当作“不是长矛”。
- 数据文件必须放在 `data/tetra/` 下；Tetra 的 DataStore 只读取该命名空间。
- 不要按 Tetra 自带的模块 key 判定长矛。

### 结构与数据

- archetype 包含矛头、矛杆、枪纂，以及复用 Tetra 剑的强化附层和绑定/镶嵌槽。
- 本模组自己的部件模块放在 `data/tetra/modules/spear/`。绑定、镶嵌、强化附层直接复用 Tetra 剑的数据，不重复造模块或配方。
- 变体前缀不能复用 Tetra 的 `spearhead/`、`basic_blade/` 等，否则会覆盖 Tetra 自带变体。
- 积竹木柲使用单模块加原地改进，不通过换模块实现阶段；阶段配方必须是 improvement，不能写 `moduleKey` / `moduleVariant`。
- 枪纂的百分比攻击力必须放在 `extract.primaryAttributes`，让 Tetra 按材料 primary 缩放；不要写成固定顶层 attributes。
- 描述文本不要写死具体百分比或完整度消耗；这些数值由数据展示，描述只写“提高攻击力”等定性说明。
- 槽位坐标使用 Tetra GUI 的偏移量级，不要写自定义大坐标。
- 替换数据、卷轴解锁、战利品、交易、翻译键统一由现有持类管理；新增内容时同步检查数据文件与翻译。
- 翻译使用 Tetra 的 I18n key：槽位、模块、原理图、解锁提示、卷轴名称分别使用对应命名空间。配方 key 使用斜杠路径，不是点分 key。

### 打磨、升级、维修

- 除尖锐与稳定外，打磨复用 Tetra 自带定义；不要自造同名打磨，否则工作台会出现重复项。
- 本模组自己的打磨只保留尖锐与稳定；requirement 不要按模块 key 过滤。
- 尖锐加成、稳定加成等数值由对应的数据/常量统一决定；业务代码不要重复写一份。
- 锯齿、回火等升级复用 Tetra 的数据，但使用本模组的矛头文案；不要再复用剑刃 localizationKey。
- 工作台维修需要同时满足“物品类已注册维修原理图”和“模块变体能解析 RepairDefinition”。`TetraSpearRepairSetup` 与 `ItemModuleRepairMixin` 两处必须同时存在。

### 第三方联动

- 材料数据只要落在本模组模块的 materials 前缀下就能直接使用；模块的 improvements 目录要允许第三方共享改进。
- `item_effects`、`synergies`、`crafting_effects` 分别通过现有钩子和门禁接入；不要把第三方数据复制进本模组。
- 模块门禁映射有两处：schematic requirement 与 crafting effect condition。两处都要覆盖，只修一处会漏掉另一类第三方数据。
- 不要 mixin Tetra 的 `ItemModularHandheld`；发行版中的 SRG 名无法被 refmap 正确映射。改为在 `ItemStack` / `Item` 的 MC 方法上拦截。
- 客户端渲染、tint、名称、全息球条目、贴图叠加各自有固定入口；新增外观逻辑放对应 client 类，不要散落在业务代码。
- 全息球条目必须使用 Tetra GUI 能正确采样的纹理尺寸，不能直接引用普通物品图标；翻译键必须存在。
- 第三方矛尖贴图只在运行期从资源位置读取，不复制进本模组 jar；uv 必须按现有 texel 工具转换。
- 工作台姿态依赖 `isGui3d()`；不要删除固定平躺的包装层。
- 属性查询存在重入风险；`ItemStackTetraMixin` 的 ThreadLocal 闸门不能删除。
- 附魔、突进、书附魔的放行逻辑必须继续走 `SpearEnchantments.supportsSpearEnchantment`；不要恢复 `instanceof SpearItem` 门禁。

## 9. 第三方 ABI

以下签名是 Avaritia Spear 等第三方模组按字面描述符依赖的稳定接口，只能新增重载，不能删改：

- `SpearItem.jerotesLungeForwardMaybe(LivingEntity)`
- `SpearItem.rushAttack(LivingEntity, float)`
- `SpearItem.getHitEntitiesAlong(LivingEntity, SpearItem, float, Predicate<Entity>)`
- `SpearAttackHandler.onPlayerAttack(AttackEntityEvent)`
- `SpearSounds` 中现有的攻击、命中、使用、突进音效字段
- `SpearCondition(int, float, float)`

改这些类前先跑 `CompatibilityGameTests`。旧版 `SpearItem` 重载与新的 `SpearLike` 重载必须共用同一实现。

## 10. 自检与禁止

1. `./gradlew compileJava` 通过。
2. `./gradlew runGameTestServer` 日志出现全部测试通过。
3. 行为改动有对应 GameTest；配置改动同步中英文 README、配置文档和 KubeJS 指南；Tetra 改动跑 `TetraCompatGameTests`；资源包改动跑 `ResourcepackCompatGameTests`。
4. `git status` 只有预期文件。
5. 提交信息为中文并说明动机。
6. 涉及原版数值、阈值、概率或时长时，注明来源（Minecraft Wiki 或原版 jar 反编译），不要凭印象写。

禁止：

- 改 `main`、动上游历史、force push。
- 在业务代码直接读配置或写死版本差异。
- 把语义不符的 tag 塞进长矛归类；新增归类先在本文件说明理由。
- 删除或弱化 GameTest 来让构建变绿。
- 跳过 `reobfJar`、关闭测试或注释掉报错代码。
- 提交密钥、token、本机绝对路径。
