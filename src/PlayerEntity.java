import Physics.CustomAABB;
import World.*;
import World.Chunk.*;
import World.Entities.Entity;
import World.Entities.LivingEntity;
import World.Terrain.BaseTerrainGenerator;
import World.Terrain.NetherTerrain;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.Random;
import java.util.List;
import Inventory.*;
import Configuration.Config;
import Input.Input;
import static org.lwjgl.glfw.GLFW.*; // For key codes

public class PlayerEntity extends LivingEntity {
    private final Input input;
    private final Window window;
    private final Camera camera;
    private final Inventory inventory;
    private final Config config;

    private final float REACH_DISTANCE = 5.0f;
    private final float mouseSensitivity = 0.1f;
    private final float playerEyeHeight = 0.9f; // Relative to player's base (position.y)

    private double lastMouseX = -1, lastMouseY = -1;
    private boolean firstMouse = true;
    private final Random randomGenerator;

    private final Vector2f nearFarIntersection = new Vector2f(); // For block raycasting

    // Jump mechanics
    private float coyoteTimer = 0.0f;
    private float jumpBufferTimer = 0.0f;
    private boolean jumpKeyHeld = false;

    // Fly mode state
    private boolean isFlying = false;
    private float lastSpacePressTime = -1.0f; // Time of the last spacebar press for double-tap detection
    private final float DOUBLE_SPACE_PRESS_INTERVAL = 0.3f; // Max interval for double-tap in seconds


    public PlayerEntity(Input input, Window window, BaseTerrainGenerator worldTerrain, Vector3f initialPosition, Config config) {
        super(worldTerrain, initialPosition, new Vector3f(0.6f, 1.8f, 0.6f), 20.0f); // Dimensions, health
        this.input = input;
        this.window = window;
        this.inventory = new Inventory(36); // Standard inventory size
        this.randomGenerator = new Random();
        this.config = config; // Store config directly if not relying on superclass field

        Vector3f cameraInitialPos = new Vector3f(initialPosition).add(0, getEyeHeight(), 0);
        this.camera = new Camera(cameraInitialPos, window);
        this.camera.setYaw(this.yaw); // Sync initial camera yaw with entity yaw

        // Initialize mouse position for look controls
        this.lastMouseX = input.getMouseX();
        this.lastMouseY = input.getMouseY();
        this.firstMouse = true;
    }

    /**
     * Main update method for the player.
     * @param deltaTime Time since the last frame.
     * @param currentTime Current game time, used for input timing like double-taps.
     */
    public void update(float deltaTime, float currentTime) {
        if (config.isDebugFlyModeEnabled()) {
            handleFlyModeToggle(currentTime);
        }

        handleMouseLook();

        if (!isFlying || !config.isDebugFlyModeEnabled()) { // Normal jump/fall mechanics if not flying
            handleJumpBuffering(deltaTime);
            handleCoyoteTime(deltaTime);
            jumpKeyHeld = input.isKeyDown(GLFW_KEY_SPACE); // Update jumpKeyHeld only if not flying
        } else {
            jumpKeyHeld = false; // Ensure jumpKeyHeld is false if flying
        }

        handleKeyboardMovement(deltaTime); // Handles both walking and flying
        handleBlockInteractionInput();

        super.update(deltaTime); // Calls Entity.update -> applyGravity, moveEntity, updateLogic

        // Update camera position to player's eye level and sync yaw
        Vector3f currentEyePosition = new Vector3f(this.position).add(0, getEyeHeight(), 0);
        this.camera.setPosition(currentEyePosition);
        this.camera.setYaw(this.yaw); // Sync camera yaw with entity's body yaw
    }

    @Override
    protected void updateLogic(float deltaTime) {
        // Player-specific passive logic (e.g., regeneration) can go here
    }

    private void handleFlyModeToggle(float currentTime) {
        if (input.isKeyPressed(GLFW_KEY_SPACE)) {
            if (lastSpacePressTime > 0 && (currentTime - lastSpacePressTime) < DOUBLE_SPACE_PRESS_INTERVAL) {
                isFlying = !isFlying;
                if (isFlying) {
                    velocity.y = 0; // Neutralize vertical velocity when starting to fly
                    isOnGround = false; // Player is not on ground when flying
                    System.out.println("Fly mode: ON");
                } else {
                    System.out.println("Fly mode: OFF");
                }
                lastSpacePressTime = -1.0f; // Reset to prevent immediate re-toggle on next single press
            } else {
                lastSpacePressTime = currentTime; // Record time of the first press
            }
        }

        // Reset lastSpacePressTime if a double tap doesn't occur quickly enough
        if (lastSpacePressTime > 0 && (currentTime - lastSpacePressTime) >= DOUBLE_SPACE_PRESS_INTERVAL) {
            lastSpacePressTime = -1.0f;
        }
    }

    private void handleMouseLook() {
        double currentMouseX = input.getMouseX();
        double currentMouseY = input.getMouseY();
        if (firstMouse) {
            lastMouseX = currentMouseX;
            lastMouseY = currentMouseY;
            firstMouse = false;
            return;
        }
        float xOffset = (float) (currentMouseX - lastMouseX) * mouseSensitivity;
        float yOffset = (float) (lastMouseY - currentMouseY) * mouseSensitivity; // Inverted Y
        lastMouseX = currentMouseX;
        lastMouseY = currentMouseY;

        this.yaw += xOffset; // Rotate player body
        this.camera.rotatePitch(yOffset); // Rotate camera view up/down
    }

    private void handleJumpBuffering(float deltaTime) {
        if (jumpBufferTimer > 0) {
            jumpBufferTimer -= deltaTime;
        }
        if (input.isKeyPressed(GLFW_KEY_SPACE)) { // A single press for jump
            jumpBufferTimer = config.getJumpBufferTime();
        }
    }

    private void handleCoyoteTime(float deltaTime) {
        if (!isOnGround) {
            if (coyoteTimer > 0) {
                coyoteTimer -= deltaTime;
            }
        } else {
            coyoteTimer = config.getCoyoteTime();
        }
    }

    private void handleKeyboardMovement(float deltaTime) {
        if (isFlying && config.isDebugFlyModeEnabled()) {
            handleFlyingMovement(deltaTime);
        } else {
            handleWalkingMovement(deltaTime);
        }
    }

    private void handleFlyingMovement(float deltaTime) {
        velocity.zero(); // Reset velocity for direct control each frame

        Vector3f flyDirection = new Vector3f(0,0,0);
        // Use camera's full 3D forward and robust right vectors for intuitive flight
        Vector3f camForward = camera.getForwardDirection(false);
        Vector3f camRight = camera.getRightDirection(false);

        if (input.isKeyDown(GLFW_KEY_W)) flyDirection.add(camForward);
        if (input.isKeyDown(GLFW_KEY_S)) flyDirection.sub(camForward);
        if (input.isKeyDown(GLFW_KEY_A)) flyDirection.sub(camRight);
        if (input.isKeyDown(GLFW_KEY_D)) flyDirection.add(camRight);

        // Vertical movement for flying
        if (input.isKeyDown(GLFW_KEY_SPACE)) flyDirection.y += 1.0f;
        if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) flyDirection.y -= 1.0f;

        if (flyDirection.lengthSquared() > 0) {
            flyDirection.normalize();
            velocity.set(flyDirection.mul(config.getFlySpeed())); // Set velocity directly based on fly speed
        }
        isOnGround = false; // Explicitly not on ground when flying
    }

    private void handleWalkingMovement(float deltaTime) {
        Vector3f inputDir = new Vector3f(0,0,0);
        // Horizontal forward vector based on player's yaw (body orientation)
        Vector3f forwardXZ = new Vector3f((float)Math.cos(Math.toRadians(this.yaw)), 0, (float)Math.sin(Math.toRadians(this.yaw))).normalize();
        Vector3f rightXZ = new Vector3f((float)Math.cos(Math.toRadians(this.yaw + 90)), 0, (float)Math.sin(Math.toRadians(this.yaw + 90))).normalize();

        if (input.isKeyDown(GLFW_KEY_W)) inputDir.add(forwardXZ);
        if (input.isKeyDown(GLFW_KEY_S)) inputDir.sub(forwardXZ);
        if (input.isKeyDown(GLFW_KEY_A)) inputDir.sub(rightXZ); // Strafe left
        if (input.isKeyDown(GLFW_KEY_D)) inputDir.add(rightXZ); // Strafe right

        if (inputDir.lengthSquared() > 0) {
            inputDir.normalize();
        }

        // Apply acceleration/deceleration for walking/running
        Vector3f targetVelocityXZ = new Vector3f(inputDir).mul(config.getMaxSpeed());
        float accel = config.getAcceleration();
        float decel = isOnGround ? config.getGroundDeceleration() : config.getAirDeceleration();

        // X-axis velocity change
        if (Math.abs(targetVelocityXZ.x - velocity.x) > 0.01f) {
            float diffX = targetVelocityXZ.x - velocity.x;
            float changeX = Math.signum(diffX) * accel * deltaTime;
            if (Math.abs(changeX) > Math.abs(diffX)) changeX = diffX;
            velocity.x += changeX;
        } else if (Math.abs(targetVelocityXZ.x) < 0.01f && Math.abs(velocity.x) > 0.01f) { // Decelerate if no input
            float changeX = -Math.signum(velocity.x) * decel * deltaTime;
            if (Math.abs(changeX) > Math.abs(velocity.x)) changeX = -velocity.x;
            velocity.x += changeX;
        }

        // Z-axis velocity change
        if (Math.abs(targetVelocityXZ.z - velocity.z) > 0.01f) {
            float diffZ = targetVelocityXZ.z - velocity.z;
            float changeZ = Math.signum(diffZ) * accel * deltaTime;
            if (Math.abs(changeZ) > Math.abs(diffZ)) changeZ = diffZ;
            velocity.z += changeZ;
        } else if (Math.abs(targetVelocityXZ.z) < 0.01f && Math.abs(velocity.z) > 0.01f) { // Decelerate if no input
            float changeZ = -Math.signum(velocity.z) * decel * deltaTime;
            if (Math.abs(changeZ) > Math.abs(velocity.z)) changeZ = -velocity.z;
            velocity.z += changeZ;
        }

        // Clamp horizontal speed to maxSpeed if actively moving
        Vector2f currentHorizontalVelocity = new Vector2f(velocity.x, velocity.z);
        if (currentHorizontalVelocity.lengthSquared() > config.getMaxSpeed() * config.getMaxSpeed()) {
            if (inputDir.lengthSquared() > 0) { // Only clamp if there's movement input
                currentHorizontalVelocity.normalize().mul(config.getMaxSpeed());
                velocity.x = currentHorizontalVelocity.x;
                velocity.z = currentHorizontalVelocity.y;
            }
        }

        // Jump logic
        if (jumpBufferTimer > 0) {
            if (isOnGround || coyoteTimer > 0) { // Can jump if on ground or within coyote time
                velocity.y = config.getJumpUpSpeed();
                isOnGround = false; // No longer on ground after jumping
                coyoteTimer = 0;    // Consume coyote time
                jumpBufferTimer = 0;// Consume jump buffer
            }
        }
    }

    @Override
    protected void applyGravity(float deltaTime) {
        if (isFlying && config.isDebugFlyModeEnabled()) {
            // When flying, gravity is disabled. Velocity.y is controlled by handleFlyingMovement.
            // If no up/down input in handleFlyingMovement, velocity.y will be 0 due to velocity.zero().
            return;
        }

        // Standard gravity logic for walking/falling
        float currentGravity = config.getFallAcceleration();
        // Modify gravity if jump is ended early (releasing space)
        if (velocity.y > 0 && !jumpKeyHeld) { // jumpKeyHeld is false if flying
            currentGravity *= config.getJumpEndEarlyGravityModifier();
        }

        if (!isOnGround) { // Apply gravity if not on ground (and not flying)
            velocity.y -= currentGravity * deltaTime;
            if (velocity.y < -config.getMaxFallSpeed()) { // Clamp to terminal velocity
                velocity.y = -config.getMaxFallSpeed();
            }
        } else if (velocity.y < 0) { // If on ground and somehow moving down, stop vertical movement
            velocity.y = 0;
        }
    }

    private void handleBlockInteractionInput() {
        Vector3f rayOrigin = camera.getPosition();
        Vector3f rayDirection = camera.getForwardDirection(true); // Use camera's line of sight
        Block targetedBlock = null;
        Vector3f intersectionPoint = new Vector3f();
        float closestDistance = REACH_DISTANCE + 0.1f; // Start a bit beyond reach

        ChunkId playerChunkId = Chunk.getChunkIdAtWorldPosition(this.position);
        int raycastChunkRadius = 1; // Search current chunk and immediate neighbors
        List<Block> blocksToRaycast = worldTerrain.getBlocksInRadius(playerChunkId, raycastChunkRadius);

        for (Block block : blocksToRaycast) {
            CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
            nearFarIntersection.set(0,0); // Reset intersection distances
            if (blockAABB.intersectRay(rayOrigin, rayDirection, nearFarIntersection) && nearFarIntersection.x < closestDistance) {
                if (nearFarIntersection.x >= 0 && nearFarIntersection.x <= REACH_DISTANCE) { // Check if within reach
                    closestDistance = nearFarIntersection.x;
                    targetedBlock = block;
                }
            }
        }

        if (targetedBlock != null) {
            // Calculate exact intersection point on the block face
            intersectionPoint.set(rayOrigin).add(new Vector3f(rayDirection).mul(closestDistance));
        }

        // Left-click: Break block
        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT) && targetedBlock != null) {
            onBlockInteraction(targetedBlock, intersectionPoint, Hand.MAIN_HAND);
        }

        // Right-click: Place block
        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT) && targetedBlock != null) {
            Vector3f blockCenter = targetedBlock.getPosition();
            Vector3f hitRelativeToCenter = new Vector3f(intersectionPoint).sub(blockCenter);
            Vector3f placementNormal = new Vector3f(); // Normal of the face hit
            float maxComponent = 0.0001f; // Epsilon for floating point comparison

            // Determine which face was hit based on the intersection point relative to block center
            if (Math.abs(hitRelativeToCenter.x) > Math.abs(hitRelativeToCenter.y) && Math.abs(hitRelativeToCenter.x) > Math.abs(hitRelativeToCenter.z)) {
                placementNormal.set(Math.signum(hitRelativeToCenter.x), 0, 0);
            } else if (Math.abs(hitRelativeToCenter.y) > Math.abs(hitRelativeToCenter.z)) {
                placementNormal.set(0, Math.signum(hitRelativeToCenter.y), 0);
            } else {
                placementNormal.set(0, 0, Math.signum(hitRelativeToCenter.z));
            }

            // Ensure a valid normal was determined (should always be if targetedBlock is not null)
            if (placementNormal.lengthSquared() > 0.5f) {
                Vector3f newBlockPosition = new Vector3f(targetedBlock.getPosition()).add(placementNormal);
                CustomAABB playerWorldBB = this.getBoundingBoxWorld(); // Player's current bounding box
                CustomAABB newBlockBB = CustomAABB.forBlock(newBlockPosition); // AABB of the potential new block

                // Check if new block position is not inside player and not already occupied
                if (!playerWorldBB.testAABB(newBlockBB) && !worldTerrain.isBlockAt(newBlockPosition)) {
                    Vector3f randomColor = new Vector3f(randomGenerator.nextFloat() * 0.8f + 0.2f,
                            randomGenerator.nextFloat() * 0.8f + 0.2f,
                            randomGenerator.nextFloat() * 0.8f + 0.2f); // Brighter random colors
                    worldTerrain.addBlock(new Block(newBlockPosition, randomColor));
                }
            }
        }
    }

    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand) {
        if (block != null && hand == Hand.MAIN_HAND) { // Assuming main hand breaks blocks
            worldTerrain.removeBlock(block);
        }
    }

    @Override
    public void onEntityInteraction(Entity target, Hand hand) {
        if (target instanceof LivingEntity && hand == Hand.MAIN_HAND) {
            ItemStack mainHandItem = inventory.getEquipped(EquipmentSlot.MAIN_HAND);
            float damage = 1.0f; // TODO: Base damage, could be modified by item
            // if (mainHandItem != null && !mainHandItem.isEmpty()) { /* Modify damage based on item */ }
            this.attackLivingEntity((LivingEntity)target, damage);
        }
    }

    public void interact(Entity entity, Hand hand) {
        if (entity != null && entity.isValid()) {
            onEntityInteraction(entity, hand);
        }
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
        inventory.setEquipped(slot, stack);
    }

    @Override
    public float getEyeHeight() {
        // If crouching is implemented, this could change. For now, constant.
        return playerEyeHeight;
    }

    public Camera getCamera() {
        return camera;
    }

    public Inventory getInventory() {
        return inventory;
    }
}
