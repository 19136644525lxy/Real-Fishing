package dev.realfishing.entity;

import dev.realfishing.fishing.CastMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * 浮漂实体：抛投阶段的质点弹道（重力 + 二次空气阻力 + 子步进），入水/落地后静止并溅水。
 * 不负责：鱼线（M2）、咬钩与搏鱼（M3–M4）、会话状态机（FishingManager）。
 *
 * <p>物理是纯确定性积分，双端各自跑同样的 tick，不需要逐帧同步位置；
 * 控制字段只有 owner（用于客户端认领预测实体）。
 */
public class BobberEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(BobberEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 超过这个 tick 数仍未落水/落地，强制熄火，防止穿模飞出世界边界。 */
    private static final int MAX_FLIGHT_TICKS = 120;

    /** 速度单位格/秒（与 CastMath 一致），不使用原版 blocks/tick。 */
    private Vec3 flightVelocity = Vec3.ZERO;
    private boolean landed;
    private int landedTicks;

    /** 仅客户端：本地预测生成的替身，服务器实体到达并落定后由 ClientCast 丢弃。不同步。 */
    private boolean predicted;

    public boolean isPredicted() {
        return predicted;
    }

    public void setPredicted(boolean predicted) {
        this.predicted = predicted;
    }

    public BobberEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public void setOwner(UUID owner) {
        this.entityData.set(DATA_OWNER, Optional.of(owner));
    }

    public Optional<UUID> owner() {
        return this.entityData.get(DATA_OWNER);
    }

    public boolean isLanded() {
        return landed;
    }

    public boolean isInWaterState() {
        BlockPos pos = blockPosition();
        FluidState fluid = level().getFluidState(pos);
        return fluid.is(FluidTags.WATER) || isInWater();
    }

    /** 由抛投方调用，传入格/秒的初速。 */
    public void launch(Vec3 velocityPerSecond) {
        this.flightVelocity = velocityPerSecond;
        this.setDeltaMovement(velocityPerSecond);
        this.landed = false;
        this.landedTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (landed) {
            landedTicks++;
            return;
        }
        if (!level().isClientSide && tickCount > MAX_FLIGHT_TICKS) {
            land();
            return;
        }

        Vec3 v = flightVelocity;
        for (int i = 0; i < CastMath.SUBSTEPS; i++) {
            double drag = CastMath.AIR_DRAG * v.length();
            v = v.add(
                    -drag * v.x * CastMath.SUBSTEP,
                    (-CastMath.GRAVITY - drag * v.y) * CastMath.SUBSTEP,
                    -drag * v.z * CastMath.SUBSTEP);
            move(MoverType.SELF, v.scale(CastMath.SUBSTEP));
            if (isInWaterState() || this.horizontalCollision || this.verticalCollision) {
                land();
                return;
            }
        }
        flightVelocity = v;
        this.setDeltaMovement(v);
    }

    private void land() {
        if (landed) {
            return;
        }
        landed = true;
        flightVelocity = Vec3.ZERO;
        setDeltaMovement(Vec3.ZERO);

        if (isInWaterState()) {
            // 贴到水面，避免半个漂沉在水里
            BlockPos pos = blockPosition();
            // 水域可能是多层：向上找到最上层的水方块，否则浮漂会停在第二层水里被水面盖住。
            int guard = 0;
            while (guard++ < 8 && !level().getFluidState(pos.above()).isEmpty()) {
                pos = pos.above();
            }
            FluidState fluid = level().getFluidState(pos);
            double surface = pos.getY() + fluid.getHeight(level(), pos);
            // 浮漂要露出水面：贴到水面之上一点点，否则会被半透明水面挡掉（看起来"入水即消失"）。
            setPos(getX(), surface + 0.30, getZ());
            if (level().isClientSide) {
                for (int i = 0; i < 8; i++) {
                    level().addParticle(ParticleTypes.SPLASH,
                            getX() + (random.nextDouble() - 0.5) * 0.4,
                            getY() + 0.05,
                            getZ() + (random.nextDouble() - 0.5) * 0.4,
                            0.0, 0.05, 0.0);
                }
            } else {
                level().playSound(null, getX(), getY(), getZ(),
                        SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.2F);
            }
        } else if (level().isClientSide) {
            for (int i = 0; i < 4; i++) {
                level().addParticle(ParticleTypes.CRIT,
                        getX(), getY() + 0.05, getZ(),
                        0.0, 0.02, 0.0);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("owner")) {
            setOwner(tag.getUUID("owner"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        owner().ifPresent(id -> tag.putUUID("owner", id));
    }
}
