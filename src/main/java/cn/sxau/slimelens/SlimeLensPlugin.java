package cn.sxau.slimelens;

import cn.sxau.slimelens.command.SlimeLensCommand;
import cn.sxau.slimelens.data.LensIndex;
import cn.sxau.slimelens.gui.LensGui;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunGuideOpenEvent;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunItemRegistryFinalizedEvent;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class SlimeLensPlugin extends JavaPlugin implements Listener {

    private LensIndex index;
    private LensGui gui;
    private BukkitTask fallbackIndexTask;
    private BukkitTask indexTask;
    private boolean slimefunRegistryFinalized;
    private NamespacedKey welcomeMessageKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        index = new LensIndex();
        gui = new LensGui(this, index);
        welcomeMessageKey = new NamespacedKey(this, "welcome-message-shown");

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);

        SlimeLensCommand command = new SlimeLensCommand(this);
        getCommand("slimelens").setExecutor(command);
        getCommand("slimelens").setTabCompleter(command);

        fallbackIndexTask = getServer().getScheduler().runTaskLater(this, () -> {
            if (!slimefunRegistryFinalized) {
                rebuildIndex();
            }
        }, 20L * 15L);
    }

    public LensGui gui() {
        return gui;
    }

    public void rebuildIndex() {
        if (indexTask != null) {
            indexTask.cancel();
        }

        try {
            LensIndex.RebuildSession session = index.beginRebuild(getConfig().getBoolean("index-vanilla-recipes"));
            int workUnitsPerTick = Math.max(1, getConfig().getInt("index-work-units-per-tick", 48));
            indexTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        if (!session.advance(workUnitsPerTick)) {
                            return;
                        }

                        LensIndex.IndexStats stats = session.stats();
                        getLogger().info("已索引 " + stats.slimefunItems() + " 个粘液物品、"
                                + stats.machines() + " 台机器、"
                                + stats.recipes() + " 条配方（原版 "
                                + stats.vanillaRecipes() + " 条）。");
                        indexTask = null;
                        cancel();
                    } catch (Exception | LinkageError error) {
                        indexTask = null;
                        cancel();
                        getLogger().log(Level.SEVERE, "SlimeLens 配方索引构建失败", error);
                    }
                }
            }.runTaskTimer(this, 1L, 1L);
        } catch (Exception | LinkageError error) {
            getLogger().log(Level.SEVERE, "SlimeLens 配方索引构建失败", error);
        }
    }

    public void reloadAndRebuild(CommandSender sender) {
        reloadConfig();
        rebuildIndex();
        sender.sendMessage("§aSlimeLens 已开始分批重建配方索引。");
    }

    @EventHandler
    public void onGuideOpen(SlimefunGuideOpenEvent event) {
        if (!getConfig().getBoolean("intercept-guide-book")) {
            return;
        }

        event.setCancelled(true);
        getServer().getScheduler().runTask(this, () -> gui.openHome(event.getPlayer()));
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("slimelens.use")
                || player.getPersistentDataContainer().has(welcomeMessageKey, PersistentDataType.BYTE)) {
            return;
        }

        player.getPersistentDataContainer().set(welcomeMessageKey, PersistentDataType.BYTE, (byte) 1);
        getServer().getScheduler().runTaskLater(this, () -> {
            player.sendMessage("§a[SlimeLens] §f已启用物品查询。");
            player.sendMessage("§e手持物品执行 /slimelens §7即可查询。");
            player.sendMessage("§7详情页可直接查看 §f怎么做 §7和 §f能做什么§7。");
        }, 20L);
    }

    @EventHandler
    public void onSlimefunRegistryReady(SlimefunItemRegistryFinalizedEvent event) {
        if (slimefunRegistryFinalized) {
            return;
        }

        slimefunRegistryFinalized = true;
        if (fallbackIndexTask != null) {
            fallbackIndexTask.cancel();
            fallbackIndexTask = null;
        }
        getServer().getScheduler().runTaskLater(this, this::rebuildIndex, 100L);
    }

    @Override
    public void onDisable() {
        if (fallbackIndexTask != null) {
            fallbackIndexTask.cancel();
        }
        if (indexTask != null) {
            indexTask.cancel();
        }
    }
}
