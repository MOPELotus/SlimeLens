package cn.sxau.slimelens.data;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class LensRecipe {

    private final String id;
    private final String stationName;
    private final ItemStack station;
    private final ItemStack[] inputs;
    private final ItemStack output;
    private final List<String> notes;

    public LensRecipe(
            String id,
            String stationName,
            ItemStack station,
            ItemStack[] inputs,
            ItemStack output,
            List<String> notes) {
        this.id = id;
        this.stationName = stationName;
        this.station = station == null ? null : station.clone();
        this.inputs = copyInputs(inputs);
        this.output = output == null ? null : output.clone();
        this.notes = List.copyOf(notes);
    }

    public String id() {
        return id;
    }

    public String stationName() {
        return stationName;
    }

    public ItemStack station() {
        return station == null ? null : station.clone();
    }

    public ItemStack[] inputs() {
        return copyInputs(inputs);
    }

    public ItemStack output() {
        return output == null ? null : output.clone();
    }

    public List<String> notes() {
        return notes;
    }

    private static ItemStack[] copyInputs(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[9];
        if (source == null) {
            return copy;
        }

        for (int i = 0; i < Math.min(source.length, copy.length); i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }

        return copy;
    }
}
