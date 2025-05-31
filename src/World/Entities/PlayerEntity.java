package World.Entities;

import Graphics.Camera;
import Graphics.Window;
import Physics.CustomAABB;
import World.*;
import World.Chunk.*;
import World.Entities.Entity;
import World.Entities.Hook;
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

    private float lastReleaseGameTime = -1.0f;

    private float currentCameraRoll = 0.0f;
    private final float maxCameraRoll = 30.0f;
    private final float cameraRollSpeed = 15.0f;

    private float particleSpawnCooldown = 0.0f;
    private final float PARTICLE_SPAWN_INTERVAL = 0.025f;
    private ParticleSpawner particleSpawner;

    private float slashCooldownTimer = 0.0f;
    private static final float SLASH_COOLDOWN = 0.3f; // seconds
    private static final float SLASH_DASH_SPEED_ENEMY = 100.0f; // Speed when dashing to an enemy
    private static final float SLASH_DASH_IMPULSE_FORWARD = 15.0f; // Impulse strength when dashing forward
    private static final float TARGETED_DASH_ENEMY_DETECTION_RANGE = 70.0f; // How close an enemy needs to be for a targeted dash
    // Updated SLASH_DIMENSIONS for a large cube surrounding the player
    private static final Vector3f SLASH_DIMENSIONS = new Vector3f(6.0f, 6.0f, 6.0f);

    // Hook related fields
    private enum HookState {
        READY,      // Can shoot a new hook
        SHOT,       // Hook has been fired, traveling or waiting for attachment confirmation
        STABILIZED, // Hook is attached to a block or entity
        RELEASING   // Hook is in the process of being released (for impulse, etc.)
    }
    private HookState currentHookState = HookState.READY;
    private Hook activeHook = null;
    private Vector3f hookTargetPoint = null;
    private float currentHookStringLength = 0.0f;

    private final float HOOK_MAX_RANGE = 100.0f; // Max distance hook can be shot
    private final float GAS_FORCE_MAGNITUDE = 60.0f; // Force applied when RELEASING gas (continuous)
    private final float GAS_IMPULSE_ON_PRESS_MAGNITUDE = 20.0f; // Impulse when PRESSING space with hook
    private final float RELEASE_IMPULSE_MAGNITUDE = 18.0f; // Impulse when RELEASING a stabilized hook
    private final float HOOK_TENSION_CORRECTION_FACTOR = 0.8f; // How strongly to correct position/velocity due to tension
    private final float SIMILAR_DIRECTION_THRESHOLD = 0.7f; // Cosine of angle for speed retention logic (e.g., > cos(45 deg))
    private static final int MAX_STUCK_RECOVERY_ATTEMPTS = 16; // Max attempts to find a safe spot
    private static final float STUCK_RECOVERY_SEARCH_RADIUS_INCREMENT = 0.5f; // How much to expand search radius each attempt
    private static final float RELEASE_GAS_TIME = 0.5f; // Time between applying gas impulse

    private static final float SLASH_LIFESPAN = 0.3f; // seconds

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

        this.particleSpawner = new ParticleSpawner(this.worldTerrain, this.camera);
    }

    public void update(float deltaTime, float currentTime) {
        if (config.isDebugFlyModeEnabled()) {
            handleFlyModeToggle(currentTime);
        }

        this.skipCollisionProcessing = isFlying && config.isDebugFlyModeEnabled();

        handleMouseLook();
        handleHookInput(deltaTime);
        handleAttackInput(deltaTime);

        if (!isFlying || !config.isDebugFlyModeEnabled()) {
            handleJumpBuffering(deltaTime);
            handleCoyoteTime(deltaTime);
            jumpKeyHeld = input.isKeyDown(GLFW_KEY_SPACE);
        } else {
            jumpKeyHeld = false;
            isOnGround = false;
        }

        handleKeyboardMovement(deltaTime,currentTime);

        if (currentHookState == HookState.STABILIZED && activeHook != null && activeHook.isAttached()) {
            handleHookTension(deltaTime);
        }

        float absoluteMaxSpeed = config.getFlySpeed();
        float currentSpeedSq = velocity.lengthSquared();

        if (currentSpeedSq > absoluteMaxSpeed * absoluteMaxSpeed) {
            float currentSpeed = (float) Math.sqrt(currentSpeedSq);
            velocity.mul(absoluteMaxSpeed / currentSpeed);
        }

        super.update(deltaTime, currentTime);

        if (config.isEnablePlayerAirRoll() && !isOnGround && velocity.lengthSquared() > 100f) {
            Vector3f playerVelocityXZ = new Vector3f(velocity.x, 0, velocity.z);
            if (playerVelocityXZ.lengthSquared() > 0.01f) {
                playerVelocityXZ.normalize();
                Vector3f cameraRightXZ = camera.getRightDirection(false).normalize();
                float relativeMovementDot = playerVelocityXZ.dot(cameraRightXZ);
                float targetRoll = -relativeMovementDot * maxCameraRoll;
                if (currentCameraRoll < targetRoll) {
                    currentCameraRoll += cameraRollSpeed * deltaTime;
                    if (currentCameraRoll > targetRoll) currentCameraRoll = targetRoll;
                } else if (currentCameraRoll > targetRoll) {
                    currentCameraRoll -= cameraRollSpeed * deltaTime;
                    if (currentCameraRoll < targetRoll) currentCameraRoll = targetRoll;
                }
            }
        } else {
            if (currentCameraRoll > 0.01f) {
                currentCameraRoll -= cameraRollSpeed * deltaTime;
                if (currentCameraRoll < 0) currentCameraRoll = 0;
            } else if (currentCameraRoll < -0.01f) {
                currentCameraRoll += cameraRollSpeed * deltaTime;
                if (currentCameraRoll > 0) currentCameraRoll = 0;
            } else {
                currentCameraRoll = 0;
            }
        }
        this.camera.setRoll(currentCameraRoll);

        Vector3f currentEyePosition = new Vector3f(this.position).add(0, getEyeHeight(), 0);
        this.camera.setPosition(currentEyePosition);
        this.camera.setYaw(this.yaw);
    }

    private void handleAttackInput(float deltaTime) {
        if (slashCooldownTimer > 0) {
            slashCooldownTimer -= deltaTime;
        }

        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT) && slashCooldownTimer <= 0) {
            slashCooldownTimer = SLASH_COOLDOWN;

            LivingEntity targetEnemy = findClosestEnemyForTargetedDash();

            if (targetEnemy != null) {
                // Dash towards the enemy
                Vector3f directionToEnemy = new Vector3f(targetEnemy.getPosition()).sub(this.position).normalize();
                if (directionToEnemy.lengthSquared() > 0.001f) { // Ensure direction is valid
                    this.velocity.set(directionToEnemy.mul(SLASH_DASH_SPEED_ENEMY));
                } else { // Fallback if already at the same position (should be rare)
                    this.velocity.set(camera.getForwardDirection(true).mul(SLASH_DASH_SPEED_ENEMY));
                }
            } else {
                // Dash forward (impulse)
                Vector3f forwardDir = camera.getForwardDirection(true);
                this.addVelocity(new Vector3f(forwardDir).mul(SLASH_DASH_IMPULSE_FORWARD));
                this.isOnGround = false;
            }

            // Spawn Slash Entity, centered on the player
            Slash slash = new Slash(
                    this.worldTerrain,
                    this.camera,
                    this,
                    this.getPosition(), // Initial position at player's current location
                    SLASH_DIMENSIONS,    // Large hitbox dimensions
                    SLASH_LIFESPAN,      // Defined in PlayerEntity, assuming it exists (0.3f in example)
                    this.particleSpawner
            );
            worldTerrain.addEntity(slash);
        }
    }

    // Add this new helper method to PlayerEntity.java:
    private LivingEntity findClosestEnemyForTargetedDash() {
        LivingEntity closestEnemy = null;
        float minDistanceSq = TARGETED_DASH_ENEMY_DETECTION_RANGE * TARGETED_DASH_ENEMY_DETECTION_RANGE;

        List<Entity> entities = worldTerrain.getEntities();
        for (Entity entity : entities) {
            // Ensure we are checking against valid, living enemies, not the player, and not other utility entities.
            if (entity instanceof Enemy && entity.isValid() && entity != this) {
                Vector3f directionToEntity = new Vector3f(entity.getPosition()).sub(this.position);
                float distSq = directionToEntity.lengthSquared();

                if (distSq < minDistanceSq) {
                    Vector3f entityDirectionNormalized = new Vector3f(directionToEntity).normalize();
                    Vector3f forwardDir = camera.getForwardDirection(true);
                    // Check if the enemy is roughly in front of the player (e.g., within a ~120 degree cone)
                    if (entityDirectionNormalized.dot(forwardDir) > 0.5) {
                        minDistanceSq = distSq;
                        closestEnemy = (LivingEntity) entity;
                    }
                }
            }
        }
        return closestEnemy;
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
                return true;
            }
        }
        return false;
    }


    private Vector3f findNearestSafeSpot() {
        Vector3f searchCenter = new Vector3f(this.position);
        Vector3f entityDimensions = new Vector3f(
                localBoundingBox.max.x - localBoundingBox.min.x,
                localBoundingBox.max.y - localBoundingBox.min.y,
                localBoundingBox.max.z - localBoundingBox.min.z
        );
        CustomAABB testBox = new CustomAABB(localBoundingBox.min, localBoundingBox.max);

        for (int attempt = 0; attempt < MAX_STUCK_RECOVERY_ATTEMPTS; attempt++) {
            float currentSearchRadius = STUCK_RECOVERY_SEARCH_RADIUS_INCREMENT * (attempt +1);
            for (float yOffset = 0; yOffset <= currentSearchRadius * 2; yOffset += entityDimensions.y / 2.0f) {
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
                    if (!collision) {
                        Vector3f posBelow = new Vector3f(testPos).sub(0, entityDimensions.y / 2f + 0.1f, 0);
                        if(worldTerrain.isBlockAt(posBelow) || worldTerrain.isBlockAt(new Vector3f(testPos).sub(0,0.1f,0))) {
                            return testPos;
                        }
                    }
                }
            }
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
                        if (!collision) return testPos;
                    }
                }
            }
        }
        return null;
    }


    private void handleFlyModeToggle(float currentTime) {
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

    private void handleKeyboardMovement(float deltaTime, float currentTime) {
        if (isFlying && config.isDebugFlyModeEnabled()) {
            handleFlyingMovement(deltaTime);
        } else {
            handleWalkingAndGasMovement(deltaTime, currentTime);
        }
    }

    private void handleFlyingMovement(float deltaTime) {
        velocity.zero();
        Vector3f flyDirection = new Vector3f(0,0,0);
        Vector3f camForward = camera.getForwardDirection(true); // Use true for flying
        Vector3f camRight = camera.getRightDirection(true);   // Use true for flying
        Vector3f worldUp = new Vector3f(0, 1, 0);

        if (input.isKeyDown(GLFW_KEY_W)) flyDirection.add(camForward);
        if (input.isKeyDown(GLFW_KEY_S)) flyDirection.sub(camForward);
        if (input.isKeyDown(GLFW_KEY_A)) flyDirection.sub(camRight);
        if (input.isKeyDown(GLFW_KEY_D)) flyDirection.add(camRight);
        if (input.isKeyDown(GLFW_KEY_SPACE)) flyDirection.add(worldUp);
        if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) flyDirection.sub(worldUp);

        if (flyDirection.lengthSquared() > 0) {
            flyDirection.normalize();
            velocity.set(flyDirection.mul(config.getFlySpeed()));
        }
        isOnGround = false;
    }

    private void handleWalkingAndGasMovement(float deltaTime, float currentTime) {
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

        if(input.isKeyPressed(GLFW_KEY_SPACE) && currentHookState == HookState.STABILIZED && !isOnGround && !isFlying && currentTime - lastReleaseGameTime > RELEASE_GAS_TIME){
            Vector3f camForward = camera.getForwardDirection(true);
            addVelocity(camForward.mul(GAS_IMPULSE_ON_PRESS_MAGNITUDE).add(activeHook.getPosition().sub(getPosition()).normalize().mul(GAS_IMPULSE_ON_PRESS_MAGNITUDE)));

            isOnGround = false;
            coyoteTimer = 0;
            jumpBufferTimer = 0;
            lastReleaseGameTime = currentTime;
        }

        if (input.isKeyDown(GLFW_KEY_SPACE) && !isOnGround && currentHookState == HookState.STABILIZED && !isFlying) {
            Vector3f gasForceDirection = camera.getForwardDirection(true);
            addVelocity(gasForceDirection.mul(GAS_FORCE_MAGNITUDE * deltaTime).add(0, GAS_FORCE_MAGNITUDE * deltaTime * 0.8f, 0));

            particleSpawnCooldown -= deltaTime;
            if (particleSpawnCooldown <= 0) {
                Vector3f particleOrigin = new Vector3f(this.position);
                // Optional: Slightly offset the particle origin if needed
                // particleOrigin.add(new Vector3f(camera.getForwardDirection(true)).mul(-0.3f)); // Example offset

                int particleCount = 3 + randomGenerator.nextInt(2); // 3 or 4 particles
                float burstSpeed = 0.2f + randomGenerator.nextFloat() * 0.3f; // Small outward speed (e.g., 0.2 to 0.5)

                particleSpawner.spawnBurst(
                        particleOrigin,
                        particleCount,
                        burstSpeed,
                        3.0f,                // Lifespan of 3 seconds
                        0.0f,                // No gravity effect for these gas particles
                        new Vector3f(1,1,1), // White color
                        0.1f,               // Size of the particle sprite
                        new Vector3f(0,0,0)  // No additional base velocity for the burst
                );
                particleSpawnCooldown = PARTICLE_SPAWN_INTERVAL;
            }
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
            if (velocity.y < -DEFAULT_TERMINAL_VELOCITY) {
                velocity.y = -DEFAULT_TERMINAL_VELOCITY;
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
            LivingEntity targetedEntity = null;
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
                        intersectionPoint.set(rayOrigin).add(new Vector3f(rayDirection).mul(closestDistance));
                    }
                }
            }

            List<Entity> allEntities = worldTerrain.getEntities();
            for (Entity entity : allEntities) {
                if (entity == this || entity == activeHook || !(entity instanceof LivingEntity) || !entity.isValid()) {
                    continue;
                }
                if (entity instanceof Bullet) { // Do not allow hooking bullets
                    continue;
                }

                LivingEntity livingTarget = (LivingEntity) entity;
                CustomAABB entityAABB = livingTarget.getBoundingBoxWorld();
                Vector2f entityIntersection = new Vector2f(); // Use a new Vector2f for entity intersection

                if (entityAABB.intersectRay(rayOrigin, rayDirection, entityIntersection)) {
                    if (entityIntersection.x >= 0 && entityIntersection.x < closestDistance && entityIntersection.x <= HOOK_MAX_RANGE) {
                        Vector3f hitWorldPoint = new Vector3f(rayOrigin).add(new Vector3f(rayDirection).mul(entityIntersection.x));
                        boolean shieldHit = false;
                        if (livingTarget.hasCustomBlockingGeometry()) {
                            if (livingTarget.checkCustomBlockingGeometry(hitWorldPoint, this.getPosition())) {
                                shieldHit = true;
                                // Optional: Spawn spark particles for hook hitting shield
                                if (particleSpawner != null) {
                                    particleSpawner.spawnBurst(
                                            hitWorldPoint,
                                            10, // count
                                            2.0f, // burstSpeed
                                            0.5f, // lifespan
                                            0.1f, // gravityScale
                                            new Vector3f(0.8f, 0.8f, 0.8f), // spark color
                                            0.1f, // size
                                            null // baseVelocity
                                    );
                                }
                            }
                        }

                        if (!shieldHit) {
                            closestDistance = entityIntersection.x;
                            targetedEntity = livingTarget;
                            targetedBlock = null; // Entity takes precedence
                            intersectionPoint.set(hitWorldPoint); // Update the main intersection point
                        }
// If shieldHit is true, we don't update closestDistance or targetedEntity,
// effectively ignoring the shielded hit for attachment.
                    }
                }
            }

            if (targetedEntity != null) {
                hookTargetPoint = new Vector3f(intersectionPoint);
                activeHook = new Hook(this, worldTerrain, hookTargetPoint);
                worldTerrain.addEntity(activeHook);
                currentHookStringLength = this.position.distance(hookTargetPoint);
                activeHook.attachToEntity(targetedEntity, hookTargetPoint, currentHookStringLength);
                currentHookState = HookState.STABILIZED;
                // System.out.println("Hook attached to entity: " + targetedEntity.getId());
            } else if (targetedBlock != null) {
                hookTargetPoint = new Vector3f(intersectionPoint);
                activeHook = new Hook(this, worldTerrain, hookTargetPoint);
                worldTerrain.addEntity(activeHook);
                currentHookStringLength = this.position.distance(hookTargetPoint);
                activeHook.attach(targetedBlock, hookTargetPoint, currentHookStringLength);
                currentHookState = HookState.STABILIZED;
                // System.out.println("Hook attached to block at: " + hookTargetPoint);
            }
        }

        if (!input.isMouseButtonDown(GLFW_MOUSE_BUTTON_RIGHT) && (currentHookState == HookState.STABILIZED || currentHookState == HookState.SHOT)) {
            if (activeHook != null) {
                boolean wasStabilized = activeHook.isAttached();
                activeHook.detach(); // This will call onHookReleased and set state to READY

                if (wasStabilized && !isFlying) { // Only apply impulse if not in fly mode
                    Vector3f impulseDirection = new Vector3f(velocity).normalize();
                    if (impulseDirection.lengthSquared() == 0 && camera != null) {
                        impulseDirection = camera.getForwardDirection(true);
                    }
                    if (impulseDirection.lengthSquared() > 0) {
                        addVelocity(impulseDirection.mul(RELEASE_IMPULSE_MAGNITUDE).add(0, RELEASE_IMPULSE_MAGNITUDE * 0.5f, 0)); // Reduced upward component
                    }
                }
            } else { // Should not happen if activeHook.detach() works correctly
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
        if (activeHook == null || !activeHook.isAttached()) return; // No active or attached hook

        hookTargetPoint = activeHook.getAttachedPoint(); // Get current (potentially dynamic) attach point
        if (hookTargetPoint == null) { // Entity might have become invalid
            activeHook.detach();
            return;
        }

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
                activeHook.detach();
                return;
            }

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

    public Hook getActiveHook() {
        if (currentHookState == HookState.STABILIZED || currentHookState == HookState.SHOT) {
            return activeHook;
        }
        return null;
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
    protected void populateModelComponents() {

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