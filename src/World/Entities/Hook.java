package World.Entities;

import Graphics.EntityModel;
import Graphics.ModelComponent; // Added
import Graphics.ModelRenderer; // Added for initializeModels signature
import World.Block;
import World.Terrain.BaseTerrainGenerator;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList; // Added

public class Hook extends Entity {

    private LivingEntity owner;
    private boolean isAttached = false;
    private Vector3f attachedPoint = null;
    private Block attachedBlock = null;
    private float currentStringLength = 0.0f;
    private final float MODEL_SIZE = 0.7f; // Adjusted size for a hook
    private final Vector3f MODEL_COLOR = new Vector3f(0.7f, 0.7f, 0.7f); // Grey color for hook

    public Hook(LivingEntity owner, BaseTerrainGenerator worldTerrain, Vector3f initialPosition) {
        super(worldTerrain, initialPosition, new Vector3f(0.7f, 0.7f, 0.7f)); // Collision box
        this.owner = owner;
        this.velocity.zero();
    }

    @Override
    protected void populateModelComponents() {
        if (modelComponents.isEmpty()) { // Only add if not already populated
            EntityModel hookModel = EntityModel.createCubeModel(MODEL_SIZE, MODEL_COLOR);
            // For the hook, the local transform can be identity as its orientation is handled by the main entity's rotation.
            modelComponents.add(new ModelComponent(hookModel, new Matrix4f().identity()));
        }
    }

    @Override
    public Matrix4f getModelMatrix() {
        // The main entity transform (position, yaw, pitch, roll)
        Matrix4f baseEntityMatrix = new Matrix4f().translate(position);

        Vector3f direction = new Vector3f(velocity);
        if (isAttached && attachedPoint != null && owner != null) {
            // If attached, point towards the owner (or from owner to hook point for visual effect)
            owner.getPosition().sub(this.position, direction);
        } else if (direction.lengthSquared() == 0) {
            if (owner instanceof PlayerEntity) {
                direction = ((PlayerEntity) owner).getCamera().getForwardDirection(true);
            } else {
                direction.set(0, 0, 1);
            }
        }

        if (direction.lengthSquared() > 0.001f) {
            direction.normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction);
            baseEntityMatrix.rotate(rotation);
        }

        baseEntityMatrix.rotateZ((float)Math.toRadians(this.roll));

        return baseEntityMatrix;
    }


    @Override
    protected void updateLogic(float deltaTime) {
        // ... (existing logic)
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
        this.position.set(point);
        this.currentStringLength = initialStringLength;
        this.velocity.zero();
    }

    public void detach() {
        this.isAttached = false;
        this.attachedBlock = null;
        this.attachedPoint = null;
        this.currentStringLength = 0;
        this.kill();
        if (owner instanceof PlayerEntity) {
            ((PlayerEntity) owner).onHookReleased();
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