package cn.sxau.slimelens.command;

import cn.sxau.slimelens.SlimeLensPlugin;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class SlimeLensCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("search", "recipes", "uses", "reload");
    private final SlimeLensPlugin plugin;

    public SlimeLensCommand(SlimeLensPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家执行。");
            return true;
        }
        if (!player.hasPermission("slimelens.use")) {
            player.sendMessage("§c你没有使用 SlimeLens 的权限。");
            return true;
        }
        if (args.length == 0) {
            if (player.getInventory().getItemInMainHand().getType().isAir()) {
                plugin.gui().openHome(player);
            } else {
                plugin.gui().openItemDirect(player, player.getInventory().getItemInMainHand().clone());
            }
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "search" -> {
                if (args.length < 2) {
                    player.sendMessage("§e用法: /" + label + " search <关键词>");
                } else {
                    plugin.gui().openSearch(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                }
            }
            case "recipes", "recipe" -> plugin.gui().openRecipesForHand(player);
            case "uses", "use" -> plugin.gui().openUsesForHand(player);
            case "reload" -> {
                if (!player.hasPermission("slimelens.admin")) {
                    player.sendMessage("§c你没有重建索引的权限。");
                } else {
                    plugin.reloadAndRebuild(player);
                }
            }
            default -> player.sendMessage("§e/" + label + " [search|recipes|uses|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
