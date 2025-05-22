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
import java.util.ArrayList;
import java.util.List;

public class ChunkMesh {
    private int vaoId;
    private int vboId;
    private int eboId;
    private int indexCount;

    // Normals for each face (Front, Back, Top, Bottom, Right, Left)
    // Matches the FACE_NEIGHBOR_OFFSETS order
    private static final Vector3f[] FACE_NORMALS = {
            new Vector3f( 0.0f,  0.0f,  1.0f), // Front face (+Z)
            new Vector3f( 0.0f,  0.0f, -1.0f), // Back face (-Z)
            new Vector3f( 0.0f,  1.0f,  0.0f), // Top face (+Y)
            new Vector3f( 0.0f, -1.0f,  0.0f), // Bottom face (-Y)
            new Vector3f( 1.0f,  0.0f,  0.0f), // Right face (+X)
            new Vector3f(-1.0f,  0.0f,  0.0f)  // Left face (-X)
    };

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

    // Define axes for quad expansion for each face type
    // u: primary expansion axis, v: secondary expansion axis
    // Example: For +Z face, u is along X (width), v is along Y (height)
    private static final int[][] FACE_EXPANSION_AXES = {
            // u_axis_idx, v_axis_idx (0=X, 1=Y, 2=Z)
            {0, 1}, // +Z (Front): u along X, v along Y
            {0, 1}, // -Z (Back): u along X, v along Y
            {0, 2}, // +Y (Top): u along X, v along Z
            {0, 2}, // -Y (Bottom): u along X, v along Z
            {1, 2}, // +X (Right): u along Y, v along Z
            {1, 2}  // -X (Left): u along Y, v along Z
    };


    private static final int POSITION_COMPONENTS = 3;
    private static final int NORMAL_COMPONENTS = 3;
    private static final int COLOR_COMPONENTS = 3;
    private static final int FLOATS_PER_VERTEX = POSITION_COMPONENTS + NORMAL_COMPONENTS + COLOR_COMPONENTS; // 9

    public ChunkMesh() {
        // VAO, VBO, EBO will be created in buildMesh
    }

    private Block getBlockAtLocal(int lx, int ly, int lz, Block[][][] localBlocks) {
        if (lx < 0 || lx >= Chunk.CHUNK_SIZE_X ||
                ly < 0 || ly >= Chunk.CHUNK_SIZE_Y ||
                lz < 0 || lz >= Chunk.CHUNK_SIZE_Z) {
            return null; // Out of bounds of this chunk
        }
        return localBlocks[lx][ly][lz];
    }

    private boolean isFaceExposed(int lx, int ly, int lz, int faceIndex,
                                  Block[][][] localBlocks, Vector3f expectedColor) {
        Vector3f offset = FACE_NEIGHBOR_OFFSETS[faceIndex];
        int nlx = lx + (int)offset.x;
        int nly = ly + (int)offset.y;
        int nlz = lz + (int)offset.z;

        if (nlx < 0 || nlx >= Chunk.CHUNK_SIZE_X ||
                nly < 0 || nly >= Chunk.CHUNK_SIZE_Y ||
                nlz < 0 || nlz >= Chunk.CHUNK_SIZE_Z) {
            // Faces at chunk boundaries are considered exposed for intra-chunk meshing.
            // TODO: For inter-chunk meshing, query neighbor chunks here.
            return true;
        }

        Block neighborBlock = localBlocks[nlx][nly][nlz];
        if (neighborBlock == null) {
            return true; // Exposed if neighbor cell is empty
        }
        // Not exposed (culled) if neighbor block has the same color
        return !neighborBlock.getColor().equals(expectedColor);
    }


    public void buildMesh(List<Block> blocks, Vector3f chunkOrigin) {
        if (isInitialized()) {
            cleanup();
        }

        if (blocks.isEmpty()) {
            this.indexCount = 0;
            return;
        }

        List<Float> vertexDataList = new ArrayList<>();
        List<Integer> indexDataList = new ArrayList<>();
        int currentVertexOffset = 0;

        // 1. Populate localBlocks array for quick access
        Block[][][] localBlocks = new Block[Chunk.CHUNK_SIZE_X][Chunk.CHUNK_SIZE_Y][Chunk.CHUNK_SIZE_Z];
        for (Block block : blocks) {
            Vector3f blockWorldPos = block.getPosition();
            // Calculate local coordinates (indices) within the chunk
            int lx = (int) Math.floor(blockWorldPos.x - chunkOrigin.x);
            int ly = (int) Math.floor(blockWorldPos.y - chunkOrigin.y);
            int lz = (int) Math.floor(blockWorldPos.z - chunkOrigin.z);

            if (lx >= 0 && lx < Chunk.CHUNK_SIZE_X &&
                    ly >= 0 && ly < Chunk.CHUNK_SIZE_Y &&
                    lz >= 0 && lz < Chunk.CHUNK_SIZE_Z) {
                localBlocks[lx][ly][lz] = block;
            }
        }

        // 2. Visited array for faces: [x][y][z][faceIndex]
        boolean[][][][] visitedFaces = new boolean[Chunk.CHUNK_SIZE_X][Chunk.CHUNK_SIZE_Y][Chunk.CHUNK_SIZE_Z][6];

        // 3. Iterate through each block cell in the chunk
        for (int lx = 0; lx < Chunk.CHUNK_SIZE_X; lx++) {
            for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                for (int lz = 0; lz < Chunk.CHUNK_SIZE_Z; lz++) {
                    Block currentBlock = localBlocks[lx][ly][lz];
                    if (currentBlock == null) {
                        continue;
                    }
                    Vector3f currentColor = currentBlock.getColor();

                    // 4. Iterate through each of the 6 faces
                    for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
                        if (visitedFaces[lx][ly][lz][faceIndex] ||
                                !isFaceExposed(lx, ly, lz, faceIndex, localBlocks, currentColor)) {
                            continue;
                        }

                        // --- Start Greedy Meshing for this exposed, unvisited face ---
                        int originalLx = lx;
                        int originalLy = ly;
                        int originalLz = lz;

                        Vector3f faceNormal = FACE_NORMALS[faceIndex];
                        int uAxis = FACE_EXPANSION_AXES[faceIndex][0]; // 0=X, 1=Y, 2=Z
                        int vAxis = FACE_EXPANSION_AXES[faceIndex][1];

                        int[] currentPos = {lx, ly, lz};

                        // Width and height of the quad in block units
                        int quadWidth = 1;
                        int quadHeight = 1;

                        // --- Expand in primary direction (u-axis) ---
                        for (int u = 1; u < Chunk.CHUNK_SIZE_X; u++) { // Max possible extent
                            int nextLu = currentPos[uAxis] + u;
                            int checkLx = (uAxis == 0) ? nextLu : originalLx;
                            int checkLy = (uAxis == 1) ? nextLu : originalLy;
                            int checkLz = (uAxis == 2) ? nextLu : originalLz;

                            if (checkLx >= Chunk.CHUNK_SIZE_X || checkLy >= Chunk.CHUNK_SIZE_Y || checkLz >= Chunk.CHUNK_SIZE_Z) break;


                            Block blockToTest = localBlocks[checkLx][checkLy][checkLz];

                            if (blockToTest != null &&
                                    blockToTest.getColor().equals(currentColor) &&
                                    !visitedFaces[checkLx][checkLy][checkLz][faceIndex] &&
                                    isFaceExposed(checkLx, checkLy, checkLz, faceIndex, localBlocks, currentColor)) {
                                quadWidth++;
                            } else {
                                break;
                            }
                        }

                        // --- Expand in secondary direction (v-axis) ---
                        // Try to extend the 1D strip (of quadWidth) along the v-axis
                        outerLoop:
                        for (int v = 1; v < Chunk.CHUNK_SIZE_Y; v++) { // Max possible extent
                            int nextLv = currentPos[vAxis] + v;
                            int checkLxBase = (vAxis == 0) ? nextLv : originalLx;
                            int checkLyBase = (vAxis == 1) ? nextLv : originalLy;
                            int checkLzBase = (vAxis == 2) ? nextLv : originalLz;

                            if (checkLxBase >= Chunk.CHUNK_SIZE_X || checkLyBase >= Chunk.CHUNK_SIZE_Y || checkLzBase >= Chunk.CHUNK_SIZE_Z) break;


                            // Check all blocks in the new row/column being added
                            for (int u_scan = 0; u_scan < quadWidth; u_scan++) {
                                int currentLu = currentPos[uAxis] + u_scan;
                                int checkLx = (uAxis == 0) ? currentLu : checkLxBase;
                                int checkLy = (uAxis == 1) ? currentLu : checkLyBase;
                                int checkLz = (uAxis == 2) ? currentLu : checkLzBase;

                                // Adjust for v-axis scan
                                if (uAxis != 0 && vAxis == 0) checkLx = nextLv; else if (uAxis == 0) checkLx = currentLu;
                                if (uAxis != 1 && vAxis == 1) checkLy = nextLv; else if (uAxis == 1) checkLy = currentLu;
                                if (uAxis != 2 && vAxis == 2) checkLz = nextLv; else if (uAxis == 2) checkLz = currentLu;


                                Block blockToTest = localBlocks[checkLx][checkLy][checkLz];

                                if (blockToTest == null ||
                                        !blockToTest.getColor().equals(currentColor) ||
                                        visitedFaces[checkLx][checkLy][checkLz][faceIndex] ||
                                        !isFaceExposed(checkLx, checkLy, checkLz, faceIndex, localBlocks, currentColor)) {
                                    break outerLoop; // Cannot extend this strip further
                                }
                            }
                            quadHeight++; // Successfully extended the strip by one unit in v-direction
                        }

                        // --- Mark all faces in this quad as visited ---
                        for (int u = 0; u < quadWidth; u++) {
                            for (int v = 0; v < quadHeight; v++) {
                                int visitLx = originalLx, visitLy = originalLy, visitLz = originalLz;
                                if (uAxis == 0) visitLx += u; else if (vAxis == 0) visitLx +=v;
                                if (uAxis == 1) visitLy += u; else if (vAxis == 1) visitLy +=v;
                                if (uAxis == 2) visitLz += u; else if (vAxis == 2) visitLz +=v;
                                visitedFaces[visitLx][visitLy][visitLz][faceIndex] = true;
                            }
                        }

                        // --- Add vertices for the merged quad ---
                        // Coordinates are relative to the chunk's origin (minCorner)
                        float x = originalLx;
                        float y = originalLy;
                        float z = originalLz;

                        // Define the 4 corner vertices of the quad based on faceIndex, quadWidth, quadHeight
                        Vector3f v0 = new Vector3f(), v1 = new Vector3f(), v2 = new Vector3f(), v3 = new Vector3f();

                        switch (faceIndex) {
                            case 0: // +Z (Front) Normal (0,0,1). u=X (width), v=Y (height)
                                v0.set(x,           y,            z + 1);
                                v1.set(x + quadWidth, y,            z + 1);
                                v2.set(x + quadWidth, y + quadHeight, z + 1);
                                v3.set(x,           y + quadHeight, z + 1);
                                break;
                            case 1: // -Z (Back) Normal (0,0,-1). u=X (width), v=Y (height)
                                v0.set(x,           y,            z);
                                v1.set(x,           y + quadHeight, z);
                                v2.set(x + quadWidth, y + quadHeight, z);
                                v3.set(x + quadWidth, y,            z);
                                break;
                            case 2: // +Y (Top) Normal (0,1,0). u=X (width), v=Z (depth)
                                v0.set(x,           y + 1, z);
                                v1.set(x,           y + 1, z + quadHeight); // quadHeight is depth here
                                v2.set(x + quadWidth, y + 1, z + quadHeight);
                                v3.set(x + quadWidth, y + 1, z);
                                break;
                            case 3: // -Y (Bottom) Normal (0,-1,0). u=X (width), v=Z (depth)
                                v0.set(x,           y,     z);
                                v1.set(x + quadWidth, y,     z);
                                v2.set(x + quadWidth, y,     z + quadHeight); // quadHeight is depth here
                                v3.set(x,           y,     z + quadHeight);
                                break;
                            case 4: // +X (Right) Normal (1,0,0). u=Y (height), v=Z (depth)
                                v0.set(x + 1, y,            z);
                                v1.set(x + 1, y + quadWidth,  z); // quadWidth is height here
                                v2.set(x + 1, y + quadWidth,  z + quadHeight); // quadHeight is depth
                                v3.set(x + 1, y,            z + quadHeight);
                                break;
                            case 5: // -X (Left) Normal (-1,0,0). u=Y (height), v=Z (depth)
                                v0.set(x,     y,            z);
                                v1.set(x,     y,            z + quadHeight); // quadHeight is depth
                                v2.set(x,     y + quadWidth,  z + quadHeight); // quadWidth is height
                                v3.set(x,     y + quadWidth,  z);
                                break;
                        }

                        // Add vertices (Position, Normal, Color)
                        Vector3f[] quadVertices = {v0, v1, v2, v3};
                        for (Vector3f vertPos : quadVertices) {
                            vertexDataList.add(vertPos.x);
                            vertexDataList.add(vertPos.y);
                            vertexDataList.add(vertPos.z);
                            vertexDataList.add(faceNormal.x);
                            vertexDataList.add(faceNormal.y);
                            vertexDataList.add(faceNormal.z);
                            vertexDataList.add(currentColor.x);
                            vertexDataList.add(currentColor.y);
                            vertexDataList.add(currentColor.z);
                        }

                        // Add indices (two triangles for the quad)
                        indexDataList.add(currentVertexOffset + 0);
                        indexDataList.add(currentVertexOffset + 1);
                        indexDataList.add(currentVertexOffset + 2);
                        indexDataList.add(currentVertexOffset + 0);
                        indexDataList.add(currentVertexOffset + 2);
                        indexDataList.add(currentVertexOffset + 3);
                        currentVertexOffset += 4;
                    }
                }
            }
        }


        this.indexCount = indexDataList.size();
        if (this.indexCount == 0) {
            if (isInitialized()) cleanup();
            return;
        }

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

            int stride = FLOATS_PER_VERTEX * Float.BYTES;
            GL20.glVertexAttribPointer(0, POSITION_COMPONENTS, GL11.GL_FLOAT, false, stride, 0);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(1, NORMAL_COMPONENTS, GL11.GL_FLOAT, false, stride, (long)POSITION_COMPONENTS * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);
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
        vaoId = 0; vboId = 0; eboId = 0;
        indexCount = 0;
    }

    public boolean isInitialized() {
        return vaoId != 0;
    }
}