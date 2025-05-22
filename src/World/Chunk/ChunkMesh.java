package World.Chunk;

import World.Block;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class ChunkMesh {
    private int vaoId;
    private int vboId;
    private int eboId;
    private int indexCount;

    // Normals (unchanged)
    private static final Vector3f[] FACE_NORMALS = {
            new Vector3f( 0.0f,  0.0f,  1.0f), // Front face (+Z) index 0
            new Vector3f( 0.0f,  0.0f, -1.0f), // Back face (-Z) index 1
            new Vector3f( 0.0f,  1.0f,  0.0f), // Top face (+Y) index 2
            new Vector3f( 0.0f, -1.0f,  0.0f), // Bottom face (-Y) index 3
            new Vector3f( 1.0f,  0.0f,  0.0f), // Right face (+X) index 4
            new Vector3f(-1.0f,  0.0f,  0.0f)  // Left face (-X) index 5
    };
    private static final Vector3f[] FACE_NEIGHBOR_OFFSETS = {
            new Vector3f( 0,  0,  1), new Vector3f( 0,  0, -1),
            new Vector3f( 0,  1,  0), new Vector3f( 0, -1,  0),
            new Vector3f( 1,  0,  0), new Vector3f(-1,  0,  0)
    };
    private static final int[][] FACE_EXPANSION_AXES = {
            {0, 1}, {0, 1}, {0, 2}, {0, 2}, {1, 2}, {1, 2}
    };

    // Bit allocation for first integer (Position + Normal)
    private static final int POS_BITS_X = 6;
    private static final int POS_BITS_Y = 6;
    private static final int POS_BITS_Z = 6;
    private static final int NORMAL_BITS = 3; // 0-5, needs 3 bits
    // Total for first int: 6+6+6+3 = 21 bits

    // Bit allocation for second integer (Color RGB)
    private static final int COLOR_R_BITS = 8;
    private static final int COLOR_G_BITS = 8;
    private static final int COLOR_B_BITS = 8;
    // Total for second int: 8+8+8 = 24 bits

    public ChunkMesh() {}

    private Block getBlockAtLocal(int lx, int ly, int lz, Block[][][] localBlocks) {
        if (lx < 0 || lx >= Chunk.CHUNK_SIZE_X || ly < 0 || ly >= Chunk.CHUNK_SIZE_Y || lz < 0 || lz >= Chunk.CHUNK_SIZE_Z) {
            return null;
        }
        return localBlocks[lx][ly][lz];
    }

    private boolean isFaceExposed(int lx, int ly, int lz, int faceIndex, Block[][][] localBlocks, Vector3f currentBlockColor) {
        Vector3f offset = FACE_NEIGHBOR_OFFSETS[faceIndex];
        int nlx = lx + (int)offset.x;
        int nly = ly + (int)offset.y;
        int nlz = lz + (int)offset.z;

        if (nlx < 0 || nlx >= Chunk.CHUNK_SIZE_X || nly < 0 || nly >= Chunk.CHUNK_SIZE_Y || nlz < 0 || nlz >= Chunk.CHUNK_SIZE_Z) {
            return true;
        }
        Block neighborBlock = localBlocks[nlx][nly][nlz];
        if (neighborBlock == null) {
            return true;
        }
        // Cull if neighbor block has the same color (simplistic, real games use transparency/block type)
        return !neighborBlock.getColor().equals(currentBlockColor);
    }

    public void buildMesh(List<Block> blocks, Vector3f chunkOrigin) {
        if (isInitialized()) {
            cleanup();
        }
        if (blocks.isEmpty()) {
            this.indexCount = 0;
            return;
        }

        List<Integer> vertexDataList = new ArrayList<>(); // Will store pairs of integers
        List<Integer> indexDataList = new ArrayList<>();
        int currentVertexOffset = 0;

        Block[][][] localBlocks = new Block[Chunk.CHUNK_SIZE_X][Chunk.CHUNK_SIZE_Y][Chunk.CHUNK_SIZE_Z];
        for (Block block : blocks) {
            Vector3f blockWorldPos = block.getPosition();
            int lx = (int) Math.floor(blockWorldPos.x - chunkOrigin.x);
            int ly = (int) Math.floor(blockWorldPos.y - chunkOrigin.y);
            int lz = (int) Math.floor(blockWorldPos.z - chunkOrigin.z);
            if (lx >= 0 && lx < Chunk.CHUNK_SIZE_X && ly >= 0 && ly < Chunk.CHUNK_SIZE_Y && lz >= 0 && lz < Chunk.CHUNK_SIZE_Z) {
                localBlocks[lx][ly][lz] = block;
            }
        }

        boolean[][][][] visitedFaces = new boolean[Chunk.CHUNK_SIZE_X][Chunk.CHUNK_SIZE_Y][Chunk.CHUNK_SIZE_Z][6];

        for (int lx = 0; lx < Chunk.CHUNK_SIZE_X; lx++) {
            for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                for (int lz = 0; lz < Chunk.CHUNK_SIZE_Z; lz++) {
                    Block currentBlock = localBlocks[lx][ly][lz];
                    if (currentBlock == null) continue;

                    Vector3f currentColorVec = currentBlock.getColor();

                    for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
                        if (visitedFaces[lx][ly][lz][faceIndex] || !isFaceExposed(lx, ly, lz, faceIndex, localBlocks, currentColorVec)) {
                            continue;
                        }

                        int originalLx = lx, originalLy = ly, originalLz = lz;
                        int uAxis = FACE_EXPANSION_AXES[faceIndex][0], vAxis = FACE_EXPANSION_AXES[faceIndex][1];
                        int quadWidth = 1, quadHeight = 1;

                        // Greedy meshing: Expand width (u-axis)
                        for (int u = 1; u < Math.max(Chunk.CHUNK_SIZE_X, Math.max(Chunk.CHUNK_SIZE_Y, Chunk.CHUNK_SIZE_Z)); u++) {
                            int testLx = originalLx, testLy = originalLy, testLz = originalLz;
                            if (uAxis == 0) testLx += u; else if (uAxis == 1) testLy += u; else testLz += u;
                            if (testLx >= Chunk.CHUNK_SIZE_X || testLy >= Chunk.CHUNK_SIZE_Y || testLz >= Chunk.CHUNK_SIZE_Z) break;
                            Block blockToTest = localBlocks[testLx][testLy][testLz];
                            if (blockToTest != null && blockToTest.getColor().equals(currentColorVec) &&
                                    !visitedFaces[testLx][testLy][testLz][faceIndex] &&
                                    isFaceExposed(testLx, testLy, testLz, faceIndex, localBlocks, currentColorVec)) {
                                quadWidth++;
                            } else break;
                        }

                        // Greedy meshing: Expand height (v-axis)
                        outerLoop:
                        for (int v = 1; v < Math.max(Chunk.CHUNK_SIZE_X, Math.max(Chunk.CHUNK_SIZE_Y, Chunk.CHUNK_SIZE_Z)); v++) {
                            for (int u_scan = 0; u_scan < quadWidth; u_scan++) {
                                int testLx = originalLx, testLy = originalLy, testLz = originalLz;
                                if (uAxis == 0) testLx += u_scan; else if (vAxis == 0) testLx += v; // Apply u_scan to uAxis, v to vAxis
                                if (uAxis == 1) testLy += u_scan; else if (vAxis == 1) testLy += v;
                                if (uAxis == 2) testLz += u_scan; else if (vAxis == 2) testLz += v;

                                // This part needs to be careful to construct the correct test coordinates based on u/v and axes
                                // Simplified logic for setting coordinates for scanning row/column:
                                int currentScanLx = originalLx, currentScanLy = originalLy, currentScanLz = originalLz;
                                if(uAxis == 0) currentScanLx += u_scan; else if(uAxis == 1) currentScanLy += u_scan; else currentScanLz += u_scan;
                                if(vAxis == 0) currentScanLx += v;    else if(vAxis == 1) currentScanLy += v;    else currentScanLz += v;


                                if (currentScanLx >= Chunk.CHUNK_SIZE_X || currentScanLy >= Chunk.CHUNK_SIZE_Y || currentScanLz >= Chunk.CHUNK_SIZE_Z) break outerLoop;
                                Block blockToTest = localBlocks[currentScanLx][currentScanLy][currentScanLz];
                                if (blockToTest == null || !blockToTest.getColor().equals(currentColorVec) ||
                                        visitedFaces[currentScanLx][currentScanLy][currentScanLz][faceIndex] ||
                                        !isFaceExposed(currentScanLx, currentScanLy, currentScanLz, faceIndex, localBlocks, currentColorVec)) {
                                    break outerLoop;
                                }
                            }
                            quadHeight++;
                        }

                        for (int u = 0; u < quadWidth; u++) {
                            for (int v = 0; v < quadHeight; v++) {
                                int visitLx = originalLx, visitLy = originalLy, visitLz = originalLz;
                                if (uAxis == 0) visitLx += u; else if (vAxis == 0) visitLx +=v;
                                if (uAxis == 1) visitLy += u; else if (vAxis == 1) visitLy +=v;
                                if (uAxis == 2) visitLz += u; else if (vAxis == 2) visitLz +=v;
                                visitedFaces[visitLx][visitLy][visitLz][faceIndex] = true;
                            }
                        }

                        float cX = originalLx, cY = originalLy, cZ = originalLz; // Base corner for the quad
                        Vector3f v0=new Vector3f(), v1=new Vector3f(), v2=new Vector3f(), v3=new Vector3f();
                        switch (faceIndex) { // Define quad vertices (local to chunk)
                            case 0: v0.set(cX,cY,cZ+1); v1.set(cX+quadWidth,cY,cZ+1); v2.set(cX+quadWidth,cY+quadHeight,cZ+1); v3.set(cX,cY+quadHeight,cZ+1); break;
                            case 1: v0.set(cX,cY,cZ); v1.set(cX,cY+quadHeight,cZ); v2.set(cX+quadWidth,cY+quadHeight,cZ); v3.set(cX+quadWidth,cY,cZ); break;
                            case 2: v0.set(cX,cY+1,cZ); v1.set(cX,cY+1,cZ+quadHeight); v2.set(cX+quadWidth,cY+1,cZ+quadHeight); v3.set(cX+quadWidth,cY+1,cZ); break;
                            case 3: v0.set(cX,cY,cZ); v1.set(cX+quadWidth,cY,cZ); v2.set(cX+quadWidth,cY,cZ+quadHeight); v3.set(cX,cY,cZ+quadHeight); break;
                            case 4: v0.set(cX+1,cY,cZ); v1.set(cX+1,cY+quadWidth,cZ); v2.set(cX+1,cY+quadWidth,cZ+quadHeight); v3.set(cX+1,cY,cZ+quadHeight); break;
                            case 5: v0.set(cX,cY,cZ); v1.set(cX,cY,cZ+quadHeight); v2.set(cX,cY+quadWidth,cZ+quadHeight); v3.set(cX,cY+quadWidth,cZ); break;
                        }

                        Vector3f[] quadVertices = {v0, v1, v2, v3};
                        for (Vector3f vertPos : quadVertices) {
                            int packedPosNormal = 0;
                            int pX = (int)vertPos.x & ((1 << POS_BITS_X) - 1);
                            int pY = (int)vertPos.y & ((1 << POS_BITS_Y) - 1);
                            int pZ = (int)vertPos.z & ((1 << POS_BITS_Z) - 1);
                            int normIndex = faceIndex;
                            packedPosNormal |= pX;
                            packedPosNormal |= (pY << POS_BITS_X);
                            packedPosNormal |= (pZ << (POS_BITS_X + POS_BITS_Y));
                            packedPosNormal |= (normIndex << (POS_BITS_X + POS_BITS_Y + POS_BITS_Z));
                            vertexDataList.add(packedPosNormal);

                            int packedColor = 0;
                            int r = (int)(currentColorVec.x * 255.0f) & ((1 << COLOR_R_BITS) -1);
                            int g = (int)(currentColorVec.y * 255.0f) & ((1 << COLOR_G_BITS) -1);
                            int b = (int)(currentColorVec.z * 255.0f) & ((1 << COLOR_B_BITS) -1);
                            packedColor |= r;
                            packedColor |= (g << COLOR_R_BITS);
                            packedColor |= (b << (COLOR_R_BITS + COLOR_G_BITS));
                            vertexDataList.add(packedColor);
                        }
                        indexDataList.add(currentVertexOffset + 0); indexDataList.add(currentVertexOffset + 1); indexDataList.add(currentVertexOffset + 2);
                        indexDataList.add(currentVertexOffset + 0); indexDataList.add(currentVertexOffset + 2); indexDataList.add(currentVertexOffset + 3);
                        currentVertexOffset += 4;
                    }
                }
            }
        }

        this.indexCount = indexDataList.size();
        if (this.indexCount == 0) { if (isInitialized()) cleanup(); return; }

        IntBuffer verticesBuffer = null, indicesBuffer = null;
        try {
            verticesBuffer = MemoryUtil.memAllocInt(vertexDataList.size());
            for (Integer val : vertexDataList) verticesBuffer.put(val); // Each vertex now 2 ints
            verticesBuffer.flip();

            indicesBuffer = MemoryUtil.memAllocInt(indexDataList.size());
            for (Integer val : indexDataList) indicesBuffer.put(val);
            indicesBuffer.flip();

            vaoId = GL30.glGenVertexArrays(); GL30.glBindVertexArray(vaoId);
            vboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);
            eboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);

            int stride = 2 * Integer.BYTES; // Each vertex consists of two integers

            // Attribute 0: packedPositionNormal
            GL30.glVertexAttribIPointer(0, 1, GL11.GL_INT, stride, 0);
            GL30.glEnableVertexAttribArray(0);

            // Attribute 1: packedColor
            GL30.glVertexAttribIPointer(1, 1, GL11.GL_INT, stride, Integer.BYTES); // Offset by 1 int
            GL30.glEnableVertexAttribArray(1);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
        } finally {
            if (verticesBuffer != null) MemoryUtil.memFree(verticesBuffer);
            if (indicesBuffer != null) MemoryUtil.memFree(indicesBuffer);
        }
    }

    public void render() { /* Unchanged */ if (indexCount == 0 || vaoId == 0) return; GL30.glBindVertexArray(vaoId); GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0); GL30.glBindVertexArray(0); }
    public void cleanup() { /* Unchanged */ if (vaoId != 0) GL30.glDeleteVertexArrays(vaoId); if (vboId != 0) GL15.glDeleteBuffers(vboId); if (eboId != 0) GL15.glDeleteBuffers(eboId); vaoId = 0; vboId = 0; eboId = 0; indexCount = 0; }
    public boolean isInitialized() { /* Unchanged */ return vaoId != 0; }
}