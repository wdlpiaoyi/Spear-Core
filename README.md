# Spear Core Forked

[![](https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white)](https://github.com/deepseek-ai/deepseek-harness)

简体中文（默认） ｜ [English](README.en.md)

> 给 Minecraft 1.20.1 Forge 加七把长矛：左键戳刺、右键蓄力冲锋，冲得越快伤害越高。

| 项目 | 内容 |
|---|---|
| 游戏版本 | 1.20.1 |
| 加载器 | Forge 47.3.0+ |
| 模组 ID | `spearcore` |
| 安装端 | 客户端 + 服务端 |
| 前置 | 无（Tetra / Punchy / KubeJS 均为可选） |
| 许可证 | MIT |

本仓库是 [wdlpiaoyi/Spear-Core](https://github.com/wdlpiaoyi/Spear-Core) 的 fork（上游 [lyingice/Spear-Core](https://github.com/lyingice/Spear-Core)），由 wdlpiaoyi 与 deepseek 维护，只做 1.20.1 + Forge。展示名是 **Spear Core Forked**，但 `modId`、包名、注册命名空间仍是 `spearcore`，按 modId 写死兼容的模组（如 Avaritia Spear）继续可用。

## 特性

- **七把长矛**：木、石、铜、铁、金、钻石、下界合金，耐久 / 攻速 / 附魔能力各不相同。
- **两种打法**：左键戳刺、右键按住蓄力冲锋；蓄力阶段依次解锁击落骑手、击退、伤害。
- **速度即伤害**：按你朝目标方向的相对速度加伤害，站着捅最低，迎面冲锋最高。
- **突进附魔**：`spearcore:lunge`，最高 3 级，戳刺时把自己向前推一段，代价是耐久与饱食度。
- **怪物也会用矛**：僵尸、猪灵等按配置概率携带长矛，并且真的会冲锋。
- **动作与音效**：第一 / 第三人称持矛、蓄力、戳刺、手臂动画与命中反馈。
- **Tetra 兼容**：同时装「长手柄 + 矛头」的单头工具会按本模组长矛工作。
- **材质包兼容**：自动识别 1.21.9+ 格式的长矛资源包并在内存里改写为 1.20.1 可读。
- **Avaritia Spear 兼容**：保留上游公开方法签名，`avaritia_spear` 1.4.0 可直接使用。

## 安装

1. 游戏版本 **1.20.1**，加载器 **Forge**（47.3.0 及以上）。
2. 把 jar 放进 `mods/`。
3. **客户端和服务端都要装**；只装一边时，另一边会看到空手或不同步。
4. 单人游戏直接启动即可。

## 玩法

### 戳刺（左键）

攻击冷却条满时左键捅一下：

- 戳刺伤害 = 武器攻击力 +（可选）向前的速度加成；站着不动最低。
- 同一目标短时间内不会连续吃到多次戳刺伤害。
- 与原版的差异：本模组默认让戳刺吃速度加成，原版 26.1.2 的穿刺不吃。

### 蓄力（右键按住）

按住右键依次进入：**起手 → 击落窗口 → 击退窗口 → 伤害窗口**。

- 蓄到最长（不同材质约 6～12 秒）会自动收手。
- 蓄力时**不会**像吃东西那样被减速，可以正常走、按住疾跑冲刺。
- 击退需要足够速度；击落骑手是最早解锁的效果。

### 速度判定

系统看你**朝面向方向的速度**与**你与目标的相对速度**：

- 站着不动：伤害很低，甚至进不了击退 / 伤害窗口。
- 疾跑撞上去：伤害明显提高。
- 迎面撞向走向你的目标：相对速度最大。
- 追着同方向逃跑的目标：相对速度小。

### 突进附魔

`spearcore:lunge`，最高 3 级，只能附在矛上：戳刺时向前推进，级别越高越远；每次触发消耗 1 点耐久与饱食度，饥饿值过低、骑乘、鞘翅、水中不触发。

## 长矛一览

空手时的面板数值（附魔与属性加成另算）：

| 矛 | 攻击力 | 攻击速度 | 耐久 | 附魔能力 | 备注 |
|---|---|---|---|---|---|
| 木矛 | 1 | 1.5 | 59 | 15 | 起手最快 |
| 石矛 | 2 | 1.3 | 131 | 5 | |
| 铜矛 | 2 | 1.2 | 195 | 13 | |
| 铁矛 | 3 | 1.1 | 250 | 14 | 默认手感基准 |
| 金矛 | 1 | 1.1 | 59 | 22 | 好附魔，不耐用 |
| 钻石矛 | 4 | 1.0 | 1561 | 10 | |
| 下界合金矛 | 5 | 0.9 | 2031 | 15 | 防火、不掉岩浆 |

攻击速度数值越小越慢：木矛戳得最勤，下界合金矛单下最疼但间隔最长。

## 合成

六把普通矛在工作台里，形状是「斜着的一根材料 + 两根木棍」：

```
[ ][ ][a]
[ ][b][ ]
[b][ ][ ]
```

`a` 为材料、`b` 为木棍：

| 矛 | 材料 |
|---|---|
| 木矛 | 任意木板 ×1 + 木棍 ×2 |
| 石矛 | 圆石 ×1 + 木棍 ×2 |
| 铜矛 | 铜锭 ×1 + 木棍 ×2 |
| 铁矛 | 铁锭 ×1 + 木棍 ×2 |
| 金矛 | 金锭 ×1 + 木棍 ×2 |
| 钻石矛 | 钻石 ×1 + 木棍 ×2 |

**下界合金矛**走原版锻造台：`下界合金升级模板 + 钻石矛 + 下界合金锭`。

## 怪物

默认 `behavior.mobSpearEquipTable = "JAVA"`，按原版名单与约 5% 概率给僵尸 / 尸壳 / 僵尸村民发铁矛、猪灵 / 僵尸猪灵发金矛；掉落率 `JAVA` 为 8.5%，抢夺每级 +1%。

切到 `"MODDED"` 会启用更广的名单（卫道士 15%、凋灵骷髅 12% 等）与固定 15% 掉落。完整规则见 [docs/configuration.md](docs/configuration.md)。

## 配置

配置文件：`config/spearcore-common.toml`。

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

# Tetra 额外攻击距离按比例计入长矛射程（0=不计，1=原样，默认 0.3）
tetraExtraReachScale = 0.3

# Tetra 工具自身攻击力按比例计入长矛伤害（0=不计，1=原样，默认 0.5）
tetraDamageScale = 0.5
```

前两个开关只控制**创造物品栏与怪物携带**，关掉不影响合成、`/give` 或其他获取途径。

`[behavior]` 段还有 17 个逐项行为开关（默认全部 `JAVA` = 原版行为），以及资源包兼容、Tetra、Avaritia 兼容的完整说明：**[docs/configuration.md](docs/configuration.md)**。

## 兼容

### Tetra

同时安装「长手柄」+「矛头」的 Tetra 单头工具会像本模组长矛一样工作：左键戳刺、右键蓄力（不再是投掷），复用同一套姿态、判定与动画。

- 只支持 **Tetra 1.20.1-6.17.0 及以上**。
- 数值由 Tetra 材料算出的攻击力 / 攻速拟合，高值区间放缓；Tetra 工具攻击力先按 `tetraDamageScale` 打折。
- Tetra 的攻击距离延伸按 `tetraExtraReachScale` 计入。
- 固定 Java 语义：不受 `jabCooldown` / `jabToChargeDelay` 控制。
- 模型用本仓库自己的四张灰阶图（16×16 图标 + 32×32 手持），游戏内按材料 tint。

### Avaritia Spear

**Avaritia Spear 1.4.0** 是上游作者 Lyingice 制作的联动模组，按 `spearcore` 这个 modId 与上游公开方法编译。本 fork 保留相同方法签名（含 `getHitEntitiesAlong` 的 `SpearItem` 旧重载），所以可以直接一起用；`CompatibilityGameTests` 会钉住这些兼容缝。

### 其它

- **JEI**：直接搜「矛」查看配方。
- **KubeJS**：支持用脚本注册自己的矛，见 **[kjs-guide.md](kjs-guide.md)**。
- **Punchy**：安装后蓄力动画按本模组各材质的阶段切换，可用 `punchyStageBridge` 关闭。
- **材质包**：1.21.9+ 格式的长矛包会被自动改写加载，磁盘文件不改动。

## 常见问题

**为什么矛的攻击力比剑低？**
伤害主要来自速度加成；站着砍不如剑，疾跑冲锋可以远超剑。

**左键没反应？**
攻击冷却条没满，或目标刚被戳过还在免疫时间内。

**创造模式搜不到矛？**
默认关闭创造栏显示，见配置的 `enableVanillaSpears` / `enableCopperSpear`；也可以直接 `/give`。

**支持 1.21.1 / NeoForge 吗？**
不支持。本仓库只做 1.20.1 + Forge；`main` 是上游遗留代码，不构建、不维护。

**和别的模组冲突吗？**
JEI、KubeJS、Punchy、Tetra、Avaritia Spear 都已适配；遇到其它冲突请带上 `logs/latest.log` 开 issue。

## 开发

- 公开物品 / 标签 / 类继承 / 事件 / 扩展点 / KubeJS / Punchy / 移植说明：**[docs/development.md](docs/development.md)**
- 配置项完整表：**[docs/configuration.md](docs/configuration.md)**
- KubeJS 教程：**[kjs-guide.md](kjs-guide.md)**
- 仓库约定与自检：**[AGENTS.md](AGENTS.md)**

```bash
./gradlew build              # 产物：build/libs/spear-core-forked-1.20.1-forge-3.3.0.jar
./gradlew runServer          # 无头启动，自检会打印已注册的长矛数量
./gradlew runGameTestServer  # 自动化测试，看日志里的 "All N required tests passed :)"
```

发布 jar 必须经过 `reobfJar`（`build` 已包含），不要绕过。

## 许可证

MIT License。上游代码与美术版权归 Lyingice；本 fork 的改动与新增资源归 wdlpiaoyi 与 deepseek。
