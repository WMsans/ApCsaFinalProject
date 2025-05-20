package World.Entities;

import Inventory.*;
import World.Terrain;
import org.joml.Vector3f;

public abstract class LivingEntity extends Entity {
    protected float health;
    protected float maxHealth;

    // Basic line of sight parameters
    protected float sightRange = 16.0f; // Max distance to see other entities

    public LivingEntity(Terrain worldTerrain, Vector3f initialPosition, Vector3f dimensions, float maxHealth) {
        super(worldTerrain, initialPosition, dimensions);
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public void damage(float amount) {
        if (!isValid) return;
        this.health -= amount;
        if (this.health <= 0) {
            this.health = 0;
            kill(); // Entity dies if health is 0 or less
        }
    }

    public void heal(float amount) {
        if (!isValid) return;
        this.health += amount;
        if (this.health > maxHealth) {
            this.health = maxHealth;
        }
    }

    /**
     * Handles attacking another living entity.
     * @param target The LivingEntity to attack.
     * @param damageAmount The amount of damage to deal.
     */
    public void attackLivingEntity(LivingEntity target, float damageAmount) {
        if (target != null && target.isValid()) {
            // Could add checks for range, line of sight, etc.
            System.out.println(this.id + " attacks " + target.id + " for " + damageAmount + " damage.");
            target.damage(damageAmount);
        }
    }

    /**
     * Checks if this entity has a line of sight to another entity.
     * This is a simplified check, a real implementation would involve raycasting.
     * @param entity The entity to check line of sight to.
     * @return True if there's a line of sight (simplified), false otherwise.
     */
    public boolean canSee(Entity entity) {
        if (entity == null || !entity.isValid()) return false;

        Vector3f directionToTarget = new Vector3f(entity.getPosition()).sub(this.getPosition());
        float distanceSquared = directionToTarget.lengthSquared();

        if (distanceSquared > sightRange * sightRange) {
            return false; // Out of sight range
        }

        // TODO: Implement actual raycasting against terrain/other obstacles
        // For now, assume clear line of sight if within range.
        return true;
    }

    /**
     * Equips an item in a specific slot.
     * This would typically be managed by an inventory component.
     * @param slot The slot to equip the item in.
     * @param stack The item stack to equip.
     */
    public abstract void equipStack(EquipmentSlot slot, ItemStack stack);

    /**
     * Returns the eye height of the entity, relative to its base position.
     * @return The eye height.
     */
    public abstract float getEyeHeight();

    @Override
    public void kill() {
        super.kill(); // Calls Entity.kill() to set isValid = false
        System.out.println("LivingEntity " + id + " has died.");
        // Additional logic for living entities on death (e.g., drop loot)
    }

    @Override
    protected void updateLogic(float deltaTime) {
        // LivingEntity specific logic can go here, e.g., health regeneration
        // if (isOnGround && health < maxHealth) {
        //    heal(0.05f * deltaTime); // Slow passive regeneration
        // }
    }
}

