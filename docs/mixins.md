# Mixin 登记表

> 规则（SPEC §4 L2）：全项目 mixin **总数 ≤ 8**，只允许注入原版类，禁止注入其他模组。
> 每加一条必须在此登记：目标类、注入点、为什么用事件/API 做不到、影响的平台。

| # | 类 | 目标 | 注入点 | 原因 | 平台 | 状态 |
|---|---|---|---|---|---|---|
| 1 | `client.ItemInHandRendererMixin` | `net.minecraft.client.renderer.ItemInHandRenderer` | `renderHandsWithItems` HEAD，`ci.cancel()` | Fabric 没有"取消手部渲染"事件；Forge 用 `RenderHandEvent` 不需要 mixin | Fabric 客户端 | 计划（M5） |
| 2 | `client.CameraMixin` | `net.minecraft.client.Camera` | `setup` TAIL | Fabric 没有相机角度事件；Forge 用 `ViewportEvent.ComputeCameraAngles` | Fabric 客户端 | 计划（M5） |

**当前占用：2 / 8。剩余预算 6。**

## 已否决的 mixin 提案

| 提案 | 否决原因 |
|---|---|
| 注入 `FishingHook` 改原版钓鱼 | 直接违反 D4，与 starcatcher/aquaculture 冲突 |
| 注入 `Player#tick` 做会话清理 | Architectury 的 `PlayerEvent` 足够 |
| 注入 `LevelRenderer` 画线 | `WorldRenderEvents` / `RenderLevelStageEvent` 足够 |

## 登记模板

```
| N | 类名 | 目标类 | 注入点（方法+at） | 为什么事件做不到 | 平台 | 状态 |
```
