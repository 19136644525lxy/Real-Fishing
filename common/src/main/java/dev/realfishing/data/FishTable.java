package dev.realfishing.data;

import dev.realfishing.RealFishing;
import dev.realfishing.item.RodStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 运行期的鱼表（SPEC §13.2）：保存最近一次数据包重载后的全部 {@link FishEntry}，
 * 提供按环境过滤 + 权重轮盘赌。不负责文件加载（{@link FishTableLoader}）与状态机（FishingManager）。
 *
 * <p>鱼表是服务端权威数据；客户端不读它，只收 {@code SessionS2C} 里的结果。
 */
public final class FishTable {
    private static volatile List<FishEntry> entries = List.of();

    private FishTable() {
    }

    /** 由重载监听器调用；加载失败时保留上一份表，避免热重载把玩法清空。 */
    public static void set(List<FishEntry> newEntries) {
        entries = List.copyOf(newEntries);
        RealFishing.LOGGER.info("鱼表已加载：{} 条", entries.size());
    }

    public static List<FishEntry> all() {
        return entries;
    }

    public static Optional<FishEntry> byId(ResourceLocation id) {
        for (FishEntry entry : entries) {
            if (entry.id().equals(id)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * 选一条鱼：环境条件全满足 + 竿型匹配 + 非 lure_only（台钓竿）。
     * 权重 = weight × 饵系数；候选为空返回 empty（调用方重掷咬钩计时）。
     */
    public static Optional<FishEntry> pick(ServerLevel level, BlockPos pos, RodStyle style, ItemStack bait,
                                           RandomSource random, long dayTime) {
        List<FishEntry> candidates = new ArrayList<>();
        double total = 0.0;
        for (FishEntry entry : entries) {
            if (!entry.allowsRod(style)) {
                continue;
            }
            if (entry.lureOnly() && style != RodStyle.LURE) {
                continue;
            }
            if (!entry.matches(level, pos, dayTime)) {
                continue;
            }
            double weight = Math.max(0.0, entry.weight() * entry.baitMultiplier(bait));
            if (weight <= 0.0) {
                continue;
            }
            candidates.add(entry);
            total += weight;
        }
        if (candidates.isEmpty() || total <= 0.0) {
            long clock = Math.floorMod(dayTime, 24000L);
            StringBuilder why = new StringBuilder();
            for (FishEntry entry : entries) {
                String state = entry.allowsRod(style)
                        ? (entry.lureOnly() && style != RodStyle.LURE ? "lure" : entry.rejection(level, pos, dayTime))
                        : "rod";
                why.append(' ').append(entry.id().getPath()).append('=').append(state);
            }
            RealFishing.LOGGER.debug("[M4] pick: none biome={} depth={} clock={} entries={}{}",
                    level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("?"),
                    FishEntry.waterDepth(level, pos), clock, entries.size(), why);
            return Optional.empty();
        }
        double roll = random.nextDouble() * total;
        for (FishEntry entry : candidates) {
            roll -= Math.max(0.0, entry.weight() * entry.baitMultiplier(bait));
            if (roll <= 0.0) {
                return Optional.of(entry);
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }
}
