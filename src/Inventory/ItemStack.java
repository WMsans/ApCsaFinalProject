package Inventory;

public class ItemStack {
    private String itemId;
    private int count;
    // Could have more properties like damage, type, NBT data, etc.

    public ItemStack(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isEmpty() {
        return count <= 0 || itemId == null || itemId.isEmpty();
    }

    // Placeholder: In a real game, this would be more complex
    public static ItemStack EMPTY = new ItemStack(null, 0);
}
