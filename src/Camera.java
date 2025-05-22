import Graphics.Frustum;
import Physics.CustomAABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private float pitch;
    private float yaw;

    private Window window;
    private Frustum frustum; // New field

    public Camera(Vector3f initialPosition, Window window) {
        this.position = new Vector3f(initialPosition);
        this.pitch = 0.0f;
        this.yaw = -90.0f; // Initialize yaw to look along negative Z
        this.window = window;
        this.frustum = new Frustum(); // Initialize frustum
    }

    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void rotatePitch(float deltaPitch) {
        this.pitch += deltaPitch;
        if (this.pitch > 89.0f) this.pitch = 89.0f;
        if (this.pitch < -89.0f) this.pitch = -89.0f;
    }

    public Vector3f getForwardDirection(boolean full3D) {
        Vector3f forward = new Vector3f();
        forward.x = (float) (Math.cos(Math.toRadians(this.yaw)) * Math.cos(Math.toRadians(this.pitch)));
        if (full3D) {
            forward.y = (float) Math.sin(Math.toRadians(this.pitch));
        } else {
            forward.y = 0; // XZ plane only
        }
        forward.z = (float) (Math.sin(Math.toRadians(this.yaw)) * Math.cos(Math.toRadians(this.pitch)));
        return forward.normalize();
    }

    /**
     * Gets the camera's right direction vector.
     * For flying, full3D should be true.
     * Made more robust for cases where the camera looks straight up or down.
     * @param full3D If true, returns the full 3D right vector. If false, returns XZ plane right vector.
     * @return The right direction vector.
     */
    public Vector3f getRightDirection(boolean full3D) {
        Vector3f forward = getForwardDirection(true); // Always use the true 3D forward for calculation base
        Vector3f rightDir = new Vector3f();
        Vector3f worldUp = new Vector3f(0, 1, 0);

        // Check if looking nearly straight up or down
        if (Math.abs(forward.y) > 0.999f) {
            // If looking straight up/down, strafing should be based on yaw, along the XZ plane
            // Rotate worldUp around Y by yaw to get an appropriate "right" for this case
            // However, simpler is to calculate right based on yaw directly in XZ plane
            rightDir.x = (float)Math.cos(Math.toRadians(yaw + 90.0f)); // Corrected: yaw is angle from +X towards +Z
            rightDir.y = 0; // Keep strafe horizontal to world in this edge case
            rightDir.z = (float)Math.sin(Math.toRadians(yaw + 90.0f));
        } else {
            // Standard cross product for other orientations
            forward.cross(worldUp, rightDir);
        }

        if (!full3D) {
            rightDir.y = 0; // Force horizontal if not full 3D requested
        }
        return rightDir.normalize();
    }


    public Matrix4f getViewMatrix() {
        Vector3f direction = getForwardDirection(true);
        Vector3f up = new Vector3f(0, 1, 0); // World up
        // If looking straight up/down, the standard cross product for 'right' can become unstable.
        // To derive a stable 'up' vector for the lookAt matrix:
        // Calculate the actual 'right' vector.
        // Then cross 'right' with 'forward' to get the camera's 'up'.
        Vector3f actualRight = getRightDirection(true); // Use the robust 3D right
        Vector3f cameraUp = new Vector3f(actualRight).cross(direction).normalize(); // camera's local up

        Vector3f lookAtTarget = new Vector3f(position).add(direction);
        return new Matrix4f().lookAt(position, lookAtTarget, cameraUp);
    }

    public Matrix4f getProjectionMatrix() {
        float fov = (float) Math.toRadians(45.0f); // Field of View
        float aspectRatio = window.getAspectRatio();
        float zNear = 0.1f;
        float zFar = 200.0f; // Consider making zFar configurable or larger for large render distances
        return new Matrix4f().perspective(fov, aspectRatio, zNear, zFar);
    }

    public void updateFrustum() {
        Matrix4f viewProjMatrix = new Matrix4f(getProjectionMatrix()).mul(getViewMatrix());
        frustum.update(viewProjMatrix);
    }

    public boolean isAABBInFrustum(CustomAABB aabb) {
        return frustum.isAABBInside(aabb);
    }

    public boolean isPointInFrustum(Vector3f point) {
        return frustum.isPointInside(point);
    }

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public float getPitch() { return pitch; }
    public float getYaw() { return yaw; }
}