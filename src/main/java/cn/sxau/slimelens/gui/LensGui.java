package cn.sxau.slimelens.gui;

import cn.sxau.slimelens.SlimeLensPlugin;
import cn.sxau.slimelens.data.ItemKey;
import cn.sxau.slimelens.data.LensIndex;
import cn.sxau.slimelens.data.LensRecipe;
import cn.sxau.slimelens.data.LensView;
import cn.sxau.slimelens.util.Items;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@SuppressWarnings("deprecation")
public final class LensGui implements Listener {

    private static final int[] LIST_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] RECIPE_SLOTS = {19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final int PAGE_SIZE = LIST_SLOTS.length;

    private final SlimeLensPlugin plugin;
    private final LensIndex index;
    private final Map<UUID, LensView> activeViews = new HashMap<>();
    private final Map<UUID, Deque<LensView>> histories = new HashMap<>();
    private final Map<UUID, LensView> pendingSearches = new ConcurrentHashMap<>();

    public LensGui(SlimeLensPlugin plugin, LensIndex index) {
        this.plugin = plugin;
        this.index = index;
    }

    public void openHome(Player player) {
        histories.remove(player.getUniqueId());
        open(player, LensView.browser(), false);
    }

    public void openSearch(Player player, String query) {
        histories.remove(player.getUniqueId());
        open(player, new LensView(LensView.Kind.SEARCH, query.trim(), 0), false);
    }

    public void openRecipesForHand(Player player) {
        openDirect(player, LensView.Kind.RECIPES, ItemKey.of(player.getInventory().getItemInMainHand()));
    }

    public void openUsesForHand(Player player) {
        openDirect(player, LensView.Kind.USES, ItemKey.of(player.getInventory().getItemInMainHand()));
    }

    public void openItem(Player player, ItemStack item) {
        ItemKey key = ItemKey.of(item);
        if (key.isAir()) {
            player.sendMessage("§c请先手持一个物品。");
            return;
        }
        open(player, new LensView(LensView.Kind.ITEM, key.value(), 0), true);
    }

    public void openItemDirect(Player player, ItemStack item) {
        ItemKey key = ItemKey.of(item);
        if (key.isAir()) {
            return;
        }

        histories.remove(player.getUniqueId());
        open(player, new LensView(LensView.Kind.ITEM, key.value(), 0), false);
    }

    private void openDirect(Player player, LensView.Kind kind, ItemKey key) {
        if (key.isAir()) {
            player.sendMessage("§c请先手持一个物品。");
            return;
        }
        histories.remove(player.getUniqueId());
        open(player, new LensView(kind, key.value(), 0), false);
    }

    private void open(Player player, LensView view, boolean pushHistory) {
        UUID id = player.getUniqueId();
        if (pushHistory) {
            LensView previous = activeViews.get(id);
            if (previous != null && !previous.equals(view)) {
                histories.computeIfAbsent(id, ignored -> new ArrayDeque<>()).push(previous);
            }
        }

        activeViews.put(id, view);
        LensHolder holder = new LensHolder(view);
        Inventory inventory = Bukkit.createInventory(holder, 54, Items.color("&2SlimeLens"));
        holder.setInventory(inventory);
        render(holder, player);
        player.openInventory(inventory);
    }

    private void render(LensHolder holder, Player player) {
        frame(holder);
        switch (holder.view().kind()) {
            case BROWSER -> renderBrowser(holder, player);
            case CATEGORY_LIST -> renderCategoryList(holder, player);
            case CATEGORY_ITEMS -> renderCategoryItems(holder, player);
            case MACHINE_LIST -> renderMachineList(holder, player);
            case SEARCH -> renderSearch(holder, player);
            case ITEM -> renderItem(holder, player);
            case RECIPES -> renderRecipeList(holder, player, index.recipesFor(key(holder.view().subject())), "制作配方");
            case USES -> renderRecipeList(holder, player, index.usesFor(key(holder.view().subject())), "作为材料");
            case MACHINE_RECIPES -> renderRecipeList(holder, player, index.recipesAt(key(holder.view().subject())), "机器用途");
            case RECIPE_DETAIL -> renderRecipeDetail(holder, player);
        }
    }

    private void frame(LensHolder holder) {
        ItemStack background = Items.button(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 9; slot++) {
            set(holder, slot, background, null);
        }
        for (int slot = 45; slot < 54; slot++) {
            set(holder, slot, background, null);
        }

        set(holder, 0, Items.button(Material.ARROW, "&f返回"), this::goBack);
        set(holder, 1, Items.button(Material.COMPASS, "&a全部物品"), this::openHome);
        set(holder, 3, Items.button(Material.SPYGLASS, "&e搜索"), player -> beginSearch(player, holder.view()));
        set(holder, 5, Items.button(Material.BOOKSHELF, "&b分类"), player -> open(player, new LensView(LensView.Kind.CATEGORY_LIST, "", 0), true));
        set(holder, 6, Items.button(Material.BLAST_FURNACE, "&6机器"), player -> open(player, new LensView(LensView.Kind.MACHINE_LIST, "", 0), true));
        set(holder, 7, Items.button(Material.CRAFTING_TABLE, "&d手持物品的配方"), this::openRecipesForHand);
        set(holder, 8, Items.button(Material.HOPPER, "&d手持物品的用途"), this::openUsesForHand);
    }

    private void renderBrowser(LensHolder holder, Player player) {
        renderItems(holder, player, visibleItems(player), "全部粘液物品");
    }

    private void renderCategoryList(LensHolder holder, Player player) {
        List<ItemGroup> groups = new ArrayList<>(index.groups());
        groups.removeIf(group -> !group.isVisible(player));
        groups.sort(Comparator.comparing(group -> Items.plain(group.getDisplayName(player)).toLowerCase(Locale.ROOT)));
        renderPaged(holder, groups, group -> Items.decorate(group.getItem(player), "&7" + group.getDisplayName(player)), group ->
                open(player, new LensView(LensView.Kind.CATEGORY_ITEMS, group.getKey().toString(), 0), true));
    }

    private void renderCategoryItems(LensHolder holder, Player player) {
        ItemGroup group = index.group(holder.view().subject());
        if (group == null) {
            showEmpty(holder, "&c这个分类已不再存在。");
            return;
        }

        List<SlimefunItem> items = new ArrayList<>();
        for (SlimefunItem item : group.getItems()) {
            if (isVisible(player, item)) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(item -> Items.plain(item.getItemName()).toLowerCase(Locale.ROOT)));
        renderItems(holder, player, items, group.getDisplayName(player));
    }

    private void renderMachineList(LensHolder holder, Player player) {
        List<SlimefunItem> machines = new ArrayList<>();
        for (SlimefunItem machine : index.machines()) {
            if (isVisible(player, machine)) {
                machines.add(machine);
            }
        }
        renderItems(holder, player, machines, "机器");
    }

    private void renderSearch(LensHolder holder, Player player) {
        String query = holder.view().subject().trim();
        if (query.isEmpty()) {
            showEmpty(holder, "&7未输入搜索词。");
            return;
        }

        String needle = query.toLowerCase(Locale.ROOT);
        List<SlimefunItem> matches = new ArrayList<>();
        for (SlimefunItem item : index.slimefunItems()) {
            if (isVisible(player, item) && matches(item, player, needle)) {
                matches.add(item);
            }
        }
        renderItems(holder, player, matches, "搜索: " + query);
    }

    private void renderItem(LensHolder holder, Player player) {
        ItemKey itemKey = key(holder.view().subject());
        ItemStack item = index.resolve(itemKey);
        if (item.getType().isAir()) {
            showEmpty(holder, "&c这个物品已不再存在。");
            return;
        }

        List<LensRecipe> recipes = index.recipesFor(itemKey);
        List<LensRecipe> uses = index.usesFor(itemKey);
        List<LensRecipe> machineRecipes = index.recipesAt(itemKey);
        SlimefunItem slimefunItem = SlimefunItem.getByItem(item);

        set(holder, 22, decorateForPlayer(player, item), null);
        set(holder, 20, Items.button(Material.CRAFTING_TABLE, "&a制作配方 (&f" + recipes.size() + "&a)"), viewer ->
                open(viewer, new LensView(LensView.Kind.RECIPES, itemKey.value(), 0), true));
        set(holder, 24, Items.button(Material.HOPPER, "&e作为材料 (&f" + uses.size() + "&e)"), viewer ->
                open(viewer, new LensView(LensView.Kind.USES, itemKey.value(), 0), true));

        if (!machineRecipes.isEmpty()) {
            set(holder, 31, Items.button(Material.FURNACE, "&6机器加工配方 (&f" + machineRecipes.size() + "&6)"), viewer ->
                    open(viewer, new LensView(LensView.Kind.MACHINE_RECIPES, itemKey.value(), 0), true));
        }

        if (slimefunItem != null) {
            ItemGroup group = slimefunItem.getItemGroup();
            set(holder, 13, Items.decorate(group.getItem(player), "&7分类: " + group.getDisplayName(player)), viewer ->
                    open(viewer, new LensView(LensView.Kind.CATEGORY_ITEMS, group.getKey().toString(), 0), true));
            set(holder, 40, itemInfo(slimefunItem, player), viewer -> tryUnlockResearch(viewer, slimefunItem, itemKey));
        } else {
            set(holder, 40, Items.button(Material.NAME_TAG, "&7Minecraft: " + item.getType().name()), null);
        }
    }

    private void renderRecipeList(LensHolder holder, Player player, List<LensRecipe> recipes, String title) {
        if (recipes.isEmpty()) {
            showEmpty(holder, "&7没有找到" + title + "。");
            return;
        }

        renderPaged(holder, recipes, recipe -> {
            ItemStack icon = recipe.output();
            return Items.decorate(icon, "&7方式: " + recipe.stationName(), "&f点击查看配方");
        }, recipe -> open(player, new LensView(LensView.Kind.RECIPE_DETAIL, recipe.id(), 0), true));
    }

    private void renderRecipeDetail(LensHolder holder, Player player) {
        LensRecipe recipe = index.recipe(holder.view().subject());
        if (recipe == null) {
            showEmpty(holder, "&c这个配方已不再存在。");
            return;
        }

        ItemStack station = recipe.station();
        if (station == null || station.getType().isAir()) {
            station = Items.button(Material.CRAFTING_TABLE, "&f" + recipe.stationName());
        }
        ItemStack finalStation = station;
        set(holder, 12, Items.decorate(station, "&7方式: " + recipe.stationName()), viewer -> openItem(viewer, finalStation));
        set(holder, 23, Items.button(Material.ARROW, "&a产出"), null);
        ItemStack output = recipe.output();
        set(holder, 25, Items.decorate(output, "&f点击查看物品"), viewer -> openItem(viewer, output));

        ItemStack[] inputs = recipe.inputs();
        for (int i = 0; i < RECIPE_SLOTS.length; i++) {
            ItemStack input = inputs[i];
            if (input != null && !input.getType().isAir()) {
                ItemStack finalInput = input;
                set(holder, RECIPE_SLOTS[i], Items.decorate(input, "&f点击查看物品"), viewer -> openItem(viewer, finalInput));
            }
        }

        int slot = 46;
        for (String note : recipe.notes()) {
            if (slot > 52) {
                break;
            }
            set(holder, slot++, Items.button(Material.PAPER, note), null);
        }
    }

    private void renderItems(LensHolder holder, Player player, List<SlimefunItem> items, String title) {
        renderPaged(holder, items, item -> decorateForPlayer(player, item.getItem()), item -> openItem(player, item.getItem()));
        set(holder, 4, Items.button(Material.PAPER, "&f" + title + " (&a" + items.size() + "&f)"), null);
    }

    private <T> void renderPaged(
            LensHolder holder, List<T> entries, java.util.function.Function<T, ItemStack> icon, java.util.function.Consumer<T> action) {
        LensView view = holder.view();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(view.page(), pages - 1);
        int start = page * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int entryIndex = start + i;
            if (entryIndex >= entries.size()) {
                break;
            }
            T entry = entries.get(entryIndex);
            set(holder, LIST_SLOTS[i], icon.apply(entry), player -> action.accept(entry));
        }

        if (page > 0) {
            set(holder, 45, Items.button(Material.ARROW, "&f上一页"), player -> open(player, view.withPage(page - 1), false));
        }
        if (page + 1 < pages) {
            set(holder, 53, Items.button(Material.ARROW, "&f下一页"), player -> open(player, view.withPage(page + 1), false));
        }
        set(holder, 49, Items.button(Material.PAPER, "&7" + (page + 1) + " / " + pages), null);
    }

    private void showEmpty(LensHolder holder, String message) {
        set(holder, 22, Items.button(Material.BARRIER, message), null);
    }

    private ItemStack itemInfo(SlimefunItem item, Player player) {
        Research research = item.getResearch();
        if (research == null || !research.isEnabled()) {
            return Items.button(Material.NAME_TAG, "&7Slimefun ID: " + item.getId());
        }

        Optional<PlayerProfile> profile = PlayerProfile.find(player);
        if (profile.isEmpty()) {
            PlayerProfile.request(player);
            return Items.button(Material.CLOCK, "&e正在读取研究状态", "&7" + research.getName(player));
        }
        if (profile.get().hasUnlocked(research)) {
            return Items.button(Material.LIME_DYE, "&a已解锁研究", "&7" + research.getName(player));
        }

        return Items.button(
                Material.EXPERIENCE_BOTTLE,
                "&e解锁研究",
                "&7" + research.getName(player),
                "&7费用: " + research.getLevelCost() + " 级",
                "&f点击解锁");
    }

    private void tryUnlockResearch(Player player, SlimefunItem item, ItemKey itemKey) {
        Research research = item.getResearch();
        if (research == null || !research.isEnabled()) {
            return;
        }

        Optional<PlayerProfile> profile = PlayerProfile.find(player);
        if (profile.isEmpty()) {
            PlayerProfile.request(player);
            player.sendMessage("§e正在读取你的粘液科技档案，请稍后再试。");
            return;
        }
        if (profile.get().hasUnlocked(research)) {
            return;
        }
        if (!research.canUnlock(player)) {
            player.sendMessage("§c你的经验等级或余额不足，无法解锁这项研究。");
            return;
        }

        research.unlock(player, false, ignored -> Bukkit.getScheduler().runTask(
                plugin, () -> open(player, new LensView(LensView.Kind.ITEM, itemKey.value(), 0), false)));
    }

    private void beginSearch(Player player, LensView currentView) {
        pendingSearches.put(player.getUniqueId(), currentView);
        player.closeInventory();
        player.sendMessage("§aSlimeLens §7请输入搜索词，输入 §fcancel §7取消。");
    }

    private void goBack(Player player) {
        Deque<LensView> history = histories.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            openHome(player);
            return;
        }

        open(player, history.pop(), false);
    }

    private List<SlimefunItem> visibleItems(Player player) {
        List<SlimefunItem> visible = new ArrayList<>();
        for (SlimefunItem item : index.slimefunItems()) {
            if (isVisible(player, item)) {
                visible.add(item);
            }
        }
        return visible;
    }

    private boolean isVisible(Player player, SlimefunItem item) {
        return !item.isDisabled()
                && !item.isDisabledIn(player.getWorld())
                && (plugin.getConfig().getBoolean("show-hidden-items") || !item.isHidden())
                && item.getItemGroup().isAccessible(player);
    }

    private static boolean matches(SlimefunItem item, Player player, String query) {
        return normalized(item.getItemName()).contains(query)
                || item.getId().toLowerCase(Locale.ROOT).contains(query)
                || normalized(item.getItemGroup().getDisplayName(player)).contains(query)
                || item.getItem().getType().name().toLowerCase(Locale.ROOT).contains(query);
    }

    private static String normalized(String value) {
        return ChatColor.stripColor(value == null ? "" : value).toLowerCase(Locale.ROOT);
    }

    private static ItemStack decorateForPlayer(Player player, ItemStack item) {
        SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
        if (slimefunItem != null && !slimefunItem.canUse(player, false)) {
            return Items.decorate(item, "&e尚未解锁或当前不可使用");
        }
        return item == null ? null : item.clone();
    }

    private static ItemKey key(String value) {
        return new ItemKey(value);
    }

    private static void set(LensHolder holder, int slot, ItemStack item, LensHolder.Action action) {
        holder.getInventory().setItem(slot, item);
        if (action != null) {
            holder.setAction(slot, action);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof LensHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        LensHolder.Action action = holder.action(event.getRawSlot());
        if (action != null) {
            action.run(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LensHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSearchChat(AsyncPlayerChatEvent event) {
        LensView previous = pendingSearches.remove(event.getPlayer().getUniqueId());
        if (previous == null) {
            return;
        }

        event.setCancelled(true);
        String query = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) {
                return;
            }
            if (query.equalsIgnoreCase("cancel")) {
                open(player, previous, false);
            } else {
                open(player, new LensView(LensView.Kind.SEARCH, query, 0), true);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        activeViews.remove(id);
        histories.remove(id);
        pendingSearches.remove(id);
    }
}
