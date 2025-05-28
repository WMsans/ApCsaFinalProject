package World.Entities.Enemies;

import Graphics.EntityModel;
import Graphics.ModelComponent;
import World.Block;
import World.Entities.*;
import World.Terrain.BaseTerrainGenerator;
import Inventory.Hand;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

public class ChromeSentinel extends Enemy {

    private static final Vector3f BODY_DIMENSIONS = new Vector3f(4.0f, 4.0f, 4.0f); // Large cube body
    private static final Vector3f BODY_COLOR_CHROME = new Vector3f(0.75f, 0.75f, 0.75f); // Reflective chrome
    private static final Vector3f BODY_COLOR_OBSIDIAN = new Vector3f(0.05f, 0.03f, 0.1f); // Polished obsidian

    private static final Vector3f LAUNCHER_DIMENSIONS = new Vector3f(0.5f, 0.5f, 3.0f); // Long cuboid
    private static final Vector3f LAUNCHER_COLOR = new Vector3f(0.2f, 0.2f, 0.2f);
    private static final Vector3f LAUNCHER_OFFSET = new Vector3f(0, 0, (BODY_DIMENSIONS.z / 2.0f) + (LAUNCHER_DIMENSIONS.z / 2.0f)); // Position in front of body

    private static final Vector3f NEON_LIGHT_COLOR = new Vector3f(0.1f, 0.8f, 1.0f); // Pulsating neon blue/cyan
    private static final float MAX_HEALTH = 200.0f;
    private static final float MOVEMENT_SPEED = 1.0f; // Slower moving
    private static final float PREFERRED_DISTANCE_MIN = 20.0f;
    private static final float PREFERRED_DISTANCE_MAX = 40.0f;
    private static final float FIRING_INTERVAL = 3.0f; // Seconds
    private float fireCooldown;
    private float currentCharge; // For neon light pulsation

    private boolean useChromeColor;

    public ChromeSentinel(BaseTerrainGenerator worldTerrain, Vector3f initialPosition) {
        super(worldTerrain, initialPosition, BODY_DIMENSIONS, MAX_HEALTH);
        this.yaw = (float) (Math.random() * 360.0); // Random initial orientation
        this.fireCooldown = FIRING_INTERVAL * (float)Math.random(); // Stagger initial firing
        this.skipCollisionProcessing = true; // It's a floating fortress
        this.isOnGround = false;
        this.useChromeColor = Math.random() > 0.5;
        this.currentCharge = 0f;
    }

    @Override
    protected void populateModelComponents() {
        if (modelComponents.isEmpty()) {
            // Body
            EntityModel bodyModel = EntityModel.createCubeModel(BODY_DIMENSIONS.x, useChromeColor ? BODY_COLOR_CHROME : BODY_COLOR_OBSIDIAN);
            modelComponents.add(new ModelComponent(bodyModel));

            // Laser Launcher
            EntityModel launcherModel = EntityModel.createCuboidModel(LAUNCHER_DIMENSIONS.x, LAUNCHER_DIMENSIONS.y, LAUNCHER_DIMENSIONS.z, LAUNCHER_COLOR);
            Matrix4f launcherTransform = new Matrix4f().translate(LAUNCHER_OFFSET);
            modelComponents.add(new ModelComponent(launcherModel, launcherTransform));

            // Pulsating Light (simple cube for now, could be part of body texture/shader later)
            // This demonstrates a component with a different color that might need shader adjustments for emissiveness.
            EntityModel lightModel = EntityModel.createCubeModel(0.5f, NEON_LIGHT_COLOR); // Smaller cube for light
            Matrix4f lightTransform = new Matrix4f().translate(0, BODY_DIMENSIONS.y / 2f + 0.25f, 0); // On top
            modelComponents.add(new ModelComponent(lightModel, lightTransform));
        }
    }

    @Override
    public Matrix4f getModelMatrix() {
        // For a floating entity, pitch might be less relevant, or it could slowly pitch towards target
        // Roll could be used for dynamic effects, but keep it simple for now.
        Matrix4f modelMatrix = new Matrix4f().translate(position);
        modelMatrix.rotateY((float)Math.toRadians(yaw));
        // modelMatrix.rotateX((float)Math.toRadians(pitch)); // Optional: if it visually tilts
        // modelMatrix.rotateZ((float)Math.toRadians(roll));  // Optional

        // The local transforms in ModelComponent will handle parts relative to this base matrix
        return modelMatrix;
    }


    @Override
    protected void updateLogic(float deltaTime) {
        // Behavior: find player, maintain distance, fire.
        PlayerEntity player = null;
        float closestDistSq = Float.MAX_VALUE;

        for (Entity e : worldTerrain.getEntities()) {
            if (e instanceof PlayerEntity && e.isValid()) {
                float distSq = e.getPosition().distanceSquared(this.position);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    player = (PlayerEntity) e;
                }
            }
        }

        currentCharge += deltaTime;
        // Update neon light color based on charge (simple pulsation)
        // This is a placeholder. Real pulsation might involve shader uniforms.
        if (!modelComponents.isEmpty()) {
            ModelComponent lightComp = modelComponents.get(modelComponents.size() -1); // Assuming light is last
            if (lightComp != null && lightComp.model() != null) { // Check if lightComp and its model are not null
                float pulse = (float) (Math.sin(currentCharge * 2.0) + 1.0) / 2.0f; // 0 to 1
                Vector3f currentLightColor = new Vector3f(NEON_LIGHT_COLOR).mul(0.5f + pulse * 0.5f);
                // To dynamically change color, we'd ideally rebuild the model or use shader uniforms.
                // For simplicity, this example doesn't dynamically rebuild the VBO here.
                // A better way: make a new EntityModel.createCubeModel(0.5f, currentLightColor)
                // and then tell ModelRenderer to rebuild it. This is inefficient.
                // Best way: use a shader uniform for the emissive color.
            }
        }


        if (player != null) {
            Vector3f directionToPlayer = new Vector3f(player.getPosition()).sub(this.position);
            float distanceToPlayer = directionToPlayer.length();

            // Aiming: Rotate YAW towards player
            this.yaw = (float) Math.toDegrees(Math.atan2(directionToPlayer.z, directionToPlayer.x)) - 90;


            // Movement: Maintain preferred distance (simplified)
            Vector3f moveDirection = new Vector3f();
            if (distanceToPlayer < PREFERRED_DISTANCE_MIN) {
                moveDirection.set(directionToPlayer).normalize().mul(-1.0f); // Move away
            } else if (distanceToPlayer > PREFERRED_DISTANCE_MAX) {
                moveDirection.set(directionToPlayer).normalize(); // Move closer
            }

            // Since it's floating, directly adjust position. No complex velocity/acceleration.
            if (moveDirection.lengthSquared() > 0.01f) {
                // Ensure movement is primarily horizontal for a floating fortress
                moveDirection.y = 0; // Keep it at the same altitude or implement altitude control
                if (moveDirection.lengthSquared() > 0.01f) {
                    moveDirection.normalize();
                }
                this.position.add(moveDirection.mul(MOVEMENT_SPEED * deltaTime));
            }


            // Firing
            fireCooldown -= deltaTime;
            if (fireCooldown <= 0) {
                fireLaser(player);
                fireCooldown = FIRING_INTERVAL;
                currentCharge = 0f; // Reset charge for light pulsation
            }
        }
    }

    private void fireLaser(PlayerEntity target) {
        Vector3f launcherWorldPosition = getLauncherWorldPosition();
        Vector3f directionToTarget = new Vector3f(target.getPosition()).add(0, target.getEyeHeight(),0).sub(launcherWorldPosition).normalize();

        Bullet bullet = new Bullet(worldTerrain, launcherWorldPosition, directionToTarget, this);
        worldTerrain.addEntity(bullet);

        System.out.println("Chrome Sentinel fires at " + target.getId());
    }

    private Vector3f getLauncherWorldPosition() {
        // Calculate the world position of the launcher tip
        // 1. Get the entity's base model matrix (handles entity position and yaw)
        Matrix4f entityMatrix = getModelMatrix();

        // 2. The launcher model component has a local offset (LAUNCHER_OFFSET)
        //    and the tip of the launcher is further along its local Z axis.
        Vector3f launcherTipLocal = new Vector3f(LAUNCHER_OFFSET)
                .add(0, 0, LAUNCHER_DIMENSIONS.z / 2f); // Tip is at the end of the launcher

        // 3. Transform this local tip position by the entity's full model matrix
        Vector3f worldTipPosition = new Vector3f();
        entityMatrix.transformPosition(launcherTipLocal, worldTipPosition);

        return worldTipPosition;
    }


    @Override
    protected void applyGravity(float deltaTime) {
        // Floating fortress, no gravity
    }

    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand) {
        // Not applicable
    }

    @Override
    public void onEntityInteraction(Entity target, Hand hand) {
        // Potentially, if player touches it, deal damage or push back
    }
}