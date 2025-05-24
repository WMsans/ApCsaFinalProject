package World.Entities;

import Graphics.EntityModel; // Added
import Graphics.ModelRenderer; // Added
import Inventory.Hand;
import Physics.CustomAABB;
import World.Block;
import World.Terrain.BaseTerrainGenerator;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import org.joml.Matrix4f; // Added
import org.joml.Quaternionf; // Added
import org.joml.Vector3f;
import org.joml.Vector2f;
import java.util.UUID;
import java.util.List;

public abstract class Entity {
    protected final UUID id;
    protected Vector3f position;
    protected Vector3f velocity;
    protected float yaw;
    protected float pitch;
    protected float roll = 0f; // Added roll

    protected boolean isValid;
    protected boolean isOnGround;
    protected BaseTerrainGenerator worldTerrain;

    protected CustomAABB localBoundingBox;
    protected EntityModel model; // Added

    protected static final float DEFAULT_GRAVITY_ACCELERATION = 19.62f;
    protected static final float DEFAULT_TERMINAL_VELOCITY = 50.0f;
    protected static final float COLLISION_SKIN_WIDTH = 0.005f;

    protected boolean skipCollisionProcessing = false; // Added: Flag to skip collision logic

    public Entity(BaseTerrainGenerator worldTerrain, Vector3f initialPosition, Vector3f dimensions) {
        this.id = UUID.randomUUID();
        this.worldTerrain = worldTerrain;
        this.position = new Vector3f(initialPosition);
        this.velocity = new Vector3f(0, 0, 0);
        this.isValid = true;
        this.isOnGround = false;
        this.yaw = 0;
        this.pitch = 0;
        this.localBoundingBox = new CustomAABB(
                -dimensions.x / 2, -dimensions.y / 2, -dimensions.z / 2,
                dimensions.x / 2,  dimensions.y / 2,  dimensions.z / 2
        );
    }

    public void update(float deltaTime, float currentTime) {
        if (!isValid) return;

        applyGravity(deltaTime);
        moveEntity(deltaTime);
        updateLogic(deltaTime);
    }

    protected void applyGravity(float deltaTime) {
        // Added: If skipping collision (e.g., flying), gravity is likely handled by specialized logic or ignored.
        if (skipCollisionProcessing) {
            return;
        }

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

        // Added: If skipping collision processing, just apply movement and exit.
        if (skipCollisionProcessing) {
            position.add(potentialMovement);
            isOnGround = false; // When skipping collisions (flying), player is not on ground.
            return;
        }

        Vector3f entityDimensions = new Vector3f(
                localBoundingBox.max.x - localBoundingBox.min.x,
                localBoundingBox.max.y - localBoundingBox.min.y,
                localBoundingBox.max.z - localBoundingBox.min.z
        );
        List<Block> collisionCandidateBlocks = worldTerrain.getBlocksForCollision(this.position, entityDimensions);

        if (potentialMovement.y != 0) {
            float targetY = position.y + potentialMovement.y;
            CustomAABB testYBounds = localBoundingBox.translate(new Vector3f(position.x, targetY, position.z));
            boolean yCollisionThisFrame = false;
            float resolvedPosY = targetY;
            for (Block block : collisionCandidateBlocks) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (testYBounds.testAABB(blockAABB)) {
                    yCollisionThisFrame = true;
                    if (potentialMovement.y < 0) {
                        resolvedPosY = blockAABB.max.y - localBoundingBox.min.y + COLLISION_SKIN_WIDTH;
                        velocity.y = 0;
                        isOnGround = true;
                    } else {
                        resolvedPosY = blockAABB.min.y - localBoundingBox.max.y - COLLISION_SKIN_WIDTH;
                        velocity.y = 0;
                    }
                    break;
                }
            }
            position.y = resolvedPosY;
            if (!yCollisionThisFrame && potentialMovement.y < 0) isOnGround = false;
        }

        if (potentialMovement.x != 0) {
            float targetX = position.x + potentialMovement.x;
            CustomAABB testXBounds = localBoundingBox.translate(new Vector3f(targetX, position.y, position.z));
            float resolvedPosX = targetX;
            for (Block block : collisionCandidateBlocks) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (testXBounds.testAABB(blockAABB)) {
                    if (potentialMovement.x < 0) resolvedPosX = blockAABB.max.x - localBoundingBox.min.x + COLLISION_SKIN_WIDTH;
                    else resolvedPosX = blockAABB.min.x - localBoundingBox.max.x - COLLISION_SKIN_WIDTH;
                    velocity.x = 0;
                    break;
                }
            }
            position.x = resolvedPosX;
        }

        if (potentialMovement.z != 0) {
            float targetZ = position.z + potentialMovement.z;
            CustomAABB testZBounds = localBoundingBox.translate(new Vector3f(position.x, position.y, targetZ));
            float resolvedPosZ = targetZ;
            for (Block block : collisionCandidateBlocks) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (testZBounds.testAABB(blockAABB)) {
                    if (potentialMovement.z < 0) resolvedPosZ = blockAABB.max.z - localBoundingBox.min.z + COLLISION_SKIN_WIDTH;
                    else resolvedPosZ = blockAABB.min.z - localBoundingBox.max.z - COLLISION_SKIN_WIDTH;
                    velocity.z = 0;
                    break;
                }
            }
            position.z = resolvedPosZ;
        }

        if (velocity.y <= 0.01f) checkIfOnGround(collisionCandidateBlocks);
        else isOnGround = false;
    }

    protected void checkIfOnGround(List<Block> collisionCandidateBlocks) {
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
            for (Block block : collisionCandidateBlocks) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                Vector2f nearFar = new Vector2f();
                if (blockAABB.intersectRay(rayOrigin, rayDir, nearFar) && nearFar.x >= -0.001f && nearFar.x <= checkRayLength) {
                    groundDetectedThisCheck = true;
                    float snapY = blockAABB.max.y - localBoundingBox.min.y + COLLISION_SKIN_WIDTH;
                    if (snapY > highestLandingY) highestLandingY = snapY;
                    break;
                }
            }
            if (groundDetectedThisCheck) break;
        }
        if (groundDetectedThisCheck) {
            if (velocity.y <= 0.01f && Math.abs(position.y - highestLandingY) < (checkRayLength + COLLISION_SKIN_WIDTH * 2.0f)) {
                position.y = highestLandingY;
                if (velocity.y < 0) velocity.y = 0;
            }
            isOnGround = true;
        } else {
            isOnGround = (velocity.y > 0);
        }
    }

    protected abstract void updateLogic(float deltaTime);

    public void teleport(Vector3f newPosition) {
        this.position.set(newPosition);
        this.velocity.set(0, 0, 0);
        this.isOnGround = false;
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
        if (additionalVelocity.lengthSquared() > 0) this.isOnGround = false;
    }

    public void kill() {
        this.isValid = false;
        if (this.model != null) {
            this.model.cleanup(); // Clean up GPU resources for the model
            this.model = null;
        }
    }

    public UUID getId() { return id; }
    public Vector3f getPosition() { return new Vector3f(position); }
    public Vector3f getVelocity() { return new Vector3f(velocity); }
    public boolean isValid() { return isValid; }
    public boolean isOnGround() { return isOnGround; }
    public CustomAABB getLocalBoundingBox() { return new CustomAABB(localBoundingBox.min, localBoundingBox.max); }
    public CustomAABB getBoundingBoxWorld() { return localBoundingBox.translate(position); }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public ChunkId getChunkId() { return Chunk.getChunkIdAtWorldPosition(this.position.x, this.position.y, this.position.z); }

    public abstract void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand);
    public abstract void onEntityInteraction(Entity target, Hand hand);

    // Model related methods
    public EntityModel getModel() { return model; }

    /**
     * Subclasses should implement this to define their visual model.
     * This method should set this.model.
     */
    protected abstract void createModelData();

    public void initializeModel(ModelRenderer modelRenderer) {
        createModelData(); // Populates this.model with vertex/index data
        if (this.model != null && modelRenderer != null) {
            modelRenderer.buildMesh(this.model); // Builds VAO/VBO and stores IDs in this.model
        }
    }

    public Matrix4f getModelMatrix() {
        Matrix4f modelMatrix = new Matrix4f().translate(position);
        // Apply yaw, pitch, roll. Standard FPS camera order is Yaw -> Pitch. Roll can be applied last locally.
        modelMatrix.rotateY((float)Math.toRadians(yaw));
        modelMatrix.rotateX((float)Math.toRadians(pitch)); // If entities can pitch independently
        modelMatrix.rotateZ((float)Math.toRadians(roll));  // If entities can roll

        // Default scale, can be overridden by subclasses if they have varying sizes for their models
        // modelMatrix.scale(1.0f); // Example scale
        return modelMatrix;
    }

    public void setRoll(float roll) { this.roll = roll; }
    public float getRoll() { return roll; }
}