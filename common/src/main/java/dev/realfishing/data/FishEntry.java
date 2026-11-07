package dev.realfishing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.realfishing.RealFishing;
import dev.realfishing.item.RodStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.tags.FluidTags;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 一条鱼的数据（SPEC §13.1）。负责保存解析后的条件与战利品；不负责文件 IO（{@link FishTableLoader}）、
 * 也不负责加权抽取（{@link FishTable}）。
 */
public final class FishEntry {
    /** 战利品条目：item + 数量区间 + 概率。 */
    public record Loot(ResourceLocation itemId, int minCount, int maxCount, float chance) {
    }

    private final ResourceLocation id;
    private final String displayName;
    private final float strength;
    private final float staminaSeconds;
    private final float turnRate;
    private final int weight;
    private final List<TagKey<Biome>> biomes;
    private final Set<String> times;
    private final Set<String> weathers;
    private final int minDepth;
    private final int maxDepth;
    private final List<TagKey<Item>> baits;
    private final List<Loot> loot;
    private final int minXp;
    private final int maxXp;
    private final Set<RodStyle> rodStyles;
    private final boolean lureOnly;

    private FishEntry(ResourceLocation id, String displayName, float strength, float staminaSeconds,
                      float turnRate, int weight, List<TagKey<Biome>> biomes, Set<String> times,
                      Set<String> weathers, int minDepth, int maxDepth, List<TagKey<Item>> baits,
                      List<Loot> loot, int minXp, int maxXp, Set<RodStyle> rodStyles, boolean lureOnly) {
        this.id = id;
        this.displayName = displayName;
        this.strength = strength;
        this.staminaSeconds = staminaSeconds;
        this.turnRate = turnRate;
        this.weight = weight;
        this.biomes = List.copyOf(biomes);
        this.times = Set.copyOf(times);
        this.weathers = Set.copyOf(weathers);
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
        this.baits = List.copyOf(baits);
        this.loot = List.copyOf(loot);
        this.minXp = minXp;
        this.maxXp = maxXp;
        this.rodStyles = Set.copyOf(rodStyles);
        this.lureOnly = lureOnly;
    }

    public ResourceLocation id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public float strength() {
        return strength;
    }

    public float staminaSeconds() {
        return staminaSeconds;
    }

    public float turnRate() {
        return turnRate;
    }

    public int weight() {
        return weight;
    }

    public List<Loot> loot() {
        return loot;
    }

    public int minXp() {
        return minXp;
    }

    public int maxXp() {
        return maxXp;
    }

    public boolean lureOnly() {
        return lureOnly;
    }

    public Set<RodStyle> rodStyles() {
        return rodStyles;
    }

    /** 竿型过滤：缺省 rod_styles 视为两种竿都能钓（SPEC §13.2）。 */
    public boolean allowsRod(RodStyle style) {
        if (style == RodStyle.LURE) {
            return rodStyles.contains(RodStyle.LURE);
        }
        return rodStyles.contains(RodStyle.FLOAT);
    }

    /** 环境条件（不含饵）：生物群系 / 时段 / 天气 / 水深。 */
    public boolean matches(ServerLevel level, BlockPos pos, long dayTime) {
        if (!matchesBiome(level.getBiome(pos))) {
            return false;
        }
        if (!times.isEmpty() && times.stream().noneMatch(t -> matchesTime(dayTime, t))) {
            return false;
        }
        if (!weathers.isEmpty() && weathers.stream().noneMatch(w -> matchesWeather(level, w))) {
            return false;
        }
        int depth = waterDepth(level, pos);
        return depth >= minDepth && depth <= maxDepth;
    }

    /** 诊断用：返回第一个不满足的条件名，全部满足返回 "ok"。 */
    public String rejection(ServerLevel level, BlockPos pos, long dayTime) {
        if (!matchesBiome(level.getBiome(pos))) {
            return "biome";
        }
        if (!times.isEmpty() && times.stream().noneMatch(t -> matchesTime(dayTime, t))) {
            return "time";
        }
        if (!weathers.isEmpty() && weathers.stream().noneMatch(w -> matchesWeather(level, w))) {
            return "weather";
        }
        int depth = waterDepth(level, pos);
        if (depth < minDepth) {
            return "shallow(" + depth + "<" + minDepth + ")";
        }
        if (depth > maxDepth) {
            return "deep(" + depth + ">" + maxDepth + ")";
        }
        return "ok";
    }

    private boolean matchesBiome(Holder<Biome> biome) {        if (biomes.isEmpty()) {
            return true;
        }
        for (TagKey<Biome> tag : biomes) {
            if (biome.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 饵的权重系数（SPEC §13.2）：完全匹配 1.5，部分匹配 1.0，不匹配 0.5；鱼没有饵要求时恒为 1.0。
     */
    public float baitMultiplier(ItemStack bait) {
        if (baits.isEmpty()) {
            return 1.0F;
        }
        if (bait.isEmpty()) {
            return 1.0F;
        }
        int matched = 0;
        for (TagKey<Item> tag : baits) {
            if (bait.is(tag)) {
                matched++;
            }
        }
        if (matched == 0) {
            return 0.5F;
        }
        return matched == baits.size() ? 1.5F : 1.0F;
    }

    public static boolean matchesTime(long dayTime, String label) {
        long t = Math.floorMod(dayTime, 24000L);
        return switch (label) {
            // MC 时间：0 = 日出，6000 = 正午，12000 = 日落，18000 = 午夜。
            case "day" -> t < 12000L;
            case "dawn_dusk" -> (t >= 12000L && t < 13800L) || t >= 22800L;
            case "night" -> t >= 13800L && t < 22800L;
            default -> true;
        };
    }

    public static boolean matchesWeather(ServerLevel level, String label) {
        if (level.isThundering()) {
            // 雷暴同时算 rain，避免数据里只写 rain 的鱼在雷雨天绝迹。
            return "thunder".equals(label) || "rain".equals(label);
        }
        if (level.isRaining()) {
            return "rain".equals(label);
        }
        return "clear".equals(label);
    }

    /** 从水面方块向下数连续的水方块数量。浮漂悬在水面之上，先向下找最多 5 格定位水面。 */
    public static int waterDepth(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        int probe = 0;
        while (probe++ < 5 && !level.getFluidState(cursor).is(FluidTags.WATER)) {
            cursor.move(0, -1, 0);
        }
        int depth = 0;
        while (depth < 64 && level.getFluidState(cursor).is(FluidTags.WATER)) {
            depth++;
            cursor.move(0, -1, 0);
        }
        return depth;
    }

    // ---------------------------------------------------------------- 解析

    public static FishEntry parse(ResourceLocation id, JsonObject json) {
        JsonObject cond = getObject(json, "conditions");
        List<TagKey<Biome>> biomes = parseTags(cond, "biomes", Registries.BIOME);
        List<TagKey<Item>> baits = parseTags(cond, "bait", Registries.ITEM);
        Set<String> times = parseStrings(cond, "time");
        Set<String> weathers = parseStrings(cond, "weather");

        List<Loot> loot = new ArrayList<>();
        JsonArray lootArray = json.has("loot") ? json.getAsJsonArray("loot") : new JsonArray();
        for (JsonElement element : lootArray) {
            JsonObject entry = element.getAsJsonObject();
            ResourceLocation itemId = new ResourceLocation(entry.get("item").getAsString());
            int[] count = parseRange(entry.has("count") ? entry.get("count") : null, 1, 1);
            float chance = entry.has("chance") ? entry.get("chance").getAsFloat() : 1.0F;
            loot.add(new Loot(itemId, count[0], count[1], chance));
        }

        int[] xp = parseRange(json.get("xp"), 1, 1);
        Set<RodStyle> styles = EnumSet.noneOf(RodStyle.class);
        if (json.has("rod_styles")) {
            for (JsonElement element : json.getAsJsonArray("rod_styles")) {
                String name = element.getAsString().toLowerCase();
                for (RodStyle style : RodStyle.values()) {
                    if (style.name().toLowerCase().equals(name)) {
                        styles.add(style);
                    }
                }
            }
        }
        if (styles.isEmpty()) {
            styles = EnumSet.allOf(RodStyle.class);
        }

        return new FishEntry(id,
                json.has("display_name") ? json.get("display_name").getAsString() : id.getPath(),
                json.has("strength") ? json.get("strength").getAsFloat() : 0.5F,
                json.has("stamina_seconds") ? json.get("stamina_seconds").getAsFloat() : 18.0F,
                json.has("turn_rate") ? json.get("turn_rate").getAsFloat() : 0.5F,
                json.has("weight") ? json.get("weight").getAsInt() : 10,
                biomes, times, weathers,
                cond.has("min_water_depth") ? cond.get("min_water_depth").getAsInt() : 1,
                cond.has("max_water_depth") ? cond.get("max_water_depth").getAsInt() : 64,
                baits, loot, xp[0], xp[1], styles,
                json.has("lure_only") && json.get("lure_only").getAsBoolean());
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static <T> List<TagKey<T>> parseTags(JsonObject parent, String key, net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> registry) {
        List<TagKey<T>> tags = new ArrayList<>();
        if (!parent.has(key)) {
            return tags;
        }
        for (JsonElement element : parent.getAsJsonArray(key)) {
            String raw = element.getAsString();
            ResourceLocation location = raw.startsWith("#")
                    ? new ResourceLocation(raw.substring(1))
                    : new ResourceLocation(raw);
            tags.add(TagKey.create(registry, location));
        }
        return tags;
    }

    private static Set<String> parseStrings(JsonObject parent, String key) {
        Set<String> values = new HashSet<>();
        if (!parent.has(key)) {
            return values;
        }
        for (JsonElement element : parent.getAsJsonArray(key)) {
            values.add(element.getAsString().toLowerCase());
        }
        return values;
    }

    private static int[] parseRange(JsonElement element, int fallbackMin, int fallbackMax) {
        if (element == null || element.isJsonNull()) {
            return new int[]{fallbackMin, fallbackMax};
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            int min = array.get(0).getAsInt();
            int max = array.size() > 1 ? array.get(1).getAsInt() : min;
            return new int[]{Math.min(min, max), Math.max(min, max)};
        }
        int value = element.getAsInt();
        return new int[]{value, value};
    }

    /** 按权重抽一条战利品（数量在区间内均匀）。 */
    public static void addLoot(FishEntry fish, RandomSource random, java.util.function.Consumer<ItemStack> consumer) {
        for (Loot entry : fish.loot) {
            if (random.nextFloat() > entry.chance()) {
                continue;
            }
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(entry.itemId());
            if (item == net.minecraft.world.item.Items.AIR) {
                RealFishing.LOGGER.warn("鱼 {} 的战利品 {} 未注册，已跳过", fish.id, entry.itemId());
                continue;
            }
            int count = entry.minCount() + (entry.maxCount() > entry.minCount()
                    ? random.nextInt(entry.maxCount() - entry.minCount() + 1) : 0);
            consumer.accept(new ItemStack(item, count));
        }
    }
}
