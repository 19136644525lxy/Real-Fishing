# ROADMAP · 写实钓鱼

> 配套 `SPEC.md`。本文件只放**可勾选的任务**，设计依据全部在 SPEC 里。
> 约定：`[ ]` 未开始，`[x]` 完成，`[~]` 进行中；每个任务后面括号里是预估人日。

---

## M0 · 骨架（2 人日）

- [x] 用 Architectury 官方模板生成 1.20.1 + `fabric,forge` 工程（0.5）
- [x] 改成 `common / fabric / forge` 三模块，包名 `dev.realfishing`（0.5）
- [x] `gradle.properties` 按 SPEC §7.1 锁定版本（0.2）
- [x] 主类 + `IPlatform` 接口 + 两端实现（0.3）
- [x] 注册空物品 `realfishing:float_rod`（占位模型）与空实体 `realfishing:bobber`（0.3）
- [x] `/realfishing` 命令（打印版本/loader）（0.2）
- [x] 双端 `runClient` 进世界、`runServer` 起服（0.5）
- [x] 验证：`common` 中无 `net.fabricmc` / `net.minecraftforge` import（0.1）
- [x] **DoD**：两端加载无报错，能拿到空竿

## M1 · 抛投（5 人日）

- [x] `FishingState` 枚举 + `FishingSession` + 服务端 `SavedData`（0.5）
- [x] `RodItem` 基类 + `RodStyle` 枚举 + `FloatRodItem` 的 `use` / `releaseUsing` 接蓄力（0.6）
- [x] `CastC2S` 包 + 服务端校验（距离/速率）（0.5）
- [x] `BobberEntity`：物理（重力/阻力/风）、入水检测、splash（1.0）
- [x] 客户端预测浮漂 + 与服务端平滑合并（1.0）
- [x] 蓄力 HUD 力度条（0.5）
- [x] 状态机 IDLE→CHARGING→CASTING→WAITING 全链路（1.0）
- [x] **DoD**：AC-2 达成（8–24 格，误差 ≤1.5 格；`CastMathTest` + 自动抛投实测 charge=0.25 → 9.07 格）

## M2 · 鱼线（6 人日）

- [x] `FishingLine` Verlet 求解器（平铺数组，无每帧分配）（1.5）
- [x] 距离约束 + 端点钉死 + 水面阻力（1.0）
- [x] 张力计算 + 低通 + 断线计时（0.5）
- [x] JUnit：静止长度、下垂角、10 万步稳定性（1.0）
- [x] Catmull-Rom 细分 + 相机朝向四边形带渲染（1.0）
- [x] 竿尖世界坐标跟随（玩家移动惯性）（0.5）
- [x] 降级路径：求解异常 → 直线 + 日志（0.5）
- [x] **DoD**：AC-3 达成；`.\gradlew :common:test` 全绿（Fabric/Forge 双端客户端截图验证：线从竿尖出发、静止自然下垂）

## M3 · 搏鱼（7 人日）

- [x] `FightModel`：鱼体力/冲刺/朝向扰动（1.5）
- [x] 收线/放线/抬竿/泄力四种输入 + `ReelC2S`（1.0）
- [x] 张力-泄力耦合 + 自动放线（1.0）
- [x] 断线（0.25 s）+ 断线表现（音效/粒子/甩线）（0.5）
- [x] 上鱼判定 + 战利品发放 + 经验（0.5）
- [x] `SessionS2C` 10 Hz 同步 + `sessionId` 过滤（1.0）
- [x] 张力/体力 HUD（0.5）
- [x] 手动调参一轮（记入 `docs/tuning.md`）（1.0）
- [x] **DoD**：AC-4 达成；单人完整钓上一条鱼（自动冒烟：tier=1 上鱼 → 背包 salmon×1，状态回 IDLE；断线用例 tension=1.02 → SNAPPED）

## M4 · 内容（台钓竿）（4 人日）

- [x] 鱼表 JSON schema + 加载器 + 权重轮盘赌（1.0）
- [x] `rod_styles` / `lure_only` 字段的**解析与过滤**（v0.1 只有 `float`）（0.3）
- [x] 8 条鱼的数据（覆盖河流/湖泊/海洋/沼泽/洞穴）（1.0）
- [x] 自定义战利品物品 + 贴图 + 模型（0.8）
- [x] 音效 6 个（含 `reel` 循环）+ `sounds.json`（0.5）
- [x] 配方（竿/饵）+ 创造标签（0.4）
- [x] 坏 JSON 不崩测试（0.3）
- [x] **DoD**：8 种鱼可钓；删掉一个 JSON 字段仍能启动并打日志

## M5 · 手感（6 人日）

- [ ] 取消原版手部渲染（Forge 事件 / Fabric mixin）（0.5）
- [ ] 世界锚定竿渲染 + 4 段弯曲（1.5）
- [ ] 三组 grip 偏移与插值切换（1.0）
- [ ] 相机 spring arm + roll 限制（1.0）
- [ ] 搏鱼时相机轻微跟随（0.5）
- [ ] 与 Iris/Sodium 同装测试（1.0）
- [ ] 录屏对比 + 主观评分 ≥ 7/10（0.5）
- [ ] **DoD**：AC-1 达成

## M6 · 收尾 + 兼容口子（8 人日）

- [ ] 专用服务器 + 2 客户端 30 分钟回归（1.0）
- [ ] 与 starcatcher / aquaculture 同装测试（1.0）
- [ ] **§17.1 四个扩展点**：`#realfishing:catchable_entities`、`#realfishing:fish_items` 两个 tag（0.5）
- [ ] **§17.1 扩展点**：`result` 字段宽松解析（任意命名空间 item id）+ `entity` 可空（0.5）
- [ ] **§17.1 扩展点**：`RealFishingAPI.register(...)` 空壳 + javadoc（0.5）
- [ ] 兼容自测：用 `kubejs`/数据包往 tag 里塞一个外部 item，能钓到（0.5）
- [ ] 性能采样：物理 <0.05 ms、渲染 <0.15 ms、无每帧分配（1.0）
- [ ] 配置加载/重载/默认值回退（0.5）
- [ ] `docs/mixins.md` 登记全部 mixin（0.2）
- [ ] 中英文语言文件补全（0.5）
- [ ] 构建产物双 jar 验证 + 安装说明（0.3）
- [ ] 打 tag `v0.1.0`（0.2）
- [ ] **DoD**：AC-5/6/7/8 全达成；外部 item 能通过 JSON 钓到

## M8 · v0.2 跨模组鱼 + 3D 鱼（+9 人日，v0.1 之后）

> 前置：M6 的四个扩展点已落地。这一阶段**不改协议、不改物理**，只填内容。

- [ ] `catchable_entities` 实体接入：上鱼时生成外部实体（2.0）
- [ ] `ICompatAdapter` 接口 + 内置 2 个适配（aquaculture / starcatcher 的鱼物品）（2.0）
- [ ] 三种体型模板 `FishModel`（`FUSIFORM`/`DEPRESSED`/`ELONGATED`）（2.0）
- [ ] 程序化摆尾（`setupAnim` 里按速度驱 `sin`，无动画资源）（1.0）
- [ ] 鱼影实体 + 简单游动 AI + 生成规则（按群系/水深/时间）（2.0）
- [ ] **DoD**：装 2 个外部钓鱼模组，我们的竿能钓到它们的鱼；水下能看到 3 种体型的鱼影

## M9 · v0.2 路亚竿（+8 人日，**排在 v0.1 之后，一根一根做**）

> 前置：M6 完成（`float_rod` 已稳定、四个兼容口子已落地）。**本阶段不改台钓竿的行为**，只加新物品。
> 物品 id：`realfishing:lure_rod`；基类共用 `RodItem`，`RodStyle.LURE`。

- [ ] `LureRodItem`（`RodStyle.LURE`：短竿、硬调、有卷线器）（0.5）
- [ ] 状态 `RETRIEVING` + 转移（WAITING↔RETRIEVING，收线时浮漂换成假饵）（1.0）
- [ ] 假饵物品 3 种：米诺 / 亮片 / 软虫（各带 `lure_weight` 与泳姿参数）（1.0）
- [ ] 收线驱动假饵游动：位置积分 + 泳姿（左右摆 + 上下窜）+ 深度控制（1.5）
- [ ] 抽竿（jerk）：短按左键给假饵一个脉冲，触发掠食鱼追击（1.0）
- [ ] 咬钩改为"游动中咬"：不进入 WAITING，咬钩即 FIGHTING，0.6 s 反应窗（1.0）
- [ ] 鱼表过滤：`lure_only: true` 的鱼只有路亚能钓（0.5）
- [ ] 竿尖抖动渲染 + 收线音效循环（0.8）
- [ ] 手感调参 + 与台钓对比录屏（0.7）
- [ ] **DoD**：假饵能钓上 3 种掠食鱼；同一水域台钓与路亚的咬钩鱼种明显不同；台钓行为零回归

## M7 · 可选（+6 人日）

- [ ] 挂底与拉断
- [ ] 鱼群/热点（同一水域连续上鱼概率上升）
- [ ] 天气与时段对咬钩的可见影响
- [ ] 传说鱼 + 成就

---

## 并行建议

```
v0.1（一根竿：台钓）
M0 ──► M1 ──► M2 ──► M3 ──► M5 ──► M6
                 └──► M4（可与 M3 并行，互不阻塞）

v0.2（第二根竿 + 兼容，二选一先做）
M8（兼容/3D 鱼）──┐
M9（路亚竿）──────┴──► 两者互不依赖
```

> **硬规矩**：v0.1 结束前不开 M9。`lure_rod` 的物品注册在 M9 的第一个任务，不要提前出现在创造栏。

## 每完成一个里程碑

1. `.\gradlew build` 双端产物可加载；
2. 跑 §19.2 的手动清单；
3. 在 `docs/tuning.md` 记参数快照；
4. 打 tag（`m0`…`m6`）。
