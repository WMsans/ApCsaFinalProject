package World.Entities;

import Configuration.Config;
import Graphics.Camera;
import Graphics.EntityModel;
import Graphics.ModelComponent;
import Physics.CustomAABB;
import World.Block;
import World.Terrain.BaseTerrainGenerator;
import Inventory.Hand;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Slash extends Entity {

    private PlayerEntity owner;
    private Camera camera; // For billboarding
    private ParticleSpawner particleSpawner;

    private float age;
    private final float lifespan; // How long the slash effect lasts
    private final Vector3f dimensions; // Dimensions of the slash collision/visual area

    private Set<UUID> hitEnemies; // To ensure each enemy is hit only once

    private static final Vector3f DEFAULT_SLASH_COLOR = new Vector3f(0.7f, 0.7f, 1.0f); // Light blue
    private static final float PARTICLE_BURST_COUNT = 50;
    private static final float PARTICLE_BURST_SPEED = 5.0f;
    private static final float PARTICLE_LIFESPAN = 1.5f;
    private static final float PARTICLE_SIZE = 0.2f;
    private static final Vector3f PARTICLE_COLOR_BLUE = new Vector3f(0.2f, 0.5f, 1.0f);


    public Slash(BaseTerrainGenerator worldTerrain, Camera camera, PlayerEntity owner, Vector3f initialPosition, Vector3f dimensions, float lifespan, ParticleSpawner particleSpawner) {
        super(worldTerrain, initialPosition, dimensions); // Dimensions used for localBoundingBox
        this.owner = owner;
        this.camera = camera;
        this.dimensions = new Vector3f(dimensions);
        this.lifespan = lifespan;
        this.particleSpawner = particleSpawner;
        this.age = 0.0f;
        this.skipCollisionProcessing = true; // Slash hitbox is manually checked, doesn't interact with terrain physically
        this.isOnGround = false;
        this.hitEnemies = new HashSet<>();
    }

    @Override
    protected void populateModelComponents() {
        if (modelComponents.isEmpty()) {
            // For now, a simple quad. Replace with animated sprite logic.
            // The size here is for the visual model, distinct from collision dimensions if needed.
            EntityModel slashModel = EntityModel.createQuadModel(Math.max(dimensions.x, dimensions.z) * 0.8f, DEFAULT_SLASH_COLOR);
            modelComponents.add(new ModelComponent(slashModel));
        }
    }

    @Override
    public Matrix4f getModelMatrix() {
        // Billboard effect: always face the camera
        Matrix4f modelMatrix = new Matrix4f();
        Vector3f camPos = camera.getPosition();

        Vector3f lookDir = new Vector3f(this.position).sub(camPos).normalize();
        Vector3f rightDir = new Vector3f(camera.getRightDirection(true));
        if (Math.abs(lookDir.dot(rightDir)) > 0.999f) {
            rightDir.set(0,0,1).cross(lookDir).normalize();
            if(rightDir.lengthSquared() < 0.001f) rightDir.set(1,0,0);
        }
        Vector3f upDir = new Vector3f(rightDir).cross(lookDir).normalize();
        rightDir.set(lookDir).cross(upDir).normalize(); // Re-orthogonalize right

        modelMatrix.m00(rightDir.x); modelMatrix.m01(rightDir.y); modelMatrix.m02(rightDir.z); modelMatrix.m03(0);
        modelMatrix.m10(upDir.x);    modelMatrix.m11(upDir.y);    modelMatrix.m12(upDir.z);    modelMatrix.m13(0);
        modelMatrix.m20(-lookDir.x); modelMatrix.m21(-lookDir.y); modelMatrix.m22(-lookDir.z); modelMatrix.m23(0);
        modelMatrix.m30(this.position.x); modelMatrix.m31(this.position.y); modelMatrix.m32(this.position.z); modelMatrix.m33(1);

        // The quad model is in XY plane, size is handled by createQuadModel.
        // No additional scaling here unless the quad model is unit size and needs scaling.
        // If createQuadModel's size parameter is visual size, no need to scale here.
        // modelMatrix.scale(this.dimensions.x, this.dimensions.y, this.dimensions.z); // If quad is unit sized.

        return modelMatrix;
    }


    @Override
    protected void updateLogic(float deltaTime) {
        age += deltaTime;
        if (age >= lifespan) {
            kill();
            return;
        }

        // Keep the slash centered below the camera
        this.position.set(owner.getCamera().getPosition()).sub(0, 0.8f, 0); // Adjust Y offset as needed

        // Collision detection with enemies
        CustomAABB slashHitbox = this.getBoundingBoxWorld(); // Recalculate world bounds each frame as it moves
        List<Entity> entities = worldTerrain.getEntities();

        for (Entity entity : entities) {
            if (entity instanceof Enemy && entity.isValid() && !hitEnemies.contains(entity.getId())) {
                Enemy enemy = (Enemy) entity;
                if (slashHitbox.testAABB(enemy.getBoundingBoxWorld())) { // Broad phase collision
                    boolean hitShield = false;
                    Vector3f impactCenter = enemy.getPosition();

                    if (enemy.hasCustomBlockingGeometry()) {
                        if (enemy.checkCustomBlockingGeometry(enemy.getPosition(), owner.getPosition())) {
                            hitShield = true;
                            impactCenter.set(enemy.getPosition()).add(new Vector3f(enemy.getForwardDirection(false)).mul(1.5f)); // Approx shield offset
                        }
                    }
                    if (hitShield) {
                        System.out.println("Slash blocked by shield!");
                        if (particleSpawner != null) {
                            particleSpawner.spawnBurst(
                                    impactCenter, (int) (PARTICLE_BURST_COUNT / 2), PARTICLE_BURST_SPEED * 0.5f,
                                    PARTICLE_LIFESPAN * 0.5f, 0.05f, new Vector3f(1.0f, 0.9f, 0.2f), // Yellow particles
                                    PARTICLE_SIZE * 1.2f, null);
                        }
                        kill();
                    } else { // Not hit shield
                        // Apply massive damage
                        owner.attackLivingEntity(enemy, Float.MAX_VALUE); // Deal effectively infinite damage
                        hitEnemies.add(enemy.getId()); // Mark as hit

                        // Spawn particle burst
                        if (particleSpawner != null) {
                            particleSpawner.spawnBurst(
                                    // Spawn particles at the enemy's position for better visual feedback of the hit
                                    enemy.getPosition().add(0, enemy.getLocalBoundingBox().getHeight() / 2f, 0), // Center of enemy
                                    (int) PARTICLE_BURST_COUNT,
                                    PARTICLE_BURST_SPEED,
                                    PARTICLE_LIFESPAN,
                                    0.1f, // Slight gravity effect for particles
                                    PARTICLE_COLOR_BLUE,
                                    PARTICLE_SIZE,
                                    new Vector3f(0, 1.0f, 0) // Slight upward base velocity for burst
                            );
                        }
                    }
                }
            }
        }
    }

    // Particles typically don't interact
    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand) {}
    @Override
    public void onEntityInteraction(Entity target, Hand hand) {}
}