# HANDOFF.md · 交接说明

> 给"下一个工作区 / 下一个接手的 AI 或人"。读完这一页，你就知道**现在有什么、还缺什么、下一步做什么**。
> 设计细节在 [SPEC.md](SPEC.md)，任务在 [ROADMAP.md](ROADMAP.md)，纪律在 [AGENTS.md](AGENTS.md)。

---

## 1. 一句话现状

**M0 骨架 + M1 抛投 + M2 鱼线已完成**，Fabric 端已实测蓄力→抛投→入水→浮漂/鱼线渲染全链路；下一步 M3 搏鱼。

- 目标：Minecraft 1.20.1 写实钓鱼模组，Fabric + Forge 双端，Architectury 三层，纯 Java 物理。
- 进度：M0、M1、M2 已勾选完成（`ROADMAP.md`）；M3–M6（v0.1 台钓竿）待开工；M9（v0.2 路亚竿）只有规格。
- 已冻结的决策、参数、协议、里程碑全部写在 SPEC，**不要重新讨论，直接照做**。
- 构建/验证命令见 AGENTS §3；`GRADLE_USER_HOME=F:\.gradlecache`，JDK 17。

---

## 2. 目录里有什么

| 路径 | 内容 | 状态 |
|---|---|---|
| `SPEC.md` | 设计规格书，22 节 + 3 附录 | ✅ 定稿 |
| `ROADMAP.md` | M0–M7 + M9 可勾选任务清单（含人日、DoD） | ✅ 定稿 |
| `AGENTS.md` | 接手者硬规则：7 条约束、构建命令、7 个坑 | ✅ 定稿 |
| `README.md` | 项目简介与文件指引 | ✅ |
| `docs/mixins.md` | Mixin 登记表（上限 8，已登记 2 条） | ✅ 可用 |
| `docs/tuning.md` | 手感参数基线与变更记录 | ✅ 可用 |
| `taiwan_rod_64x64.png` | 台钓竿原始参考图（64×64） | 📌 参考，**不进包** |
| `lure_rod_64x64.png` | 路亚竿原始参考图（64×64） | 📌 参考，**不进包** |
| `art/taiwan_rod_32.png` | 台钓竿图标成品（32×32，16 色） | ✅ 可进包 |
| `art/lure_rod_32.png` | 路亚竿图标成品（32×32，16 色） | ✅ 可进包 |
| `art/*_32@8x.png` | 放大 8 倍的预览图 | 👀 只看，**不进包** |
| `art/to_32.py` | 上述图标的处理脚本（可重跑调参） | 🔧 工具 |
| `gradle.properties` | 版本锁定（SPEC §7.1）：MC 1.20.1 / Architectury 9.2.14 / Loom 1.7.417 / Forge 47.3.33 | ✅ |
| `settings.gradle` / `build.gradle` | 三模块工程 + 统一 toolchain 17 / JUnit 5 | ✅ |
| `gradlew` / `gradle/wrapper` | Gradle 8.8 wrapper（`validateDistributionUrl=false`） | ✅ |
| `common/` | 玩法/物理/数据层（无平台 import）：`RealFishing`、`IPlatform`、`ModItems`、`ModEntities`、`RodItem`、`FloatRodItem`、`BobberEntity`、`RealFishingCommands`；M1 新增 `fishing/`（`FishingState`/`FishingSession`/`CastMath`/`FishingManager`）、`data/FishingSavedData`、`net/CastC2S`、`client/`（`BobberRenderer`/`FishingHud`/`ClientCast`/`ClientSetup`/`DebugAutoCast`）；M2 新增 `physics/FishingLine`、`client/ClientLineSim`、`client/ClientLineRenderer` | ✅ M0/M1/M2 |
| `fabric/` / `forge/` | 平台实现：入口类 + `*Platform` + 资源/元数据 | ✅ M0 |
| `build-tools/rcon.js` | 开发期 RCON 客户端（`node build-tools/rcon.js <pass> <cmd...>`），**不随模组发布** | 🔧 工具 |
| `common/.../client/DebugAutoCast` | 开发期自动抛投：设 `REALFISHING_AUTOCAST=1` 后进世界自动塞竿、蓄力、松手，用于无输入环境冒烟测试 | 🔧 工具 |

**没有的东西**：M3 及以后的玩法代码（`FightModel` 搏鱼、鱼表、音效、S2C 会话同步）。目前只有 C2S 的 `CastC2S`；HUD 只有蓄力条，没有张力/鱼讯。

---

## 3. 美术资产说明（重要）

### 3.1 已就绪

两张 32×32 图标已按像素纪律处理过：

- 16 色（原图分别是 238 / 416 色）；
- 硬边、无抗锯齿、无洋红 matte；
- 轮廓外扩 1 px 并压暗，等效原版描边；
- 杆身在 32 网格上 2–3 px 宽，缩到 16 逻辑像素后仍有 1 个实体像素。

**为什么是 32 而不是 16**：32 在 GUI 缩放 ≥ 2 时与屏幕像素 1:1，细节完整；缩放 = 1 时会被 mipmap 平均、显得偏淡。这是斜放细杆的固有取舍，已接受。**不要**把 32 的图直接交给 16 的槽位——要么保持 32，要么重画 16。

### 3.2 落位路径（按 SPEC §6 骨架）

| 放置源文件 | 目标路径 |
|---|---|
| `art/taiwan_rod_32.png` | `common/src/main/resources/assets/realfishing/textures/item/float_rod.png` |
| `art/lure_rod_32.png` | `common/src/main/resources/assets/realfishing/textures/item/lure_rod.png` |

> v0.1 **只用 `float_rod`**；`lure_rod` 的贴图先放着，M9 再注册（AGENTS §9 明令禁止提前注册）。

### 3.3 还缺的美术

| 资产 | 规格 | 何时需要 |
|---|---|---|
| 手持 3D 条带 | 16×64 或 64×64 **竖直**直条，杆尖细、竿尾粗 | M5（渲染） |
| 物品模型 JSON | `item/generated` + `layer0`，或 `item/handheld` | M0（放物品时就要） |
| 浮漂贴图 | 16×16 或 32×32 | M2 |
| 假饵贴图 ×3 | 16×16 | M9 |
| 鱼贴图（v0.1 用原版物品图标即可） | 16×16 | v0.2 |
| 音效 ogg | `reel` / `snap` 两个循环/单次 | M5 |

> 3D 条带**不能**直接由 45° 斜图改，必须重画成竖直条带（SPEC §14.1 的 4 段骨骼按长度切 UV）。

### 3.4 直接可用的资源片段（M0 就要）

```json
// common/src/main/resources/assets/realfishing/models/item/float_rod.json
{
  "parent": "minecraft:item/handheld_rod",
  "textures": { "layer0": "realfishing:item/float_rod" }
}
```

> 用 `item/handheld_rod`（原版鱼竿的父模型），不要用 `item/handheld`——后者的持握角度是剑/工具的角度。

```json
// lang/en_us.json
{ "item.realfishing.float_rod": "Float Rod" }

// lang/zh_cn.json
{ "item.realfishing.float_rod": "台钓竿" }
```

---

## 4. 第一小时做什么（照抄即可）

```powershell
# 0. 确认环境
java -version          # 必须是 17
cd F:\RealisticFishing

# 1. 读三份文档（顺序不能反）
#    SPEC.md → ROADMAP.md → AGENTS.md

# 2. 用 Architectury 官方模板生成骨架（1.20.1 + fabric,forge）
#    再把 SPEC §6 的包结构套进去。不要手写 build.gradle。

# 3. 版本按 SPEC §7.1 锁定，别自己升：
#    minecraft 1.20.1 / architectury 9.2.14 / fabric loader 0.16.10
#    fabric api 0.92.12+1.20.1 / forge 47.3.33 / Java 17

# 4. 验证三连
.\gradlew :common:test
.\gradlew :fabric:runClient
.\gradlew :forge:runClient
```

**M0 的交付物**：空竿能右键打日志 + 两个平台都能进世界 + 单测框架能跑。**不要**在 M0 写物理。

> 以上流程 M0 已全部走完（2026-11-07）。新接手者从 M1 开始，直接跑 `.\gradlew :common:test` 确认环境即可。

---

## 5. 已解决的未知项 & M0–M2 踩坑（务必先看）

**三个未知项现状：**

1. `NetworkManager` 签名已 `javap` 核对（architectury-9.2.14.jar）：`registerReceiver(NetworkManager.Side, ResourceLocation, NetworkManager.NetworkReceiver)`；发送侧 `sendToPlayer/sendToServer/canServerReceive` 存在，M1 用前再核参数。
2. Loom **1.7.417 + Gradle 8.8** 可用（本机没有 8.6 的发行包；wrapper 已锁 8.8）。wrapper 的 `validateDistributionUrl=false` 是必须的——构建机访问不了 `services.gradle.org`。
3. `ViewportEvent.ComputeCameraAngles` 与 Iris 的先后顺序 **仍未验证**，M5 做相机时第一件事就是验证它。

**M0 踩过的坑（都已修，别踩回去）：**

| 现象 | 根因 | 修法 |
|---|---|---|
| `:forge` 报 `Could not find method forge()` | loom 在 `apply` 时按 `loom.platform` 建平台依赖配置，architectury 的 `forge()` 来不及 | 根 `build.gradle` 的 subprojects 块里，**apply loom 之前** `if (project.name == "forge") project.ext["loom.platform"] = "forge"` |
| Forge 起服崩 `Can't get event bus for mod 'realfishing'` | Architectury 只给 `architectury` 自己登记了 mod 总线，Forge 端每个 mod 要自己登记 | `RealFishingForge` 构造器开头 `EventBuses.registerModEventBus(RealFishing.MOD_ID, context.getModEventBus())` |
| Forge 客户端弹 "Warning while loading mods" 挡住 quickPlay | dev 的 `forge/build/resources/main` 被当资源包校验，缺 `pack.mcmeta` | `forge/build.gradle` 的 `processResources` 从 `common/src/main/resources/pack.mcmeta` 复制一份；`shadowJar` 设 `duplicatesStrategy = EXCLUDE` |
| 日志 `No data fixer registered for realfishing:bobber` | 1.20.1 `EntityType.Builder.build(String)` 在 `serialize=true` 时会查 datafixer 表 | 浮漂 builder 加 `.noSave()`（它本来就不该落盘） |
| javac 报非法字符 / 中文注释变乱码 | PowerShell `Set-Content -Encoding utf8` 写 BOM；`Get-Content\|Set-Content` 走 GBK 转码 | 写文件用 `New-Object Text.UTF8Encoding($false)`；改代码用编辑工具而不是 PS 管道 |
| 控制台里中文初始化日志显示成乱码 | Windows 控制台代码页（GBK）不是 UTF-8，不是代码问题 | 忽略这一段；`run/logs/latest.log` 里是正常 UTF-8 |
| 自动抛投时 `/give` 没生效，手里还是 air | 单人存档没开作弊，客户端发的命令被拒 | `DebugAutoCast` 改为 `client.getSingleplayerServer().execute(...)` 直接塞 0 号快捷栏 |
| 自动化跑客户端时 release → cast 隔了 13 秒 | 单人游戏窗口失焦会暂停，服务端 tick 停摆 | `options.txt` 设 `pauseOnLostFocus:false`；日志时间戳不能当真实延迟看 |
| 按住右键进度条满了却抛不出去 | `getUseDuration` 返回 40 tick，到第 40 tick 物品使用自动结束，`releaseUsing` 不再触发 | 改成弓式 `USE_DURATION = 72000`，蓄力读 `player.getTicksUsingItem()` |
| 自动抛投 `used` 一直是 0 | `Minecraft.handleKeybinds` 每 tick 检查右键，键没按下就立刻 `releaseUsingItem` | 自动化要 `client.options.keyUse.setDown(true/false)` 模拟按住 |
| 鱼线/浮漂看不见 | 浮漂四边形太小（0.18 格宽，8 格外只有几像素），且落点正好被准星挡住 | 放大到 0.32×0.44；`land()` 向上找到最上层水方块再贴水面 +0.08 |

**调试入口**：`build-tools/rcon.js`（Node 写的极简 RCON 客户端，改 `forge/run-server/server.properties` 开 `enable-rcon` 后用 `node build-tools/rcon.js <pass> <cmd...>`）。`/realfishing version` 已验证返回 `Real Fishing 0.1.0 (forge)`，`summon realfishing:bobber` 也能生成实体。

---

## 6. 不要重开的讨论（已冻结）

| 决策 | 结论 |
|---|---|
| D1 结构 | Architectury 三层，`common` 不含平台 import |
| D2 物理 | 纯 Java Verlet，24 质点，**不引物理引擎、不上原生库** |
| D3 权威 | 服务端权威，客户端只做预测与渲染 |
| D4 物品 | 两个独立物品：`float_rod`（v0.1）、`lure_rod`（v0.2），共用 `RodItem` 基类 |
| D5 兼容 | 不改原版 `FishingHook` / `fishing_rod`；只做 §17.1 的四个扩展点 |
| D6 数据 | 鱼表走数据包 JSON，权重可热改 |
| D7 配置 | 自写 JSON，**不用 Cloth Config** |
| D8 映射 | `officialMojangMappings` |

理由都在 SPEC §4（L1–L9 教训），反对意见先看那节再提。

---

## 7. 交接前的自检清单

- [x] 新工作区能用 JDK 17 打开本目录；
- [x] `SPEC.md` / `ROADMAP.md` / `AGENTS.md` 三份文档已读；
- [x] 两张 32×32 图标已知落位路径（上面 §3.2）；
- [x] 知道 v0.1 只做 `float_rod`，`lure_rod` 是 M9；
- [x] 知道 mixin 上限 8，且新增必须登记 `docs/mixins.md`；
- [x] M0 已验证：`common` 无平台 import、双端进世界、`/realfishing version` 与浮漂实体可用。\n- [x] M1 已验证：`CastMathTest` 覆盖 AC-2 射程；Fabric 端 `REALFISHING_AUTOCAST=1` 实测 `cast accepted → landed line=9.07`（charge=0.25）。\n- [ ] M1 待人工确认：蓄力条与浮漂贴图的**视觉效果**（自动化环境无法截图）。

---

## 8. 出问题时找谁

- 规格有歧义 → 以 `SPEC.md` 为准，仍不清则在 `docs/tuning.md` 记录假设再动手，别私改 SPEC。
- 参数变了 → 同步 `docs/tuning.md` 的基线表。
- 新增 mixin → 同步 `docs/mixins.md`，并写清"为什么事件做不到"。

---

*本文件随项目演进更新；最后更新：M1 抛投完成（2026-11-07）。*
