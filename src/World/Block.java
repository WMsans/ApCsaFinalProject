package World;

import org.joml.Vector3f;

public class Block {
    private Vector3f position; // Center position of the block
    private Vector3f color;    // R, G, B color of the block

    public Block(float x, float y, float z, Vector3f color) {
        this.position = new Vector3f(x, y, z);
        this.color = color;
    }

    public Block(Vector3f position, Vector3f color) {
        this.position = new Vector3f(position); // Create a new instance
        this.color = new Vector3f(color);       // Create a new instance
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getColor() {
        return color;
    }

    public void setPosition(Vector3f position) {
        this.position = position;
    }

    public void setColor(Vector3f color) {
        this.color = color;
    }
}
