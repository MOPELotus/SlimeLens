package cn.sxau.slimelens.data;

import cn.sxau.slimelens.util.Items;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.recipes.MinecraftRecipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.RecipeChoice.ExactChoice;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;

public final class LensIndex {

    private Map<ItemKey, List<LensRecipe>> recipesByOutput = Map.of();
    private Map<ItemKey, List<LensRecipe>> usesByInput = Map.of();
    private Map<ItemKey, List<LensRecipe>> recipesByStation = Map.of();
    private Map<String, LensRecipe> recipesById = Map.of();
    private Map<ItemKey, ItemStack> knownItems = Map.of();
    private Map<String, ItemGroup> groups = Map.of();
    private List<SlimefunItem> slimefunItems = List.of();
    private List<SlimefunItem> machines = List.of();

    public RebuildSession beginRebuild(boolean indexVanillaRecipes) {
        List<SlimefunItem> loadedItems = new ArrayList<>(Slimefun.getRegistry().getEnabledSlimefunItems());
        List<Recipe> vanillaRecipes = new ArrayList<>();

        if (indexVanillaRecipes) {
            Iterator<Recipe> iterator = org.bukkit.Bukkit.recipeIterator();
            iterator.forEachRemaining(vanillaRecipes::add);
        }

        return new RebuildSession(loadedItems, vanillaRecipes);
    }

    public final class RebuildSession {

        private final Map<ItemKey, List<LensRecipe>> outputIndex = new HashMap<>();
        private final Map<ItemKey, List<LensRecipe>> inputIndex = new HashMap<>();
        private final Map<ItemKey, List<LensRecipe>> stationIndex = new HashMap<>();
        private final Map<String, LensRecipe> idIndex = new HashMap<>();
        private final Map<ItemKey, ItemStack> itemIndex = new HashMap<>();
        private final Map<String, ItemGroup> groupIndex = new HashMap<>();
        private final List<SlimefunItem> itemList = new ArrayList<>();
        private final List<SlimefunItem> machineList = new ArrayList<>();
        private final List<SlimefunItem> loadedItems;
        private final List<Recipe> vanillaRecipes;

        private int slimefunCursor;
        private int vanillaCursor;
        private int indexedVanillaRecipes;
        private boolean complete;
        private IndexStats stats;

        private RebuildSession(List<SlimefunItem> loadedItems, List<Recipe> vanillaRecipes) {
            this.loadedItems = loadedItems;
            this.vanillaRecipes = vanillaRecipes;
        }

        public boolean advance(int workUnits) {
            if (complete) {
                return true;
            }

            int remaining = Math.max(1, workUnits);
            while (remaining > 0 && slimefunCursor < loadedItems.size()) {
                indexSlimefunItem(loadedItems.get(slimefunCursor++));
                remaining--;
            }

            while (remaining > 0 && vanillaCursor < vanillaRecipes.size()) {
                if (indexVanillaRecipe(
                        vanillaRecipes.get(vanillaCursor),
                        vanillaCursor,
                        outputIndex,
                        inputIndex,
                        stationIndex,
                        idIndex,
                        itemIndex)) {
                    indexedVanillaRecipes++;
                }
                vanillaCursor++;
                remaining--;
            }

            if (slimefunCursor == loadedItems.size() && vanillaCursor == vanillaRecipes.size()) {
                publish();
                complete = true;
            }

            return complete;
        }

        public IndexStats stats() {
            if (!complete) {
                throw new IllegalStateException("The index build has not finished yet");
            }
            return stats;
        }

        private void indexSlimefunItem(SlimefunItem item) {
            if (item == null || item.isDisabled()) {
                return;
            }

            itemList.add(item);
            groupIndex.put(item.getItemGroup().getKey().toString(), item.getItemGroup());
            remember(itemIndex, item.getItem());
            remember(itemIndex, item.getRecipeOutput());

            ItemStack[] inputs = item.getRecipe();
            LensRecipe recipe = new LensRecipe(
                    "slimefun:item:" + item.getId(),
                    recipeTypeName(item.getRecipeType()),
                    item.getRecipeType().toItem(),
                    inputs,
                    item.getRecipeOutput(),
                    List.of("&8Slimefun 物品合成"));
            addRecipe(recipe, inputKeys(inputs), outputIndex, inputIndex, stationIndex, idIndex, itemIndex);

            if (item instanceof RecipeDisplayItem displayItem) {
                machineList.add(item);
                indexMachineRecipes(item, displayItem, outputIndex, inputIndex, stationIndex, idIndex, itemIndex);
            }
        }

        private void publish() {
            Comparator<SlimefunItem> sorter = Comparator.comparing(
                    item -> Items.plain(item.getItemName()).toLowerCase(Locale.ROOT));
            itemList.sort(sorter);
            machineList.sort(sorter);
            sortRecipeLists(outputIndex.values());
            sortRecipeLists(inputIndex.values());
            sortRecipeLists(stationIndex.values());

            recipesByOutput = immutableRecipeMap(outputIndex);
            usesByInput = immutableRecipeMap(inputIndex);
            recipesByStation = immutableRecipeMap(stationIndex);
            recipesById = Map.copyOf(idIndex);
            knownItems = immutableItemMap(itemIndex);
            groups = Map.copyOf(groupIndex);
            slimefunItems = List.copyOf(itemList);
            machines = List.copyOf(machineList);
            stats = new IndexStats(slimefunItems.size(), machines.size(), recipesById.size(), indexedVanillaRecipes);
        }
    }

    public List<SlimefunItem> slimefunItems() {
        return slimefunItems;
    }

    public List<SlimefunItem> machines() {
        return machines;
    }

    public Collection<ItemGroup> groups() {
        return groups.values();
    }

    public ItemGroup group(String key) {
        return groups.get(key);
    }

    public List<LensRecipe> recipesFor(ItemKey key) {
        return recipesByOutput.getOrDefault(key, List.of());
    }

    public List<LensRecipe> usesFor(ItemKey key) {
        return usesByInput.getOrDefault(key, List.of());
    }

    public List<LensRecipe> recipesAt(ItemKey station) {
        return recipesByStation.getOrDefault(station, List.of());
    }

    public LensRecipe recipe(String id) {
        return recipesById.get(id);
    }

    public ItemStack resolve(ItemKey key) {
        if (key == null || key.isAir()) {
            return new ItemStack(Material.AIR);
        }

        if (key.isSlimefun()) {
            SlimefunItem slimefunItem = SlimefunItem.getById(key.slimefunId());
            if (slimefunItem != null) {
                return slimefunItem.getItem().clone();
            }
        }

        ItemStack known = knownItems.get(key);
        if (known != null) {
            return known.clone();
        }

        Material material = key.material();
        return material.isAir() ? new ItemStack(Material.BARRIER) : new ItemStack(material);
    }

    private boolean indexVanillaRecipe(
            Recipe recipe,
            int fallback,
            Map<ItemKey, List<LensRecipe>> outputIndex,
            Map<ItemKey, List<LensRecipe>> inputIndex,
            Map<ItemKey, List<LensRecipe>> stationIndex,
            Map<String, LensRecipe> idIndex,
            Map<ItemKey, ItemStack> itemIndex) {
        Optional<MinecraftRecipe<? super Recipe>> minecraftRecipe = MinecraftRecipe.of(recipe);
        if (minecraftRecipe.isEmpty() || recipe.getResult() == null || recipe.getResult().getType().isAir()) {
            return false;
        }

        try {
            RecipeChoice[] choices = Slimefun.getMinecraftRecipeService().getRecipeShape(recipe);
            ItemStack[] inputs = new ItemStack[9];
            Set<ItemKey> inputKeys = new LinkedHashSet<>();

            for (int i = 0; i < Math.min(choices.length, inputs.length); i++) {
                ChoiceItems choiceItems = choiceItems(choices[i]);
                inputs[i] = choiceItems.display();
                inputKeys.addAll(choiceItems.keys());
                remember(itemIndex, choiceItems.display());
            }

            RecipeType recipeType = new RecipeType(minecraftRecipe.get());
            String recipeId = recipeId(recipe, fallback);
            LensRecipe lensRecipe = new LensRecipe(
                    "minecraft:" + recipeId,
                    recipeTypeName(recipeType),
                    recipeType.toItem(),
                    inputs,
                    recipe.getResult(),
                    List.of("&8原版配方: " + recipeId));
            addRecipe(lensRecipe, inputKeys, outputIndex, inputIndex, stationIndex, idIndex, itemIndex);
            return true;
        } catch (Exception | LinkageError ignored) {
            // Unsupported server recipes remain available through their owning plugin.
            return false;
        }
    }

    private void indexMachineRecipes(
            SlimefunItem machine,
            RecipeDisplayItem displayItem,
            Map<ItemKey, List<LensRecipe>> outputIndex,
            Map<ItemKey, List<LensRecipe>> inputIndex,
            Map<ItemKey, List<LensRecipe>> stationIndex,
            Map<String, LensRecipe> idIndex,
            Map<ItemKey, ItemStack> itemIndex) {
        List<ItemStack> recipes;
        try {
            recipes = displayItem.getDisplayRecipes();
        } catch (Exception | LinkageError ignored) {
            return;
        }

        for (int i = 0; i + 1 < recipes.size(); i += 2) {
            ItemStack input = recipes.get(i);
            ItemStack output = recipes.get(i + 1);
            if (output == null || output.getType().isAir()) {
                continue;
            }

            ItemStack[] inputs = new ItemStack[9];
            inputs[4] = input == null ? null : input.clone();
            LensRecipe lensRecipe = new LensRecipe(
                    "slimefun:machine:" + machine.getId() + ':' + (i / 2),
                    machine.getItemName(),
                    machine.getItem(),
                    inputs,
                    output,
                    List.of("&8机器配方"));
            addRecipe(lensRecipe, inputKeys(inputs), outputIndex, inputIndex, stationIndex, idIndex, itemIndex);
        }
    }

    private static ChoiceItems choiceItems(RecipeChoice choice) {
        if (choice == null) {
            return ChoiceItems.EMPTY;
        }

        if (choice instanceof MaterialChoice materialChoice) {
            List<Material> materials = materialChoice.getChoices();
            if (materials.isEmpty()) {
                return ChoiceItems.EMPTY;
            }

            ItemStack display = new ItemStack(materials.getFirst());
            Set<ItemKey> keys = new LinkedHashSet<>();
            for (Material material : materials) {
                keys.add(ItemKey.of(new ItemStack(material)));
            }
            return new ChoiceItems(display, keys);
        }

        if (choice instanceof ExactChoice exactChoice) {
            List<ItemStack> items = exactChoice.getChoices();
            if (items.isEmpty()) {
                return ChoiceItems.EMPTY;
            }

            Set<ItemKey> keys = new LinkedHashSet<>();
            for (ItemStack item : items) {
                keys.add(ItemKey.of(item));
            }
            return new ChoiceItems(items.getFirst().clone(), keys);
        }

        ItemStack display = choice.getItemStack();
        return display == null ? ChoiceItems.EMPTY : new ChoiceItems(display, Set.of(ItemKey.of(display)));
    }

    private static void addRecipe(
            LensRecipe recipe,
            Set<ItemKey> inputKeys,
            Map<ItemKey, List<LensRecipe>> outputIndex,
            Map<ItemKey, List<LensRecipe>> inputIndex,
            Map<ItemKey, List<LensRecipe>> stationIndex,
            Map<String, LensRecipe> idIndex,
            Map<ItemKey, ItemStack> itemIndex) {
        ItemStack output = recipe.output();
        ItemKey outputKey = ItemKey.of(output);
        if (outputKey.isAir() || idIndex.containsKey(recipe.id())) {
            return;
        }

        idIndex.put(recipe.id(), recipe);
        outputIndex.computeIfAbsent(outputKey, ignored -> new ArrayList<>()).add(recipe);
        remember(itemIndex, output);

        for (ItemKey inputKey : inputKeys) {
            if (!inputKey.isAir()) {
                inputIndex.computeIfAbsent(inputKey, ignored -> new ArrayList<>()).add(recipe);
            }
        }

        ItemStack station = recipe.station();
        ItemKey stationKey = ItemKey.of(station);
        if (!stationKey.isAir()) {
            stationIndex.computeIfAbsent(stationKey, ignored -> new ArrayList<>()).add(recipe);
            remember(itemIndex, station);
        }

        for (ItemStack input : recipe.inputs()) {
            remember(itemIndex, input);
        }
    }

    private static void remember(Map<ItemKey, ItemStack> itemIndex, ItemStack item) {
        ItemKey key = ItemKey.of(item);
        if (!key.isAir() && item != null) {
            itemIndex.putIfAbsent(key, item.clone());
        }
    }

    private static Set<ItemKey> inputKeys(ItemStack[] inputs) {
        Set<ItemKey> keys = new LinkedHashSet<>();
        if (inputs == null) {
            return keys;
        }

        for (ItemStack input : inputs) {
            ItemKey key = ItemKey.of(input);
            if (!key.isAir()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static String recipeTypeName(RecipeType type) {
        ItemStack item = type.toItem();
        return item == null ? type.getKey().getKey() : Items.name(item);
    }

    private static String recipeId(Recipe recipe, int fallback) {
        if (recipe instanceof Keyed keyed) {
            NamespacedKey key = keyed.getKey();
            return key.getNamespace() + ':' + key.getKey();
        }
        return recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT) + ':' + fallback;
    }

    private static void sortRecipeLists(Collection<List<LensRecipe>> lists) {
        for (List<LensRecipe> recipes : lists) {
            recipes.sort(Comparator.comparing(recipe -> Items.plain(recipe.stationName()).toLowerCase(Locale.ROOT)));
        }
    }

    private static Map<ItemKey, List<LensRecipe>> immutableRecipeMap(Map<ItemKey, List<LensRecipe>> source) {
        Map<ItemKey, List<LensRecipe>> copy = new HashMap<>();
        source.forEach((key, recipes) -> copy.put(key, List.copyOf(recipes)));
        return Map.copyOf(copy);
    }

    private static Map<ItemKey, ItemStack> immutableItemMap(Map<ItemKey, ItemStack> source) {
        Map<ItemKey, ItemStack> copy = new HashMap<>();
        source.forEach((key, item) -> copy.put(key, item.clone()));
        return Map.copyOf(copy);
    }

    private record ChoiceItems(ItemStack display, Set<ItemKey> keys) {
        private static final ChoiceItems EMPTY = new ChoiceItems(null, Set.of());
    }

    public record IndexStats(int slimefunItems, int machines, int recipes, int vanillaRecipes) {}
}
