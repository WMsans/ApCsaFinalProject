package World.Entities;

import Graphics.EntityModel;
import Graphics.ModelComponent;
import Inventory.EquipmentSlot;
import Inventory.Hand;
import Inventory.ItemStack;
import World.Block;
import World.Terrain.BaseTerrainGenerator;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Bullet extends LivingEntity {

    private static final float BULLET_SPEED = 50.0f;
    private static final float BULLET_LIFESPAN = 3.0f; // seconds
    private static final float BULLET_DAMAGE = 5.0f;
    private static final Vector3f BULLET_DIMENSIONS = new Vector3f(0.2f, 0.2f, 0.2f);
    private static final Vector3f BULLET_COLOR = new Vector3f(1.0f, 0.1f, 0.1f); // Bright red

    private float lifeTimer;
    private Entity shooter; // The entity that fired this bullet

    public Bullet(BaseTerrainGenerator worldTerrain, Vector3f initialPosition, Vector3f direction, Entity shooter) {
        super(worldTerrain, initialPosition, BULLET_DIMENSIONS, 1.0f); // Minimal health
        this.velocity.set(direction).normalize().mul(BULLET_SPEED);
        this.lifeTimer = BULLET_LIFESPAN;
        this.shooter = shooter;
        this.skipCollisionProcessing = false; // Bullets should collide
        this.isOnGround = false; // Bullets are typically airborne
    }

    @Override
    protected void populateModelComponents() {
        if (modelComponents.isEmpty()) {
            EntityModel bulletModel = EntityModel.createCubeModel(BULLET_DIMENSIONS.x, BULLET_COLOR);
            modelComponents.add(new ModelComponent(bulletModel));
        }
    }

    @Override
    protected void applyGravity(float deltaTime) {
        // Bullets might not be affected by gravity, or have very little
        // super.applyGravity(deltaTime * 0.1f); // Example: reduced gravity
        // For now, no gravity:
    }

    @Override
    public Matrix4f getModelMatrix() {
        Matrix4f modelMatrix = new Matrix4f().translate(position);
        if (velocity.lengthSquared() > 0.001f) {
            Vector3f dirNorm = new Vector3f(velocity).normalize();
            // Default model forward is typically along Z-axis (0,0,1) or X-axis (1,0,0)
            // Assuming the cube model's "front" aligns with positive Z if not rotated.
            Vector3f modelForward = new Vector3f(0, 0, 1);

            Quaternionf rotation = new Quaternionf().rotationTo(modelForward, dirNorm);
            modelMatrix.rotate(rotation);
        }
        return modelMatrix;
    }


    @Override
    protected void updateLogic(float deltaTime) {
        lifeTimer -= deltaTime;
        if (lifeTimer <= 0) {
            kill();
            return;
        }

        // Check for collision with entities (simplified)
        // A more robust solution would use the physics engine or spatial partitioning
        for (Entity entity : worldTerrain.getEntities()) {
            if (entity.isValid() && entity != this && entity != shooter && !(entity instanceof Bullet)) {
                if (this.getBoundingBoxWorld().testAABB(entity.getBoundingBoxWorld())) {
                    if (entity instanceof LivingEntity) {
                        ((LivingEntity) entity).damage(BULLET_DAMAGE);
                    }
                    kill(); // Bullet is destroyed on impact
                    return;
                }
            }
        }
    }

    @Override
    protected void moveEntity(float deltaTime) {
        // Simplified moveEntity for bullets: only check block collision
        if (deltaTime == 0) return;
        Vector3f potentialMovement = new Vector3f(velocity).mul(deltaTime);

        // Test for block collision
        Vector3f nextPosition = new Vector3f(position).add(potentialMovement);
        if (worldTerrain.isBlockAt(nextPosition)) {
            // Optional: Create an impact effect or sound
            kill(); // Destroy bullet on block impact
            return;
        }

        position.add(potentialMovement); // Apply movement if no collision
    }


    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand) {
        // Bullets don't interact this way
    }

    @Override
    public void onEntityInteraction(Entity target, Hand hand) {
        // Interactions handled in updateLogic's collision check
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
        // Bullets don't have equipment
    }

    @Override
    public float getEyeHeight() {
        return BULLET_DIMENSIONS.y / 2.0f;
    }
}