# 写实钓鱼 · Real Fishing — 实施规格书 v1.0

> 目标读者：接手实现的新工作区（DSH 会话）。
> 读完本文件即可开工，不需要回读历史对话。
> 项目根目录：`F:\RealisticFishing`

---

## 0. 一页速览

| 项 | 值 |
|---|---|
| Minecraft | **1.20.1**（仅此版本，不做跨版本） |
| Java | **17**（toolchain 锁定） |
| 加载器 | **Fabric + Forge 双端**，用 **Architectury** 三层结构 |
| 依赖 | 仅 **Fabric API**（Fabric 侧）、**Architectury API**（两端）、Forge 本体 |
| 物理 | **纯 Java 手写 Verlet**，24 节点鱼线（专用求解器，不是通用引擎） |
| 物品 | 两根竿：`float_rod`（台钓，v0.1）、`lure_rod`（路亚，v0.2/M9），**一根一根做** |
| 明确禁止 | 原生库 / JNI / Rapier / GeckoLib / Cloth Config / 任何其他第三方 Java 库 |
| 网络 | Architectury `NetworkManager`，自定义二进制协议，**只同步两个端点 + 张力** |
| 代码规模预估 | 约 6–8 k 行 Java（不含资源） |
| 工期预估 | v0.1 核心 38 人日 + 打磨 10 ≈ **48 人日**（只含台钓竿）；v0.2：M8 兼容/3D 鱼 **+9**、M9 路亚竿 **+8** |
| 模组 id | `realfishing` |
| 包名 | `dev.realfishing`（可整体替换，全局搜索替换即可） |

**一句话架构**：`common` 放全部玩法逻辑与物理，`fabric`/`forge` 只放入口、平台实现、客户端渲染钩子；平台差异被压缩到 `platform` 包和客户端渲染器两处。

---

## 1. 目标与验收标准

### 1.1 体验目标（来自需求原话）

- 玩家持竿时**视角自由**，"宛如现实世界"：转头时竿身在世界里，不是贴在屏幕上的贴图。
- 鱼竿**更真实**：有重量、有弯曲、有惯性，抛投有蓄力，鱼线会垂、会绷、会断。

### 1.2 可测验收标准（Definition of Done）

| 编号 | 验收条件 | 验证方式 |
|---|---|---|
| AC-1 | 手持鱼竿时原版第一人称手部模型**不渲染**；竿身以世界坐标锚定在手上，转头有 1–2 帧惯性延迟，不随视角刚性旋转 | 录屏逐帧检查 |
| AC-2 | 抛投距离随蓄力在 **8–24 格** 变化；落点与准星水平误差 ≤ 1.5 格 | 10 次抛投取平均 |
| AC-3 | 鱼线 24 节点，60 FPS 下无可见抖动；静止时自然下垂，绷紧时近似直线 | 录屏 + 目测 |
| AC-4 | 搏鱼有张力条与泄力档位；张力 >100% 持续 **0.25 s** 断线；断线有音效与粒子 | 手动 |
| AC-5 | 咬钩延迟 3–15 s，假咬概率 25%，假咬有独立音效 | 统计 50 次 |
| AC-6 | 专用服务器 + 2 客户端连续 30 分钟无崩溃、无可见 desync（鱼线两端一致） | 联机回归 |
| AC-7 | 与 `starcatcher`、`aquaculture` 同装不冲突：不改它们的类，不替换原版 `FishingHook` | 同装启动 + 各钓 10 次 |
| AC-8 | 客户端帧耗时增量 < **0.3 ms**（i5-12400 级 CPU） | Spark/JFR 采样 |

---

## 2. 非目标（明确不做）

1. 不做**通用**刚体物理引擎，不引入 Rapier / Bullet / JBox2D / 任何物理库。
   > 注意措辞：我们确实要**自己写一套物理**，但它是**单一用途的鱼线约束求解器**（24 个质点 + 距离约束 + 阻尼，约 300 行），不是"物理管线"——没有 broadphase、没有岛屿分割、没有多形状碰撞、没有 CCD。区别见 §10.7。
2. 不做原生库、不做 JNI（理由见 §4 L1，数据说话）。
3. **v0.1 不做鱼的 3D 模型**；v0.2 用"通用鱼形 + 程序化摆尾"补上，但不做骨骼动画系统、不引 GeckoLib（方案见 §14.5）。
4. 不做钓鱼比赛、排行榜、经济系统。
5. 不做 1.20.1 之外的版本适配。
6. **v0.1 不做深度兼容**，但**必须预留扩展点**（entity/item tag + JSON 字段 + 可选注册 API），让 v0.2 能低成本接别的模组的鱼。兼容不是"以后再说"，是"现在留口子"——见 §17.1。


---

## 3. 冻结决策（ADR）

> 以下 8 条是**已定**的，接手者不要重新讨论；要改必须先给出能推翻它的数据。

### D1 · 加载器结构：Architectury 三层

- **决策**：`common` + `fabric` + `forge` 三个 Gradle 子项目，用 Architectury Loom。
- **理由**：需求指定 Architectury；它把注册表、事件、网络、平台检测都抽象好了，双端差异只剩相机与手部渲染两处钩子。
- **代价**：多一层构建复杂度；Architectury API 9.2.14 是 1.20.1 的最后一版，不再更新，API 以它为准。

### D2 · 物理：纯 Java Verlet，不碰原生

- **决策**：鱼线 = 24 个质点 + 距离约束，固定步长 1/120 s，Gauss-Seidel 迭代 10 次。
- **理由**：见 §4 L1/L2 的 Sable 实测数据。钓鱼的物理量级（~24 质点、1 条链）比 Sable（数百刚体 + 体素碰撞 + 岛屿分割）小 4–5 个数量级，Java 里是微秒级开销，上 JNI 纯负收益。
- **代价**：没有通用碰撞/约束求解器；鱼线不能和复杂几何做真实碰撞——用"水面高度 + 简单 AABB"近似即可。

### D3 · 权威模型：服务端权威 + 客户端预测

- **决策**：状态机、鱼、张力、断线判定全部在服务端；客户端只做输入采集、本地预测（抛投轨迹、线的插值）和表现。
- **理由**：防作弊（改客户端不能改鱼重/上鱼判定）、双端一致。
- **代价**：高延迟下需要客户端平滑，见 §15.3。

### D4 · 物品：两根独立竿（台钓 / 路亚），**一根一根做**，不改原版鱼竿

- **决策**：最终有两个物品——`realfishing:float_rod`（台钓/悬坠）与 `realfishing:lure_rod`（路亚）；共用一个基类 `RodItem` + `RodStyle` 枚举，差异走数据（长度/调性/线组/有无卷线器/可用饵）。**v0.1 只做 `float_rod`，`lure_rod` 放到 v0.2 的 M9**——不并行、不半成品、做完一根再开下一根。
- **理由**：两者机制差异大（立漂看漂 vs 收线诱鱼），拆开后手感才对；但鱼线物理、搏鱼模型、抛投、会话状态机是共用内核，拆成两套系统是浪费。顺序推进避免"两根竿都只做一半"。
- **代价**：物品多一个、配方多一条；`lure_rod` 要等 v0.2。命名上 v0.1 先不占 `lure_rod` 的 id，避免未实现物品出现在创造栏。

### D5 · 数据驱动：鱼表用数据包 JSON

- **决策**：`data/realfishing/fish/*.json`，权重、生物群系、时间、天气、深度、饵全在 JSON 里。
- **理由**：加鱼不改代码；整合包作者可自行扩展；与 Sable 的 `block_properties` 数据包思路一致（§4 L6）。
- **代价**：需要写 schema 校验与错误日志（坏 JSON 不能崩游戏）。

### D6 · 配置：自写 JSON + 命令重载，不用 Cloth Config

- **决策**：`config/realfishing.json`，`/realfishing reload`；服务端字段同步给客户端。
- **理由**：需求禁止额外依赖；Cloth Config 会再拉 Architectury 之外的依赖链。
- **代价**：没有 GUI，改配置要开文件——可接受。

### D7 · 映射：官方 Mojang mappings

- **决策**：`loom.officialMojangMappings()`，不引 Parchment。
- **理由**：少一个下载源与版本对齐点；参数名靠 IDE 反编译即可。
- **代价**：参数名可读性略差。

### D8 · 网络：Architectury `NetworkManager` + 自定义字节协议

- **决策**：不用原版 `CustomPayload` 手写注册；用 Architectury 的跨平台通道。
- **理由**：一套代码两端跑，Forge 的 `SimpleChannel` 与 Fabric 的 `ServerPlayNetworking` 差异被吃掉。
- **代价**：需要按 9.2.14 的实际方法签名写（见 §7.5 的核对提示）。

---

## 4. 经验教训（把上一轮调研变成规则）

> 这些不是背景故事，是**约束**。违反任意一条都会让项目回到已知的坑里。

**L1 · 原生库 = 平台矩阵，钓鱼量级不划算。**
Sable 2.0.6 的实测：`sable_rapier_binaries.zip.l4z` 9.07 MB，内含 5 个平台二进制（`x86_64/aarch64` × `windows/linux/macos`）；运行时用 LZ4 解压、按 `NATIVE_NAME` 从 zip 抽文件、写到 `.sable/natives/`、先 `Files.delete` 再 `System.load`。它的 changelog 里连续多个版本都在修 natives：Intel Mac 要单独 ship、AArch64 Linux 缺库、x86 Linux 支持坏、glibc 兼容、加载失败要加日志。GitHub 上还有 Windows 11 + Java 25 的 `UnsatisfiedLinkError`、macOS Apple Silicon 崩溃。
→ **规则**：本项目零原生代码。任何"用 Rust/C++ 加速"的提议，先回答"它比 24 个质点的 Java Verlet 快在哪"。

**L2 · Mixin 数量就是兼容性负债。**
Sable 主 jar 638 个类、**238 个 mixin 条目**（`sable.mixins.json` 143 通用 + 85 客户端，fabric 另 3+7），官方自己写 "incredibly intrusive"；mcmod 投票 **94.44% 认为兼容差、91.67% 认为不稳定**。
→ **规则**：本项目 mixin 总数**硬上限 8 个**，且只允许注入原版类，绝不注入其他模组。每加一个 mixin 必须在 `docs/mixins.md` 登记：目标类、注入点、为什么事件/API 做不到。

**L3 · 必须有降级路径。**
Sable 在原生库加载失败时会退到 `StaticPhysicsPipeline`（结构不动，游戏还能进），这是它能活下来的原因。
→ **规则**：物理求解任何异常（NaN、数组越界）必须 catch 并降级为"直线鱼线 + 禁用搏鱼物理"，同时打日志，**不允许崩客户端**。

**L4 · 渲染与物理必须解耦。**
Sable 把渲染器单独交给 Ocelot 做，物理只管位姿。
→ **规则**：`FishingLine` 只输出 `double[] positions`；渲染器只读不写；两者之间只通过 `LineRenderState`（不可变快照）通信。

**L5 · 网络按需，别抄大件。**
Sable 自建 UDP 管线（认证/keepalive/echo/TCP 回退）是因为它要同步几百个刚体的状态；钓鱼每 tick 只需要 ~200 字节。
→ **规则**：走 MC 自带 TCP，10–20 Hz，量化传输；不做 UDP。

**L6 · 数据驱动优先。**
Sable 的重量、维度物理全是数据包 JSON。
→ **规则**：鱼、饵、竿参数尽量进 JSON；代码里只留"算法"。

**L7 · 许可要干净。**
Sable 是 PolyForm Shield（源码可见、禁止做竞品）。
→ **规则**：本项目全部自研，不复制 Sable/Rapier 代码；参考只到"思路"层面。

**L8 · 版本范围写死。**
Sable 至今只支持 1.21.1。
→ **规则**：`1.20.1` 写进 `gradle.properties` 与文档，不做 1.20.2+/1.21 的兼容承诺。

**L9 · 瓶颈是手感，不是加载器。**
上一轮评估：双端适配只占 ~1/4 工时，相机、动画、调参占大头。
→ **规则**：里程碑里给"手感打磨"单独留 6 人日（M5），不要塞进功能里程碑。

---

## 5. 架构总览

```
                 ┌──────────────────────────────┐
                 │  common（纯玩法，无平台 API）  │
                 │  fishing / physics / net /   │
                 │  config / data / registry    │
                 └──────────────┬───────────────┘
                                │ Architectury API
        ┌───────────────────────┴───────────────────────┐
        │                                               │
┌───────▼────────┐                             ┌────────▼───────┐
│ fabric 模块     │                             │ forge 模块      │
│ 入口/平台实现    │                             │ 入口/平台实现    │
│ 客户端 mixin     │                             │ 事件订阅        │
└────────────────┘                             └────────────────┘
```

**依赖方向**：`fabric`/`forge` → `common` → Architectury API → Minecraft。`common` 里**不允许**出现 `net.fabricmc.*` 或 `net.minecraftforge.*` 的 import（CI 用 grep 检查）。

| 包 | 职责 | 平台相关？ |
|---|---|---|
| `dev.realfishing` | 主类、常量、注册入口 | 否 |
| `dev.realfishing.registry` | 物品/实体/音效/粒子/创造标签 | 否（用 Architectury `DeferredRegister`） |
| `dev.realfishing.item` | `RodItem`（基类）+ `FloatRodItem`（v0.1）/ `LureRodItem`（v0.2）+ `BaitItem` | 否 |
| `dev.realfishing.entity` | `BobberEntity` | 否 |
| `dev.realfishing.fishing` | 状态机、会话、搏鱼模型、鱼表加载 | 否 |
| `dev.realfishing.physics` | Verlet 求解器、张力、断线 | 否（纯函数，可单测） |
| `dev.realfishing.net` | 包定义与编解码 | 否 |
| `dev.realfishing.config` | JSON 配置 | 否 |
| `dev.realfishing.platform` | `IPlatform` 接口 + 服务加载 | 接口 |
| `dev.realfishing.client` | 渲染器、相机、输入、HUD | 是（放各平台模块） |
| `dev.realfishing.mixin` | 少量注入 | 是（放各平台模块） |

---

## 6. 仓库骨架

```
F:\RealisticFishing\
├─ settings.gradle
├─ build.gradle
├─ gradle.properties
├─ gradlew / gradlew.bat / gradle/wrapper/
├─ SPEC.md                  ← 本文件
├─ ROADMAP.md               ← 里程碑与任务清单
├─ AGENTS.md                ← 给接手 agent 的工作规则
├─ docs/
│  ├─ mixins.md             ← mixin 登记表（L2 要求）
│  └─ tuning.md             ← 手感参数调参记录
├─ common/
│  ├─ build.gradle
│  └─ src/
│     ├─ main/java/dev/realfishing/...        （见 §5 包结构）
│     ├─ main/resources/
│     │  ├─ realfishing.mixins.json           （common 侧 mixin，若有）
│     │  ├─ assets/realfishing/...
│     │  └─ data/realfishing/fish/*.json
│     └─ test/java/dev/realfishing/physics/   （JUnit 5，纯算法）
├─ fabric/
│  ├─ build.gradle
│  └─ src/main/
│     ├─ java/dev/realfishing/fabric/
│     │  ├─ RealFishingFabric.java
│     │  ├─ FabricPlatform.java
│     │  └─ client/...
│     └─ resources/fabric.mod.json
└─ forge/
   ├─ build.gradle
   └─ src/main/
      ├─ java/dev/realfishing/forge/
      │  ├─ RealFishingForge.java
      │  ├─ ForgePlatform.java
      │  └─ client/...
      └─ resources/META-INF/mods.toml
```

---

## 7. 构建配置

### 7.1 版本锁定（`gradle.properties`）

```properties
# 平台
minecraft_version=1.20.1
enabled_platforms=fabric,forge
java_version=17

# 模组
mod_version=0.1.0
maven_group=dev.realfishing
archives_base_name=realfishing

# 依赖（1.20.1 时代的最后一批稳定版本）
architectury_api_version=9.2.14
fabric_loader_version=0.16.10
fabric_api_version=0.92.12+1.20.1
forge_version=47.3.33
```

> Fabric Loader 用 0.16.x 即可（0.20.x 也兼容，但整合包环境以 0.16 更稳）；Forge **47.3.33 必须与服务器一致**（用户服务器就是 `forge-1.20.1-47.3.33`）。

### 7.2 `settings.gradle`

```groovy
pluginManagement {
    repositories {
        maven { url "https://maven.architectury.dev/" }
        maven { url "https://maven.fabricmc.net/" }
        maven { url "https://maven.minecraftforge.net/" }
        gradlePluginPortal()
    }
}
rootProject.name = "realfishing"
include("common", "fabric", "forge")
```

### 7.3 根 `build.gradle`

```groovy
plugins {
    id "architectury-plugin" version "3.4-SNAPSHOT"
    id "dev.architectury.loom" version "1.7.417" apply false
}

architectury {
    minecraft = rootProject.minecraft_version
}

subprojects {
    apply plugin: "dev.architectury.loom"
    apply plugin: "java"

    loom { silentMojangMappingsLicense() }

    repositories {
        maven { url "https://maven.architectury.dev/" }
        maven { url "https://maven.fabricmc.net/" }
        maven { url "https://maven.minecraftforge.net/" }
    }

    dependencies {
        minecraft "com.mojang:minecraft:${rootProject.minecraft_version}"
        mappings loom.officialMojangMappings()
        testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"
    }

    java {
        toolchain { languageVersion = JavaLanguageVersion.of(17) }
        withSourcesJar()
    }

    tasks.withType(JavaCompile).configureEach {
        options.encoding = "UTF-8"
        options.release = 17
    }

    test { useJUnitPlatform() }
}

allprojects {
    version = rootProject.mod_version
    group = rootProject.maven_group
}
```

**关键点**
- `architectury-plugin` 负责生成 `architectury.common.json`（不要手写）。
- Loom 版本 `1.7.417` 支持 Gradle 8.6+；若拉取失败，改用 Architectury 官方模板里的版本（模板仓库 `architectury/architectury-templates`，选 `1.20.1` + `fabric,forge`），**不要自己猜版本**。
- 统一 JUnit 5，物理求解器必须能在无 MC 环境跑测试。

### 7.4 子项目

`common/build.gradle`
```groovy
architectury { common(rootProject.enabled_platforms.split(",")) }

dependencies {
    modImplementation "dev.architectury:architectury:${rootProject.architectury_api_version}"
}
```

`fabric/build.gradle`
```groovy
architectury { fabric() }

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath
    runs {
        client { client(); runDir "run" }
        server { server(); runDir "run-server" }
    }
}

dependencies {
    modImplementation "net.fabricmc:fabric-loader:${rootProject.fabric_loader_version}"
    modApi "net.fabricmc.fabric-api:fabric-api:${rootProject.fabric_api_version}"
    modApi "dev.architectury:architectury-fabric:${rootProject.architectury_api_version}"
}
```

`forge/build.gradle`
```groovy
architectury { forge() }

loom {
    forge {
        mixinConfig "realfishing.mixins.json"
        mixinConfig "realfishing-client.mixins.json"
    }
}

dependencies {
    forge "net.minecraftforge:forge:${rootProject.minecraft_version}-${rootProject.forge_version}"
    modApi "dev.architectury:architectury-forge:${rootProject.architectury_api_version}"
}
```

> Forge 侧 mixin 必须在 `loom.forge.mixinConfig` 里声明，并确保 jar 里带上 `META-INF/mods.toml` 的 `[[mixins]]`（Loom 会处理，跑一次 `:forge:build` 后检查 jar 内容）。

### 7.5 Architectury 9.2.14 的 API 核对清单

9.2.14 不再更新，方法名以 jar 为准。开工第一步用 `javap` 确认：

```powershell
javap -cp <architectury-fabric-9.2.14.jar> dev.architectury.networking.NetworkManager
javap -cp <...> dev.architectury.registry.registries.DeferredRegister
javap -cp <...> dev.architectury.event.events.client.ClientTickEvent
```

需要确认的三件事：
1. `NetworkManager.registerReceiver(Side, ResourceLocation, Receiver)` 的准确签名与 `PacketContext` 用法；
2. `DeferredRegister.create(Registries.ITEM, "realfishing")` 与 `.register()` 的调用方式；
3. `NetworkManager.registerS2CPayloadType` / `registerC2SPayloadType` 是否需要显式声明（9.x 有的版本要，有的不要）。

> 不确定就用最小可运行 demo 验证：发一个空包，客户端打印日志。别在没验证的情况下写完整协议。

### 7.6 运行

```powershell
.\gradlew :fabric:runClient
.\gradlew :forge:runClient
.\gradlew :fabric:runServer      # 双端联机验证
.\gradlew :common:test           # 物理单测，秒级
.\gradlew build                  # 产出 fabric/forge 两个 jar
```

---

## 8. 平台抽象层

`common` 只用 Architectury 能覆盖的东西；覆盖不到的走接口 + `ServiceLoader` 或平台入口注入。

```java
package dev.realfishing.platform;

public interface IPlatform {
    String loaderName();                 // "fabric" / "forge"
    boolean isClient();
    java.nio.file.Path configDir();

    /** 客户端每帧调用，用于相机与手部渲染；服务端为空实现 */
    default void registerClientHooks(ClientHooks hooks) {}
}
```

```java
package dev.realfishing.platform;

public interface ClientHooks {
    /** 每帧在相机 setup 之后调用，允许修改 yaw/pitch/roll */
    void onCameraSetup(CameraContext ctx);

    /** 返回 true 表示取消原版第一人称手部渲染（只对持竿玩家） */
    boolean shouldCancelHandRender();

    /** 世界渲染阶段（相机已就位），用于画竿与线 */
    void onWorldRender(RenderContext ctx);
}
```

**两端实现要点**

| 钩子 | Forge | Fabric |
|---|---|---|
| 相机角度 | `ViewportEvent.ComputeCameraAngles`（可 setYaw/setPitch/setRoll） | mixin `Camera#setup`（`@Inject(at=TAIL)`），或 `WorldRenderEvents.START` 前置计算 |
| 取消手部渲染 | `RenderHandEvent` → `event.setCanceled(true)` | mixin `ItemInHandRenderer#renderHandsWithItems` → `ci.cancel()` |
| 世界渲染 | `RenderLevelStageEvent`（`Stage.AFTER_ENTITIES`） | `WorldRenderEvents.AFTER_ENTITIES` |
| 按键 | `RegisterKeyMappingsEvent` | `KeyBindingHelper.registerKeyBinding` |
| 客户端 tick | `ClientTickEvent`（Architectury 通用） | 同左 |

> **Forge 相机事件只在 `Entity` 视角下触发**，旁观者模式要单独判断；Fabric 的 `Camera#setup` 在 `GameRenderer#renderLevel` 里调用，注意别在 `setup` 里读 `camera.getPosition()`（此时还没写回）。
>
> 这是全项目**唯一**需要平台分叉的地方。其他逻辑一律走 Architectury。

---

## 9. 玩法状态机

### 9.1 状态

```java
public enum FishingState {
    IDLE,       // 没拿竿 / 没抛
    CHARGING,   // 按住右键蓄力（0.6–2.0 s）
    CASTING,    // 浮漂/假饵飞行中
    WAITING,    // 入水等待咬钩（台钓：等漂；路亚：还没开始收线）
    RETRIEVING, // 【仅路亚·v0.2】收线中，假饵在游，鱼在游动中咬
    BITE,       // 咬钩窗口（0.8 s 内右键提竿）
    FIGHTING,   // 搏鱼
    LANDING,    // 收线到岸（<2 格）
    SNAPPED     // 断线收场（0.5 s 后回 IDLE）
}
```

> `RETRIEVING` 是**路亚独有**的状态，v0.1 的 `FloatRodItem` 永远不会进入它。加这个枚举值不违反"一根一根做"——先留位，不写转移逻辑（M9 再补）。

### 9.2 转移表

| 从 | 触发 | 守卫 | 到 | 权威侧 |
|---|---|---|---|---|
| IDLE | 右键按下（持竿） | 不在冷却、有饵 | CHARGING | 客户端预测 + 服务端确认 |
| CHARGING | 松开右键 | 蓄力 ≥ 0.15 s | CASTING | 服务端 |
| CHARGING | 松开右键 | 蓄力 < 0.15 s | IDLE | 服务端 |
| CASTING | 浮漂入水/落地 | — | WAITING | 服务端 |
| CASTING | 飞行 > 5 s | — | IDLE（回收） | 服务端 |
| WAITING | 计时到 | 命中鱼表 | BITE | 服务端 |
| WAITING | 计时到 | 未命中（空钩） | WAITING（重掷） | 服务端 |
| BITE | 右键 | 在 0.8 s 窗口内 | FIGHTING | 服务端 |
| BITE | 窗口结束 | — | WAITING（鱼跑） | 服务端 |
| WAITING | 收线（**仅路亚·v0.2**） | `rodStyle == LURE` | RETRIEVING | 服务端 |
| RETRIEVING | 停止收线（**仅路亚**） | — | WAITING（假饵悬停/下沉） | 服务端 |
| RETRIEVING | 鱼咬（**仅路亚**） | 命中 `lure` 鱼表 | FIGHTING（0.6 s 反应窗） | 服务端 |
| FIGHTING | 张力 > 1.0 且持续 0.25 s | — | SNAPPED | 服务端 |
| FIGHTING | 鱼体力 = 0 且距离 < 2 | — | LANDING | 服务端 |
| FIGHTING | 玩家松开/换手/死亡 | — | IDLE | 服务端 |
| LANDING | 动画结束 | — | IDLE（给战利品） | 服务端 |
| SNAPPED | 0.5 s | — | IDLE | 服务端 |

### 9.3 会话对象

```java
public final class FishingSession {
    public final UUID playerId;
    public FishingState state = FishingState.IDLE;
    public int sessionId;              // 自增，用于丢弃过期包
    public float charge;               // 0..1
    public double lineLength;          // 当前放出长度（格）
    public double drag;                // 泄力 0..1
    public double tension;             // 0..1.5
    public double fishStamina;         // 0..1
    public FishProfile fish;           // 咬钩后才有
    public FishingLine line;           // 服务端模拟
    public RodStyle rodStyle = RodStyle.FLOAT;  // 会话开始时从主手物品快照，之后不随换手变化
    public long stateSince;            // tick
}
```

- 服务端：`Map<UUID, FishingSession>` 放在 `ServerLevel` 的 `SavedData`（`FishingSavedData`）里，登出保存、重启恢复。
- 客户端：只保留只读副本 `ClientFishingState`，用于 HUD/渲染。

### 9.4 每 tick 顺序（服务端）

```
1. 读输入包（C2S: Cast / Reel / SetDrag / Release），合并到 session
2. 状态机 tick（计时、转移）
3. 物理 tick：line.solve(dt) → tension
4. 判罚：断线 / 上鱼 / 超时
5. 按 10 Hz 发 S2C 状态包（不是每 tick）
```

---

## 10. 鱼线物理

### 10.1 参数

| 参数 | 值 | 说明 |
|---|---|---|
| `NODES` | 24 | 首节点在竿尖，末节点在浮漂/鱼 |
| `DT` | 1/60 s | 固定步长（1/120 更稳但性价比低，见下） |
| `MAX_SUBSTEPS` | 4 | 每 tick 最多 4 次（20 Hz 下实际用 3 次） |
| `ITERATIONS` | 10 | 距离约束迭代 |
| `DAMPING` | 0.02 | 每子步速度衰减 |
| `GRAVITY` | 0.05 格/t² | 比实体重力（0.08）轻，线更飘 |
| `AIR_DRAG` | 0.0015 | 与速度平方成正比 |
| `WATER_DRAG` | 0.02 | 节点在水下时 |
| `MAX_STRAIN` | 0.12 | 拉伸 12% 视为满张力 |
| `BREAK_HOLD` | 0.25 s | 满张力持续多久断线 |

> **为什么是 1/60 而不是 1/120**：20 Hz 的 tick 内要覆盖 1/6 s；`DT=1/120` 需要 20 次子步，24 节点 × 20 × 10 迭代 = 4800 次约束求解/tick，收益却只有"更稳一点点"。`DT=1/60` + 3 次子步 = 720 次，肉眼无差别。若后续加大风或强水流，再降到 1/90 或 1/120 并压 `ITERATIONS`。

### 10.2 积分（Verlet）

```
v       = (p - pPrev) * (1 - DAMPING)
pPrev   = p
a       = GRAVITY + wind + drag(p, v)
p      += v + a * DT * DT
```

- `wind`：全局常量向量（默认 0），雷雨时乘以 2。
- `drag`：`-k * |v| * v`，`k` 在水下取 `WATER_DRAG`，否则 `AIR_DRAG`。
- 所有坐标用 `double`（`Vector3d` 或裸 `double[3]`），**不要用 float**——长线累积误差明显。

### 10.3 约束

```
for it in 0..ITERATIONS:
  for i in 0..NODES-2:
    d      = p[i+1] - p[i]
    len    = |d|
    diff   = (len - rest) / len * 0.5 * STIFFNESS   // STIFFNESS = 0.9
    if i != 0: p[i]   += d * diff          // 首节点钉死
    if i+1 != last: p[i+1] -= d * diff     // 末节点钉死
```

- `rest = lineLength / (NODES-1)`，随放线长度实时变化。
- 首节点每 tick 从竿尖世界坐标写入（含玩家移动的惯性）。
- 末节点：WAITING 时跟随浮漂实体位置；FIGHTING 时由鱼的运动驱动。

### 10.4 张力与断线

```
strain  = (len_i - rest) / rest          // 取所有段最大值
tension = clamp(strain / MAX_STRAIN, 0, 1.5)

if tension > 1.0: breakTimer += dt else breakTimer = 0
if breakTimer > BREAK_HOLD: → SNAPPED
```

- 张力曲线做**低通滤波**（`tension = lerp(tension, raw, 0.25)`），否则 HUD 抖得没法看。
- 断线时给末节点一个初速度，让线"甩出去"再消失（纯表现）。

### 10.5 水面与碰撞

- 水下判定：节点 `y < fluidTopAt(x,z)`（用 `level.getFluidState` 或简化的海平面 62/63）。
- 水下：`WATER_DRAG` + 轻微浮力（`a.y += 0.01`）。
- 与方块碰撞：**不做**。只做"节点低于地面高度时夹住"这一条（`y = max(y, groundY + 0.05)`），够用且便宜。
- 挂底（可选，v2）：末节点连续 2 s 速度 < 0.01 且张力 > 0.6 → 提示"挂底"，玩家可拉断。

### 10.6 渲染

- 位置插值：`alpha = accumulator / DT`，渲染用 `lerp(pPrev, p, alpha)`。
- 曲线：Catmull-Rom 穿过 24 个节点，细分 2 段 → 46 个点。
- 画法：相机朝向的四边形带（`VertexConsumer` + `RenderType.entityCutout` 或自建 `RenderType`），宽度 `0.02 + tension * 0.01` 格；不要用 `RenderType.lines()`（线宽在多数驱动上恒为 1px，且 Iris 下会被后处理吃掉）。
- 颜色：白色偏灰，水下段变暗。

### 10.7 为什么不用刚体

- Rapier 的 rope 也是"点链 + 约束"（Sable 的 `RapierRopeHandle` 提供 `create/readPose/addPoint/setAttachment`），本质相同。
- 刚体引擎会带来：世界碰撞查询、休眠、岛屿管理、序列化——这些对一根线全是负担。
- Verlet 的稳定性对"细长链"更好，且可以任意调阻尼做手感。

---

## 11. 搏鱼模型

### 11.1 玩家输入

| 操作 | 默认键 | 效果 |
|---|---|---|
| 收线 | 按住右键 | `lineLength -= reelSpeed * dt`（reelSpeed 1.2 格/s） |
| 抬竿（pump） | 左键点按 | 短时 `tension += 0.15`，把鱼往岸边拉 0.3 格 |
| 调泄力 | 滚轮 / `[` `]` | `drag` 0..1，步长 0.1 |
| 放线 | 潜行 + 右键 | `lineLength += 2.0 * dt` |
| 放弃 | 双击潜行 | 回 IDLE |

### 11.2 鱼的拉力

```
F_fish = strength * (1 + sprint * 0.6) * dir      // dir 单位向量，随鱼朝向
sprint  : 每 2–5 s 随机切换，持续 0.6–1.5 s
stamina : 每秒 -1/staminaSeconds，冲刺时 -2.5/staminaSeconds
stamina = 0 时 F_fish *= 0.35，且不再冲刺
```

鱼的朝向：朝"远离玩家"的方向游，每 0.3–1.0 s 加一次随机扰动（`turnRate` 控制），撞到岸边则折返。

### 11.3 收放线与张力耦合

```
if tension > drag:                       // 泄力起作用
    lineLength += (tension - drag) * 6.0 * dt    // 自动放线
else if reeling:
    lineLength -= reelSpeed * dt
lineLength = clamp(lineLength, 1.5, 80)
```

- `drag` 高 → 不容易断线，但鱼跑得远、遛鱼久。
- `drag` 低 → 收得快，但张力容易爆。
- 这是整个玩法的手感核心，参数放 `config`，方便调。

### 11.4 上鱼判定

```
if fishStamina <= 0 and distance(player, bobber) < 2.0 and tension < 0.6:
    → LANDING
```

`LANDING` 播 0.8 s 收竿动画，然后按 `loot` 表给物品，经验 1–6 点。

### 11.5 参数表（初值，允许 ±50% 调）

| 鱼档 | strength | staminaSeconds | turnRate | 说明 |
|---|---|---|---|---|
| 小鱼（鳟/鲈） | 0.25 | 8 | 0.8 | 3–5 s 上岸 |
| 中鱼（鲤/鲑） | 0.5 | 18 | 0.5 | 10–20 s |
| 大鱼（狗鱼/鲟） | 0.8 | 35 | 0.3 | 30–60 s，易断线 |
| 传说（可选） | 1.0 | 60 | 0.2 | 需要满级竿 |

---

## 12. 抛投

1. `CHARGING` 时 `charge` 线性增长到 1（2 s），HUD 显示力度条。
2. 松手：初速度 `v = lerp(9, 22, charge)` 格/s，方向 = 玩家视线 `+ (0, 0.15, 0)`（略微上抛）。
3. 客户端立即生成预测浮漂（`BobberEntity` 客户端副本），服务端权威实体稍后同步；两者用 §15.3 的平滑合并。
4. 飞行积分：`dt=1/120`，重力 0.08，空气阻力 0.002，风。
5. 入水/落地 → `WAITING`，`lineLength = 实际飞行距离`，播 splash 粒子与音效。
6. 若飞行 > 5 s 或超出 32 格 → 回收。

**两竿的差异（同一个抛投算法，不同参数，v0.2 才有 `lure` 分支）**：

| 参数 | `float_rod`（台钓） | `lure_rod`（路亚） |
|---|---|---|
| 竿长 | 4.5 格（长竿抛远） | 2.2 格（短竿抛准） |
| 初速 | `lerp(9, 22, charge)` | `lerp(7, 18, charge)`，且上限受假饵重量限制 |
| 落点 | 浮漂停住 | 假饵落水后**立刻开始** `RETRIEVING` 或等玩家收线 |
| 精度 | 低（浮漂随水流漂） | 高（假饵贴底/贴结构） |

> 代码上只是一个 `RodStyle` 参数表，不是两套抛投实现。

---

## 13. 鱼与咬钩（数据驱动）

### 13.1 JSON schema

`data/realfishing/fish/rainbow_trout.json`
```json
{
  "id": "realfishing:rainbow_trout",
  "display_name": "虹鳟",
  "strength": 0.3,
  "stamina_seconds": 9,
  "turn_rate": 0.7,
  "weight": 100,
  "conditions": {
    "biomes": ["#minecraft:is_river", "#minecraft:is_taiga"],
    "time": ["day", "dawn_dusk"],
    "weather": ["clear", "rain"],
    "min_water_depth": 2,
    "max_water_depth": 12,
    "bait": ["#realfishing:bait_insect"]
  },
  "loot": [
    { "item": "realfishing:raw_trout", "count": [1, 2], "chance": 0.85 },
    { "item": "minecraft:bone_meal", "count": [1, 3], "chance": 0.15 }
  ],
  "xp": [1, 4],
  "rod_styles": ["float"],          // 可省略 = 两种竿都能钓；路亚鱼写 ["lure"] 或 ["float","lure"]
  "lure_only": false                // 掠食鱼：只有假饵（路亚）会咬，v0.2 生效
}
```

### 13.2 选取算法

```
候选 = 所有 JSON 中 conditions 全部满足 且 rod_styles 含当前竿型 者
权重 = weight * baitMultiplier * timeMultiplier * weatherMultiplier
若 候选 为空 → 空钩（重掷一次咬钩计时）
否则按权重轮盘赌
```

- `baitMultiplier`：饵标签完全匹配 1.5，部分匹配 1.0，不匹配 0.5。
- 坏 JSON：捕获 `JsonParseException`，打 `ERROR` 日志并跳过该文件，**不崩**。
- **竿型过滤**（v0.1 只有 `float`，字段缺省视为两种都吃，便于 v0.2 加路亚时不改老数据）：`float` 竿吃 `rod_styles` 缺省或含 `float` 的鱼；`lure` 竿只吃含 `lure` 的鱼（掠食性）。

### 13.3 咬钩计时

```
delay = clamp(lognormal(mu = ln(6.0), sigma = 0.45), 3.0, 15.0)   // 秒
假咬概率 0.25：浮漂下沉 0.15 格 + 音效，0.5 s 后回弹，不进入 BITE
```

---

## 14. 渲染与手感

### 14.1 世界锚定持竿

- 持竿玩家：取消原版手部渲染（见 §8），在 `AFTER_ENTITIES` 里自己画。
- 竿的姿态 = 玩家 `bodyYaw` + 手臂偏移 + 竿的局部旋转；**不吃相机 pitch**，这样转头时竿留在世界里。
- 竿的 4 段骨骼由张力驱动弯曲：`bend = tension * 0.25 rad`，逐段递减。
- 手部：不做 IK，做 3 组固定 grip 偏移（idle / cast / fight），用插值切换。IK 是 2 周工作量，收益不匹配。
- **两根竿共用一个 `RodRenderer`**，按 `RodStyle` 换模型、长度、弯曲系数；路亚竿的竿尖抖动（收线/抽竿）在 M9 里作为 `RodStyle.LURE` 的分支加上，不改台钓路径。

### 14.2 相机 spring arm

```
target   = rodTip + (0, -0.12, 0)        // 相机看向竿尖下方
camPos   = criticallyDampedSpring(camPos, target, stiffness = 120, damping = 2*sqrt(120))
roll     += (rodRoll * 0.35 - roll) * (1 - exp(-8 * dt))
pitch    = clamp(pitch, -80, 80)
```

- 弹簧用**临界阻尼**，避免转头时相机晃。
- 只在 `FIGHTING` 时启用"相机跟随鱼"（张力大时轻微拉向鱼的方向，幅度 ≤ 5°）。
- 与 OptiFine/Iris 的兼容：不改投影矩阵，只改 yaw/pitch/roll。

### 14.3 音效与粒子清单

| 事件 | 音效 | 粒子 |
|---|---|---|
| 抛投 | `item.trident.throw`（变调 1.4） | 无 |
| 入水 | `entity.generic.splash` | `splash` |
| 咬钩 | `entity.fishing_bobber.splash` + 自定义 | 水花 |
| 收线 | 循环 `realfishing:reel`（自定义 ogg） | 无 |
| 断线 | `entity.item.break` + `realfishing:snap` | `crit` |
| 上鱼 | `entity.player.levelup`（变调 1.2） | `happy_villager` |

### 14.4 HUD

- 力度条（CHARGING）：屏幕下方 60×6 px。
- 张力条（FIGHTING）：右侧竖条，颜色 绿→黄→红。
- 鱼体力条：张力条旁边细条。
- 全部用 `GuiGraphics` 画，不引第三方 HUD 库。

### 14.5 鱼的 3D 模型（v0.2，答案：**便宜，但别做骨骼动画**）

结论先说：**静态 3D 鱼模型很便宜，动态摆尾也能白嫖，只有"逐鱼种手工 K 帧"很贵——而后者我们不做。**

| 做法 | 成本 | 评价 |
|---|---|---|
| 原版物品贴图（v0.1） | 0 | 够用，先用这个 |
| Blockbench 导 `Java Block/Item` JSON（静态鱼） | 10–20 分钟/种 | 便宜；一个 `FishModel` 类按 JSON 的 part 名驱动即可 |
| GeckoLib 骨骼动画 | — | **禁止**（第三方依赖） |
| 1.20.1 内置 `AnimationDefinition` + `KeyframeAnimation` 手写 K 帧 | 1–2 人日/种 | 能做，但**性价比极低**，且 1.20.1 的渲染侧支持很原始 |
| **程序化摆尾（推荐）** | 0.5 人日/骨架 | 尾巴按 `sin(t * freq + phase)` 转 X 轴，身体轻微 S 形；频率 = 游速，幅度 = 加速度 |

**推荐架构（一次做对，之后加鱼零代码）**：

1. **三种体型模板**，覆盖 90% 的鱼：`FUSIFORM`（纺锤，鳟/鲈）、`DEPRESSED`（侧扁，鲤/鲫）、`ELONGATED`（细长，鳗/狗鱼）。
2. 每种模板一个 `FishModel`（`EntityModel` 子类），part 名固定：`body / head / tail / finTop / finSide`。
3. 鱼的差异走**数据**：`texture`（贴图）+ `scale`（0.6–1.6）+ `tint`（颜色乘算）。
4. 摆尾在 `setupAnim` 里算，不读任何动画资源：

```java
float swing = Mth.sin(ageInTicks * 0.45f + tailPhase) * (0.25f + speedFactor * 0.5f);
tail.xRot = swing; body.yRot = swing * 0.08f; finSide.zRot = swing * 0.3f;
```

**真正麻烦的不是模型，是"鱼在哪"**：咬钩前水下要有鱼影，这需要实体 + AI + 生成规则（v0.2 的工作量在 AI 不在建模）。 v0.1 不做，避免把 6 人日花在一条看不见的鱼上。

---

## 15. 网络

### 15.1 包表

| 包 | 方向 | 字段 | 频率 |
|---|---|---|---|
| `CastC2S` | C→S | `float charge, float yaw, float pitch` | 事件 |
| `ReelC2S` | C→S | `boolean reeling, byte drag` | 5 Hz（变化时） |
| `ReleaseC2S` | C→S | — | 事件 |
| `SessionS2C` | S→C | `int sessionId, byte state, short bobberX/Y/Z (1/16 格), byte tension, byte stamina, short lineLength` | 10 Hz |
| `BiteS2C` | S→C | `int sessionId, boolean fake` | 事件 |
| `ResultS2C` | S→C | `byte result(land/snap), ItemStack` | 事件 |

### 15.2 关键设计：**不同步线节点**

- 客户端和服务端用**同一个** `FishingLine` 类、同一套参数。
- 同步的只有：竿尖（由玩家位置推出，两端一致）、浮漂位置（10 Hz + 客户端插值）、`lineLength`、`tension`。
- 客户端每帧用这三个输入跑本地求解器 → 线形状两端一致，**带宽省 95%**。
- 浮漂位置量化到 1/16 格（short），张力/体力量化到 byte（0–255 映射 0–1.5）。

### 15.3 平滑与纠偏

- 客户端对 `bobber` 用**指数平滑**追踪服务端位置：`pos = lerp(pos, serverPos, 1 - exp(-15*dt))`。
- 误差 > 3 格时瞬移（防止卡在墙里）。
- `sessionId` 不匹配的包直接丢弃（防止旧会话污染）。

### 15.4 反作弊边界

- 服务端校验：抛投距离 ≤ 32 格、收线速率 ≤ 上限、`drag` 在 0..1。
- 客户端上报的只有"意图"，结果全在服务端算。

---

## 16. 配置

`config/realfishing.json`
```json
{
  "line": { "nodes": 24, "gravity": 0.05, "maxStrain": 0.12, "breakHold": 0.25 },
  "fight": { "reelSpeed": 1.2, "dragStep": 0.1, "pumpImpulse": 0.15 },
  "cast": { "minVelocity": 9.0, "maxVelocity": 22.0, "maxDistance": 32 },
  "bite": { "minDelay": 3.0, "maxDelay": 15.0, "fakeChance": 0.25 },
  "client": { "cameraSpringStiffness": 120, "lineWidth": 0.02 }
}
```

- 加载失败 → 用内置默认值 + 日志警告。
- `/realfishing reload` 重载；`client` 段只在客户端生效，其余服务端权威并在登录时下发。
- 不做 GUI。

---

## 17. 兼容性

### 17.1 兼容分三档（v0.1 只做第 1 档，但第 2/3 档的口子必须留好）

| 档 | 目标 | 成本 | 何时 |
|---|---|---|---|
| **A · 不冲突** | 同装能启动、各钓各的、互不覆盖原版 | 低（不 mixin 别人、不改原版、独立 id） | **v0.1 必须** |
| **B · 数据互通** | 我们的竿能钓到别的模组的鱼（以物品形式）；别的模组能通过 JSON 注册到我们的鱼表 | 中（tag + JSON 解析 + 物品映射） | **v0.1 留接口**，v0.2 实现 |
| **C · 行为互通** | 别的模组的鱼实体参与我们的搏鱼（用它们的模型、我们的物理） | 高（要读实体属性/自定义 adapter） | v0.3+，按需 |

**留口子的具体做法（v0.1 就写进去，各 20 行以内）**：

1. 两个 tag：`#realfishing:catchable_entities`（实体 id）与 `#realfishing:fish_items`（物品 id），数据包可追加。
2. 鱼表 JSON 的 `result` 字段允许写**任意** item id，不校验命名空间 → 别的模组的鱼物品直接可用。
3. `FishProfile` 里 `entity` 字段可空；为空时只产出物品、不生成实体。
4. 一个 8 行的 `RealFishingAPI.register(...)` 静态入口（v0.1 只做空壳 + javadoc），供 v0.2 的代码级接入。

> 这四条加起来不到 100 行，但决定了"以后能不能加"，现在不写、以后就要改协议。

### 17.2 其他模组"加新鱼"怎么接（这是最常见的需求）

现实里有四种形态，我们分别怎么处理：

| 形态 | 例子 | 处理 |
|---|---|---|
| A. 只加**物品**（生鱼/熟鱼） | 多数食物类模组 | **零成本**：JSON `result` 写它的 item id，直接能钓到 |
| B. 加**实体**（会游的鱼） | Aquaculture、Starcatcher 的部分鱼 | 中成本：`catchable_entities` tag + 一条 JSON 引用其 entity id；上鱼时生成该实体再用桶/物品收 |
| C. 加**自己的钓鱼系统** | Aquaculture 的竿、Starcatcher 的机制 | 不接：两套系统并行，谁的竿用谁的规则（这就是 A 档"不冲突"） |
| D. 提供**API** 让别人注册鱼 | 少数库型模组 | 我们提供 `RealFishingAPI`，它们调我们 |

**关键结论**：只要"钓上来的东西"是 `ItemStack`，跨模组兼容就几乎是免费的——因为 MC 的战利品本来就是物品。真正贵的只有"让别人的鱼实体按我们的搏鱼逻辑挣扎"（C 档），那个 v0.3 再说。

### 17.3 兼容预算

- v0.1 允许为兼容写的代码：**≤150 行**（tag 读取 + JSON 宽松解析 + 空壳 API）。
- 不允许为了兼容去 mixin 别人的类、不允许 `@Redirect` 别人的方法、不允许反射改别人的字段。
- 兼容测试用例固定三个：`starcatcher`、`aquaculture`、`原版`（见 §19.2）。

### 17.4 兼容矩阵

| 对象 | 策略 |
|---|---|
| 原版 `FishingHook` | 完全不碰；原版竿照常工作 |
| `starcatcher` / `aquaculture` | 不 mixin 它们的类；它们的竿/战利品不受影响；我们的竿用独立 tag `#realfishing:rods` |
| Sodium / Iris | 只用自定义 `RenderType` + 顶点缓冲，不改 shader 管线；线的渲染放 `AFTER_ENTITIES`（Iris 阴影 pass 之外） |
| OptiFine | 不承诺；相机只改角度，理论上兼容 |
| 其他模组的"鱼" | 见 §17.2：物品零成本、实体走 tag、系统级不接 |
| 服务端无客户端模组 | 服务端可独立运行（物理全在服务端），客户端无模组则看不到线（正常） |

**Forge/Fabric 差异清单**（写代码前先看这张表）

| 能力 | Forge | Fabric |
|---|---|---|
| 注册表 | `DeferredRegister`（Architectury 统一） | 同左 |
| 网络 | Architectury `NetworkManager` | 同左 |
| 相机 | `ViewportEvent.ComputeCameraAngles` | mixin `Camera` |
| 手部渲染取消 | `RenderHandEvent` | mixin `ItemInHandRenderer` |
| 客户端 tick | Architectury `ClientTickEvent` | 同左 |
| 配置目录 | `FMLPaths.CONFIGDIR` | `FabricLoader.getInstance().getConfigDir()` |
| 实体渲染注册 | `EntityRenderersEvent.RegisterRenderers` | `EntityRendererRegistry.register` |

---

## 18. 性能预算

| 项 | 预算 | 测量 |
|---|---|---|
| 物理（24 节点 × 3 子步 × 10 迭代） | < 0.05 ms/tick | JFR 或 `System.nanoTime` 打点 |
| 渲染（46 段线 + 竿） | < 0.15 ms/frame | Spark |
| 网络 | < 300 B/s/玩家 | 抓包/日志计数 |
| 内存 | < 2 MB 常驻（无每帧分配） | 避免 `new Vector3d` 热路径，用对象池/裸数组 |
| GC | 无每帧分配 | JFR 分配采样 |

> 热路径禁止：`Stream`、装箱、`String.format`、`new` 在 `solve()` 内。物理类全部用 `double[]` 平铺数组。

---

## 19. 测试

### 19.1 单元测试（`common/src/test`，不依赖 MC）

- `VerletSolverTest`：静止链应保持 rest 长度（误差 < 1e-6）；单端固定时自由端下垂角度合理；10 万步不发散、不出 NaN。
- `TensionTest`：拉伸 12% → tension ≈ 1.0；断线计时器行为。
- `FishTableTest`：权重轮盘赌分布（10 万次采样，偏差 < 2%）；坏 JSON 被跳过。
- `CastTest`：45° 抛射，`v=9/22` 的射程落在预期区间。

### 19.2 手动清单（每个里程碑跑一次）

1. 单人创造：拿竿 → 蓄力 → 抛 → 入水 → 等咬 → 提竿 → 搏鱼 → 上鱼。
2. 断线：把泄力调到 0，硬拉，确认 0.25 s 后断。
3. 换维度/死亡/退出重进：会话正确清理，无幽灵浮漂。
4. 双人：A 钓鱼时 B 在旁边看，A 的线在 B 屏幕上形状一致。
5. 专用服务器：`runServer` + 两个客户端，30 分钟。
6. 与 starcatcher/aquaculture 同装：各自钓 10 次。

### 19.3 回归基线

每个里程碑结束打 tag（`m0`…`m6`），保留 `docs/tuning.md` 的参数快照，方便回滚手感。

---

## 20. 里程碑

| 里程碑 | 内容 | DoD | 人日 |
|---|---|---|---|
| **M0 骨架** | 三模块 Gradle 跑通；空物品/实体注册；双端 `runClient` 进世界；`/realfishing` 命令 | 两端都能加载，日志无报错 | 2 |
| **M1 抛投** | 状态机 IDLE→CHARGING→CASTING→WAITING；蓄力条；浮漂实体与入水 | AC-2 达成 | 5 |
| **M2 鱼线** | Verlet 求解器 + 渲染 + 单测；竿尖跟随 | AC-3 达成；单测全绿 | 6 |
| **M3 搏鱼** | 张力/泄力/断线/上鱼；鱼 AI 简版 | AC-4 达成 | 7 |
| **M4 内容（台钓竿）** | `float_rod` + 鱼表 JSON × 8、战利品、音效、配方、创造标签 | 8 种鱼可钓，坏 JSON 不崩 | 4 |
| **M5 手感** | 世界锚定竿、相机 spring arm、动画切换、HUD 打磨 | AC-1 达成；录屏对比通过 | 6 |
| **M6 收尾 + 兼容口子** | 双端联机回归、性能采样、**§17.1 四个扩展点**、文档 | AC-5/6/7/8 全达成；外部 item 能经 JSON 钓到 | 8 |
| **M7 可选** | 挂底、鱼群、天气影响、更多鱼 | — | +6 |
| **M8 = v0.2** | 跨模组鱼接入（实体/物品）、三种体型 `FishModel`、程序化摆尾、鱼影 AI | 装 2 个外部钓鱼模组能钓到它们的鱼 | +9 |
| **M9 = v0.2** | **路亚竿** `lure_rod`：`RETRIEVING` 状态、假饵物品、抽竿动作、掠食鱼权重 | 假饵收线能上鱼，与台钓竿手感明显不同 | +8 |

**合计：v0.1 核心 38 人日（M0–M6）；含 M7 与缓冲 ≈ 48 人日；v0.2 两个模块 M8（+9）+ M9（+8）= 17 人日。**

### 20.1 双竿的推进次序（**一根一根做**）

```
M0–M6  ──►  v0.1：只有 float_rod（台钓）能玩
                │
                ├─► M8（兼容 + 3D 鱼）──┐  两者互不依赖，
                └─► M9（路亚竿）────────┘  可并行也可串行
```

- **不允许**在 M4 之前插入 `lure_rod` 的物品占位——v0.1 创造栏里只出现 `float_rod` 一根竿。
- M9 开工前提：M3 的搏鱼内核冻结、M6 的四个兼容接口已落地（路亚要复用 `entity` 可空与 tag）。
- M9 期间**不改** `FloatRodItem` 的行为，只加 `LureRodItem` 与 `RETRIEVING` 分支；改动冲突时以"台钓行为不变"为准。

> 排期建议：M0–M2 必须串行；M3 与 M4 可并行（一个人写搏鱼，一个人写数据/资源）；M5 必须在 M3 之后（要真实张力数据才能调相机）。

---

## 21. 风险登记

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| Forge 相机事件与 Iris 冲突 | 中 | 高 | 只改角度不改矩阵；Iris 环境单独测 |
| Architectury 9.2.14 网络 API 与文档不符 | 中 | 中 | §7.5 的 `javap` 核对 + 空包 demo，先验证再写协议 |
| 手感调参反复，超出 6 人日 | 高 | 中 | 参数全进配置；`docs/tuning.md` 记录每次改动与主观评分 |
| 双端构建环境（JDK/Gradle/loom）折腾 | 中 | 中 | 从官方模板起步，不手搓；CI 只跑 `:common:test` + `build` |
| 鱼线在高延迟下抖动 | 中 | 中 | 客户端本地求解 + 端点平滑；不做逐节点同步 |
| 与 starcatcher 的物品/配方冲突 | 低 | 中 | 独立 id + 独立 tag；配方用 `conditions` 避免重复 |
| 客户端崩溃（物理 NaN） | 低 | 高 | §4 L3 的降级路径 + 单测覆盖 |

---

## 22. 交接：新工作区的第一小时

1. **环境**：JDK 17（`java -version` 确认），Gradle 用 wrapper。
2. **起步**：优先从 Architectury 官方模板生成 1.20.1 + fabric,forge 工程，再把本文件的包结构套进去；**不要**从零手写 `build.gradle`。
3. **验证顺序**：
   ```powershell
   .\gradlew :common:test          # 先让单测能跑
   .\gradlew :fabric:runClient     # 再让客户端能进
   .\gradlew :forge:runClient
   ```
4. **第一个 PR**：M0 骨架 + 一个能右键打日志的空竿。
5. **每个 PR 的检查**：`common` 里没有 `net.fabricmc` / `net.minecraftforge` import；mixin 数量没有增长（或已登记）；`docs/tuning.md` 有更新（若动了参数）。
6. **不要做的事**：不引新依赖、不上原生库、不改原版钓鱼、不做 GUI 配置。

---

## 附录 A · 关键类骨架

```java
// dev.realfishing.physics.FishingLine —— 纯 Java，无 MC 依赖，可单测
public final class FishingLine {
    public static final int NODES = 24;

    private final double[] px = new double[NODES];  // 位置（平铺，避免对象分配）
    private final double[] py = new double[NODES];
    private final double[] pz = new double[NODES];
    private final double[] ox = new double[NODES];  // 上一帧
    private final double[] oy = new double[NODES];
    private final double[] oz = new double[NODES];

    private double restLength;   // 每段静止长度
    private double tension;      // 低通后的张力
    private double breakTimer;

    public void reset(Vector3d tip, Vector3d end);
    public void setEndpoints(Vector3d tip, Vector3d end);
    public void setLength(double totalLength);
    public void solve(double dt, int substeps, Vector3d wind, FluidQuery fluid);
    public double tension();
    public boolean shouldSnap();
    public void writeRenderPositions(float alpha, double[] out);
}
```

```java
// dev.realfishing.fishing.FightModel —— 服务端每 tick 调用
public final class FightModel {
    public void tick(FishingSession s, PlayerInput in, double dt) {
        // 1. 鱼体力/冲刺
        // 2. 收放线
        // 3. 更新 lineLength，重设 line.restLength
        // 4. line.solve(...)
        // 5. tension 低通
    }
}
```

```java
// dev.realfishing.net.SessionS2C
public record SessionS2C(int sessionId, byte state, short bx, short by, short bz,
                         byte tension, byte stamina, short lineLength) {
    public static final ResourceLocation ID = new ResourceLocation("realfishing", "session");
}
```

## 附录 B · 可调参数总表（手感旋钮）

| 旋钮 | 默认 | 调大的效果 |
|---|---|---|
| `line.gravity` | 0.05 | 线更垂、抛投更短 |
| `line.maxStrain` | 0.12 | 更难断线 |
| `fight.reelSpeed` | 1.2 | 收线更快、张力涨得更快 |
| `fight.pumpImpulse` | 0.15 | 抬竿收益更大 |
| `bite.fakeChance` | 0.25 | 假咬更多、更折磨 |
| `cast.maxVelocity` | 22 | 抛得更远 |
| `client.cameraSpringStiffness` | 120 | 相机跟手更硬、更晕 |

## 附录 C · 命名约定

- 类：`PascalCase`；包全小写；常量 `UPPER_SNAKE`。
- 网络包：`XxxC2S` / `XxxS2C`，`record` + 静态 `ID` + `encode/decode`。
- 注册 id：`realfishing:<snake_case>`。
- Mixin 类名：`<Target>Mixin`，放 `dev.realfishing.mixin` 或 `...mixin.client`。
- 语言文件：`en_us.json` 与 `zh_cn.json` 同时维护。
