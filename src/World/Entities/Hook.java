package World.Entities;

import World.Block;
import World.Terrain.BaseTerrainGenerator;
import org.joml.Vector3f;

public class Hook extends Entity {

    private LivingEntity owner;
    private boolean isAttached = false;
    private Vector3f attachedPoint = null;
    private Block attachedBlock = null;
    private float currentStringLength = 0.0f;

    public Hook(LivingEntity owner, BaseTerrainGenerator worldTerrain, Vector3f initialPosition) {
        // Dimensions for a hook entity can be very small, or it might not need collision itself initially
        super(worldTerrain, initialPosition, new Vector3f(0.1f, 0.1f, 0.1f));
        this.owner = owner;
        this.velocity.zero(); // Hook might have its own movement logic if not instant
        System.out.println("Hook created, state: Ready");
    }

    @Override
    protected void updateLogic(float deltaTime) {
        // Hook-specific logic, e.g., traveling to target if not instant
        // For now, we assume instant attachment handled by World.Entities.PlayerEntity
        if (isAttached && owner != null) {
            // Maintain string length or other properties if needed
            float distanceToOwner = owner.getPosition().distance(this.position);
            // This is a simplified view; World.Entities.PlayerEntity will manage the dynamic length
            // System.out.println("Hook attached. String length: " + currentStringLength + ", Current distance to owner: " + distanceToOwner);
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
        System.out.println("Hook state: Stabilized at " + point);
    }

    public void detach() {
        this.isAttached = false;
        this.attachedBlock = null;
        this.attachedPoint = null;
        this.currentStringLength = 0;
        this.kill(); // Mark the hook entity as invalid so it can be cleaned up
        System.out.println("Hook state: Released and removed.");
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