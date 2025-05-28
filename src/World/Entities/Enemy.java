package World.Entities;

import Inventory.EquipmentSlot;
import Inventory.ItemStack;
import World.Terrain.BaseTerrainGenerator;
import org.joml.Vector3f;

public abstract class Enemy extends LivingEntity {

    public Enemy(BaseTerrainGenerator worldTerrain, Vector3f initialPosition, Vector3f dimensions, float maxHealth) {
        super(worldTerrain, initialPosition, dimensions, maxHealth);
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
        // Most enemies might not use equipment in the same way players do.
        // This can be overridden by specific enemy types if needed.
        // For now, leave it empty or throw an UnsupportedOperationException.
        // System.out.println("Enemy " + id + " cannot equip items.");
    }

    @Override
    public float getEyeHeight() {
        // Default eye height, can be overridden by specific enemies.
        // Assuming eye height is roughly 80% of total height.
        return (this.localBoundingBox.max.y - this.localBoundingBox.min.y) * 0.8f;
    }

    // updateLogic will be implemented by specific enemy types for their AI
}