# 写实钓鱼 · Real Fishing

Minecraft **1.20.1** 写实钓鱼模组，Fabric + Forge 双端，Architectury 三层结构，纯 Java 物理。

## 文件指引

| 文件 | 作用 |
|---|---|
| [HANDOFF.md](HANDOFF.md) | **交接说明**：现在有什么、缺什么、第一小时做什么（新人从这页开始） |
| [SPEC.md](SPEC.md) | **设计规格书**：目标、验收标准、冻结决策、架构、物理公式、网络协议、里程碑、风险 |
| [ROADMAP.md](ROADMAP.md) | 可勾选的任务清单（M0–M6 约 48 人日，M8 = v0.2 再加 9） |
| [AGENTS.md](AGENTS.md) | 给接手者的硬规则：约束、构建命令、DoD、常见坑 |
| [docs/mixins.md](docs/mixins.md) | Mixin 登记表（总数上限 8） |
| [docs/tuning.md](docs/tuning.md) | 手感参数调参记录 |
| `art/` | 图标成品（32×32）与处理脚本；`*_64x64.png` 是参考原图 |

## 30 秒了解

- 持竿时取消原版手部渲染，鱼竿**锚定在世界空间**，转头不跟屏幕转。
- 鱼线 = 24 质点 Verlet，固定步长，张力驱动弯曲，>100% 持续 0.25 s 断线。
- 搏鱼 = 收线 / 抬竿 / 泄力 / 放线 四个操作对抗鱼的冲刺与体力。
- 服务端权威，网络只同步**两个端点 + 张力**，客户端本地复算整条线。
- 不碰原版钓鱼，不引第三方库，不上原生代码。

## 两根竿，一根一根做

| 物品 | 玩法 | 何时 |
|---|---|---|
| `realfishing:float_rod` | 台钓：立漂、看漂、等口 | **v0.1**（M0–M6） |
| `realfishing:lure_rod` | 路亚：假饵、收线、抽竿诱鱼 | **v0.2**（M9） |

共用 `RodItem` 基类 + `RodStyle` 枚举，差异走数据；v0.1 的创造栏里只有台钓竿。

## 新工作区起步

```powershell
cd F:\RealisticFishing
# 1. 读 SPEC.md 与 AGENTS.md
# 2. 用 Architectury 官方模板生成 1.20.1 + fabric,forge 骨架
# 3. .\gradlew :fabric:runClient
```

详见 `SPEC.md` §22「交接：新工作区的第一小时」。
