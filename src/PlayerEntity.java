// import org.joml.Intersectionf; // Removed JOML Intersectionf
import Physics.CustomAABB;
import World.*;
import World.Entities.*;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Random;
import Inventory.*;
import Input.*;

import static org.lwjgl.glfw.GLFW.*;

public class PlayerEntity extends LivingEntity {
    private final Input input;
    private final Window window;
    private final Camera camera;
    private final Inventory inventory;

    private final float REACH_DISTANCE = 5.0f;
    private final float playerMoveSpeed = 5.0f;
    private final float mouseSensitivity = 0.1f;
    private final float playerEyeHeight = 1.62f;
    private final float playerJumpVelocity = 7.0f;

    private double lastMouseX = -1, lastMouseY = -1;
    private boolean firstMouse = true;
    private final Random randomGenerator;

    private final Vector2f nearFarIntersection = new Vector2f();

    public PlayerEntity(Input input, Window window, Terrain worldTerrain, Vector3f initialPosition) {
        super(worldTerrain, initialPosition, new Vector3f(0.6f, 1.8f, 0.6f), 20.0f);
        this.input = input;
        this.window = window;
        this.inventory = new Inventory(36);
        this.randomGenerator = new Random();

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
        handleKeyboardMovement(deltaTime);
        handleBlockInteractionInput();
        super.update(deltaTime);
        Vector3f currentEyePosition = new Vector3f(this.position).add(0, getEyeHeight(), 0);
        this.camera.setPosition(currentEyePosition);
        this.camera.setYaw(this.yaw);
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

    private void handleKeyboardMovement(float deltaTime) {
        Vector3f forwardXZ = new Vector3f((float)Math.sin(Math.toRadians(this.yaw)), 0, (float)Math.cos(Math.toRadians(this.yaw))).normalize();
        Vector3f rightXZ = new Vector3f((float)Math.sin(Math.toRadians(this.yaw + 90)), 0, (float)Math.cos(Math.toRadians(this.yaw + 90))).normalize();
        Vector3f desiredVelocityXZ = new Vector3f(0,0,0); // Store XZ components here

        float currentSpeed = playerMoveSpeed;

        if (input.isKeyDown(GLFW_KEY_W)) desiredVelocityXZ.add(forwardXZ);
        if (input.isKeyDown(GLFW_KEY_S)) desiredVelocityXZ.sub(forwardXZ);
        if (input.isKeyDown(GLFW_KEY_A)) desiredVelocityXZ.sub(rightXZ);
        if (input.isKeyDown(GLFW_KEY_D)) desiredVelocityXZ.add(rightXZ);

        if (desiredVelocityXZ.lengthSquared() > 0) {
            desiredVelocityXZ.normalize().mul(currentSpeed);
        }

        this.velocity.x = desiredVelocityXZ.x;
        this.velocity.z = desiredVelocityXZ.z;

        if (input.isKeyPressed(GLFW_KEY_SPACE) && isOnGround) {
            this.velocity.y = playerJumpVelocity;
            isOnGround = false;
        }
        // Gravity will handle the rest of Y velocity in super.update()
    }

    private void handleBlockInteractionInput() {
        Vector3f rayOrigin = camera.getPosition();
        Vector3f rayDirection = camera.getForwardDirection(true);
        Block targetedBlock = null;
        Vector3f intersectionPoint = new Vector3f();
        float closestDistance = REACH_DISTANCE + 0.1f;

        for (Block block : worldTerrain.getBlocks()) {
            CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition()); // Use static helper
            nearFarIntersection.set(0,0); // Reset
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
            Vector3f blockCenter = targetedBlock.getPosition();
            Vector3f hitRelativeToCenter = new Vector3f(intersectionPoint).sub(blockCenter);
            Vector3f placementNormal = new Vector3f();
            float maxComponent = 0.0001f;

            if (Math.abs(hitRelativeToCenter.x) > maxComponent) {
                maxComponent = Math.abs(hitRelativeToCenter.x);
                placementNormal.set(Math.signum(hitRelativeToCenter.x), 0, 0);
            }
            if (Math.abs(hitRelativeToCenter.y) > maxComponent) {
                maxComponent = Math.abs(hitRelativeToCenter.y);
                placementNormal.set(0, Math.signum(hitRelativeToCenter.y), 0);
            }
            if (Math.abs(hitRelativeToCenter.z) > maxComponent) {
                placementNormal.set(0, 0, Math.signum(hitRelativeToCenter.z));
            }

            if (placementNormal.lengthSquared() > 0.5f) {
                Vector3f newBlockPosition = new Vector3f(blockCenter).add(placementNormal);
                CustomAABB playerWorldBB = this.getBoundingBoxWorld();
                CustomAABB newBlockBB = CustomAABB.forBlock(newBlockPosition);

                if (!playerWorldBB.testAABB(newBlockBB) && !worldTerrain.isBlockAt(newBlockPosition)) {
                    Vector3f randomColor = new Vector3f(randomGenerator.nextFloat(), randomGenerator.nextFloat(), randomGenerator.nextFloat());
                    worldTerrain.addBlock(new Block(newBlockPosition, randomColor));
                }
            }
        }
    }

    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand) {
        if (block != null && hand == Hand.MAIN_HAND) {
            System.out.println("Player " + id + " broke block at " + block.getPosition());
            worldTerrain.removeBlock(block);
        }
    }

    @Override
    public void onEntityInteraction(Entity target, Hand hand) {
        System.out.println("Player " + id + " interacted with entity " + target.getId() + " with " + hand);
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
        System.out.println("Player " + id + " equipped " + (stack != null ? stack.getItemId() : "nothing") + " in " + slot);
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

