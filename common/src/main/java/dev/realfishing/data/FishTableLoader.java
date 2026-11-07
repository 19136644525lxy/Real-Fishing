package dev.realfishing.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.realfishing.RealFishing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从数据包 {@code data/<namespace>/fish/*.json} 读取鱼表（SPEC §13.1）。
 * 单条坏 JSON 只记 ERROR 并跳过，绝不让重载失败——这是 M4 的 DoD 之一。
 */
public final class FishTableLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public FishTableLoader() {
        super(GSON, "fish");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        List<FishEntry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            try {
                if (!entry.getValue().isJsonObject()) {
                    RealFishing.LOGGER.error("鱼表 {} 不是 JSON 对象，已跳过", entry.getKey());
                    continue;
                }
                JsonObject json = entry.getValue().getAsJsonObject();
                entries.add(FishEntry.parse(entry.getKey(), json));
            } catch (RuntimeException exception) {
                // 单条坏数据不能拖垮整次重载（DoD：删字段仍能启动并打日志）。
                RealFishing.LOGGER.error("鱼表 {} 解析失败，已跳过：{}", entry.getKey(), exception.toString());
            }
        }
        FishTable.set(entries);
    }
}
