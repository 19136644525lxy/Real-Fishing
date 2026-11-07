# 手感调参记录

> 每次改参数都记一行：改了什么、为什么、主观评分（1–10）、对比录屏文件名。
> 调参原则：**一次只动一个旋钮**，否则无法归因。

## 当前参数快照（v0.1.0 基线）

| 参数 | 值 | 备注 |
|---|---|---|
| `line.nodes` | 24 | 少于 16 会在绷紧时看到折角 |
| `line.gravity` | 0.05 | 实体重力 0.08；线更飘 |
| `line.maxStrain` | 0.12 | 拉伸 12% = 满张力 |
| `line.breakHold` | 0.25 s | 低于 0.15 s 会"莫名其妙断线" |
| `fight.reelSpeed` | 1.2 格/s | |
| `fight.pumpImpulse` | 0.15 | |
| `fight.dragStep` | 0.1 | |
| `cast.minVelocity` | 9 | 8 格 |
| `cast.maxVelocity` | 22 | 24 格 |
| `bite.fakeChance` | 0.25 | |
| `client.cameraSpringStiffness` | 120 | 临界阻尼 `2*sqrt(k)` |
| `client.lineWidth` | 0.02 | 绷紧时 +0.01 |

## 抛投积分的实现值（M1）

SPEC §12 给的是"每 tick"量纲（重力 0.08 格/tick²、阻力 0.002/tick）。若直接按 20 Hz 换算成
格/s² 就是 32 格/s²，满蓄力 22 格/s 只能飞 4–5 格，与 AC-2（8–24 格）冲突。
M1 以验收标准为准，改用"每秒"量纲并由 `CastMath` 固定 80 Hz 子步积分：

| 参数 | 值 | 备注 |
|---|---|---|
| `cast.gravity` | 6.0 格/s² | 比现实 9.8 轻，手感更"抛"而不是"砸" |
| `cast.airDrag` | 0.02 /s | 线性阻力，二次项在 22 格/s 时也只占几个百分点 |
| `cast.substep` | 0.0125 s（4 子步/tick） | 高速段不会穿过水面 |
| `cast.upwardBias` | 0.15 | 视线方向加 (0, 0.15, 0) 再归一化，补偿下垂 |
| `cast.chargeTicks` | 40（2 s 满蓄力） | 与 `getUseDuration` 一致 |
| 实测射程 | charge=0 → 8.0 格；charge=1 → 23.6 格 | Node 弹道模拟 + 单测双确认 |

## 鱼线的实现值（M2）

SPEC §10 给的是"每 tick"量纲，FishingLine 内部统一换算成"每秒"并由 60 Hz 子步积分：

| 参数 | 值 | 备注 |
|---|---|---|
| line.nodes | 24 | 23 段，段长 = 线长/23 |
| line.dt | 1/60 s | 每 tick 最多 3 个子步（MAX_SUBSTEPS=4） |
| line.gravity | 20.0 格/s² | = 0.05 格/tick²，视觉上比实体更"飘" |
| line.airDrag | 0.002 /s | 线很轻，几乎只受重力 |
| line.waterDrag | 0.02 /s | 水面以下节点额外阻尼 |
| line.buoyancy | 2.0 格/s² | 水面以下节点上浮，避免整根线沉底 |
| line.maxStrain | 0.12 | 拉伸 12% 记满张力 |
| line.breakHold | 0.25 s | 满张力持续这么久才断 |
| line.iterations | 10（上限 40） | 每次 relax 后若 maxStrain ≤ 0.01 提前收敛 |
| 渲染 | Catmull-Rom ×2 细分（47 点），四边形带宽 0.022 | 不用 RenderType.lines() |

## 搏鱼的实现值（M3）

战斗模型同样是"每秒"量纲，服务端 20 Hz 驱动，客户端拿 10 Hz 快照只做显示：

| 参数 | 值 | 备注 |
|---|---|---|
| fight.reelSpeed | 1.2 格/s | 按住右键收线的基础速度 |
| fight.autoRelease | 6.0 格/s | 张力超过泄力时自动放线的速度 |
| fight.drag | 0.2（默认，`[`/`]` 调 0–1） | 超过这个张力就开始自动放线 |
| fight.breakTension | 1.0 | 张力归一化上限 |
| fight.breakTicks | 5 tick（0.25 s） | 满张力持续这么久才断线 |
| fight.minLine / maxLine | 1.5 / 80 格 | 收到最短即视为到脚边，可上鱼 |
| 鱼档位 | SMALL 0.25 力 / 8 s 体力；MEDIUM 0.5 / 18 s；BIG 0.8 / 35 s | 体力归零后仍需收线到 1.6 格 |
| 上鱼判定 | 体力 ≤ 0 且张力 < 0.6 且（水平距离 < 2.5 或线 ≤ 1.6） | M3 简化；M4 接鱼表后再细分 |
| 断线表现 | `ITEM_BREAK` 音效 + 水花 + SNAPPED 0.8 s | 不进冷却，直接可重抛 |

## 变更日志

| 日期 | 参数 | 旧 → 新 | 原因 | 评分 | 备注 |
|---|---|---|---|---|---|
| 2026-11-07 | `cast.gravity` / `cast.airDrag` | — → 6.0 / 0.02 | 按 AC-2 反推，SPEC §12 的 tick 量纲无法满足 8–24 格 | — | 待 M3 手感实测后复核 |
| 2026-11-07 | `line.iterations` | 固定 10 → 自适应（≤40，maxStrain≤0.01 停） | 固定 10 次在绷紧时会把 10 格线拉长 7%，单测直接失败 | — | 长跑 25k tick 稳定 |
| 2026-11-07 | ight.* | 基线 | M3 首次实测：tier=1 收线约 15 s 上鱼；断线在 tension=1.02 触发 | — | 手感待 M5 加动画后复核 |

## 调参方法

1. 固定场景：平原水池（3×3×2 深），晴天，白天，无风。
2. 每种参数测 10 次抛投 / 3 次搏鱼，录屏。
3. 只看三件事：**抛投落点是否可预期**、**张力条是否可读**、**相机是否晕**。
4. 评分 < 7 就回滚，不要"再调一点看看"。
