package Graphics;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class EntityModel {
    private float[] vertices; // x, y, z, r, g, b
    private int[] indices;

    private int vaoId;
    private int vboId;
    private int eboId;
    private int indexCount;

    public EntityModel(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
        this.indexCount = indices.length;
    }

    public float[] getVertices() {
        return vertices;
    }

    public int[] getIndices() {
        return indices;
    }

    public int getVaoId() {
        return vaoId;
    }

    public void setVaoId(int vaoId) {
        this.vaoId = vaoId;
    }

    public int getVboId() {
        return vboId;
    }

    public void setVboId(int vboId) {
        this.vboId = vboId;
    }

    public int getEboId() {
        return eboId;
    }

    public void setEboId(int eboId) {
        this.eboId = eboId;
    }

    public int getIndexCount() {
        return indexCount;
    }

    public void cleanup() {
        if (vaoId != 0) {
            GL30.glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        if (vboId != 0) {
            GL15.glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (eboId != 0) {
            GL15.glDeleteBuffers(eboId);
            eboId = 0;
        }
        indexCount = 0;
    }

    public static EntityModel createCubeModel(float size, Vector3f color) {
        float halfSize = size / 2.0f;
        float[] vertices = {
                // Front face
                -halfSize, -halfSize,  halfSize, color.x, color.y, color.z,
                halfSize, -halfSize,  halfSize, color.x, color.y, color.z,
                halfSize,  halfSize,  halfSize, color.x, color.y, color.z,
                -halfSize,  halfSize,  halfSize, color.x, color.y, color.z,
                // Back face
                -halfSize, -halfSize, -halfSize, color.x, color.y, color.z,
                -halfSize,  halfSize, -halfSize, color.x, color.y, color.z,
                halfSize,  halfSize, -halfSize, color.x, color.y, color.z,
                halfSize, -halfSize, -halfSize, color.x, color.y, color.z,
                // Top face
                -halfSize,  halfSize, -halfSize, color.x, color.y, color.z,
                -halfSize,  halfSize,  halfSize, color.x, color.y, color.z,
                halfSize,  halfSize,  halfSize, color.x, color.y, color.z,
                halfSize,  halfSize, -halfSize, color.x, color.y, color.z,
                // Bottom face
                -halfSize, -halfSize, -halfSize, color.x, color.y, color.z,
                halfSize, -halfSize, -halfSize, color.x, color.y, color.z,
                halfSize, -halfSize,  halfSize, color.x, color.y, color.z,
                -halfSize, -halfSize,  halfSize, color.x, color.y, color.z,
                // Right face
                halfSize, -halfSize, -halfSize, color.x, color.y, color.z,
                halfSize,  halfSize, -halfSize, color.x, color.y, color.z,
                halfSize,  halfSize,  halfSize, color.x, color.y, color.z,
                halfSize, -halfSize,  halfSize, color.x, color.y, color.z,
                // Left face
                -halfSize, -halfSize, -halfSize, color.x, color.y, color.z,
                -halfSize, -halfSize,  halfSize, color.x, color.y, color.z,
                -halfSize,  halfSize,  halfSize, color.x, color.y, color.z,
                -halfSize,  halfSize, -halfSize, color.x, color.y, color.z
        };

        int[] indices = {
                0, 1, 2, 0, 2, 3,       // Front
                4, 5, 6, 4, 6, 7,       // Back
                8, 9, 10, 8, 10, 11,    // Top
                12, 13, 14, 12, 14, 15, // Bottom
                16, 17, 18, 16, 18, 19, // Right
                20, 21, 22, 20, 22, 23  // Left
        };
        return new EntityModel(vertices, indices);
    }
    public static EntityModel createQuadModel(float size, Vector3f color) {
        float halfSize = size / 2.0f;
        // Vertices for a quad in the XY plane, centered at origin
        // Will be rotated by the model matrix to face the camera
        float[] vertices = {
                // Position (x,y,z), Color (r,g,b)
                -halfSize, -halfSize, 0.0f, color.x, color.y, color.z, // Bottom-left
                halfSize, -halfSize, 0.0f, color.x, color.y, color.z, // Bottom-right
                halfSize,  halfSize, 0.0f, color.x, color.y, color.z, // Top-right
                -halfSize,  halfSize, 0.0f, color.x, color.y, color.z  // Top-left
        };

        int[] indices = {
                0, 1, 2, // First triangle
                2, 3, 0  // Second triangle
        };
        return new EntityModel(vertices, indices);
    }
    public static EntityModel createCuboidModel(float width, float height, float depth, Vector3f color) {
        float halfWidth = width / 2.0f;
        float halfHeight = height / 2.0f;
        float halfDepth = depth / 2.0f;

        float[] vertices = {
                // Front face
                -halfWidth, -halfHeight,  halfDepth, color.x, color.y, color.z,
                halfWidth, -halfHeight,  halfDepth, color.x, color.y, color.z,
                halfWidth,  halfHeight,  halfDepth, color.x, color.y, color.z,
                -halfWidth,  halfHeight,  halfDepth, color.x, color.y, color.z,
                // Back face
                -halfWidth, -halfHeight, -halfDepth, color.x, color.y, color.z,
                -halfWidth,  halfHeight, -halfDepth, color.x, color.y, color.z,
                halfWidth,  halfHeight, -halfDepth, color.x, color.y, color.z,
                halfWidth, -halfHeight, -halfDepth, color.x, color.y, color.z,
                // Top face
                -halfWidth,  halfHeight, -halfDepth, color.x, color.y, color.z,
                -halfWidth,  halfHeight,  halfDepth, color.x, color.y, color.z,
                halfWidth,  halfHeight,  halfDepth, color.x, color.y, color.z,
                halfWidth,  halfHeight, -halfDepth, color.x, color.y, color.z,
                // Bottom face
                -halfWidth, -halfHeight, -halfDepth, color.x, color.y, color.z,
                halfWidth, -halfHeight, -halfDepth, color.x, color.y, color.z,
                halfWidth, -halfHeight,  halfDepth, color.x, color.y, color.z,
                -halfWidth, -halfHeight,  halfDepth, color.x, color.y, color.z,
                // Right face
                halfWidth, -halfHeight, -halfDepth, color.x, color.y, color.z,
                halfWidth,  halfHeight, -halfDepth, color.x, color.y, color.z,
                halfWidth,  halfHeight,  halfDepth, color.x, color.y, color.z,
                halfWidth, -halfHeight,  halfDepth, color.x, color.y, color.z,
                // Left face
                -halfWidth, -halfHeight, -halfDepth, color.x, color.y, color.z,
                -halfWidth, -halfHeight,  halfDepth, color.x, color.y, color.z,
                -halfWidth,  halfHeight,  halfDepth, color.x, color.y, color.z,
                -halfWidth,  halfHeight, -halfDepth, color.x, color.y, color.z
        };

        int[] indices = {
                0, 1, 2, 0, 2, 3,       // Front
                4, 5, 6, 4, 6, 7,       // Back
                8, 9, 10, 8, 10, 11,    // Top
                12, 13, 14, 12, 14, 15, // Bottom
                16, 17, 18, 16, 18, 19, // Right
                20, 21, 22, 20, 22, 23  // Left
        };
        return new EntityModel(vertices, indices);
    }
}