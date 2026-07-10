package cn.sxau.slimelens.util;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class Items {

    private Items() {}

    public static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        if (lore.length > 0) {
            List<String> lines = new ArrayList<>(lore.length);
            for (String line : lore) {
                lines.add(color(line));
            }
            meta.setLore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack decorate(ItemStack source, String... extraLore) {
        if (source == null || source.getType().isAir()) {
            return null;
        }

        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        if (extraLore.length > 0) {
            lore.add("");
            for (String line : extraLore) {
                lore.add(color(line));
            }
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static String name(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "未知物品";
        }

        SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
        if (slimefunItem != null) {
            return slimefunItem.getItemName();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        return item.getType().name();
    }

    public static String plain(String value) {
        return ChatColor.stripColor(value == null ? "" : value);
    }

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
