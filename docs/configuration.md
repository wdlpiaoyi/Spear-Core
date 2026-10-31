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

# 是否让 Tetra 单头工具（长手柄 + 矛头）按本模组长矛工作
tetraCompat = true

# Tetra 提供的攻击距离延伸按比例计入本模组长矛射程（0=完全不计，1=原样计入，默认 0.3）
tetraExtraReachScale = 0.3

# Tetra 工具自身攻击力按比例计入长矛伤害（0=完全不计，1=原样计入，默认 0.5）
tetraDamageScale = 0.5
```

前两个开关控制的是（**默认都开着**）：

- 这些矛**会不会出现在创造模式物品栏**；
- 怪物**会不会携带**这些矛。

**关掉也不影响**合成、`/give` 和其他正常获取途径——物品一直在，只是不在创造栏里、怪物也不带。

### 资源包兼容

`resourcepackCompat` 控制 **1.21.9+ 格式的长矛材质包**：开启时（默认）会把 `resourcepacks/` 里含长矛贴图的包以 " [Spear Core 贴图兼容]" 的名字注册进资源包列表，在内存里把 `pack_format` 补成 15，并把 `minecraft:` 下的长矛贴图映射到 `spearcore:` 命名空间。**磁盘上的资源包文件不会被改动。**

这项功能原先是独立的 **Spear Core Resourcepack Compat** 模组，现已并入本体：如果你装过它（`spearrpcompat` 的 jar），删掉那个 jar 即可，装载本模组后行为不变。

### Tetra 兼容

`tetraCompat` 控制 Tetra 单头工具的长矛化：同时装了**长手柄**（`single/handle` → `long_handle/`）和**矛头**（`single/head` → `spearhead/`）的单头工具，会像本模组的长矛一样工作——左键戳刺、右键蓄力（不再是 Tetra 的投掷），并复用同一套长矛姿态与命中判定。

- 数值由 Tetra 按材料算出的攻击力 / 攻击速度**拟合**，不复刻本模组各材质的固定数值（也不复刻金矛那类反常数值）；高数值区间会放缓，避免 overpowered。Tetra 新增的模组材料 / 附属材料同样适用。
- Tetra 自己提供的攻击距离延伸（长手柄、紫水晶插槽等的 `forge:attack_range`）只按 `tetraExtraReachScale`（0~1，默认 0.3）计入长矛射程；其他模组 / 整合包给的 reach 不受影响。
- Tetra 工具自带的攻击力会先按 `tetraDamageScale`（0~1，默认 0.5）打折，再参与长矛伤害计算；玩家基础攻击力、力量效果附魔与其他来源不受影响，物品 tooltip 显示的就是打折后的数值。
- 只适配 **Tetra 1.20.1-6.17.0 及以上**；6.9.0 分支不保证可用。
- Tetra 工具固定按 **Java 语义**：不受 `jabCooldown`（全背包共享冷却）和 `jabToChargeDelay`（戳刺后摇）控制——这两项依赖「同一物品实例」，而 Tetra 每种材料组合都是不同实例，无法按物品共享冷却。
- 只改玩家手持的单头工具；怪物 AI、副手戳刺等 Tetra 原有行为不变。
- 模型由「Tetra 模块贴图」组成：手柄/矛头各一套 16×16 物品栏图标和 32×32 手持贴图（共 4 张灰阶图，游戏内按材料色 tint）。手持与 GUI 都走 `item/generated` 的 layer0/layer1 两层平面（与本模组长矛同一条渲染路径），两层像素互不重叠，按 layer 顺序叠放，不需要厚度或 Z 偏移。模型由 `forge:separate_transforms` 在手持 / GUI 之间切换，display 直接复用本模组长矛。
- 手柄/矛头的 16×16 物品栏图标和 32×32 手持贴图是本仓库自己的灰阶美术，游戏内按材料色 tint。
- 蓄力姿态用独立的平滑抬升曲线（不再套贴图长矛的分段曲线），避免举矛过程中跳变 / 指向偏移；正常的「掏出 / 收起」动画保留。
- 第一人称与第三人称的持握、蓄力、戳刺、手臂动画完全复用贴图长矛的实现（`SpearAnimations`），模型 display 也直接抄贴图长矛，因此手感与指向一致。
- 指向方块/挥空时也会播放长矛攻击音效；创造模式下按原版剑的语义处理：左键不会破坏方块（`Item#canAttackBlock` 返回 `false`），因此 WieldYourPower 之类的「强制破坏」也不会排队。

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

`jabDamage = "BEDROCK"` 时，多出来的 1 点伤害加在攻击力属性上，因此**属性面板与戳刺会一起 +1**；蓄力是否也 +1 取决于 `chargeStrength`（`BEDROCK` 时蓄力基线也吃攻击力修饰符）。

## 与原版的差异

以下几处是本模组有意与 Java 原版不同（能做成开关的都已在上一节列出），其余行为默认按原版还原：

- **蓄力伤害基线**：原版是固定 1 点（空手伤害）加相对速度倍数；本模组始终以武器攻击力为基线，`chargeStrength` 只决定力量/虚弱这类修饰符算不算。
- **戳刺速度加成**：原版戳刺只看武器攻击力，不随移动速度增加；本模组默认不加（`jabSpeedBonus = "MODDED"` 才启用）。
- **同目标接触冷却**：原版一次蓄力对同一目标只结算一次，戳刺也没有额外冷却；本模组默认就是原版行为，`chargeRepeatHit` / `jabRepeatHit` 的 `MODDED` 档才加 10 tick 冷却。
- **最小攻击距离归零**：原版 2 格内打不到；`minAttackRange = "MODDED"` 时归零，可以贴脸捅。
- **戳刺后摇**：原版 Java 版戳完能立刻再举矛；`jabToChargeDelay = "BEDROCK"` 时按该矛的使用冷却补上这段后摇。
- **移动靶补偿**：原版按服务器位置判定（打移动靶要留提前量）；`movingTargetAssist = "MODDED"` 时按客户端看到的位置回退 3 tick。
- **怪物携带长矛**：原版只有僵尸/尸壳/僵尸村民/僵尸马骑手带铁矛、猪灵/僵尸猪灵带金矛；本模组默认走原版名单，概率表与随机材质属于 `MODDED` 档（`mobSpearEquipTable`、`mobSpearDropChance`）。

---
