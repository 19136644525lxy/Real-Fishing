package dev.realfishing.item;

import net.minecraft.world.item.Item;

/**
 * 所有鱼竿的基类：保存竿型与共用属性，具体行为由子类逐个里程碑补齐。
 * 不负责：抛投/搏鱼逻辑（M1–M3 放到 fishing 包，由会话驱动）。
 */
public class RodItem extends Item {
    private final RodStyle style;

    protected RodItem(RodStyle style, Properties properties) {
        super(properties);
        this.style = style;
    }

    public RodStyle style() {
        return style;
    }
}
