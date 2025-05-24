package World.Entities;

import Graphics.EntityModel; // Added
import World.Block;
import World.Terrain.BaseTerrainGenerator;
import org.joml.Matrix4f; // Added
import org.joml.Quaternionf; // Added
import org.joml.Vector3f;

public class Hook extends Entity {

    private LivingEntity owner;
    private boolean isAttached = false;
    private Vector3f attachedPoint = null;
    private Block attachedBlock = null;
    private float currentStringLength = 0.0f;
    private final float MODEL_SIZE = 1f; // Size of the hook model
    private final Vector3f MODEL_COLOR = new Vector3f(1.0f, 1.0f, 1.0f); // White

    public Hook(LivingEntity owner, BaseTerrainGenerator worldTerrain, Vector3f initialPosition) {
        super(worldTerrain, initialPosition, new Vector3f(1f, 1f, 1f)); // Collision box matches model
        this.owner = owner;
        this.velocity.zero();
        // System.out.println("Hook created, state: Ready"); // Keep for debugging if needed
    }

    @Override
    protected void createModelData() {
        this.model = EntityModel.createCubeModel(MODEL_SIZE, MODEL_COLOR);
    }

    @Override
    public Matrix4f getModelMatrix() {
        Matrix4f modelMatrix = new Matrix4f().translate(position);

        Vector3f direction = new Vector3f(velocity);
        if (direction.lengthSquared() == 0) {
            // Default orientation if no velocity (e.g., look along positive Z)
            // Or, if owner exists and is PlayerEntity, could use camera direction
            if (owner instanceof PlayerEntity) {
                direction = ((PlayerEntity) owner).getCamera().getForwardDirection(true);
            } else {
                direction.set(0, 0, 1); // Default Z forward
            }
        }
        direction.normalize();

        // Rotate to align the model's local Z-axis (or X, depending on model) with the direction
        // Using Quaternionf for robust rotation
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction); // Assumes model's forward is +Z
        modelMatrix.rotate(rotation);

        // Apply roll if needed, for hook it's likely not necessary
        // modelMatrix.rotateZ((float)Math.toRadians(this.roll));


        // Scale is handled by the model's vertex positions already (MODEL_SIZE)
        // If you wanted to scale an arbitrary model, you'd apply it here:
        // modelMatrix.scale(MODEL_SIZE);
        return modelMatrix;
    }


    @Override
    protected void updateLogic(float deltaTime) {
        // If hook is not attached and has a velocity, it's traveling
        if (!isAttached && velocity.lengthSquared() > 0.01f) {
            // Could add logic for collision with blocks while traveling to auto-attach
            // For now, attachment is handled by PlayerEntity raycast
        }

        if (isAttached && owner != null) {
            // The hook entity itself is static once attached.
            // The player entity manages the tension and string length.
            // If owner moves too far or detaches, this hook entity should be removed.
            if (!owner.isValid() || owner.getPosition().distanceSquared(this.position) > (currentStringLength + 5.0f) * (currentStringLength + 5.0f) ) {
                // Owner gone or too far, detach (this will also kill this entity)
                // detach(); // Detach is usually called by player input or tension logic
            }
        } else if (!isAttached && velocity.lengthSquared() < 0.01f && owner != null) {
            // If hook isn't attached and not moving (e.g. after player releases but before it hits anything)
            // it should probably be removed after a short timeout or if it falls too far.
            // For now, PlayerEntity handles immediate removal on release.
        }
    }

    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Inventory.Hand hand) {
        // Hooks probably don't interact with blocks after being shot
    }

    @Override
    public void onEntityInteraction(Entity target, Inventory.Hand hand) {
        // Hooks probably don't interact with other entities directly
    }

    public void attach(Block block, Vector3f point, float initialStringLength) {
        this.isAttached = true;
        this.attachedBlock = block;
        this.attachedPoint = new Vector3f(point);
        this.position.set(point); // Hook is now at the attached point
        this.currentStringLength = initialStringLength;
        this.velocity.zero(); // Stop hook movement
        // System.out.println("Hook state: Stabilized at " + point);
    }

    public void detach() {
        this.isAttached = false;
        this.attachedBlock = null;
        this.attachedPoint = null;
        this.currentStringLength = 0;
        this.kill(); // Mark the hook entity as invalid so it can be cleaned up
        // System.out.println("Hook state: Released and removed.");
        if (owner instanceof PlayerEntity) {
            ((PlayerEntity) owner).onHookReleased(); // Notify player
        }
    }

    public boolean isAttached() {
        return isAttached;
    }

    public Vector3f getAttachedPoint() {
        return attachedPoint;
    }

    public LivingEntity getOwner() {
        return owner;
    }

    public float getCurrentStringLength() { return currentStringLength; }
    public void setCurrentStringLength(float length) { this.currentStringLength = length; }
}