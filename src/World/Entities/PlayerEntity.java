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

    private final float HOOK_MAX_RANGE = 30.0f; // Max distance hook can be shot
    private final float GAS_FORCE_MAGNITUDE = 25.0f; // Force applied when releasing gas
    private final float RELEASE_IMPULSE_MAGNITUDE = 15.0f; // Impulse when releasing a stabilized hook
    private final float HOOK_TENSION_CORRECTION_FACTOR = 0.8f; // How strongly to correct position/velocity due to tension

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
        handleHookInput(deltaTime); // New: Handle hook-related inputs

        if (!isFlying || !config.isDebugFlyModeEnabled()) {
            handleJumpBuffering(deltaTime);
            handleCoyoteTime(deltaTime);
            jumpKeyHeld = input.isKeyDown(GLFW_KEY_SPACE);
        } else {
            jumpKeyHeld = false;
        }

        handleKeyboardMovement(deltaTime); // Handles walking, flying, and now gas release
        // handleBlockInteractionInput(); // Original block breaking/placing logic - now replaced by hook

        super.update(deltaTime); // Entity.update -> applyGravity, moveEntity, updateLogic

        if (currentHookState == HookState.STABILIZED && activeHook != null && activeHook.isAttached()) {
            handleHookTension(deltaTime);
        }


        Vector3f currentEyePosition = new Vector3f(this.position).add(0, getEyeHeight(), 0);
        this.camera.setPosition(currentEyePosition);
        this.camera.setYaw(this.yaw);
    }

    @Override
    protected void updateLogic(float deltaTime) {
        // Player-specific passive logic
    }

    private void handleFlyModeToggle(float currentTime) {
        if (input.isKeyPressed(GLFW_KEY_SPACE) && currentHookState != HookState.STABILIZED) { // Don't toggle fly if hooked
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
        // Only buffer jump if space is pressed and not trying to release gas with a hook
        if (input.isKeyPressed(GLFW_KEY_SPACE) && !(currentHookState == HookState.STABILIZED && !isOnGround)) {
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
            handleWalkingAndGasMovement(deltaTime); // Modified to include gas
        }
    }

    private void handleFlyingMovement(float deltaTime) {
        velocity.zero();
        Vector3f flyDirection = new Vector3f(0,0,0);
        Vector3f camForward = camera.getForwardDirection(true);
        Vector3f camRight = camera.getRightDirection(true); // Use true 3D right for flying

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

        if (inputDir.lengthSquared() > 0) {
            inputDir.normalize();
        }

        Vector3f targetVelocityXZ = new Vector3f(inputDir).mul(config.getMaxSpeed());
        float accel = config.getAcceleration();
        float decel = isOnGround ? config.getGroundDeceleration() : config.getAirDeceleration();

        // X-axis velocity change
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

        // Z-axis velocity change
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

        Vector2f currentHorizontalVelocity = new Vector2f(velocity.x, velocity.z);
        if (currentHorizontalVelocity.lengthSquared() > config.getMaxSpeed() * config.getMaxSpeed()) {
            if (inputDir.lengthSquared() > 0) {
                currentHorizontalVelocity.normalize().mul(config.getMaxSpeed());
                velocity.x = currentHorizontalVelocity.x;
                velocity.z = currentHorizontalVelocity.y;
            }
        }

        // Gas Release Logic (only if not flying)
        if (input.isKeyDown(GLFW_KEY_SPACE) && !isOnGround && currentHookState == HookState.STABILIZED) {
            System.out.println("Player trying to release gas.");
            Vector3f gasForceDirection = camera.getForwardDirection(true); // Gas propels in camera's facing direction
            addVelocity(gasForceDirection.mul(GAS_FORCE_MAGNITUDE * deltaTime)); // Apply as acceleration over time
            // Tension will be handled by handleHookTension
        } else if (input.isKeyDown(GLFW_KEY_SPACE) && !isOnGround && currentHookState != HookState.STABILIZED) {
            //System.out.println("Gas release attempted but hook not stabilized or player on ground.");
            // Potentially add a short burst effect here even if not hooked, or a sound. For now, it does nothing.
        }


        // Jump logic (not when releasing gas with hook)
        if (jumpBufferTimer > 0 && !(input.isKeyDown(GLFW_KEY_SPACE) && currentHookState == HookState.STABILIZED)) {
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
            return; // No gravity when flying
        }
        // No gravity if hook is stabilized and player is not on ground (tension and gas handle vertical movement)
        if (currentHookState == HookState.STABILIZED && !isOnGround) {
            // If player is being pulled upwards or maintained by tension, gravity might be counteracted.
            // For a strong pull effect, we can reduce or negate gravity here.
            // For now, let's allow some gravity unless player is actively using gas upwards or tension is strong.
            // This part might need more tuning based on desired feel.
            // Let's assume tension and gas are primary vertical controllers when hooked.
            // However, a base level of gravity should still apply unless specific conditions are met.
        }

        float currentGravity = config.getFallAcceleration();
        if (velocity.y > 0 && !jumpKeyHeld && !(currentHookState == HookState.STABILIZED && input.isKeyDown(GLFW_KEY_SPACE))) {
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
        // Shoot Hook (Right Mouse Button Down)
        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT) && currentHookState == HookState.READY) {
            Vector3f rayOrigin = camera.getPosition();
            Vector3f rayDirection = camera.getForwardDirection(true);
            Block targetedBlock = null;
            Vector3f intersectionPoint = new Vector3f();
            float closestDistance = HOOK_MAX_RANGE + 0.1f;

            ChunkId playerChunkId = Chunk.getChunkIdAtWorldPosition(this.position);
            // Raycast a bit further for hooks than for block breaking
            int raycastChunkRadius = (int)Math.ceil(HOOK_MAX_RANGE / Math.min(Chunk.CHUNK_SIZE_X, Math.min(Chunk.CHUNK_SIZE_Y, Chunk.CHUNK_SIZE_Z)));
            List<Block> blocksToRaycast = worldTerrain.getBlocksInRadius(playerChunkId, raycastChunkRadius);

            for (Block block : blocksToRaycast) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                nearFarIntersection.set(0,0);
                if (blockAABB.intersectRay(rayOrigin, rayDirection, nearFarIntersection) && nearFarIntersection.x < closestDistance) {
                    if (nearFarIntersection.x >= 0 && nearFarIntersection.x <= HOOK_MAX_RANGE) {
                        closestDistance = nearFarIntersection.x;
                        targetedBlock = block;
                    }
                }
            }

            if (targetedBlock != null) {
                hookTargetPoint = new Vector3f(rayOrigin).add(new Vector3f(rayDirection).mul(closestDistance));
                activeHook = new Hook(this, worldTerrain, hookTargetPoint); // Hook starts at target point (instant for now)
                worldTerrain.addEntity(activeHook); // Add hook to world (if your terrain/world manager handles entities)

                currentHookStringLength = this.position.distance(hookTargetPoint);
                activeHook.attach(targetedBlock, hookTargetPoint, currentHookStringLength);
                currentHookState = HookState.STABILIZED;
                // System.out.println("Hook SHOT and STABILIZED at " + hookTargetPoint + " on block " + targetedBlock.getPosition() + ". Initial string length: " + currentHookStringLength);
            } else {
                System.out.println("Hook shot FAILED - no target block in range.");
            }
        }

        // Release Hook (Right Mouse Button Up)
        if (!input.isMouseButtonDown(GLFW_MOUSE_BUTTON_RIGHT) && (currentHookState == HookState.STABILIZED || currentHookState == HookState.SHOT)) {
            if (activeHook != null) {
                boolean wasStabilized = activeHook.isAttached();
                activeHook.detach(); // This will set its state, mark as invalid, and call onHookReleased()
                activeHook = null;
                hookTargetPoint = null;

                if (wasStabilized) {
                    System.out.println("Hook RELEASED from stabilized state. Applying impulse.");
                    Vector3f impulseDirection = new Vector3f(velocity).normalize();
                    if (impulseDirection.lengthSquared() == 0 && camera != null) { // If standing still, impulse in look direction
                        impulseDirection = camera.getForwardDirection(true);
                    }
                    if (impulseDirection.lengthSquared() > 0) {
                        addVelocity(impulseDirection.mul(RELEASE_IMPULSE_MAGNITUDE));
                    }
                } else {
                    System.out.println("Hook shot CANCELED before stabilization.");
                }
                // State is set to READY in onHookReleased
            } else {
                currentHookState = HookState.READY; // Ensure state reset if no active hook for some reason
            }
        }
    }

    // Called by Hook when it's detached
    public void onHookReleased() {
        this.currentHookState = HookState.READY;
        this.currentHookStringLength = 0;
        this.hookTargetPoint = null;
        System.out.println("Player notified: Hook ready.");
    }

    private void handleHookTension(float deltaTime) {
        if (activeHook == null || !activeHook.isAttached() || hookTargetPoint == null) return;

        Vector3f playerPos = getPosition();
        Vector3f toHook = new Vector3f(hookTargetPoint).sub(playerPos);
        float distanceToHookTarget = toHook.length();

        // Update string length if player is closer
        if (distanceToHookTarget < currentHookStringLength) {
            currentHookStringLength = distanceToHookTarget;
            activeHook.setCurrentStringLength(currentHookStringLength);
            // System.out.println("String length shortened to: " + currentHookStringLength);
        }

        // Apply tension if player is trying to exceed string length
        if (distanceToHookTarget > currentHookStringLength + 0.01f) { // Add small tolerance
            // System.out.println("TENSION: Player distance " + distanceToHookTarget + " > string length " + currentHookStringLength);
            Vector3f pullDirection = toHook.normalize();

            // Correct position to be on the sphere defined by the hook string
            Vector3f correctedPosition = new Vector3f(hookTargetPoint).sub(new Vector3f(pullDirection).mul(currentHookStringLength));
            this.position.set(correctedPosition); // Directly set position to maintain string length constraint

            // Adjust velocity: Dampen velocity component moving away from the hook
            // Project current velocity onto the pullDirection (radial component)
            float radialVelocityMagnitude = velocity.dot(pullDirection);
            if (radialVelocityMagnitude > 0) { // If moving away from the hook point
                Vector3f radialVelocity = new Vector3f(pullDirection).mul(radialVelocityMagnitude);
                // Subtract the radial velocity component that's moving away
                velocity.sub(radialVelocity.mul(HOOK_TENSION_CORRECTION_FACTOR)); // Apply a factor to control how "rubbery" the string is
            }

            // If player is swinging, a simple position correction + velocity dampening might feel too rigid.
            // A more physical approach would involve applying a spring-like force.
            // For now, this aims to keep the player within the string's radius.
            // System.out.println("Applied tension. New velocity: " + velocity);
            isOnGround = false; // Tension usually means player is airborne or being pulled
        }
        if (currentHookStringLength < 0.5f && distanceToHookTarget < 0.6f) { // Player is very close to hook point
            System.out.println("Player reached hook point. Releasing hook automatically.");
            if (activeHook != null) activeHook.detach(); // Auto-release
        }
    }


    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand) {
        // Re-enable if RMB is not used for hook, or use another button for breaking
        // if (block != null && hand == Hand.MAIN_HAND) {
        //     worldTerrain.removeBlock(block);
        // }
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