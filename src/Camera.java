import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position; // This will be set by PlayerEntity to its eye position
    private float pitch;       // Up/down look, controlled by PlayerEntity's mouse input
    private float yaw;         // Left/right look, controlled by PlayerEntity's mouse input (and synced with PlayerEntity's yaw)

    private Window window; // For aspect ratio

    public Camera(Vector3f initialPosition, Window window) {
        this.position = new Vector3f(initialPosition);
        this.pitch = 0.0f;
        this.yaw = -90.0f; // Default orientation
        this.window = window;
    }

    /**
     * Directly sets the camera's position.
     * Called by PlayerEntity to sync with its eye level.
     */
    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    /**
     * Directly sets the camera's yaw.
     * Called by PlayerEntity to sync with its body's yaw.
     */
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    /**
     * Rotates only the camera's pitch. Yaw is handled by PlayerEntity.
     * @param deltaPitch Change in pitch.
     */
    public void rotatePitch(float deltaPitch) {
        this.pitch += deltaPitch;
        // Constrain pitch
        if (this.pitch > 89.0f) this.pitch = 89.0f;
        if (this.pitch < -89.0f) this.pitch = -89.0f;
    }


    /**
     * Gets the camera's forward direction vector based on its current pitch and yaw.
     * @param full3D If true, returns the full 3D forward vector. If false, returns the XZ plane forward vector (y=0).
     * @return The forward direction vector.
     */
    public Vector3f getForwardDirection(boolean full3D) {
        Vector3f forward = new Vector3f();
        // Use camera's pitch and yaw for its direct line of sight
        forward.x = (float) (Math.cos(Math.toRadians(this.yaw)) * Math.cos(Math.toRadians(this.pitch)));
        if (full3D) {
            forward.y = (float) Math.sin(Math.toRadians(this.pitch));
        } else {
            forward.y = 0;
        }
        forward.z = (float) (Math.sin(Math.toRadians(this.yaw)) * Math.cos(Math.toRadians(this.pitch)));
        return forward.normalize();
    }

    // getRightDirection can remain similar, using camera's yaw and pitch.
    public Vector3f getRightDirection(boolean full3D) {
        Vector3f forward = getForwardDirection(full3D);
        Vector3f worldUp = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f();
        forward.cross(worldUp, right);
        if (!full3D) {
            right.y = 0;
        }
        return right.normalize();
    }


    public Matrix4f getViewMatrix() {
        Vector3f direction = getForwardDirection(true);
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f lookAtTarget = new Vector3f(position).add(direction);
        return new Matrix4f().lookAt(position, lookAtTarget, up);
    }

    public Matrix4f getProjectionMatrix() {
        float fov = (float) Math.toRadians(45.0f);
        float aspectRatio = window.getAspectRatio();
        float zNear = 0.1f;
        float zFar = 200.0f; // Increased far plane
        return new Matrix4f().perspective(fov, aspectRatio, zNear, zFar);
    }

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public float getPitch() { return pitch; }
    public float getYaw() { return yaw; }
}
