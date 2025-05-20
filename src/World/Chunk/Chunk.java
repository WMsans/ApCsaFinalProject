package World.Chunk;

import org.joml.Vector3f;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import World.Block;


/**
 * Represents a 3D segment of the world containing blocks.
 * Chunk coordinates are integer-based (e.g., 0,0,0 or 1,0,0).
 * World coordinates of blocks within this chunk will still be their absolute world positions.
 */
public class Chunk {
    private final ChunkId id;
    private final List<Block> blocks; // Blocks store their absolute world positions
    private final Vector3f minCorner; // World coordinates of the minimum corner of this chunk
    private final Vector3f maxCorner; // World coordinates of the maximum corner of this chunk
    public static int CHUNK_SIZE_X = 16; // Default, will be updated by Config
    public static int CHUNK_SIZE_Y = 16;
    public static int CHUNK_SIZE_Z = 16;


    public Chunk(ChunkId id) {
        this.id = id;
        // Use CopyOnWriteArrayList for thread-safe modifications if blocks are added/removed concurrently.
        // For single-threaded updates, ArrayList is fine.
        this.blocks = new CopyOnWriteArrayList<>(); // Or new ArrayList<>();

        // Calculate world boundaries of this chunk
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
    }

    public ChunkId getId() {
        return id;
    }

    public void addBlock(Block block) {
        // Optional: Could add a check here to ensure the block's position
        // actually falls within this chunk's boundaries, though it's generally
        // the responsibility of the World/Terrain manager to place it correctly.
        if (!blocks.contains(block)) {
            blocks.add(block);
        }
    }

    public boolean removeBlock(Block block) {
        return blocks.remove(block);
    }

    /**
     * Returns an unmodifiable view of the blocks in this chunk.
     * This prevents external modification of the internal list.
     * The blocks themselves are mutable.
     * @return An unmodifiable list of blocks.
     */
    public List<Block> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    /**
     * Checks if a given world position is within this chunk's boundaries.
     * @param worldPosition The world position to check.
     * @return True if the position is within this chunk, false otherwise.
     */
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

    // Static method to update chunk dimensions from Config
    public static void setChunkDimensions(int sizeX, int sizeY, int sizeZ) {
        CHUNK_SIZE_X = sizeX;
        CHUNK_SIZE_Y = sizeY;
        CHUNK_SIZE_Z = sizeZ;
    }

    /**
     * Helper method to calculate the ChunkId for a given world position.
     * @param worldX World X coordinate.
     * @param worldY World Y coordinate.
     * @param worldZ World Z coordinate.
     * @return The ChunkId.
     */
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

