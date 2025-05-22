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
import java.util.ArrayList; // Added for dynamic lists
import java.util.HashSet; // Added for quick neighbor lookup
import java.util.List;
import java.util.Set; // Added for Set type

public class ChunkMesh {
    private int vaoId;
    private int vboId;
    private int eboId;
    private int indexCount; // Number of indices

    // Standard cube vertices (positions only, relative to block center)
    // Order: Front, Back, Top, Bottom, Right, Left
    private static final float[] CUBE_POSITIONS = {
            // Front face (+Z)
            -0.5f, -0.5f,  0.5f,   0.5f, -0.5f,  0.5f,    0.5f,  0.5f,  0.5f,   -0.5f,  0.5f,  0.5f,
            // Back face (-Z)
            -0.5f, -0.5f, -0.5f,  -0.5f,  0.5f, -0.5f,    0.5f,  0.5f, -0.5f,    0.5f, -0.5f, -0.5f,
            // Top face (+Y)
            -0.5f,  0.5f, -0.5f,  -0.5f,  0.5f,  0.5f,    0.5f,  0.5f,  0.5f,    0.5f,  0.5f, -0.5f,
            // Bottom face (-Y)
            -0.5f, -0.5f, -0.5f,   0.5f, -0.5f, -0.5f,    0.5f, -0.5f,  0.5f,   -0.5f, -0.5f,  0.5f,
            // Right face (+X)
            0.5f, -0.5f, -0.5f,   0.5f,  0.5f, -0.5f,    0.5f,  0.5f,  0.5f,    0.5f, -0.5f,  0.5f,
            // Left face (-X)
            -0.5f, -0.5f, -0.5f,  -0.5f, -0.5f,  0.5f,   -0.5f,  0.5f,  0.5f,   -0.5f,  0.5f, -0.5f,
    };

    private static final float[] CUBE_NORMALS = {
            // Front face (+Z)
            0.0f,  0.0f,  1.0f,   0.0f,  0.0f,  1.0f,   0.0f,  0.0f,  1.0f,   0.0f,  0.0f,  1.0f,
            // Back face (-Z)
            0.0f,  0.0f, -1.0f,   0.0f,  0.0f, -1.0f,   0.0f,  0.0f, -1.0f,   0.0f,  0.0f, -1.0f,
            // Top face (+Y)
            0.0f,  1.0f,  0.0f,   0.0f,  1.0f,  0.0f,   0.0f,  1.0f,  0.0f,   0.0f,  1.0f,  0.0f,
            // Bottom face (-Y)
            0.0f, -1.0f,  0.0f,   0.0f, -1.0f,  0.0f,   0.0f, -1.0f,  0.0f,   0.0f, -1.0f,  0.0f,
            // Right face (+X)
            1.0f,  0.0f,  0.0f,   1.0f,  0.0f,  0.0f,   1.0f,  0.0f,  0.0f,   1.0f,  0.0f,  0.0f,
            // Left face (-X)
            -1.0f,  0.0f,  0.0f,  -1.0f,  0.0f,  0.0f,  -1.0f,  0.0f,  0.0f,  -1.0f,  0.0f,  0.0f,
    };

    // Indices for a single quad (two triangles), relative to the 4 vertices of a face
    private static final int[] QUAD_INDICES = { 0, 1, 2, 0, 2, 3 };

    // Offsets to find neighbor blocks, matching CUBE_POSITIONS face order:
    // Front (+Z), Back (-Z), Top (+Y), Bottom (-Y), Right (+X), Left (-X)
    private static final Vector3f[] FACE_NEIGHBOR_OFFSETS = {
            new Vector3f( 0,  0,  1), // Front face (+Z)
            new Vector3f( 0,  0, -1), // Back face (-Z)
            new Vector3f( 0,  1,  0), // Top face (+Y)
            new Vector3f( 0, -1,  0), // Bottom face (-Y)
            new Vector3f( 1,  0,  0), // Right face (+X)
            new Vector3f(-1,  0,  0)  // Left face (-X)
    };

    private static final int POSITION_COMPONENTS = 3;
    private static final int NORMAL_COMPONENTS = 3;
    private static final int COLOR_COMPONENTS = 3;
    private static final int FLOATS_PER_VERTEX = POSITION_COMPONENTS + NORMAL_COMPONENTS + COLOR_COMPONENTS; // 9

    public ChunkMesh() {
        // VAO, VBO, EBO will be created in buildMesh
    }

    public void buildMesh(List<Block> blocks, Vector3f chunkOrigin) {
        if (isInitialized()) {
            cleanup(); // Clean up old GL objects if rebuilding
        }

        if (blocks.isEmpty()) {
            this.indexCount = 0;
            return;
        }

        List<Float> vertexDataList = new ArrayList<>();
        List<Integer> indexDataList = new ArrayList<>();
        int currentVertexBaseOffset = 0; // Overall offset for indices in the combined mesh

        // Create a quick lookup for block world positions in the current chunk
        Set<Vector3f> blockWorldPositionsInChunk = new HashSet<>();
        for (Block b : blocks) {
            blockWorldPositionsInChunk.add(b.getPosition());
        }

        for (Block block : blocks) {
            Vector3f blockColor = block.getColor();
            Vector3f blockWorldPos = block.getPosition();
            // Position of the block's center relative to the chunk's origin (minCorner)
            Vector3f blockPosRelToChunk = new Vector3f(blockWorldPos).sub(chunkOrigin);

            // Iterate through each of the 6 faces of a cube
            for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
                // Determine the world position of a potential neighbor on this face
                Vector3f neighborWorldPos = new Vector3f(blockWorldPos).add(FACE_NEIGHBOR_OFFSETS[faceIndex]);

                boolean isExposed = !blockWorldPositionsInChunk.contains(neighborWorldPos);

                if (isExposed) {
                    // This face is exposed, add its vertices and indices to the mesh data
                    int vertexStartIndexInCubeData = faceIndex * 4; // Each face has 4 vertices in CUBE_POSITIONS

                    for (int i = 0; i < 4; i++) { // For each of the 4 vertices of this face
                        int cubeVertexArrayIndex = (vertexStartIndexInCubeData + i) * POSITION_COMPONENTS;
                        int cubeNormalArrayIndex = (vertexStartIndexInCubeData + i) * NORMAL_COMPONENTS;

                        // Position (relative to chunk origin)
                        vertexDataList.add(CUBE_POSITIONS[cubeVertexArrayIndex + 0] + blockPosRelToChunk.x);
                        vertexDataList.add(CUBE_POSITIONS[cubeVertexArrayIndex + 1] + blockPosRelToChunk.y);
                        vertexDataList.add(CUBE_POSITIONS[cubeVertexArrayIndex + 2] + blockPosRelToChunk.z);

                        // Normal
                        vertexDataList.add(CUBE_NORMALS[cubeNormalArrayIndex + 0]);
                        vertexDataList.add(CUBE_NORMALS[cubeNormalArrayIndex + 1]);
                        vertexDataList.add(CUBE_NORMALS[cubeNormalArrayIndex + 2]);

                        // Color
                        vertexDataList.add(blockColor.x);
                        vertexDataList.add(blockColor.y);
                        vertexDataList.add(blockColor.z);
                    }

                    // Add indices for this face's quad
                    // These indices are relative to the `currentVertexBaseOffset`
                    for (int quadIndex : QUAD_INDICES) {
                        indexDataList.add(currentVertexBaseOffset + quadIndex);
                    }
                    currentVertexBaseOffset += 4; // We added 4 vertices for this face
                }
            }
        }

        this.indexCount = indexDataList.size();
        if (this.indexCount == 0) {
            // No visible faces, ensure no GL resources are active if they were created then emptied
            if (isInitialized()) cleanup();
            return;
        }

        // Convert ArrayLists to native buffers
        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;

        try {
            verticesBuffer = MemoryUtil.memAllocFloat(vertexDataList.size());
            for (Float val : vertexDataList) verticesBuffer.put(val);
            verticesBuffer.flip();

            indicesBuffer = MemoryUtil.memAllocInt(indexDataList.size());
            for (Integer val : indexDataList) indicesBuffer.put(val);
            indicesBuffer.flip();

            vaoId = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vaoId);

            vboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

            eboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);

            // Vertex attribute pointers
            int stride = FLOATS_PER_VERTEX * Float.BYTES;
            // Position
            GL20.glVertexAttribPointer(0, POSITION_COMPONENTS, GL11.GL_FLOAT, false, stride, 0);
            GL20.glEnableVertexAttribArray(0);
            // Normal
            GL20.glVertexAttribPointer(1, NORMAL_COMPONENTS, GL11.GL_FLOAT, false, stride, (long)POSITION_COMPONENTS * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);
            // Color
            GL20.glVertexAttribPointer(2, COLOR_COMPONENTS, GL11.GL_FLOAT, false, stride, (long)(POSITION_COMPONENTS + NORMAL_COMPONENTS) * Float.BYTES);
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