package World.Entities.Enemies;

import Graphics.EntityModel;
import Graphics.ModelComponent;
import Physics.CustomAABB;
import World.Block;
import World.Entities.Enemy;
import World.Entities.Entity;
import World.Entities.PlayerEntity;
import World.Entities.LivingEntity; // Required for override
import World.Terrain.BaseTerrainGenerator;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class AegisGuardian extends Enemy {

    private static final Vector3f BODY_DIMENSIONS = new Vector3f(1.8f, 2.5f, 1.8f);
    private static final Vector3f BODY_COLOR = new Vector3f(0.4f, 0.4f, 0.6f);

    public static final Vector3f SHIELD_DIMENSIONS = new Vector3f(7.5f, 7.5f, .3f);
    private static final Vector3f SHIELD_COLOR = new Vector3f(1.0f, 0.9f, 0.2f);
    public static final Vector3f SHIELD_LOCAL_OFFSET = new Vector3f(0, 0, (BODY_DIMENSIONS.z / 2.0f) + (SHIELD_DIMENSIONS.z / 2.0f) + 0.1f);

    private CustomAABB localShieldAABB;

    private static final float MAX_HEALTH = 150.0f;

    public AegisGuardian(BaseTerrainGenerator worldTerrain, Vector3f initialPosition) {
        super(worldTerrain, initialPosition, BODY_DIMENSIONS, MAX_HEALTH);
        this.yaw = (float) (Math.random() * 360.0);
        this.skipCollisionProcessing = false;
        this.isOnGround = false;

        Vector3f shieldMin = new Vector3f(SHIELD_LOCAL_OFFSET)
                .sub(SHIELD_DIMENSIONS.x / 2f, SHIELD_DIMENSIONS.y / 2f, SHIELD_DIMENSIONS.z / 2f);
        Vector3f shieldMax = new Vector3f(SHIELD_LOCAL_OFFSET)
                .add(SHIELD_DIMENSIONS.x / 2f, SHIELD_DIMENSIONS.y / 2f, SHIELD_DIMENSIONS.z / 2f);
        this.localShieldAABB = new CustomAABB(shieldMin, shieldMax);
    }

    @Override
    protected void populateModelComponents() {
        if (modelComponents.isEmpty()) {
            EntityModel bodyModel = EntityModel.createCuboidModel(BODY_DIMENSIONS.x, BODY_DIMENSIONS.y, BODY_DIMENSIONS.z, BODY_COLOR);
            modelComponents.add(new ModelComponent(bodyModel));

            EntityModel shieldModel = EntityModel.createCuboidModel(SHIELD_DIMENSIONS.x, SHIELD_DIMENSIONS.y, SHIELD_DIMENSIONS.z, SHIELD_COLOR);
            Matrix4f shieldTransform = new Matrix4f().translate(SHIELD_LOCAL_OFFSET);
            modelComponents.add(new ModelComponent(shieldModel, shieldTransform));
        }
    }

    @Override
    protected void updateLogic(float deltaTime) {
        PlayerEntity player = null;
        float closestDistSq = Float.MAX_VALUE;

        for (Entity e : worldTerrain.getEntities()) {
            if (e instanceof PlayerEntity && e.isValid()) {
                float distSq = e.getPosition().distanceSquared(this.position);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    player = (PlayerEntity) e;
                }
            }
        }

        if (player != null) {
            Vector3f directionToPlayer = new Vector3f(player.getPosition()).sub(this.position);
            this.yaw = (float) Math.toDegrees(Math.atan2(directionToPlayer.z, directionToPlayer.x)) - 90;
        }
    }

    @Override
    public boolean hasCustomBlockingGeometry() {
        return true;
    }

    @Override
    public boolean checkCustomBlockingGeometry(Vector3f worldInteractionPoint, Vector3f attackerWorldPosition) {
        Matrix4f worldToLocal = new Matrix4f(getModelMatrix()).invert();
        Vector3f attackerLocalPos = new Vector3f();
        worldToLocal.transformPosition(new Vector3f(attackerWorldPosition), attackerLocalPos);

        // Shield is on local +Z. If attacker is effectively "behind" the shield's front plane, it might not block.
        // SHIELD_LOCAL_OFFSET.z - SHIELD_DIMENSIONS.z / 2.0f is approx the shield's back plane in local Z.
        // If attacker's local Z is less than this, they are behind or to the side of the shield's main blocking face.
        if (attackerLocalPos.z < (SHIELD_LOCAL_OFFSET.z - SHIELD_DIMENSIONS.z / 2.0f) - 0.5f) { // Added buffer for "clearly behind"
            return false; // Shield doesn't block from this angle
        }

        // Check if the worldInteractionPoint (e.g., hook impact, center of slash) hits the shield geometry
        Vector3f localInteractionPoint = new Vector3f();
        worldToLocal.transformPosition(new Vector3f(worldInteractionPoint), localInteractionPoint);

        return true;
    }

    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Inventory.Hand hand) {}

    @Override
    public void onEntityInteraction(Entity target, Inventory.Hand hand) {}
}