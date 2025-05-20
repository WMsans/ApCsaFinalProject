import Physics.CustomAABB;
import World.*; // Wildcard import for Chunk, ChunkId, Block, Terrain
import World.Chunk.*;
import World.Entities.Entity;
import World.Entities.LivingEntity;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.Random;
import java.util.List; // For list of blocks
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

    private final float REACH_DISTANCE = 5.0f;
    private final float mouseSensitivity = 0.1f;
    private final float playerEyeHeight = 0.9f;

    private double lastMouseX = -1, lastMouseY = -1;
    private boolean firstMouse = true;
    private final Random randomGenerator;

    private final Vector2f nearFarIntersection = new Vector2f();

    private float coyoteTimer = 0.0f;
    private float jumpBufferTimer = 0.0f;
    private boolean jumpKeyHeld = false;


    public PlayerEntity(Input input, Window window, Terrain worldTerrain, Vector3f initialPosition, Config config) {
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

    @Override
    public void update(float deltaTime) {
        handleMouseLook();
        handleJumpBuffering(deltaTime);
        handleCoyoteTime(deltaTime);
        handleKeyboardMovement(deltaTime);
        handleBlockInteractionInput(); // Raycasting for block interaction

        // Entity.update() handles applyGravity and moveEntity
        super.update(deltaTime); // This will call PlayerEntity's applyGravity if overridden, then Entity's moveEntity

        Vector3f currentEyePosition = new Vector3f(this.position).add(0, getEyeHeight(), 0);
        this.camera.setPosition(currentEyePosition);
        this.camera.setYaw(this.yaw);

        jumpKeyHeld = input.isKeyDown(GLFW_KEY_SPACE);
    }

    @Override
    protected void updateLogic(float deltaTime) {
        // Player-specific logic
    }

    private void handleMouseLook() {
        double currentMouseX = input.getMouseX();
        double currentMouseY = input.getMouseY();
        if (firstMouse) {
            lastMouseX = currentMouseX;
            lastMouseY = currentMouseY;
            firstMouse = false;
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
        if (input.isKeyPressed(GLFW_KEY_SPACE)) {
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

        Vector2f currentHorizontalVelocity = new Vector2f(velocity.x, velocity.z);
        if (currentHorizontalVelocity.lengthSquared() > config.getMaxSpeed() * config.getMaxSpeed()) {
            if (inputDir.lengthSquared() > 0) {
                currentHorizontalVelocity.normalize().mul(config.getMaxSpeed());
                velocity.x = currentHorizontalVelocity.x;
                velocity.z = currentHorizontalVelocity.y;
            }
        }

        if (jumpBufferTimer > 0) {
            if (isOnGround || coyoteTimer > 0) {
                velocity.y = config.getJumpUpSpeed();
                isOnGround = false;
                coyoteTimer = 0;
                jumpBufferTimer = 0;
            }
        }
    }

    @Override
    protected void applyGravity(float deltaTime) { // Overridden from LivingEntity/Entity
        float currentGravity = config.getFallAcceleration();
        if (velocity.y > 0 && !jumpKeyHeld) {
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

    private void handleBlockInteractionInput() {
        Vector3f rayOrigin = camera.getPosition();
        Vector3f rayDirection = camera.getForwardDirection(true);
        Block targetedBlock = null;
        Vector3f intersectionPoint = new Vector3f();
        float closestDistance = REACH_DISTANCE + 0.1f;

        // Determine player's chunk and search radius for raycasting
        ChunkId playerChunkId = Chunk.getChunkIdAtWorldPosition(this.position);
        // Search a small radius of chunks, e.g., current and immediate neighbors (radius 1)
        // For REACH_DISTANCE = 5, a radius of 1 chunk (16 units) might not be enough if player is at edge.
        // Max reach is 5. Chunk size is 16. If player is at 0,0,0 in chunk 0,0,0, they can reach into chunk 0,0,1 if block is at z=15.5 + 5 = 20.5 (which is in chunk 1)
        // So, a radius of ceil(REACH_DISTANCE / CHUNK_SIZE) + 1 might be safer, or simply check a fixed small radius like 1 or 2.
        // For simplicity, let's use a fixed small radius for raycasting.
        int raycastChunkRadius = 1; // Check current chunk and immediate neighbors
        List<Block> blocksToRaycast = worldTerrain.getBlocksInRadius(playerChunkId, raycastChunkRadius);


        for (Block block : blocksToRaycast) { // Iterate over blocks from nearby chunks
            CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
            nearFarIntersection.set(0,0);
            if (blockAABB.intersectRay(rayOrigin, rayDirection, nearFarIntersection) && nearFarIntersection.x < closestDistance) {
                if (nearFarIntersection.x >= 0 && nearFarIntersection.x <= REACH_DISTANCE) {
                    closestDistance = nearFarIntersection.x;
                    targetedBlock = block;
                }
            }
        }

        if (targetedBlock != null) {
            intersectionPoint.set(rayOrigin).add(new Vector3f(rayDirection).mul(closestDistance));
        }

        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT) && targetedBlock != null) {
            onBlockInteraction(targetedBlock, intersectionPoint, Hand.MAIN_HAND);
        }

        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT) && targetedBlock != null) {
            Vector3f blockCenter = targetedBlock.getPosition(); // Use actual targeted block's center
            Vector3f hitRelativeToCenter = new Vector3f(intersectionPoint).sub(blockCenter);
            Vector3f placementNormal = new Vector3f();
            float maxComponent = 0.0001f;

            if (Math.abs(hitRelativeToCenter.x) > maxComponent && Math.abs(hitRelativeToCenter.x) > Math.abs(hitRelativeToCenter.y) && Math.abs(hitRelativeToCenter.x) > Math.abs(hitRelativeToCenter.z)) {
                placementNormal.set(Math.signum(hitRelativeToCenter.x), 0, 0);
            } else if (Math.abs(hitRelativeToCenter.y) > maxComponent && Math.abs(hitRelativeToCenter.y) > Math.abs(hitRelativeToCenter.z)) {
                placementNormal.set(0, Math.signum(hitRelativeToCenter.y), 0);
            } else if (Math.abs(hitRelativeToCenter.z) > maxComponent) {
                placementNormal.set(0, 0, Math.signum(hitRelativeToCenter.z));
            }

            if (placementNormal.lengthSquared() > 0.5f) {
                Vector3f newBlockPosition = new Vector3f(targetedBlock.getPosition()).add(placementNormal);
                CustomAABB playerWorldBB = this.getBoundingBoxWorld();
                CustomAABB newBlockBB = CustomAABB.forBlock(newBlockPosition);

                if (!playerWorldBB.testAABB(newBlockBB) && !worldTerrain.isBlockAt(newBlockPosition)) {
                    Vector3f randomColor = new Vector3f(randomGenerator.nextFloat(), randomGenerator.nextFloat(), randomGenerator.nextFloat());
                    worldTerrain.addBlock(new Block(newBlockPosition, randomColor)); // Terrain will put it in correct chunk
                }
            }
        }
    }

    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand) {
        if (block != null && hand == Hand.MAIN_HAND) {
            worldTerrain.removeBlock(block); // Terrain handles removing from chunk
        }
    }

    @Override
    public void onEntityInteraction(Entity target, Hand hand) {
        if (target instanceof LivingEntity && hand == Hand.MAIN_HAND) {
            ItemStack mainHandItem = inventory.getEquipped(EquipmentSlot.MAIN_HAND);
            float damage = 1.0f;
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
        return playerEyeHeight;
    }

    public Camera getCamera() {
        return camera;
    }

    public Inventory getInventory() {
        return inventory;
    }
}
