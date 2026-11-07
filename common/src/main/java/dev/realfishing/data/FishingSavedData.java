package dev.realfishing.data;

import dev.realfishing.fishing.FishingSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 主世界存档里的钓鱼会话表（SPEC §9.3 要求 SavedData）。只存"每个玩家一条会话"，
 * 不存浮漂实体本身——实体带 noSave，重启后按"浮漂已丢失"把会话复位成 IDLE。
 * 不负责：状态机推进（FishingManager 负责）。
 */
public final class FishingSavedData extends SavedData {
    public static final String FILE_ID = "realfishing_sessions";

    private final Map<UUID, FishingSession> sessions = new LinkedHashMap<>();

    public FishingSavedData() {
    }

    public FishingSavedData(CompoundTag tag) {
        ListTag list = tag.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            FishingSession s = FishingSession.load(list.getCompound(i));
            sessions.put(s.playerId, s);
        }
    }

    public static FishingSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(FishingSavedData::new, FishingSavedData::new, FILE_ID);
    }

    public Collection<FishingSession> sessions() {
        return sessions.values();
    }

    public FishingSession getOrCreate(UUID playerId) {
        FishingSession s = sessions.get(playerId);
        if (s == null) {
            s = new FishingSession(playerId);
            sessions.put(playerId, s);
            setDirty();
        }
        return s;
    }

    public FishingSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public void remove(UUID playerId) {
        if (sessions.remove(playerId) != null) {
            setDirty();
        }
    }

    public void markDirty() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (FishingSession s : sessions.values()) {
            list.add(s.save());
        }
        tag.put("sessions", list);
        return tag;
    }
}
