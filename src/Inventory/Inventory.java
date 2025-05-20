package Inventory;

import org.joml.Vector3f; // For potential item properties
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Inventory {
    private final Map<EquipmentSlot, ItemStack> equippedItems;
    private final List<ItemStack> mainInventory;
    private final int mainInventorySize;

    public Inventory(int mainInventorySize) {
        this.mainInventorySize = mainInventorySize;
        this.equippedItems = new HashMap<>();
        this.mainInventory = new ArrayList<>(mainInventorySize);
        for (int i = 0; i < mainInventorySize; i++) {
            mainInventory.add(ItemStack.EMPTY);
        }
    }

    public ItemStack getEquipped(EquipmentSlot slot) {
        return equippedItems.getOrDefault(slot, ItemStack.EMPTY);
    }

    public void setEquipped(EquipmentSlot slot, ItemStack stack) {
        equippedItems.put(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    public ItemStack getItem(int slot) {
        if (slot >= 0 && slot < mainInventory.size()) {
            return mainInventory.get(slot);
        }
        return ItemStack.EMPTY;
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < mainInventory.size()) {
            mainInventory.set(slot, stack == null ? ItemStack.EMPTY : stack);
        }
    }

    public boolean addItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true; // Nothing to add

        // Try to stack with existing items
        for (int i = 0; i < mainInventory.size(); i++) {
            ItemStack current = mainInventory.get(i);
            if (!current.isEmpty() && current.getItemId().equals(stack.getItemId())) {
                // Assuming a max stack size (e.g., 64) - not implemented here
                int canAdd = 64 - current.getCount(); // Placeholder max stack size
                if (canAdd > 0) {
                    int toAdd = Math.min(canAdd, stack.getCount());
                    current.setCount(current.getCount() + toAdd);
                    stack.setCount(stack.getCount() - toAdd);
                    if (stack.isEmpty()) return true;
                }
            }
        }
        // Try to add to an empty slot
        for (int i = 0; i < mainInventory.size(); i++) {
            if (mainInventory.get(i).isEmpty()) {
                mainInventory.set(i, stack);
                return true;
            }
        }
        return false; // Inventory full
    }

    public void clear() {
        equippedItems.clear();
        for (int i = 0; i < mainInventory.size(); i++) {
            mainInventory.set(i, ItemStack.EMPTY);
        }
    }
}
