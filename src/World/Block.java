package World;

import org.joml.Vector3f;

public class Block {
    private Vector3f position; // Center position of the block
    private Vector3f color;    // R, G, B color of the block
    private int packedColor;   // New field for packed color

    public Block(float x, float y, float z, Vector3f color) {
        this.position = new Vector3f(x, y, z);
        setColor(color); // Use setter to ensure packedColor is updated
    }

    public Block(Vector3f position, Vector3f color) {
        this.position = new Vector3f(position); // Create a new instance
        setColor(color); // Use setter to ensure packedColor is updated
    }

    private void updatePackedColor() {
        // Pack RGB into an integer: R (8 bits), G (8 bits), B (8 bits)
        // Ensure color components are clamped to 0-255 range
        int r = Math.max(0, Math.min(255, (int)(this.color.x * 255.0f)));
        int g = Math.max(0, Math.min(255, (int)(this.color.y * 255.0f)));
        int b = Math.max(0, Math.min(255, (int)(this.color.z * 255.0f)));
        this.packedColor = (r << 16) | (g << 8) | b;
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getColor() {
        return color;
    }

    public int getPackedColor() { // Getter for the packed color
        return packedColor;
    }

    public void setPosition(Vector3f position) {
        this.position = position;
    }

    public void setColor(Vector3f color) {
        this.color = new Vector3f(color); // Create a new instance
        updatePackedColor(); // Update packed color whenever color changes
    }
}