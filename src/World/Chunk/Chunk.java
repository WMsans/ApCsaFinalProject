package World.Chunk;

import Physics.CustomAABB; // New import
import org.joml.Vector3f;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList; // Keep for thread-safe block modification
import World.Block;


public class Chunk {
    private final ChunkId id;
    private final List<Block> blocks;
    private final Vector3f minCorner;
    private final Vector3f maxCorner;
    private final CustomAABB boundingBox; // New AABB for the chunk

    private ChunkMesh chunkMesh; // New field for batched mesh
    private boolean needsMeshRebuild = true; // Flag to rebuild mesh

    public static int CHUNK_SIZE_X = 16;
    public static int CHUNK_SIZE_Y = 16;
    public static int CHUNK_SIZE_Z = 16;


    public Chunk(ChunkId id) {
        this.id = id;
        this.blocks = new CopyOnWriteArrayList<>();

        this.minCorner = new Vector3f(
                (float)id.x * CHUNK_SIZE_X,
                (float)id.y * CHUNK_SIZE_Y,
                (float)id.z * CHUNK_SIZE_Z
        );
        this.maxCorner = new Vector3f(
                (float)(id.x + 1) * CHUNK_SIZE_X,
                (float)(id.y + 1) * CHUNK_SIZE_Y,
                (float)(id.z + 1) * CHUNK_SIZE_Z
        );
        this.boundingBox = new CustomAABB(this.minCorner, this.maxCorner); // Initialize AABB
        this.chunkMesh = new ChunkMesh(); // Initialize mesh object
    }

    public ChunkId getId() {
        return id;
    }

    public void addBlock(Block block) {
        if (!blocks.contains(block)) {
            blocks.add(block);
            needsMeshRebuild = true; // Mark for rebuild
        }
    }

    public boolean removeBlock(Block block) {
        boolean removed = blocks.remove(block);
        if (removed) {
            needsMeshRebuild = true; // Mark for rebuild
        }
        return removed;
    }

    public List<Block> getBlocks() {
        return Collections.unmodifiableList(blocks); // Still provide access if needed elsewhere
    }

    // Provides direct access to the mutable list for mesh building, use with caution
    public List<Block> getModifiableBlocks() {
        return blocks;
    }


    public boolean isWorldPositionInChunk(Vector3f worldPosition) {
        return worldPosition.x >= minCorner.x && worldPosition.x < maxCorner.x &&
                worldPosition.y >= minCorner.y && worldPosition.y < maxCorner.y &&
                worldPosition.z >= minCorner.z && worldPosition.z < maxCorner.z;
    }

    public Vector3f getMinCorner() {
        return new Vector3f(minCorner);
    }

    public Vector3f getMaxCorner() {
        return new Vector3f(maxCorner);
    }

    public CustomAABB getAABB() { // Getter for the chunk's AABB
        return boundingBox;
    }

    // Mesh management
    public ChunkMesh getOrCreateMesh() {
        if (chunkMesh == null) { // Should be initialized in constructor, but as a safeguard
            chunkMesh = new ChunkMesh();
            needsMeshRebuild = true; // Force build if it was null
        }
        if (needsMeshRebuild || !chunkMesh.isInitialized()) {
            if(chunkMesh.isInitialized()) chunkMesh.cleanup(); // Clean old mesh if rebuilding
            List<Block> currentBlocks = getModifiableBlocks(); // Get the actual list for building
            if (!currentBlocks.isEmpty()) {
                // Pass blocks and chunk's world origin (minCorner)
                chunkMesh.buildMesh(currentBlocks, this.minCorner);
            } else {
                chunkMesh.cleanup(); // Ensure no VAO if no blocks
            }
            needsMeshRebuild = false;
        }
        return chunkMesh;
    }

    public void cleanupMesh() {
        if (chunkMesh != null && chunkMesh.isInitialized()) {
            chunkMesh.cleanup();
        }
    }


    public static void setChunkDimensions(int sizeX, int sizeY, int sizeZ) {
        CHUNK_SIZE_X = sizeX;
        CHUNK_SIZE_Y = sizeY;
        CHUNK_SIZE_Z = sizeZ;
    }

    public static ChunkId getChunkIdAtWorldPosition(float worldX, float worldY, float worldZ) {
        int chunkX = (int) Math.floor(worldX / CHUNK_SIZE_X);
        int chunkY = (int) Math.floor(worldY / CHUNK_SIZE_Y);
        int chunkZ = (int) Math.floor(worldZ / CHUNK_SIZE_Z);
        return new ChunkId(chunkX, chunkY, chunkZ);
    }

    public static ChunkId getChunkIdAtWorldPosition(Vector3f worldPosition) {
        return getChunkIdAtWorldPosition(worldPosition.x, worldPosition.y, worldPosition.z);
    }
}