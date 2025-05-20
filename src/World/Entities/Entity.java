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
    protected static final float COLLISION_SKIN_WIDTH = 0.005f; // Small offset to prevent sticking

    public Entity(Terrain worldTerrain, Vector3f initialPosition, Vector3f dimensions) {
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

    public void update(float deltaTime) {
        if (!isValid) return;

        applyGravity(deltaTime);
        moveEntity(deltaTime); // Collision handling is within moveEntity
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
        Vector3f originalPosition = new Vector3f(position); // Keep original position for reference

        // --- Y-axis movement and collision ---
        if (deltaPosition.y != 0) {
            position.y += deltaPosition.y; // Tentatively move Y
            CustomAABB movedYBounds = getBoundingBoxWorld();
            boolean yCollisionThisFrame = false;

            for (Block block : worldTerrain.getBlocks()) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (movedYBounds.testAABB(blockAABB)) {
                    yCollisionThisFrame = true;
                    if (deltaPosition.y < 0) { // Moving down
                        position.y = blockAABB.max.y - localBoundingBox.min.y + COLLISION_SKIN_WIDTH; // Land on top + skin
                        velocity.y = 0;
                        isOnGround = true;
                    } else { // Moving up
                        position.y = blockAABB.min.y - localBoundingBox.max.y - COLLISION_SKIN_WIDTH; // Hit ceiling - skin
                        velocity.y = 0;
                    }
                    break;
                }
            }
            if (!yCollisionThisFrame) { // If moved Y and no collision
                isOnGround = false; // Explicitly set if not colliding vertically
            }
        } else {
            // If not trying to move vertically, re-check ground status
            // This helps if player walks off a ledge without vertical velocity input
            checkIfOnGround();
        }


        // --- X-axis movement and collision ---
        if (deltaPosition.x != 0) {
            position.x += deltaPosition.x; // Tentatively move X
            CustomAABB movedXBounds = getBoundingBoxWorld();

            for (Block block : worldTerrain.getBlocks()) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (movedXBounds.testAABB(blockAABB)) {
                    if (deltaPosition.x < 0) { // Moving left
                        position.x = blockAABB.max.x - localBoundingBox.min.x + COLLISION_SKIN_WIDTH;
                    } else { // Moving right
                        position.x = blockAABB.min.x - localBoundingBox.max.x - COLLISION_SKIN_WIDTH;
                    }
                    velocity.x = 0;
                    break;
                }
            }
        }

        // --- Z-axis movement and collision ---
        if (deltaPosition.z != 0) {
            position.z += deltaPosition.z; // Tentatively move Z
            CustomAABB movedZBounds = getBoundingBoxWorld();

            for (Block block : worldTerrain.getBlocks()) {
                CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                if (movedZBounds.testAABB(blockAABB)) {
                    if (deltaPosition.z < 0) { // Moving "forward" (typically decreasing Z)
                        position.z = blockAABB.max.z - localBoundingBox.min.z + COLLISION_SKIN_WIDTH;
                    } else { // Moving "backward" (typically increasing Z)
                        position.z = blockAABB.min.z - localBoundingBox.max.z - COLLISION_SKIN_WIDTH;
                    }
                    velocity.z = 0;
                    break;
                }
            }
        }

        // An additional ground check can be useful after all movements,
        // especially if skin width pushes entity slightly above ground.
        // However, the Y-collision logic should primarily handle isOnGround.
        // If velocity.y is very small (e.g. after landing), a final snap might be good.
        if (Math.abs(velocity.y) < 0.1f) { // If Y velocity is small (e.g. after landing)
            checkIfOnGround(); // Perform a final snap / ground check
        }
    }

    protected void checkIfOnGround() {
        CustomAABB worldBB = getBoundingBoxWorld();
        // Start ray slightly inside the bottom of the AABB to avoid self-intersection issues if skin pushes out
        Vector3f rayOrigin = new Vector3f(position.x, worldBB.min.y + COLLISION_SKIN_WIDTH * 0.5f, position.z);
        Vector3f rayDir = new Vector3f(0, -1, 0);
        // Check distance needs to be slightly more than skin width to detect ground properly
        float checkDist = COLLISION_SKIN_WIDTH * 1.5f;
        boolean groundFound = false;

        for (Block block : worldTerrain.getBlocks()) {
            CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
            Vector2f nearFar = new Vector2f();

            if (blockAABB.intersectRay(rayOrigin, rayDir, nearFar) && nearFar.x <= checkDist && nearFar.x >= 0) {
                groundFound = true;
                // Snap precisely to the surface, overriding any minor skin width effects from Y-collision
                position.y = blockAABB.max.y - localBoundingBox.min.y;
                if(velocity.y < 0) velocity.y = 0;
                break;
            }
        }
        this.isOnGround = groundFound;
    }

    protected abstract void updateLogic(float deltaTime);

    public void teleport(Vector3f newPosition) {
        this.position.set(newPosition);
        this.velocity.set(0, 0, 0);
        this.isOnGround = false; // Force re-evaluation
        checkIfOnGround(); // Snap to ground if applicable after teleport
    }

    public void addVelocity(Vector3f additionalVelocity) {
        this.velocity.add(additionalVelocity);
        this.isOnGround = false; // Adding velocity likely means not on ground anymore, or needs re-check
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
        return localBoundingBox.translate(position);
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    public abstract void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand);
    public abstract void onEntityInteraction(Entity target, Hand hand);
}
