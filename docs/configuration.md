# 配置与行为开关

配置文件：`config/spearcore-common.toml`（Forge COMMON 配置）。下面按用途分组，全部配置项都在这一页。

## 配置项


配置文件在 `config/spearcore-common.toml`。

### 总开关

```toml
# 是否启用原版材质长矛（木、石、铁、金、钻石、下界合金）
enableVanillaSpears = true

# 是否启用铜矛
enableCopperSpear = true

# 安装 Punchy 时，让其矛蓄力动画按本模组各材质的阶段切换
punchyStageBridge = true

# 是否识别 1.21.9+ 格式的长矛材质包，并在内存中改写成 1.20.1 可读格式
resourcepackCompat = true

# Tetra 提供的攻击距离延伸按比例计入本模组长矛射程（0=完全不计，1=原样计入，默认 1.0）
tetraExtraReachScale = 1.0

# Tetra 物品自身攻击力按比例计入长矛伤害（0=完全不计，1=原样计入，默认 1.0）
tetraDamageScale = 1.0
```

前两个开关控制的是（**默认都开着**）：

- 这些矛**会不会出现在创造模式物品栏**；
- 怪物**会不会携带**这些矛。

**关掉也不影响**合成、`/give` 和其他正常获取途径——物品一直在，只是不在创造栏里、怪物也不带。

### 资源包兼容

`resourcepackCompat` 控制 **1.21.9+ 格式的长矛材质包**：开启时（默认）会把 `resourcepacks/` 里含长矛贴图的包以 " [Spear Core 贴图兼容]" 的名字注册进资源包列表，在内存里把 `pack_format` 补成 15，并把 `minecraft:` 下的长矛贴图映射到 `spearcore:` 命名空间。**磁盘上的资源包文件不会被改动。**

这项功能原先是独立的 **Spear Core Resourcepack Compat** 模组，现已并入本体：如果你装过它（`spearrpcompat` 的 jar），删掉那个 jar 即可，装载本模组后行为不变。

### Tetra 兼容（模块化长矛）

本模组**不再劫持 Tetra 自带的单头工具**。取而代之的是一个**独立的模块化长矛**：

- 把本模组七把贴图长矛中的任意一把放进 Tetra 工作台，数据包里的 replacement 会把它替换成 `tetra:dynamic_handheld`，并写入 archetype `modular_spear` 与四个模块槽位。
- 五个槽位是**本模组自己的矛部件**：矛头 `spear/head`（↗）、矛杆 `spear/handle`（↘）、绑定 `spear/binding`（↖）、**枪纂** `spear/butt`（↙，+0.5 攻击力）、**强化附层** `spear/fuller`（可选，抄 Tetra 剑强化血槽：金属/木/骨/结构梁）。七种材质映射到 oak / stone / copper / iron / gold / diamond / netherite（木矛 → oak），矛杆固定木棍、绑定固定线；之后可以在工作台里用材料换任意部件；绑定槽还能换成**镶嵌**（`spear/socket`，任何 `tetra:socket/*` 材料都能装，不限于宝石），矛头可在打磨面板里选伤害/耐久/速度任一条（各 I~V），五级后可再磨「尖锐」）。
- 还有三件**必须先用卷轴解锁**的替换部件（`data/tetra/schematics/spear/{shuo_head,composite_shaft,three_point_blade}.json`，`requirement` 是 `tetra:locked`）：
  - **槊头**（占 `spear/head`）：更沉更长的矛头，伤害与射程更高、攻速更低；
  - **积竹木柲**（占 `spear/handle`）：复合矛杆，更结实也更长；
  - **三尖两刃刀**（占 `spear/head`）：三尖两刃的重型矛头，伤害与射程远超普通矛头，属于「复杂原理图」（卷轴必须**直接摆在加工台上**）。
  - 卷轴本体是 Tetra 的 `tetra:scroll_rolled`（`TetraSpearScrolls` 拼 NBT）：工具匠大师卖积竹木柲卷轴，武器匠大师卖槊头卷轴，三尖两刃刀卷轴在四张下界/要塞/末地战利品表里低概率产出；摆在工作台旁（intricate 需正对上方一格）解锁。
- 只有 archetype 是 `modular_spear` 的物品才会被当成长矛（`TetraSpearProfile.isModularSpearArchetype`）。Tetra 自带的单头工具、普通模块工具一律按普通工具工作。
- Tetra 兼容从 3.4.0 起强制开启：`tetraCompat` 开关已删除，旧配置里残留的该键会被 Forge 忽略；模块化长矛始终按长矛工作。

长矛化之后的行为：

- 左键戳刺、右键蓄力（不再是 Tetra 的投掷），复用同一套长矛姿态、命中判定与动画。
- 数值由 Tetra 算出的攻击力 / 攻击速度**拟合**，不复刻本模组各材质的固定数值；高数值区间会放缓，避免 overpowered。
- Tetra 物品自身提供的攻击距离延伸（`forge:entity_reach`，即 `ForgeMod.ENTITY_REACH`）只按 `tetraExtraReachScale`（0~1，默认 1.0）计入长矛射程；其他模组 / 整合包给的 reach 不受影响。
- Tetra 物品自带的攻击力先按 `tetraDamageScale`（0~1，默认 1.0）缩放，再参与长矛伤害计算；玩家基础攻击力、力量效果、附魔与其他来源不受影响。
- 只适配 **Tetra 1.20.1-6.17.0 及以上**；6.9.0 分支不保证可用。
- 模块化长矛固定按 **Java 语义**：不受 `jabCooldown`（全背包共享冷却）和 `jabToChargeDelay`（戳刺后摇）控制——这两项依赖「同一物品实例」，而 Tetra 每种模块组合都是不同实例。
- 指向方块 / 挥空时播放长矛攻击音效；创造模式左键不破坏方块（`Item#canAttackBlock` 返回 `false`），因此 WieldYourPower 之类的「强制破坏」也不会排队。
- 外观是三套模型（默认 / 槊头 / 三尖两刃刀，`assets/spearcore/models/item/tetra_spear{,_shuo,_three}.json`），只换头部那一层，杆贴图共用；放在 Tetra 工作台上是平躺的。物品名按 Tetra 的命名规则拼「材料 + 矛 / 槊 / 刃」：铁矛头显示「铁矛」、槊头「铁槊」、三尖两刃刀「铁刃」；材料读不到时回退成「矛」。
- 槽位挂的是本模组自带的矛模块（`data/tetra/modules/spear/{head,handle,binding,butt,socket,shuo_head,composite_shaft,three_point_blade}.json`），不再借用 Tetra 剑的模块，物品本身不是剑。工作台里的槽位坐标写在 `data/tetra/archetypes/modular_spear.json`，是 `GuiModuleOffsets` 的像素偏移。

### 模组兼容（Avaritia Spear）

**Avaritia Spear 1.4.0** 是**上游作者 Lyingice** 制作的联动模组：它按 `spearcore` 这个 modId 和上游 Spear Core 的公开方法编译，mixins 也直接按字面签名注入上游代码。本仓库是 fork，保留相同的 modId / 包名 / 公开方法，所以能继续兼容，**不是它依赖本 fork**。

早期 3.3.x 曾把 `getHitEntitiesAlong` 的第二个参数从 `SpearItem` 改成 `SpearLike`，导致 Avaritia Spear 启动时报 `InvalidInjectionException`；现在恢复了旧的 `SpearItem` 参数重载，并把 `SpearLike` 版本转发到同一份实现，**两边都能用**。

- 改动这些公开方法前先看 `AGENTS.md` 的「第三方兼容缝」；`CompatibilityGameTests` 会用反射检查它们还在。
- `avaritia_spear` 1.4.0 + 本 fork 3.3.0 是验证过的组合；旧版 Avaritia Spear 未逐一测试。

### 行为开关（`[behavior]`）

每个细节一个独立配置项，**默认一律是 `JAVA`**（= 原版 Java 版行为）。可选值只列出"行为确实不同"的版本，与 Java 一致的版本不会重复出现。

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
shieldCoWield = "JAVA"
lunge = "JAVA"
hitSlowDown = "JAVA"
mobSpearEquipTable = "JAVA"
mobSpearDropChance = "JAVA"
minAttackRange = "JAVA"
chargeStrength = "JAVA"
jabToChargeDelay = "JAVA"
movingTargetAssist = "JAVA"
```

| 配置项 | 可选值（第一个是默认） | 说明 |
|---|---|---|
| `jabDamage` | `JAVA` / `BEDROCK` | 戳刺伤害。JAVA：木/金 1、石/铜 2、铁 3、钻 4、下界合金 5；BEDROCK：每级 +1 点。 |
| `jabSpeedBonus` | `JAVA` / `MODDED` | 戳刺是否额外加 `floor(攻速 × 倍率)`。JAVA/基岩版没有；MODDED 才有（疾跑冲锋更能打）。 |
| `jabCooldown` | `JAVA` / `BEDROCK` | JAVA：等原版攻击蓄力条回满；BEDROCK：改用物的使用冷却，且**背包里所有长矛共享**。 |
| `chargeHitboxMargin` | `JAVA` / `MODDED` | 蓄力判定箱额外膨胀：JAVA 0.125，MODDED 0.25。 |
| `chargeRepeatHit` | `JAVA` / `BEDROCK` | 蓄力重复命中：JAVA 每个目标每次蓄力只结算一次；BEDROCK 同目标 10 tick（0.5s）一次。 |
| `jabRepeatHit` | `JAVA` / `MODDED` | 戳刺重复命中：JAVA 没有额外冷却；MODDED 同目标 10 tick 内跳过。 |
| `durability` | `JAVA` / `BEDROCK` / `MODDED` | 耐久。JAVA：59/131/190/250/32/1561/2031；BEDROCK：每级 +1；MODDED：本模组原表（铜 195、金 59）。 |
| `creativeMaxRange` | `JAVA` / `BEDROCK` | 创造模式攻击距离上限：JAVA 6.5，BEDROCK 7.5。 |
| `offhandCharging` | `JAVA` / `BEDROCK` | 能否用副手举矛蓄力。JAVA 可以；BEDROCK 不行。 |
| `shieldCoWield` | `JAVA` / `BEDROCK` / `MODDED` | 矛盾共持（主手矛 + 副手盾）。JAVA：右键永远蓄力、副手盾不格挡；BEDROCK：潜行即自动举盾、起身收盾（原版基岩版的盾牌机制，[Blocking](https://minecraft.wiki/w/Blocking)）；MODDED：潜行之外还要按右键才举盾。 |
| `lunge` | `JAVA` / `MODDED` | 突进力度与饥饿门槛。JAVA：0.458 / 饥饿 <6 禁用；MODDED：0.65 / 饥饿 ≤7 禁用。 |
| `hitSlowDown` | `JAVA` / `MODDED` | 命中后攻击者自己水平速度 ×0.6 并取消奔跑。JAVA 有（原版手感）；MODDED 没有。 |
| `mobSpearEquipTable` | `JAVA` / `MODDED` | 怪物携带名单：JAVA 原版名单（铁/金矛），MODDED 本模组概率表 + 随机材质。 |
| `mobSpearDropChance` | `JAVA` / `MODDED` | 怪物矛掉落率：JAVA 8.5%（抢夺每级 +1%），MODDED 固定 15%。 |
| `minAttackRange` | `JAVA` / `MODDED` | 最小攻击距离：JAVA 2 格（怪物按 mobFactor 减半）；MODDED 归零，贴脸也能出手。 |
| `chargeStrength` | `JAVA` / `BEDROCK` | 蓄力基线是否吃攻击力修饰符（力量/虚弱药水）。JAVA 不吃（原版 [MC-302778](https://bugs.mojang.com/browse/MC-302778)）；BEDROCK 吃。 |
| `jabToChargeDelay` | `JAVA` / `BEDROCK` | 戳刺命中后重新举矛蓄力的间隔：JAVA 0 tick；BEDROCK 该矛的使用冷却（木 13、铁 19、下界合金 23 tick）。 |
| `movingTargetAssist` | `JAVA` / `MODDED` | 移动靶补偿（[MC-310857](https://bugs.mojang.com/browse/MC-310857)）：JAVA 按服务器真实位置判定；MODDED 按客户端看到的位置回退 3 tick。 |

另有两条**不提供配置项**的固定规则：

- **击落骑手所需的速度阈值写死为 Java 版数据**（木 14 / 金 13 / 石 13 / 铜 12 / 铁 11 / 钻 10 / 下界合金 9 格每秒）——这是原版 Java 版唯一可信的一套数值，基岩版对齐后与此相同。
- 判定层差异凡能复刻的都做成了开关（例如 `chargeRepeatHit` 对应基岩版的 0.5 秒重连间隔）；**引擎/渲染层**差异不在复刻范围（基岩版火焰附加点燃蜡烛/营火/TNT、蓄力暴击粒子）。

`shieldCoWield = "BEDROCK"` 只在「主手长矛 + 副手盾」时接管：潜行会自动举盾、起身自动收盾，效果完全走原版盾牌（[Blocking](https://minecraft.wiki/w/Blocking)：基岩版的盾牌由潜行激活）；不拿长矛时不会影响普通盾牌玩法。`MODDED` 是同一个机制的右键版。

`jabDamage = "BEDROCK"` 时，多出来的 1 点伤害加在攻击力属性上，因此**属性面板与戳刺会一起 +1**；蓄力是否也 +1 取决于 `chargeStrength`（`BEDROCK` 时蓄力基线也吃攻击力修饰符）。

## 与原版的差异

以下几处是本模组有意与 Java 原版不同（能做成开关的都已在上一节列出），其余行为默认按原版还原：

- **蓄力伤害基线**：原版是固定 1 点（空手伤害）加相对速度倍数；本模组的基线 = 攻击力属性的**基础值** + 手持长矛自己的攻击力加成（`ADDITION`，例如铁矛 +2），`chargeStrength` 只决定力量/虚弱这类**修饰符**算不算 —— `JAVA` 档不吃（对齐 MC-302778），`BEDROCK` 档才吃。
- **戳刺速度加成**：原版戳刺只看武器攻击力，不随移动速度增加；本模组默认不加（`jabSpeedBonus = "MODDED"` 才启用）。
- **同目标接触冷却**：原版一次蓄力对同一目标只结算一次，戳刺也没有额外冷却；本模组默认就是原版行为，`chargeRepeatHit` / `jabRepeatHit` 的 `MODDED` 档才加 10 tick 冷却。
- **最小攻击距离归零**：原版 2 格内打不到；`minAttackRange = "MODDED"` 时归零，可以贴脸捅。
- **戳刺后摇**：原版 Java 版戳完能立刻再举矛；`jabToChargeDelay = "BEDROCK"` 时按该矛的使用冷却补上这段后摇。
- **移动靶补偿**：原版按服务器位置判定（打移动靶要留提前量）；`movingTargetAssist = "MODDED"` 时按客户端看到的位置回退 3 tick。
- **怪物携带长矛**：原版只有僵尸/尸壳/僵尸村民/僵尸马骑手带铁矛、猪灵/僵尸猪灵带金矛；本模组默认走原版名单，概率表与随机材质属于 `MODDED` 档（`mobSpearEquipTable`、`mobSpearDropChance`）。

---
