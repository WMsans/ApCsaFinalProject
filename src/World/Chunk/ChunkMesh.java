package World.Chunk;

import World.Block;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List; // Keep for the input parameter

public class ChunkMesh {
    private int vaoId;
    private int vboId;
    private int eboId;
    private int indexCount; // Number of indices

    // Standard cube vertices (positions only, relative to block center)
    private static final float[] CUBE_POSITIONS = {
            // Front face
            -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,   0.5f,  0.5f,  0.5f,  -0.5f,  0.5f,  0.5f,
            // Back face
            -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,   0.5f,  0.5f, -0.5f,   0.5f, -0.5f, -0.5f,
            // Top face
            -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f,   0.5f,  0.5f,  0.5f,   0.5f,  0.5f, -0.5f,
            // Bottom face
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,   0.5f, -0.5f,  0.5f,  -0.5f, -0.5f,  0.5f,
            // Right face
            0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,   0.5f,  0.5f,  0.5f,   0.5f, -0.5f,  0.5f,
            // Left face
            -0.5f, -0.5f, -0.5f, -0.5f, -0.5f,  0.5f,  -0.5f,  0.5f,  0.5f,  -0.5f,  0.5f, -0.5f,
    };

    private static final float[] CUBE_NORMALS = {
            // Front face
            0.0f,  0.0f,  1.0f,   0.0f,  0.0f,  1.0f,   0.0f,  0.0f,  1.0f,   0.0f,  0.0f,  1.0f,
            // Back face
            0.0f,  0.0f, -1.0f,   0.0f,  0.0f, -1.0f,   0.0f,  0.0f, -1.0f,   0.0f,  0.0f, -1.0f,
            // Top face
            0.0f,  1.0f,  0.0f,   0.0f,  1.0f,  0.0f,   0.0f,  1.0f,  0.0f,   0.0f,  1.0f,  0.0f,
            // Bottom face
            0.0f, -1.0f,  0.0f,   0.0f, -1.0f,  0.0f,   0.0f, -1.0f,  0.0f,   0.0f, -1.0f,  0.0f,
            // Right face
            1.0f,  0.0f,  0.0f,   1.0f,  0.0f,  0.0f,   1.0f,  0.0f,  0.0f,   1.0f,  0.0f,  0.0f,
            // Left face
            -1.0f,  0.0f,  0.0f,  -1.0f,  0.0f,  0.0f,  -1.0f,  0.0f,  0.0f,  -1.0f,  0.0f,  0.0f,
    };

    private static final int[] CUBE_INDICES = {
            0,  1,  2,    0,  2,  3, // Front
            4,  5,  6,    4,  6,  7, // Back
            8,  9, 10,    8, 10, 11, // Top
            12, 13, 14,   12, 14, 15, // Bottom
            16, 17, 18,   16, 18, 19, // Right
            20, 21, 22,   20, 22, 23  // Left
    };

    private static final int VERTICES_PER_CUBE = 24; // 6 faces * 4 vertices
    private static final int INDICES_PER_CUBE = 36;  // 6 faces * 2 triangles * 3 indices
    private static final int POSITION_COMPONENTS = 3;
    private static final int NORMAL_COMPONENTS = 3;
    private static final int COLOR_COMPONENTS = 3;
    private static final int FLOATS_PER_VERTEX = POSITION_COMPONENTS + NORMAL_COMPONENTS + COLOR_COMPONENTS; // 9

    public ChunkMesh() {
        // VAO, VBO, EBO will be created in buildMesh
    }

    public void buildMesh(List<Block> blocks, Vector3f chunkOrigin) {
        if (blocks.isEmpty()) {
            this.indexCount = 0;
            // Ensure any existing GL resources are cleaned up if we're "building" an empty mesh
            if (isInitialized()) {
                cleanup();
            }
            return;
        }

        int numBlocks = blocks.size();
        int totalFloats = numBlocks * VERTICES_PER_CUBE * FLOATS_PER_VERTEX;
        int totalIndices = numBlocks * INDICES_PER_CUBE;

        float[] vertexData = new float[totalFloats];
        int[] indexData = new int[totalIndices];

        int floatPtr = 0;  // Pointer for vertexData array
        int indexPtr = 0;  // Pointer for indexData array
        int vertexOffset = 0; // Base for adjusting CUBE_INDICES for the current block

        for (Block block : blocks) {
            Vector3f blockColor = block.getColor();
            Vector3f blockPosRelToChunk = new Vector3f(block.getPosition()).sub(chunkOrigin);

            // Add vertex data for this block
            for (int i = 0; i < VERTICES_PER_CUBE; ++i) {
                // Position (relative to chunk origin)
                vertexData[floatPtr++] = CUBE_POSITIONS[i * POSITION_COMPONENTS + 0] + blockPosRelToChunk.x;
                vertexData[floatPtr++] = CUBE_POSITIONS[i * POSITION_COMPONENTS + 1] + blockPosRelToChunk.y;
                vertexData[floatPtr++] = CUBE_POSITIONS[i * POSITION_COMPONENTS + 2] + blockPosRelToChunk.z;

                // Normal
                vertexData[floatPtr++] = CUBE_NORMALS[i * NORMAL_COMPONENTS + 0];
                vertexData[floatPtr++] = CUBE_NORMALS[i * NORMAL_COMPONENTS + 1];
                vertexData[floatPtr++] = CUBE_NORMALS[i * NORMAL_COMPONENTS + 2];

                // Color
                vertexData[floatPtr++] = blockColor.x;
                vertexData[floatPtr++] = blockColor.y;
                vertexData[floatPtr++] = blockColor.z;
            }

            // Add index data for this block
            for (int k = 0; k < INDICES_PER_CUBE; ++k) {
                indexData[indexPtr++] = CUBE_INDICES[k] + vertexOffset;
            }
            vertexOffset += VERTICES_PER_CUBE;
        }

        this.indexCount = totalIndices;
        if (this.indexCount == 0) { // Should not happen if blocks list is not empty, but good check
            if (isInitialized()) cleanup();
            return;
        }

        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;

        try {
            // If rebuilding, clean up old GL objects first
            if (isInitialized()) {
                cleanup();
            }

            vaoId = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vaoId);

            vboId = GL15.glGenBuffers();
            verticesBuffer = MemoryUtil.memAllocFloat(totalFloats);
            verticesBuffer.put(vertexData).flip(); // Put the entire array
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

            eboId = GL15.glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(totalIndices);
            indicesBuffer.put(indexData).flip(); // Put the entire array
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);

            // Vertex attribute pointers
            int stride = FLOATS_PER_VERTEX * Float.BYTES;
            // Position
            GL20.glVertexAttribPointer(0, POSITION_COMPONENTS, GL11.GL_FLOAT, false, stride, 0);
            GL20.glEnableVertexAttribArray(0);
            // Normal
            GL20.glVertexAttribPointer(1, NORMAL_COMPONENTS, GL11.GL_FLOAT, false, stride, POSITION_COMPONENTS * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);
            // Color
            GL20.glVertexAttribPointer(2, COLOR_COMPONENTS, GL11.GL_FLOAT, false, stride, (POSITION_COMPONENTS + NORMAL_COMPONENTS) * Float.BYTES);
            GL20.glEnableVertexAttribArray(2);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);

        } finally {
            if (verticesBuffer != null) MemoryUtil.memFree(verticesBuffer);
            if (indicesBuffer != null) MemoryUtil.memFree(indicesBuffer);
        }
    }

    public void render() {
        if (indexCount == 0 || vaoId == 0) return;

        GL30.glBindVertexArray(vaoId);
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(0);
    }

    public void cleanup() {
        if (vaoId != 0) GL30.glDeleteVertexArrays(vaoId);
        if (vboId != 0) GL15.glDeleteBuffers(vboId);
        if (eboId != 0) GL15.glDeleteBuffers(eboId);
        vaoId = 0;
        vboId = 0;
        eboId = 0;
        indexCount = 0;
    }

    public boolean isInitialized() {
        return vaoId != 0;
    }
}