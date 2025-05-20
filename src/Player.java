import Input.Input;
import World.Block;
import World.Terrain;
import org.joml.Intersectionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;

public class Player {
    private Camera camera;
    private Input input;
    // private Window window; // Only needed if aspect ratio isn't passed to camera differently
    private Terrain terrain;

    private final float REACH_DISTANCE = 5.0f;
    private final float moveSpeed = 5.0f; // Units per second
    private final float mouseSensitivity = 0.1f;

    private double lastMouseX = -1, lastMouseY = -1;
    private boolean firstMouse = true;
    private Random randomGenerator;

    // For ray-AABB intersection, managed by Player
    private Vector2f nearFarIntersection = new Vector2f();


    public Player(Input input, Window window, Terrain terrain, Vector3f startPosition) {
        this.input = input;
        // this.window = window;
        this.terrain = terrain;
        // Pass window to camera for aspect ratio, or have camera request it
        this.camera = new Camera(startPosition, window);
        this.randomGenerator = new Random();
        this.lastMouseX = input.getMouseX(); // Initialize last mouse position
        this.lastMouseY = input.getMouseY();
    }

    public void update(float deltaTime) {
        handleMouseLook();
        handleKeyboardMovement(deltaTime);
        handleBlockInteraction();
    }

    private void handleMouseLook() {
        double currentMouseX = input.getMouseX();
        double currentMouseY = input.getMouseY();

        if (firstMouse) { // Initialize lastX, lastY on first update after mouse is active
            lastMouseX = currentMouseX;
            lastMouseY = currentMouseY;
            firstMouse = false;
        }

        float xOffset = (float) (currentMouseX - lastMouseX) * mouseSensitivity;
        float yOffset = (float) (lastMouseY - currentMouseY) * mouseSensitivity; // Reversed

        lastMouseX = currentMouseX;
        lastMouseY = currentMouseY;

        camera.rotate(yOffset, xOffset); // pitch, yaw
    }

    private void handleKeyboardMovement(float deltaTime) {
        float actualMoveSpeed = moveSpeed * deltaTime;
        Vector3f forward = camera.getForwardDirection(false); // Get XZ forward vector
        Vector3f right = camera.getRightDirection(false);   // Get XZ right vector
        Vector3f worldUp = new Vector3f(0, 1, 0);

        if (input.isKeyDown(GLFW_KEY_W)) {
            camera.move(forward, actualMoveSpeed);
        }
        if (input.isKeyDown(GLFW_KEY_S)) {
            camera.move(forward, -actualMoveSpeed);
        }
        if (input.isKeyDown(GLFW_KEY_A)) {
            camera.move(right, -actualMoveSpeed);
        }
        if (input.isKeyDown(GLFW_KEY_D)) {
            camera.move(right, actualMoveSpeed);
        }
        if (input.isKeyDown(GLFW_KEY_SPACE)) {
            camera.move(worldUp, actualMoveSpeed); // Fly up
        }
        if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
            camera.move(worldUp, -actualMoveSpeed); // Fly down
        }
    }

    private void handleBlockInteraction() {
        Block targetedBlock = null;
        Vector3f intersectionPoint = new Vector3f(); // To store the precise intersection point
        float closestDistance = REACH_DISTANCE + 0.1f; // Start a bit beyond reach

        Vector3f rayOrigin = camera.getPosition();
        Vector3f rayDirection = camera.getForwardDirection(true); // True for full 3D direction

        for (Block block : terrain.getBlocks()) {
            Vector3f blockPos = block.getPosition();
            Vector3f aabbMin = new Vector3f(blockPos.x - 0.5f, blockPos.y - 0.5f, blockPos.z - 0.5f);
            Vector3f aabbMax = new Vector3f(blockPos.x + 0.5f, blockPos.y + 0.5f, blockPos.z + 0.5f);

            nearFarIntersection.set(0,0); // Reset for Intersectionf
            // The intersectRayAab method in JOML expects a mutable Vector2f for nearFar output
            if (Intersectionf.intersectRayAab(rayOrigin, rayDirection, aabbMin, aabbMax, nearFarIntersection) && nearFarIntersection.x < closestDistance) {
                if (nearFarIntersection.x >= 0 && nearFarIntersection.x <= REACH_DISTANCE) { // Check if within reach and in front
                    closestDistance = nearFarIntersection.x;
                    targetedBlock = block;
                }
            }
        }

        if (targetedBlock != null) {
            // Calculate the exact intersection point on the block's surface
            intersectionPoint.set(rayOrigin).add(new Vector3f(rayDirection).mul(closestDistance));
        }

        // Break block (Left Click)
        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT)) {
            if (targetedBlock != null) {
                terrain.removeBlock(targetedBlock);
                // targetedBlock = null; // No need, will be re-evaluated next frame
            }
        }

        // Place block (Right Click)
        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT)) {
            if (targetedBlock != null) { // Must target an existing block to place adjacent
                Vector3f blockCenter = targetedBlock.getPosition();
                Vector3f hitRelativeToCenter = new Vector3f(intersectionPoint).sub(blockCenter);
                Vector3f placementNormal = new Vector3f();
                float maxComponent = 0.0001f; // Small epsilon to avoid issues with zero

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

                if (placementNormal.lengthSquared() > 0.5f) { // Valid normal
                    Vector3f newBlockPosition = new Vector3f(blockCenter).add(placementNormal);

                    // Simple collision check with player's approximate bounding box
                    Vector3f playerHeadPos = camera.getPosition();
                    Vector3f playerFeetPos = new Vector3f(playerHeadPos.x, playerHeadPos.y - 1.0f, playerHeadPos.z); // Approx. feet

                    // Ensure new block is not inside player or already occupied
                    if (newBlockPosition.distanceSquared(playerHeadPos) > 1.0f && // Not in player's head (radius ~0.5f)
                            newBlockPosition.distanceSquared(playerFeetPos) > 1.0f && // Not in player's feet
                            !terrain.isBlockAt(newBlockPosition)) {

                        Vector3f randomColor = new Vector3f(randomGenerator.nextFloat(), randomGenerator.nextFloat(), randomGenerator.nextFloat());
                        terrain.addBlock(new Block(newBlockPosition, randomColor));
                    }
                }
            }
        }
    }

    public Camera getCamera() {
        return camera;
    }
}
