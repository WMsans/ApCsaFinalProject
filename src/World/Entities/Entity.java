package World.Entities;

import Inventory.Hand;
import Physics.CustomAABB;
import World.Block;
import World.Terrain;
import org.joml.Vector3f;
import org.joml.Vector2f;
import java.util.UUID;


public abstract class Entity {
    protected final UUID id;
    protected Vector3f position;
    protected Vector3f velocity;
    protected float yaw;
    protected float pitch;

    protected boolean isValid;
    protected boolean isOnGround;
    protected Terrain worldTerrain;

    protected CustomAABB localBoundingBox; // Using CustomAABB

    protected static final float GRAVITY_ACCELERATION = -19.62f;
    protected static final float TERMINAL_VELOCITY = -50.0f;

    public Entity(Terrain worldTerrain, Vector3f initialPosition, Vector3f dimensions) {
        this.id = UUID.randomUUID();
        this.worldTerrain = worldTerrain;
        this.position = new Vector3f(initialPosition);
        this.velocity = new Vector3f(0, 0, 0);
        this.isValid = true;
        this.isOnGround = false;
        this.yaw = 0;
        this.pitch = 0;
        // Create local bounding box (centered at origin, will be translated by entity's position)
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
            velocity.y += GRAVITY_ACCELERATION * deltaTime;
            if (velocity.y < TERMINAL_VELOCITY) {
                velocity.y = TERMINAL_VELOCITY;
            }
        }
    }

    protected void moveEntity(float deltaTime) {
        Vector3f deltaPosition = new Vector3f(velocity).mul(deltaTime);
        // Store current position before attempting to move
        Vector3f oldPosition = new Vector3f(position);
        Vector3f newPosition = new Vector3f(position).add(deltaPosition);

        CustomAABB currentWorldBounds = getBoundingBoxWorld();

        // --- Y-axis movement and collision ---
        if (deltaPosition.y != 0) {
            position.y = newPosition.y; // Tentatively move Y
            CustomAABB movedYBounds = getBoundingBoxWorld(); // Bounds after tentative Y move
            boolean yCollision = false;
            float adjustYTo = position.y;

            for (Block block : worldTerrain.getBlocks()) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (movedYBounds.testAABB(blockAABB)) { // Check for overlap after Y move
                    yCollision = true;
                    if (deltaPosition.y < 0) { // Moving down
                        adjustYTo = blockAABB.max.y - localBoundingBox.min.y; // Land on top
                        velocity.y = 0;
                        isOnGround = true;
                    } else { // Moving up
                        adjustYTo = blockAABB.min.y - localBoundingBox.max.y; // Hit ceiling
                        velocity.y = 0;
                    }
                    position.y = adjustYTo; // Correct position
                    break;
                }
            }
            if (!yCollision && deltaPosition.y < 0) { // If moved down and no collision, not on ground
                isOnGround = false;
            }
        }


        // --- X-axis movement and collision ---
        if (deltaPosition.x != 0) {
            position.x = newPosition.x; // Tentatively move X
            CustomAABB movedXBounds = getBoundingBoxWorld();
            boolean xCollision = false;

            for (Block block : worldTerrain.getBlocks()) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (movedXBounds.testAABB(blockAABB)) {
                    xCollision = true;
                    if (deltaPosition.x < 0) { // Moving left
                        position.x = blockAABB.max.x - localBoundingBox.min.x;
                    } else { // Moving right
                        position.x = blockAABB.min.x - localBoundingBox.max.x;
                    }
                    velocity.x = 0;
                    break;
                }
            }
        }

        // --- Z-axis movement and collision ---
        if (deltaPosition.z != 0) {
            position.z = newPosition.z; // Tentatively move Z
            CustomAABB movedZBounds = getBoundingBoxWorld();
            boolean zCollision = false;

            for (Block block : worldTerrain.getBlocks()) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (movedZBounds.testAABB(blockAABB)) {
                    zCollision = true;
                    if (deltaPosition.z < 0) { // Moving forward (typically -Z)
                        position.z = blockAABB.max.z - localBoundingBox.min.z;
                    } else { // Moving backward (typically +Z)
                        position.z = blockAABB.min.z - localBoundingBox.max.z;
                    }
                    velocity.z = 0;
                    break;
                }
            }
        }

        // Final ground check if Y velocity was near zero or became zero
        if (Math.abs(velocity.y) < 0.01f) {
            checkIfOnGround();
        }
    }

    protected void checkIfOnGround() {
        // Raycast slightly down from entity's bottom center of its local AABB, translated to world space
        CustomAABB worldBB = getBoundingBoxWorld();
        Vector3f feetCenter = new Vector3f(position.x, worldBB.min.y, position.z); // Center of bottom face

        Vector3f rayOrigin = new Vector3f(feetCenter.x, feetCenter.y, feetCenter.z);
        Vector3f rayDir = new Vector3f(0, -1, 0);
        float checkDist = 0.05f; // Very small distance to check below feet
        boolean groundFound = false;

        for (Block block : worldTerrain.getBlocks()) {
            CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
            Vector2f nearFar = new Vector2f(); // For storing intersection results

            // Check if the ray starting from just under the entity intersects with the block
            if (blockAABB.intersectRay(rayOrigin, rayDir, nearFar) && nearFar.x <= checkDist && nearFar.x >= 0) {
                groundFound = true;
                // Correct position precisely onto the ground if slightly interpenetrating
                // This adjustment should ideally happen during Y-collision handling in moveEntity
                if (position.y + localBoundingBox.min.y < blockAABB.max.y) {
                    position.y = blockAABB.max.y - localBoundingBox.min.y;
                }
                if(velocity.y < 0) velocity.y = 0; // Stop vertical velocity if just landed
                break;
            }
        }
        this.isOnGround = groundFound;

        // If no ground found directly below by raycast, but velocity.y is zero (e.g. due to y-collision step),
        // re-evaluate. This part can be tricky. The y-collision in moveEntity should primarily set isOnGround.
        // This method is more of a secondary check or for initialization.
    }

    protected abstract void updateLogic(float deltaTime);

    public void teleport(Vector3f newPosition) {
        this.position.set(newPosition);
        this.velocity.set(0, 0, 0);
        this.isOnGround = false;
        checkIfOnGround();
    }

    public void addVelocity(Vector3f additionalVelocity) {
        this.velocity.add(additionalVelocity);
    }

    public void kill() {
        this.isValid = false;
    }

    public UUID getId() { return id; }
    public Vector3f getPosition() { return new Vector3f(position); }
    public Vector3f getVelocity() { return new Vector3f(velocity); }
    public boolean isValid() { return isValid; }
    public boolean isOnGround() { return isOnGround; }

    /**
     * Gets the entity's local bounding box (dimensions centered at origin).
     * @return A copy of the local CustomAABB.
     */
    public CustomAABB getLocalBoundingBox() {
        return new CustomAABB(localBoundingBox.min, localBoundingBox.max);
    }

    /**
     * Gets the entity's bounding box in world coordinates.
     * @return A new CustomAABB translated to the entity's current world position.
     */
    public CustomAABB getBoundingBoxWorld() {
        return localBoundingBox.translate(position);
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    public abstract void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand);
    public abstract void onEntityInteraction(Entity target, Hand hand);
}
