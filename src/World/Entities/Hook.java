package World.Entities;

import Graphics.EntityModel;
import Graphics.ModelComponent;
import Physics.CustomAABB;
import World.Block;
import World.Terrain.BaseTerrainGenerator;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Hook extends Entity {

    private LivingEntity owner;
    private boolean isAttached = false;
    private Vector3f attachedPoint = null; // Initial world-space impact point or static block attach point
    private Block attachedBlock = null;
    private LivingEntity attachedEntity = null; // New: Store attached entity
    private float currentStringLength = 0.0f;
    private final float MODEL_SIZE = 0.7f;
    private final Vector3f MODEL_COLOR = new Vector3f(0.7f, 0.7f, 0.7f);

    public Hook(LivingEntity owner, BaseTerrainGenerator worldTerrain, Vector3f initialPosition) {
        super(worldTerrain, initialPosition, new Vector3f(0.7f, 0.7f, 0.7f));
        this.owner = owner;
        this.velocity.zero();
    }

    @Override
    protected void populateModelComponents() {
        if (modelComponents.isEmpty()) {
            EntityModel hookModel = EntityModel.createCubeModel(MODEL_SIZE, MODEL_COLOR);
            modelComponents.add(new ModelComponent(hookModel, new Matrix4f().identity()));
        }
    }

    @Override
    public Matrix4f getModelMatrix() {
        Matrix4f modelMatrix = new Matrix4f().translate(this.position);

        Vector3f directionToOwner = new Vector3f();
        if (owner != null && owner.isValid()) {
            Vector3f ownerEyePosition = new Vector3f(owner.getPosition()).add(0, owner.getEyeHeight(), 0);
            ownerEyePosition.sub(this.position, directionToOwner);
        } else {
            directionToOwner.set(0, 0, -1); // Default orientation if no owner
        }

        if (directionToOwner.lengthSquared() > 0.001f) {
            directionToOwner.normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), directionToOwner);
            modelMatrix.rotate(rotation);
        }
        return modelMatrix;
    }


    @Override
    protected void updateLogic(float deltaTime) {
        if (isAttached && attachedEntity != null && attachedEntity.isValid()) {
            // Update the hook's visual position to stick to the entity
            Vector3f targetVisualPosition = calculateCurrentAttachmentWorldPosition();
            if (targetVisualPosition != null) {
                this.position.set(targetVisualPosition);
            } else { // Entity might have become invalid
                detach();
            }
        }
        // If attached to a block, its position is static relative to the world.
    }

    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Inventory.Hand hand) {
        // Not applicable for hooks
    }

    @Override
    public void onEntityInteraction(Entity target, Inventory.Hand hand) {
        // Not applicable for hooks
    }

    public void attach(Block block, Vector3f worldImpactPoint, float initialStringLength) {
        this.isAttached = true;
        this.attachedBlock = block;
        this.attachedEntity = null; // Clear entity attachment
        this.attachedPoint = new Vector3f(worldImpactPoint); // This is the static point on the block
        this.position.set(worldImpactPoint);
        this.currentStringLength = initialStringLength;
        this.velocity.zero();
    }

    // New method to attach to an entity
    public void attachToEntity(LivingEntity entity, Vector3f worldImpactPoint, float initialStringLength) {
        this.isAttached = true;
        this.attachedEntity = entity;
        this.attachedBlock = null; // Clear block attachment
        // Store the initial world impact point. The hook will visually follow the entity.
        this.attachedPoint = new Vector3f(worldImpactPoint);
        this.position.set(calculateCurrentAttachmentWorldPosition()); // Set initial position correctly
        this.currentStringLength = initialStringLength;
        this.velocity.zero();
    }

    public void detach() {
        this.isAttached = false;
        this.attachedBlock = null;
        this.attachedEntity = null; // Clear entity attachment
        this.attachedPoint = null;
        this.currentStringLength = 0;
        this.kill(); // Mark entity as invalid for removal
        if (owner instanceof PlayerEntity) {
            ((PlayerEntity) owner).onHookReleased();
        }
    }

    public boolean isAttached() {
        return isAttached;
    }

    // This method is crucial for PlayerEntity to know where the hook's anchor point is.
    // If attached to an entity, it's a dynamic point. If to a block, it's static.
    public Vector3f getAttachedPoint() {
        if (isAttached) {
            return calculateCurrentAttachmentWorldPosition();
        }
        return null;
    }

    private Vector3f calculateCurrentAttachmentWorldPosition() {
        if (attachedEntity != null && attachedEntity.isValid()) {
            // For simplicity, attach to the entity's base position + eye height (approximates center mass vertically)
            // More sophisticated: could attach to a specific bone or a point relative to the entity's orientation.
            Vector3f entityBasePos = attachedEntity.getPosition();
            // Calculate vertical center of the entity's bounding box
            CustomAABB localBB = attachedEntity.getLocalBoundingBox();
            float halfHeight = (localBB.max.y - localBB.min.y) / 2.0f;
            float verticalCenterOffset = localBB.min.y + halfHeight; // Offset from entity's base position to its vertical center

            return new Vector3f(entityBasePos.x, entityBasePos.y + verticalCenterOffset, entityBasePos.z);

        } else if (attachedBlock != null && this.attachedPoint != null) {
            return new Vector3f(this.attachedPoint); // The static point on the block
        }
        return this.position; // Fallback or if not properly attached
    }


    public LivingEntity getOwner() {
        return owner;
    }

    public float getCurrentStringLength() { return currentStringLength; }
    public void setCurrentStringLength(float length) { this.currentStringLength = length; }

    public Block getAttachedBlock() { return attachedBlock; }
    public LivingEntity getAttachedEntity() { return attachedEntity; }
}