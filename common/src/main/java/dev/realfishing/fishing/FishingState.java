package dev.realfishing.fishing;

/**
 * 钓鱼会话状态机（SPEC §9.1）。持久化与网络同步只依赖 ordinal，顺序不可随意调整。
 * 不负责：状态转移条件（在 FishingManager / FightModel 里判定）。
 */
public enum FishingState {
    /** 手持竿但未做任何动作。 */
    IDLE,
    /** 按住右键蓄力。 */
    CHARGING,
    /** 已抛出，浮漂飞行中。 */
    CASTING,
    /** 浮漂落定，等待咬钩。 */
    WAITING,
    /** 收线（v0.1 台钓竿不用，留给路亚）。 */
    RETRIEVING,
    /** 咬钩窗口。 */
    BITE,
    /** 搏鱼。 */
    FIGHTING,
    /** 收尾（上岸）。 */
    LANDING,
    /** 断线。 */
    SNAPPED;

    private static final FishingState[] VALUES = values();

    /** 网络/存档按 id 反查；越界一律回落到 IDLE，避免坏包把状态机带崩。 */
    public static FishingState byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
    }

    public byte id() {
        return (byte) ordinal();
    }
}
