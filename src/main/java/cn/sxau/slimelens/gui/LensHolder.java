package cn.sxau.slimelens.gui;

import cn.sxau.slimelens.data.LensView;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LensHolder implements InventoryHolder {

    @FunctionalInterface
    public interface Action {
        void run(Player player);
    }

    private final LensView view;
    private final Map<Integer, Action> actions = new HashMap<>();
    private Inventory inventory;

    public LensHolder(LensView view) {
        this.view = view;
    }

    public LensView view() {
        return view;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setAction(int slot, Action action) {
        actions.put(slot, action);
    }

    public Action action(int slot) {
        return actions.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
