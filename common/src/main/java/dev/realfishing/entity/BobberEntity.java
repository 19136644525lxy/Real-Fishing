package dev.realfishing.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 浮漂实体。M0 只有壳：注册 + 存读档空实现；重力/入水/同步在 M1–M2。
 * 不负责：鱼线求解（属于 physics 包）。
 */
public class BobberEntity extends Entity {
    public BobberEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
