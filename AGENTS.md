# AGENTS.md · 写实钓鱼项目工作规则

> 本文件是给接手这个仓库的 AI/人类协作者的**硬规则**。
> 开工前先读：`SPEC.md`（设计）→ `ROADMAP.md`（任务）→ 本文件（纪律）。

## 1. 项目一句话

Minecraft **1.20.1** 的写实钓鱼模组，**Fabric + Forge 双端**，**Architectury** 三层结构，**纯 Java 物理**，依赖仅 Fabric API + Architectury API。

## 2. 不可协商的约束

| # | 约束 | 违反后果 |
|---|---|---|
| 1 | **不允许任何原生库 / JNI / Rust / C++** | 平台矩阵、加载崩溃、杀软误报——见 SPEC §4 L1 |
| 2 | **不允许新增第三方 Java 依赖** | 需求明确只允许 Fabric API + Architectury API |
| 3 | **不允许改原版 `FishingHook` / `minecraft:fishing_rod`** | 与 starcatcher/aquaculture 必冲突 |
| 4 | **mixin 总数 ≤ 8**，且不得注入其他模组的类 | 兼容性负债，见 SPEC §4 L2 |
| 5 | `common` 模块里不得出现 `net.fabricmc.*` / `net.minecraftforge.*` import | 破坏三层结构 |
| 6 | 不支持 1.20.1 以外的版本 | 不做跨版本兼容承诺 |
| 7 | 物理热路径不得每帧分配对象（`new`/`Stream`/装箱） | 性能预算，见 SPEC §18 |

## 3. 构建与验证

```powershell
.\gradlew :common:test        # 物理/数据单测，秒级，最常跑
.\gradlew :fabric:runClient   # Fabric 客户端
.\gradlew :forge:runClient    # Forge 客户端
.\gradlew :fabric:runServer   # 双端联机
.\gradlew build               # 两个平台 jar
```

- 工具链 **JDK 17**；Gradle 用 wrapper，不装全局版本。
- 首次构建若 Loom/Architectury 版本拉不到：改用 Architectury 官方模板（`architectury/architectury-templates`，1.20.1 + fabric,forge）的版本号，**不要自己猜**。
- Architectury 9.2.14 的方法签名先 `javap` 核对（SPEC §7.5），别照抄网上 1.19 的示例。

## 4. 改动前必须回答的三个问题

1. 这段逻辑属于 `common`（玩法/物理/数据）还是平台层（相机/手部渲染/按键）？——平台相关的东西只准放 `fabric`/`forge`。
2. 能不能用事件/Architectury API 解决？——能就不用 mixin；用了就要在 `docs/mixins.md` 登记：目标类、注入点、为什么事件做不到。
3. 出异常时降级成什么？——物理路径必须 try/catch 到"直线鱼线 + 禁用搏鱼物理 + 日志"，不允许崩。

## 5. 代码约定

- 包名 `dev.realfishing.*`；类 `PascalCase`；常量 `UPPER_SNAKE`；资源 id `realfishing:snake_case`。
- 网络包用 `record` + 静态 `ID` + `encode/decode`，命名 `XxxC2S` / `XxxS2C`。
- 物理用 `double[]` 平铺数组 + 索引，不用 `List<Vector3d>` 存节点。
- 每个公开类写一句 Javadoc 说明"它负责什么、不负责什么"。
- 注释用中文；标识符用英文。
- 语言文件 `en_us.json` 与 `zh_cn.json` 同步维护。

## 6. 完成的定义（DoD）

一个任务算完成，必须同时满足：

1. `.\gradlew :common:test` 通过（若动了物理/数据）；
2. 双端至少 `runClient` 能进世界（若动了注册/渲染/网络）；
3. 对应的 `ROADMAP.md` 勾选 + 若动了参数则更新 `docs/tuning.md`；
4. 没有新增依赖、没有新增未登记的 mixin；
5. 提交信息格式：`<模块>: <做了什么>`，例如 `physics: 加入张力低通滤波`。

## 7. 调试入口

- 物理：`/realfishing debug line` 打开节点可视化（粒子/线段），`/realfishing debug state` 打印会话字段。
- 网络：`/realfishing debug packets` 打印收发计数与字节数。
- 鱼表：`/realfishing fish list`、`/realfishing fish test <biome> <time> <weather>` 直接看权重结果。
- 这些命令在 M0 就建好骨架，后面逐步填充——**不要用 `System.out` 调试**。

## 8. 常见坑（踩过就别再踩）

1. **Forge 相机事件**只在实体视角触发，旁观者要单独判断；不要在事件里读 `camera.getPosition()` 再写回，会打架。
2. **Fabric 的 `Camera#setup`** 在 `GameRenderer#renderLevel` 中调用，此时 `camera.getPosition()` 还没更新到本帧最终值。
3. **`ItemInHandRenderer` 的取消**只对持竿玩家生效，否则会把空手也藏了。
4. **线的渲染**别用 `RenderType.lines()`：线宽在多数驱动恒为 1px，Iris 下还会被后处理吃掉。
5. **浮漂实体**要设 `noPhysics`/自定义碰撞，别让它被水流推着跑。
6. **会话清理**：玩家死亡、换维度、退出、服务器重载都要清 `FishingSession`，否则会留下幽灵浮漂和永久 HUD。
7. **`sessionId`**：所有 S2C 包带 id，客户端丢弃过期包，否则快速重抛会串状态。

## 9. 不要做的事

- 不要为了"更真实"引入物理引擎（Rapier/Bullet/JBox2D）。
- 不要做配置 GUI（Cloth Config 是依赖，禁止）。
- 不要写"兼容层"去适配其他钓鱼模组——v0.1 只保证不冲突；但 §17.1 的四个扩展点（两个 tag、宽松 JSON、可空 entity、空壳 API）**必须写**，那是 v0.2 的低成本入口。
- 不要在没有单测的情况下改 `FishingLine` 的积分或约束。
- 不要一次性写完全部协议再测——先发一个空包验证通道（SPEC §7.5）。
- **不要在 v0.1 里注册 `lure_rod`**——两根竿一根一根做，路亚竿是 M9 的第一个任务；提前注册会导致创造栏出现空壳物品。

## 10. 未知项（需要现场确认）

- Architectury 9.2.14 的 `NetworkManager` 具体签名；
- Loom `1.7.417` 与 Gradle 8.6 的组合是否可用（不行就退回模板版本）；
- 1.20.1 下 `ViewportEvent.ComputeCameraAngles` 与 Iris 的先后顺序。

> 这三项在 M0 第一小时就能验证，**先验证，再写代码**。
