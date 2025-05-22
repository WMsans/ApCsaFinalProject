package World.Entities;

import Inventory.Hand;
import Physics.CustomAABB;
import World.Block;
import World.Terrain.BaseTerrainGenerator;
import World.Terrain.NetherTerrain; // Will use Terrain's new methods
import org.joml.Vector3f;
import org.joml.Vector2f;
import java.util.UUID;
import java.util.List; // For list of blocks

public abstract class Entity {
    protected final UUID id;
    protected Vector3f position;
    protected Vector3f velocity;
    protected float yaw;
    protected float pitch;

    protected boolean isValid;
    protected boolean isOnGround;
    protected BaseTerrainGenerator worldTerrain; // This is our world/chunk manager

    protected CustomAABB localBoundingBox; // AABB relative to entity's origin (position)

    protected static final float DEFAULT_GRAVITY_ACCELERATION = 19.62f;
    protected static final float DEFAULT_TERMINAL_VELOCITY = 50.0f;
    protected static final float COLLISION_SKIN_WIDTH = 0.005f;

    public Entity(BaseTerrainGenerator worldTerrain, Vector3f initialPosition, Vector3f dimensions) {
        this.id = UUID.randomUUID();
        this.worldTerrain = worldTerrain;
        this.position = new Vector3f(initialPosition);
        this.velocity = new Vector3f(0, 0, 0);
        this.isValid = true;
        this.isOnGround = false;
        this.yaw = 0;
        this.pitch = 0;
        // localBoundingBox is defined with min/max relative to (0,0,0) assuming entity position is the center.
        // If position is bottom-center, adjust this. For now, assume position is center.
        this.localBoundingBox = new CustomAABB(
                -dimensions.x / 2, -dimensions.y / 2, -dimensions.z / 2,
                dimensions.x / 2,  dimensions.y / 2,  dimensions.z / 2
        );
    }

    public void update(float deltaTime) {
        if (!isValid) return;

        applyGravity(deltaTime);
        moveEntity(deltaTime);
        updateLogic(deltaTime);
    }

    protected void applyGravity(float deltaTime) {
        if (!isOnGround) {
            velocity.y -= DEFAULT_GRAVITY_ACCELERATION * deltaTime;
            if (velocity.y < -DEFAULT_TERMINAL_VELOCITY) {
                velocity.y = -DEFAULT_TERMINAL_VELOCITY;
            }
        } else if (velocity.y < 0) {
            velocity.y = 0;
        }
    }

    protected void moveEntity(float deltaTime) {
        if (deltaTime == 0) return;

        Vector3f potentialMovement = new Vector3f(velocity).mul(deltaTime);

        // Get relevant blocks for collision from the current and neighboring chunks
        // The entity's dimensions are needed to determine the query area for chunks.
        Vector3f entityDimensions = new Vector3f(
                localBoundingBox.max.x - localBoundingBox.min.x,
                localBoundingBox.max.y - localBoundingBox.min.y,
                localBoundingBox.max.z - localBoundingBox.min.z
        );
        List<Block> collisionCandidateBlocks = worldTerrain.getBlocksForCollision(this.position, entityDimensions);

        // --- Y-axis movement and collision ---
        if (potentialMovement.y != 0) {
            float targetY = position.y + potentialMovement.y;
            // The localBoundingBox is relative to the entity's position.
            // So, for the test bounds, we translate it to the targetY.
            CustomAABB testYBounds = localBoundingBox.translate(new Vector3f(position.x, targetY, position.z));
            boolean yCollisionThisFrame = false;
            float resolvedPosY = targetY;

            for (Block block : collisionCandidateBlocks) { // Use filtered list
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (testYBounds.testAABB(blockAABB)) {
                    yCollisionThisFrame = true;
                    if (potentialMovement.y < 0) { // Moving down
                        resolvedPosY = blockAABB.max.y - localBoundingBox.min.y + COLLISION_SKIN_WIDTH;
                        velocity.y = 0;
                        isOnGround = true;
                    } else { // Moving up
                        resolvedPosY = blockAABB.min.y - localBoundingBox.max.y - COLLISION_SKIN_WIDTH;
                        velocity.y = 0;
                    }
                    break;
                }
            }
            position.y = resolvedPosY;
            if (!yCollisionThisFrame && potentialMovement.y < 0) {
                isOnGround = false;
            }
        }

        // --- X-axis movement and collision ---
        if (potentialMovement.x != 0) {
            float targetX = position.x + potentialMovement.x;
            CustomAABB testXBounds = localBoundingBox.translate(new Vector3f(targetX, position.y, position.z)); // Use updated Y
            float resolvedPosX = targetX;

            for (Block block : collisionCandidateBlocks) { // Use filtered list
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (testXBounds.testAABB(blockAABB)) {
                    if (potentialMovement.x < 0) {
                        resolvedPosX = blockAABB.max.x - localBoundingBox.min.x + COLLISION_SKIN_WIDTH;
                    } else {
                        resolvedPosX = blockAABB.min.x - localBoundingBox.max.x - COLLISION_SKIN_WIDTH;
                    }
                    velocity.x = 0;
                    break;
                }
            }
            position.x = resolvedPosX;
        }

        // --- Z-axis movement and collision ---
        if (potentialMovement.z != 0) {
            float targetZ = position.z + potentialMovement.z;
            CustomAABB testZBounds = localBoundingBox.translate(new Vector3f(position.x, position.y, targetZ)); // Use updated X and Y
            float resolvedPosZ = targetZ;

            for (Block block : collisionCandidateBlocks) { // Use filtered list
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (testZBounds.testAABB(blockAABB)) {
                    if (potentialMovement.z < 0) {
                        resolvedPosZ = blockAABB.max.z - localBoundingBox.min.z + COLLISION_SKIN_WIDTH;
                    } else {
                        resolvedPosZ = blockAABB.min.z - localBoundingBox.max.z - COLLISION_SKIN_WIDTH;
                    }
                    velocity.z = 0;
                    break;
                }
            }
            position.z = resolvedPosZ;
        }

        if (velocity.y <= 0.01f) {
            checkIfOnGround(collisionCandidateBlocks); // Pass relevant blocks to ground check
        } else {
            isOnGround = false;
        }
    }

    protected void checkIfOnGround(List<Block> collisionCandidateBlocks) { // Accept candidate blocks
        CustomAABB worldBB = getBoundingBoxWorld();
        float checkRayLength = COLLISION_SKIN_WIDTH * 3.0f;
        Vector3f rayDir = new Vector3f(0, -1, 0);
        boolean groundDetectedThisCheck = false;

        float insetFactor = 0.9f;
        float halfWidth = (localBoundingBox.max.x - localBoundingBox.min.x) / 2.0f * insetFactor;
        float halfDepth = (localBoundingBox.max.z - localBoundingBox.min.z) / 2.0f * insetFactor;
        float rayOriginY = worldBB.min.y + COLLISION_SKIN_WIDTH * 0.5f;

        Vector3f[] rayOrigins = {
                new Vector3f(position.x, rayOriginY, position.z),
                new Vector3f(position.x + halfWidth, rayOriginY, position.z + halfDepth),
                new Vector3f(position.x - halfWidth, rayOriginY, position.z + halfDepth),
                new Vector3f(position.x + halfWidth, rayOriginY, position.z - halfDepth),
                new Vector3f(position.x - halfWidth, rayOriginY, position.z - halfDepth)
        };

        float highestLandingY = -Float.MAX_VALUE;

        for (Vector3f rayOrigin : rayOrigins) {
            for (Block block : collisionCandidateBlocks) { // Use filtered list
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                Vector2f nearFar = new Vector2f();

                if (blockAABB.intersectRay(rayOrigin, rayDir, nearFar) && nearFar.x >= -0.001f && nearFar.x <= checkRayLength) {
                    groundDetectedThisCheck = true;
                    float snapY = blockAABB.max.y - localBoundingBox.min.y + COLLISION_SKIN_WIDTH;
                    if (snapY > highestLandingY) {
                        highestLandingY = snapY;
                    }
                    break;
                }
            }
        }

        if (groundDetectedThisCheck) {
            if (velocity.y <= 0.01f && Math.abs(position.y - highestLandingY) < (checkRayLength + COLLISION_SKIN_WIDTH * 2.0f)) { // Increased tolerance slightly for snapping
                position.y = highestLandingY;
                if (velocity.y < 0) velocity.y = 0;
            }
            isOnGround = true;
        } else {
            isOnGround = (velocity.y > 0) ? false : false; // If not moving up, and no ground, then not on ground.
        }
    }

    protected abstract void updateLogic(float deltaTime);

    public void teleport(Vector3f newPosition) {
        this.position.set(newPosition);
        this.velocity.set(0, 0, 0);
        this.isOnGround = false;
        // For checkIfOnGround after teleport, we need to get blocks around the new position.
        Vector3f entityDimensions = new Vector3f(
                localBoundingBox.max.x - localBoundingBox.min.x,
                localBoundingBox.max.y - localBoundingBox.min.y,
                localBoundingBox.max.z - localBoundingBox.min.z
        );
        List<Block> collisionCandidateBlocks = worldTerrain.getBlocksForCollision(this.position, entityDimensions);
        checkIfOnGround(collisionCandidateBlocks);
    }

    public void addVelocity(Vector3f additionalVelocity) {
        this.velocity.add(additionalVelocity);
        if (additionalVelocity.lengthSquared() > 0) {
            this.isOnGround = false;
        }
    }

    public void kill() {
        this.isValid = false;
    }

    public UUID getId() { return id; }
    public Vector3f getPosition() { return new Vector3f(position); }
    public Vector3f getVelocity() { return new Vector3f(velocity); }
    public boolean isValid() { return isValid; }
    public boolean isOnGround() { return isOnGround; }

    public CustomAABB getLocalBoundingBox() {
        return new CustomAABB(localBoundingBox.min, localBoundingBox.max);
    }

    public CustomAABB getBoundingBoxWorld() {
        // Translates the local AABB to the entity's current world position.
        // Assumes localBoundingBox min/max are relative to (0,0,0) and position is the center.
        return localBoundingBox.translate(position);
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    public abstract void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand);
    public abstract void onEntityInteraction(Entity target, Hand hand);
}
