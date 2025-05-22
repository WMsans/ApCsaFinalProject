package World.Entities;

import Graphics.Camera;
import Graphics.Window;
import Physics.CustomAABB;
import World.*;
import World.Chunk.*;
import World.Entities.Entity;
import World.Entities.Hook; // Import Hook
import World.Entities.LivingEntity;
import World.Terrain.BaseTerrainGenerator;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.Random;
import java.util.List;
import java.util.ArrayList; // Added for findNearestSafeSpot
import Inventory.*;
import Configuration.Config;
import Input.Input;
import static org.lwjgl.glfw.GLFW.*;

public class PlayerEntity extends LivingEntity {
    private final Input input;
    private final Window window;
    private final Camera camera;
    private final Inventory inventory;
    private final Config config;

    private final float REACH_DISTANCE = 5.0f; // For block breaking, if re-enabled
    private final float mouseSensitivity = 0.1f;
    private final float playerEyeHeight = 0.9f;

    private double lastMouseX = -1, lastMouseY = -1;
    private boolean firstMouse = true;
    private final Random randomGenerator;

    private final Vector2f nearFarIntersection = new Vector2f();

    private float coyoteTimer = 0.0f;
    private float jumpBufferTimer = 0.0f;
    private boolean jumpKeyHeld = false;

    private boolean isFlying = false;
    private float lastSpacePressTime = -1.0f;
    private final float DOUBLE_SPACE_PRESS_INTERVAL = 0.3f;

    // Hook related fields
    private enum HookState {
        READY,      // Can shoot a new hook
        SHOT,       // Hook has been fired, traveling or waiting for attachment confirmation
        STABILIZED, // Hook is attached to a block
        RELEASING   // Hook is in the process of being released (for impulse, etc.)
    }
    private HookState currentHookState = HookState.READY;
    private Hook activeHook = null;
    private Vector3f hookTargetPoint = null; // Point on block where hook is aimed/attached
    private float currentHookStringLength = 0.0f;

    private final float HOOK_MAX_RANGE = 128.0f; // Max distance hook can be shot
    private final float GAS_FORCE_MAGNITUDE = 50.0f; // Force applied when RELEASING gas (continuous)
    private final float GAS_IMPULSE_ON_PRESS_MAGNITUDE = 12.0f; // Impulse when PRESSING space with hook (NEW)
    private final float RELEASE_IMPULSE_MAGNITUDE = 18.0f; // Impulse when RELEASING a stabilized hook
    private final float HOOK_TENSION_CORRECTION_FACTOR = 0.8f; // How strongly to correct position/velocity due to tension
    private final float SIMILAR_DIRECTION_THRESHOLD = 0.7f; // Cosine of angle for speed retention logic (e.g., > cos(45 deg))
    private static final int MAX_STUCK_RECOVERY_ATTEMPTS = 16; // Max attempts to find a safe spot
    private static final float STUCK_RECOVERY_SEARCH_RADIUS_INCREMENT = 0.5f; // How much to expand search radius each attempt


    public PlayerEntity(Input input, Window window, BaseTerrainGenerator worldTerrain, Vector3f initialPosition, Config config) {
        super(worldTerrain, initialPosition, new Vector3f(0.6f, 1.8f, 0.6f), 20.0f);
        this.input = input;
        this.window = window;
        this.inventory = new Inventory(36);
        this.randomGenerator = new Random();
        this.config = config;

        Vector3f cameraInitialPos = new Vector3f(initialPosition).add(0, getEyeHeight(), 0);
        this.camera = new Camera(cameraInitialPos, window);
        this.camera.setYaw(this.yaw);

        this.lastMouseX = input.getMouseX();
        this.lastMouseY = input.getMouseY();
        this.firstMouse = true;
    }

    public void update(float deltaTime, float currentTime) {
        if (config.isDebugFlyModeEnabled()) {
            handleFlyModeToggle(currentTime);
        }

        handleMouseLook();
        handleHookInput(deltaTime);

        if (!isFlying || !config.isDebugFlyModeEnabled()) {
            handleJumpBuffering(deltaTime);
            handleCoyoteTime(deltaTime);
            jumpKeyHeld = input.isKeyDown(GLFW_KEY_SPACE);
        } else {
            jumpKeyHeld = false;
        }

        handleKeyboardMovement(deltaTime); // This updates velocity based on input

        // Hook tension can also affect velocity and position
        if (currentHookState == HookState.STABILIZED && activeHook != null && activeHook.isAttached()) {
            handleHookTension(deltaTime);
        }

        float absoluteMaxSpeed = config.getFlySpeed(); // Cap speed before applying movement
        float currentSpeedSq = velocity.lengthSquared();

        if (currentSpeedSq > absoluteMaxSpeed * absoluteMaxSpeed) {
            float currentSpeed = (float) Math.sqrt(currentSpeedSq);
            velocity.mul(absoluteMaxSpeed / currentSpeed);
        }

        super.update(deltaTime, currentTime); // Entity.update -> applyGravity, moveEntity, updateLogic

        // Stuck check and recovery
        if (isStuck()) {
            System.err.println("Player is stuck! Attempting recovery...");
            Vector3f safeSpot = findNearestSafeSpot();
            if (safeSpot != null) {
                System.out.println("Found safe spot at: " + safeSpot + ". Teleporting.");
                teleport(safeSpot);
                velocity.zero(); // Reset velocity after teleporting from a stuck state
                isOnGround = false; // Re-evaluate ground state after teleport
                // Immediately check if the new spot is actually safe to avoid teleport loops.
                if (isStuck()) {
                    System.err.println("Teleported to a new spot but still stuck. Emergency fallback to high up.");
                    teleport(new Vector3f(position.x, position.y + Chunk.CHUNK_SIZE_Y * 2, position.z)); // Default to high up
                }
            } else {
                System.err.println("Could not find a safe spot to recover. Player remains stuck.");
                // As a last resort, could teleport player to a known "world origin" or last safe hub.
                teleport(new Vector3f(position.x, position.y + Chunk.CHUNK_SIZE_Y * 3, position.z)); // Default to high up
            }
        }


        Vector3f currentEyePosition = new Vector3f(this.position).add(0, getEyeHeight(), 0);
        this.camera.setPosition(currentEyePosition);
        this.camera.setYaw(this.yaw);
    }

    @Override
    protected void updateLogic(float deltaTime) {
        // Player-specific passive logic
    }

    private boolean isStuck() {
        CustomAABB playerBox = getBoundingBoxWorld();
        Vector3f entityDimensions = new Vector3f(
                localBoundingBox.max.x - localBoundingBox.min.x,
                localBoundingBox.max.y - localBoundingBox.min.y,
                localBoundingBox.max.z - localBoundingBox.min.z
        );
        List<Block> nearbyBlocks = worldTerrain.getBlocksForCollision(this.position, entityDimensions);

        for (Block block : nearbyBlocks) {
            CustomAABB blockBox = CustomAABB.forBlock(block.getPosition());
            if (playerBox.testAABB(blockBox)) {
                // Check if the intersection volume is significant (optional, simple check is often enough)
                return true; // Player is intersecting with a block
            }
        }
        return false; // No intersection found
    }


    private Vector3f findNearestSafeSpot() {
        Vector3f searchCenter = new Vector3f(this.position);
        Vector3f entityDimensions = new Vector3f(
                localBoundingBox.max.x - localBoundingBox.min.x,
                localBoundingBox.max.y - localBoundingBox.min.y,
                localBoundingBox.max.z - localBoundingBox.min.z
        );
        CustomAABB testBox = new CustomAABB(localBoundingBox.min, localBoundingBox.max);

        // Search pattern: spiral outwards and upwards
        for (int attempt = 0; attempt < MAX_STUCK_RECOVERY_ATTEMPTS; attempt++) {
            float currentSearchRadius = STUCK_RECOVERY_SEARCH_RADIUS_INCREMENT * (attempt +1);
            // Check cardinal directions, then diagonals, then move up/down
            // This is a simplified search; a more robust one might use a spiral or expanding shell.

            // Check points on an expanding cylinder, prioritizing positions above the current one.
            for (float yOffset = 0; yOffset <= currentSearchRadius * 2; yOffset += entityDimensions.y / 2.0f) { // Search upwards first
                for (float angle = 0; angle < 360; angle += 45) { // Check around the player
                    float rad = (float) Math.toRadians(angle);
                    float xOffset = (float) Math.cos(rad) * currentSearchRadius;
                    float zOffset = (float) Math.sin(rad) * currentSearchRadius;

                    Vector3f testPos = new Vector3f(searchCenter.x + xOffset, searchCenter.y + yOffset, searchCenter.z + zOffset);
                    testBox = localBoundingBox.translate(testPos);

                    boolean collision = false;
                    // Need to get blocks around testPos now
                    List<Block> candidateBlocks = worldTerrain.getBlocksForCollision(testPos, entityDimensions);
                    for (Block block : candidateBlocks) {
                        CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                        if (testBox.testAABB(blockAABB)) {
                            collision = true;
                            break;
                        }
                    }
                    if (!collision) {
                        // Check if space below is solid enough to stand on (optional, but good)
                        Vector3f posBelow = new Vector3f(testPos).sub(0, entityDimensions.y / 2f + 0.1f, 0); // Check slightly below feet
                        if(worldTerrain.isBlockAt(posBelow) || worldTerrain.isBlockAt(new Vector3f(testPos).sub(0,0.1f,0))) { // Check if ground is there.
                            return testPos;
                        }
                    }
                }
            }
            // If still no spot, try searching downwards a bit as a last resort before expanding radius too much
            if(attempt > MAX_STUCK_RECOVERY_ATTEMPTS / 2) {
                for (float yOffset = -entityDimensions.y / 2.0f; yOffset >= -currentSearchRadius; yOffset -= entityDimensions.y / 2.0f) {
                    for (float angle = 0; angle < 360; angle += 45) {
                        float rad = (float) Math.toRadians(angle);
                        float xOffset = (float) Math.cos(rad) * currentSearchRadius;
                        float zOffset = (float) Math.sin(rad) * currentSearchRadius;
                        Vector3f testPos = new Vector3f(searchCenter.x + xOffset, searchCenter.y + yOffset, searchCenter.z + zOffset);
                        testBox = localBoundingBox.translate(testPos);
                        boolean collision = false;
                        List<Block> candidateBlocks = worldTerrain.getBlocksForCollision(testPos, entityDimensions);
                        for (Block block : candidateBlocks) {
                            CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                            if (testBox.testAABB(blockAABB)) {
                                collision = true;
                                break;
                            }
                        }
                        if (!collision) return testPos; // Less stringent check for downwards, might be in air
                    }
                }
            }
        }
        return null; // No safe spot found within attempts
    }


    private void handleFlyModeToggle(float currentTime) {
        // Don't toggle fly if hooked and space is pressed (as space might be for gas impulse/release)
        if (input.isKeyPressed(GLFW_KEY_SPACE) && currentHookState != HookState.STABILIZED) {
            if (lastSpacePressTime > 0 && (currentTime - lastSpacePressTime) < DOUBLE_SPACE_PRESS_INTERVAL) {
                isFlying = !isFlying;
                if (isFlying) {
                    velocity.y = 0;
                    isOnGround = false;
                    System.out.println("Fly mode: ON");
                } else {
                    System.out.println("Fly mode: OFF");
                }
                lastSpacePressTime = -1.0f;
            } else {
                lastSpacePressTime = currentTime;
            }
        }
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
        float yOffset = (float) (lastMouseY - currentMouseY) * mouseSensitivity;
        lastMouseX = currentMouseX;
        lastMouseY = currentMouseY;

        this.yaw += xOffset;
        this.camera.rotatePitch(yOffset);
    }

    private void handleJumpBuffering(float deltaTime) {
        if (jumpBufferTimer > 0) {
            jumpBufferTimer -= deltaTime;
        }
        if (input.isKeyPressed(GLFW_KEY_SPACE) &&
                !(!isFlying && currentHookState == HookState.STABILIZED && !isOnGround)) {
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
            handleWalkingAndGasMovement(deltaTime);
        }
    }

    private void handleFlyingMovement(float deltaTime) {
        velocity.zero();
        Vector3f flyDirection = new Vector3f(0,0,0);
        Vector3f camForward = camera.getForwardDirection(true);
        Vector3f camRight = camera.getRightDirection(true);

        if (input.isKeyDown(GLFW_KEY_W)) flyDirection.add(camForward);
        if (input.isKeyDown(GLFW_KEY_S)) flyDirection.sub(camForward);
        if (input.isKeyDown(GLFW_KEY_A)) flyDirection.sub(camRight);
        if (input.isKeyDown(GLFW_KEY_D)) flyDirection.add(camRight);
        if (input.isKeyDown(GLFW_KEY_SPACE)) flyDirection.y += 1.0f;
        if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) flyDirection.y -= 1.0f;

        if (flyDirection.lengthSquared() > 0) {
            flyDirection.normalize();
            velocity.set(flyDirection.mul(config.getFlySpeed()));
        }
        isOnGround = false;
    }

    private void handleWalkingAndGasMovement(float deltaTime) {
        Vector3f inputDir = new Vector3f(0,0,0);
        Vector3f forwardXZ = new Vector3f((float)Math.cos(Math.toRadians(this.yaw)), 0, (float)Math.sin(Math.toRadians(this.yaw))).normalize();
        Vector3f rightXZ = new Vector3f((float)Math.cos(Math.toRadians(this.yaw + 90)), 0, (float)Math.sin(Math.toRadians(this.yaw + 90))).normalize();

        if (input.isKeyDown(GLFW_KEY_W)) inputDir.add(forwardXZ);
        if (input.isKeyDown(GLFW_KEY_S)) inputDir.sub(forwardXZ);
        if (input.isKeyDown(GLFW_KEY_A)) inputDir.sub(rightXZ);
        if (input.isKeyDown(GLFW_KEY_D)) inputDir.add(rightXZ);

        Vector3f targetVelocityXZ;
        if (inputDir.lengthSquared() > 0) {
            inputDir.normalize();
            Vector2f currentHorizontalVel2D = new Vector2f(velocity.x, velocity.z);
            float currentHorizontalSpeed = currentHorizontalVel2D.length();
            final float maxWalkSpeed = config.getMaxSpeed();

            if (currentHorizontalSpeed > maxWalkSpeed) {
                Vector2f currentDirNorm = new Vector2f(currentHorizontalVel2D).normalize();
                Vector2f inputDir2D = new Vector2f(inputDir.x, inputDir.z);

                if (currentDirNorm.dot(inputDir2D) >= SIMILAR_DIRECTION_THRESHOLD) {
                    targetVelocityXZ = new Vector3f(inputDir.x * currentHorizontalSpeed, 0, inputDir.z * currentHorizontalSpeed);
                } else {
                    targetVelocityXZ = new Vector3f(inputDir).mul(maxWalkSpeed);
                }
            } else {
                targetVelocityXZ = new Vector3f(inputDir).mul(maxWalkSpeed);
            }
        } else {
            targetVelocityXZ = new Vector3f(0,0,0);
        }

        float accel = config.getAcceleration();
        float decel = isOnGround ? config.getGroundDeceleration() : config.getAirDeceleration();

        if (Math.abs(targetVelocityXZ.x - velocity.x) > 0.01f) {
            float diffX = targetVelocityXZ.x - velocity.x;
            float changeX = Math.signum(diffX) * accel * deltaTime;
            if (Math.abs(changeX) > Math.abs(diffX)) changeX = diffX;
            velocity.x += changeX;
        } else if (Math.abs(targetVelocityXZ.x) < 0.01f && Math.abs(velocity.x) > 0.01f) {
            float changeX = -Math.signum(velocity.x) * decel * deltaTime;
            if (Math.abs(changeX) > Math.abs(velocity.x)) changeX = -velocity.x;
            velocity.x += changeX;
        }

        if (Math.abs(targetVelocityXZ.z - velocity.z) > 0.01f) {
            float diffZ = targetVelocityXZ.z - velocity.z;
            float changeZ = Math.signum(diffZ) * accel * deltaTime;
            if (Math.abs(changeZ) > Math.abs(diffZ)) changeZ = diffZ;
            velocity.z += changeZ;
        } else if (Math.abs(targetVelocityXZ.z) < 0.01f && Math.abs(velocity.z) > 0.01f) {
            float changeZ = -Math.signum(velocity.z) * decel * deltaTime;
            if (Math.abs(changeZ) > Math.abs(velocity.z)) changeZ = -velocity.z;
            velocity.z += changeZ;
        }

        if(input.isKeyPressed(GLFW_KEY_SPACE) && currentHookState == HookState.STABILIZED && !isOnGround && !isFlying){
            System.out.println("Player applying gas impulse on space press.");
            Vector3f camForward = camera.getForwardDirection(true);
            addVelocity(camForward.mul(GAS_IMPULSE_ON_PRESS_MAGNITUDE).add(0, GAS_IMPULSE_ON_PRESS_MAGNITUDE, 0));

            isOnGround = false;
            coyoteTimer = 0;
            jumpBufferTimer = 0;
        }

        if (input.isKeyDown(GLFW_KEY_SPACE) && !isOnGround && currentHookState == HookState.STABILIZED && !isFlying) {
            Vector3f gasForceDirection = camera.getForwardDirection(true);
            addVelocity(gasForceDirection.mul(GAS_FORCE_MAGNITUDE * deltaTime).add(0, GAS_FORCE_MAGNITUDE * deltaTime, 0));
        }

        if (jumpBufferTimer > 0 && !(input.isKeyDown(GLFW_KEY_SPACE) && currentHookState == HookState.STABILIZED && !isOnGround && !isFlying)) {
            if (isOnGround || coyoteTimer > 0) {
                velocity.y = config.getJumpUpSpeed();
                isOnGround = false;
                coyoteTimer = 0;
                jumpBufferTimer = 0;
            }
        }
    }


    @Override
    protected void applyGravity(float deltaTime) {
        if (isFlying && config.isDebugFlyModeEnabled()) {
            return;
        }

        float currentGravity = config.getFallAcceleration();
        if (velocity.y > 0 && !jumpKeyHeld &&
                !(currentHookState == HookState.STABILIZED && input.isKeyDown(GLFW_KEY_SPACE) && !isOnGround)) {
            currentGravity *= config.getJumpEndEarlyGravityModifier();
        }

        if (!isOnGround) {
            velocity.y -= currentGravity * deltaTime;
            if (velocity.y < -config.getMaxFallSpeed()) {
                velocity.y = -config.getMaxFallSpeed();
            }
        } else if (velocity.y < 0) {
            velocity.y = 0;
        }
    }

    private void handleHookInput(float deltaTime) {
        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT) && currentHookState == HookState.READY) {
            Vector3f rayOrigin = camera.getPosition();
            Vector3f rayDirection = camera.getForwardDirection(true);
            Block targetedBlock = null;
            Vector3f intersectionPoint = new Vector3f();
            float closestDistance = HOOK_MAX_RANGE + 0.1f;

            ChunkId playerChunkId = Chunk.getChunkIdAtWorldPosition(this.position);
            int raycastChunkRadius = (int)Math.ceil(HOOK_MAX_RANGE / Math.min(Chunk.CHUNK_SIZE_X, Math.min(Chunk.CHUNK_SIZE_Y, Chunk.CHUNK_SIZE_Z)));
            List<Block> blocksToRaycast = worldTerrain.getBlocksInRadius(playerChunkId, raycastChunkRadius);

            for (Block block : blocksToRaycast) {
                if (block == null) continue;
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                nearFarIntersection.set(0,Float.POSITIVE_INFINITY);
                if (blockAABB.intersectRay(rayOrigin, rayDirection, nearFarIntersection) && nearFarIntersection.x < closestDistance) {
                    if (nearFarIntersection.x >= 0 && nearFarIntersection.x <= HOOK_MAX_RANGE) {
                        closestDistance = nearFarIntersection.x;
                        targetedBlock = block;
                    }
                }
            }

            if (targetedBlock != null) {
                hookTargetPoint = new Vector3f(rayOrigin).add(new Vector3f(rayDirection).mul(closestDistance));
                activeHook = new Hook(this, worldTerrain, hookTargetPoint);
                worldTerrain.addEntity(activeHook);

                currentHookStringLength = this.position.distance(hookTargetPoint);
                activeHook.attach(targetedBlock, hookTargetPoint, currentHookStringLength);
                currentHookState = HookState.STABILIZED;
            }
        }

        if (!input.isMouseButtonDown(GLFW_MOUSE_BUTTON_RIGHT) && (currentHookState == HookState.STABILIZED || currentHookState == HookState.SHOT)) {
            if (activeHook != null) {
                boolean wasStabilized = activeHook.isAttached();
                activeHook.detach();

                if (wasStabilized) {
                    Vector3f impulseDirection = new Vector3f(velocity).normalize();
                    if (impulseDirection.lengthSquared() == 0 && camera != null) {
                        impulseDirection = camera.getForwardDirection(true);
                    }
                    if (impulseDirection.lengthSquared() > 0) {
                        addVelocity(impulseDirection.mul(RELEASE_IMPULSE_MAGNITUDE).add(0, RELEASE_IMPULSE_MAGNITUDE, 0));
                    }
                }
            } else {
                currentHookState = HookState.READY;
            }
        }
    }

    public void onHookReleased() {
        this.currentHookState = HookState.READY;
        this.activeHook = null;
        this.hookTargetPoint = null;
        this.currentHookStringLength = 0;
    }

    private void handleHookTension(float deltaTime) {
        if (activeHook == null || !activeHook.isAttached() || hookTargetPoint == null) return;

        Vector3f playerPos = getPosition();
        Vector3f toHook = new Vector3f(hookTargetPoint).sub(playerPos);
        float distanceToHookTarget = toHook.length();

        if (distanceToHookTarget < currentHookStringLength) {
            currentHookStringLength = distanceToHookTarget;
            activeHook.setCurrentStringLength(currentHookStringLength);
        }

        if (distanceToHookTarget > currentHookStringLength + 0.01f) {
            Vector3f pullDirection = toHook.normalize();
            Vector3f correctedPosition = new Vector3f(hookTargetPoint).sub(new Vector3f(pullDirection).mul(currentHookStringLength));

            // Before applying corrected position, check if it would cause player to be stuck
            CustomAABB futurePlayerBox = localBoundingBox.translate(correctedPosition);
            boolean wouldBeStuck = false;
            Vector3f entityDimensions = new Vector3f(
                    localBoundingBox.max.x - localBoundingBox.min.x,
                    localBoundingBox.max.y - localBoundingBox.min.y,
                    localBoundingBox.max.z - localBoundingBox.min.z
            );
            List<Block> collisionCandidateBlocks = worldTerrain.getBlocksForCollision(correctedPosition, entityDimensions);

            for(Block block : collisionCandidateBlocks) {
                CustomAABB blockBox = CustomAABB.forBlock(block.getPosition());
                if (futurePlayerBox.testAABB(blockBox)) {
                    wouldBeStuck = true;
                    break;
                }
            }

            if (wouldBeStuck) {
                // Player would be pulled into a block. Detach hook or stop pulling.
                System.err.println("Hook tension would pull player into block. Detaching hook.");
                activeHook.detach(); // This will set currentHookState to READY via onHookReleased
                return; // Stop further tension logic for this frame
            }

            // If not stuck, apply correction
            this.position.set(correctedPosition);

            float radialVelocityMagnitude = velocity.dot(pullDirection);
            if (radialVelocityMagnitude > 0) {
                Vector3f radialVelocity = new Vector3f(pullDirection).mul(radialVelocityMagnitude);
                velocity.sub(radialVelocity.mul(HOOK_TENSION_CORRECTION_FACTOR));
            }
            isOnGround = false;
        }

        if (currentHookStringLength < 0.5f && distanceToHookTarget < 0.6f) {
            if (activeHook != null) activeHook.detach();
        }
    }


    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand) {
    }

    @Override
    public void onEntityInteraction(Entity target, Hand hand) {
        if (target instanceof LivingEntity && hand == Hand.MAIN_HAND) {
            ItemStack mainHandItem = inventory.getEquipped(EquipmentSlot.MAIN_HAND);
            float damage = 1.0f;
            this.attackLivingEntity((LivingEntity)target, damage);
        }
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
        inventory.setEquipped(slot, stack);
    }

    @Override
    public float getEyeHeight() {
        return playerEyeHeight;
    }

    public Camera getCamera() {
        return camera;
    }

    public Inventory getInventory() { return inventory; }

}