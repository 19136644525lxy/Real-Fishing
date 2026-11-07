package dev.realfishing.fishing;

import dev.realfishing.item.RodStyle;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 单个玩家的钓鱼会话（SPEC §9.3）。服务端权威，字段都是可变的基本类型，避免热路径装箱。
 * 不负责：鱼线节点（M2 的 FishingLine）、鱼的个体（M3 的 Fish）、网络包编解码。
 */
public final class FishingSession {
    public final UUID playerId;
    public FishingState state = FishingState.IDLE;
    /** 每次成功抛投 +1；客户端丢弃过期包用。 */
    public int sessionId;
    public float charge;
    public double lineLength;
    public double drag;
    public double tension;
    public double fishStamina;
    /** 当前搏鱼对象档位（0 小 / 1 中 / 2 大），M4 换成具体鱼 id。 */
    public int fishTier;
    /** 玩家这一 tick 是否按住收线（不落盘）。 */
    public boolean reeling;
    public RodStyle rodStyle = RodStyle.FLOAT;
    /** 进入当前状态的服务端 gameTime。 */
    public long stateSince;
    /** 当前浮漂实体 id，-1 表示没有。 */
    public int bobberEntityId = -1;
    /** 本次抛投的 gameTime，用于 CASTING 超时回收。 */
    public long castGameTime;
    /** 会话所在维度（dimension location 字符串），换维度时据此清理。 */
    public String dimensionId = "";

    public FishingSession(UUID playerId) {
        this.playerId = playerId;
    }

    public void transition(FishingState next, long gameTime) {
        this.state = next;
        this.stateSince = gameTime;
    }

    /** 会话回到干净状态：清掉浮漂引用与所有搏鱼中间量。 */
    public void reset(long gameTime) {
        transition(FishingState.IDLE, gameTime);
        charge = 0.0F;
        lineLength = 0.0;
        drag = 0.0;
        tension = 0.0;
        fishStamina = 0.0;
        fishTier = 0;
        reeling = false;
        bobberEntityId = -1;
        castGameTime = 0L;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", playerId);
        tag.putByte("state", state.id());
        tag.putInt("sessionId", sessionId);
        tag.putFloat("charge", charge);
        tag.putDouble("lineLength", lineLength);
        tag.putDouble("drag", drag);
        tag.putDouble("tension", tension);
        tag.putDouble("fishStamina", fishStamina);
        tag.putInt("fishTier", fishTier);
        tag.putString("rodStyle", rodStyle.name());
        tag.putLong("stateSince", stateSince);
        tag.putInt("bobber", bobberEntityId);
        tag.putLong("castGameTime", castGameTime);
        tag.putString("dimension", dimensionId);
        return tag;
    }

    public static FishingSession load(CompoundTag tag) {
        FishingSession s = new FishingSession(tag.getUUID("player"));
        s.state = FishingState.byId(tag.getByte("state"));
        s.sessionId = tag.getInt("sessionId");
        s.charge = tag.getFloat("charge");
        s.lineLength = tag.getDouble("lineLength");
        s.drag = tag.getDouble("drag");
        s.tension = tag.getDouble("tension");
        s.fishStamina = tag.getDouble("fishStamina");
        s.fishTier = tag.getInt("fishTier");
        try {
            s.rodStyle = RodStyle.valueOf(tag.getString("rodStyle"));
        } catch (IllegalArgumentException ignored) {
            s.rodStyle = RodStyle.FLOAT;
        }
        s.stateSince = tag.getLong("stateSince");
        s.bobberEntityId = tag.getInt("bobber");
        s.castGameTime = tag.getLong("castGameTime");
        s.dimensionId = tag.getString("dimension");
        return s;
    }
}
