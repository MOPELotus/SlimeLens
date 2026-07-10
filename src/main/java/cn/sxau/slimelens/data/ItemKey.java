package cn.sxau.slimelens.data;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record ItemKey(String value) {

    public static final ItemKey AIR = new ItemKey("air");

    public static ItemKey of(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return AIR;
        }

        SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
        if (slimefunItem != null) {
            return new ItemKey("sf:" + slimefunItem.getId());
        }

        return new ItemKey("mc:" + item.getType().name());
    }

    public boolean isAir() {
        return this.equals(AIR);
    }

    public boolean isSlimefun() {
        return value.startsWith("sf:");
    }

    public String slimefunId() {
        return isSlimefun() ? value.substring(3) : "";
    }

    public Material material() {
        if (!value.startsWith("mc:")) {
            return Material.AIR;
        }

        try {
            return Material.valueOf(value.substring(3));
        } catch (IllegalArgumentException ignored) {
            return Material.AIR;
        }
    }
}
