# AGENTS.md

给在这个仓库里干活的 AI 代理（以及人类协作者）的约定。这个项目明显有 AI 参与，也拦不住后来人继续用 AI——所以这里不禁止 AI，而是把「怎样才能不把事情搞砸」写清楚。**动手前先读完本文件，动手后按第 6 节自检。**

## 1. 项目是什么

- **Spear Core**：给 Minecraft 1.20.1 Forge 加长矛的模组（七种材质：木/石/铜/铁/金/钻石/下界合金）。
- 本仓库是 `github.com/lyingice/Spear-Core` 的 fork，**只维护 `1.20.1-forge` 分支**。
  - `origin` = 自己的 fork（`wdlpiaoyi/Spear-Core`），`upstream` = 原作者。
  - `main` 分支是上游 1.21.1 的代码，**不要在这里改，也不要把它合并进 1.20.1-forge**。
- 玩法还原目标：Java 版长矛行为（1.21.11 / 26.1.x 的数据），并通过配置项允许切到基岩版或「本模组自己的」行为。

## 2. 构建与测试（改完必须跑）

```bash
./gradlew compileJava          # 编译
./gradlew runGameTestServer    # 集成测试
```

- GameTest 的**通过标准**是日志出现 `All 14 required tests passed :)`（数量会随测试增加而变），失败则是 `N required tests failed :(`。退出码不总是可靠，**以日志为准**。
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
- 新增/修改注入逻辑放 `mixin/` 包，mixin 配置写进 `spearcore.mixins.json`（Punchy 相关放 `spearcore.punchy.mixins.json`，`required=false`）。
- 兼容层（KubeJS、Punchy）必须是**可选**依赖：`compileOnly` + 反射入口隔离，没装对应模组时零 ClassNotFound。

## 5. 行为配置体系（改玩法前必读）

- `config/SpearConfig.java`：Forge `COMMON` 配置。主开关（`enableVanillaSpears`、`enableCopperSpear`、`punchyStageBridge`）+ `[behavior]` 段的 14 个行为开关。
- `util/SpearRules.java`：14 个行为枚举的唯一读取入口（带异常兜底，配置文件没加载时回退 JAVA）。**任何行为判定都必须走 `SpearRules`，不要在业务代码里直接读配置或写死常量。**
- 约定：每个开关的默认值是 `JAVA`（= 原版 Java 行为）；**某版本取值和 Java 一样时，不要为它提供重复选项**（例如「戳刺速度加成」只有 `JAVA`/`MODDED` 两个值）。新增开关时同步改：`SpearConfig`（带中文注释）+ `SpearRules` + `README.md`（中文）与 `README.en.md`（英文）+ `kjs-guide.md`。
- 击落骑手的速度阈值**固定为 Java 数据**（木 14 / 金 13 / 石 13 / 铜 12 / 铁 11 / 钻 10 / 下界 9），见 `SpearStats.dismountSpeedFor`，不要再加开关。

## 6. 数据与资源约定（踩过的坑）

- **1.20.1 的 tag 目录是 `data/<ns>/tags/items/`（复数）**，不是 1.21 的 `tags/item`。写错目录不会报错，tag 直接静默失效——原仓库就是这样，所有 tag 实际都没生效。
- 长矛必须挂进这些 tag（新增长矛时同步加，注意 `#spearcore:spears` 是「六把原版材质矛」、`#spearcore:spear` 才是「全部七把含铜矛」的上游约定）：
  - `#spearcore:spears`（六把原版材质矛，不含铜）、`#spearcore:spear`（全部七把，含铜矛；KubeJS 的 `supported_items` 与突进附魔用它）、`#spearcore:copper_spear`（仅铜矛）；
  - `#minecraft:spears`（前向兼容 1.21 的名字；KubeJS 新建的矛会自动加进来）；
  - `#minecraft:swords`（近战武器归类，顺带自动进入 `#minecraft:tools` 与 `#forge:tools`）；
  - `#minecraft:piglin_loved`（仅金矛）。
- `SpearGameTests.spearsCarryItemTags` 会验证上面这些 tag，tag 文件路径写错会直接测试失败。
- 新增物品要同时补齐：注册、配方（`data/spearcore/recipes/`）、模型（`assets/.../models/item/`）、贴图、三种语言文件（`en_us` / `zh_cn` / `ja_jp`）。

## 7. 自检清单（每次提交前）

1. `./gradlew compileJava` 通过。
2. `./gradlew runGameTestServer` 日志出现 `All N required tests passed :)`。
3. 改了行为 → 有对应 GameTest；改了配置项 → `README.md` 与 `README.en.md` + `kjs-guide.md` 同步。
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
