package World;

import Configuration.Config;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;
import World.Chunk.*;

/**
 * Manages the chunks and blocks in the game world.
 */
public class Terrain {
    private final Map<ChunkId, Chunk> chunks;
    private final Random random = new Random();
    private final Config config; // Store config for easy access to chunk sizes

    public Terrain(int initialWidthInBlocks, int initialHeightInBlocks, int initialDepthInBlocks, Config config) {
        this.chunks = new HashMap<>();
        this.config = config;
        // Initialize static chunk dimensions from config if not already done
        // This is also done in Config constructor, but good to ensure it here too.
        Chunk.setChunkDimensions(config.getChunkSizeX(), config.getChunkSizeY(), config.getChunkSizeZ());
        generateInitialTerrain(initialWidthInBlocks, initialHeightInBlocks, initialDepthInBlocks);
    }

    private void generateInitialTerrain(int widthInBlocks, int heightInBlocks, int depthInBlocks) {
        // Calculate world center offset based on total blocks
        float worldOriginX = -widthInBlocks / 2.0f + 0.5f;
        float worldOriginY = 0.0f; // Assuming terrain starts at y=0
        float worldOriginZ = -depthInBlocks / 2.0f + 0.5f;

        for (int y = 0; y < heightInBlocks; y++) {
            for (int x = 0; x < widthInBlocks; x++) {
                for (int z = 0; z < depthInBlocks; z++) {
                    float blockWorldX = worldOriginX + x;
                    float blockWorldY = worldOriginY + y;
                    float blockWorldZ = worldOriginZ + z;

                    Vector3f blockPosition = new Vector3f(blockWorldX, blockWorldY, blockWorldZ);
                    Vector3f color;
                    if (y == heightInBlocks - 1) { // Top layer
                        color = ((x + z) % 2 == 0) ? new Vector3f(0.2f, 0.8f, 0.2f) : new Vector3f(0.15f, 0.6f, 0.15f);
                    } else { // Sub-layers
                        color = new Vector3f(0.5f, 0.35f, 0.2f);
                    }
                    addBlock(new Block(blockPosition, color));
                }
            }
        }
        System.out.println("Generated initial terrain with " + chunks.size() + " active chunks.");
    }

    public Chunk getChunk(ChunkId id) {
        return chunks.get(id);
    }

    public Chunk getOrCreateChunk(ChunkId id) {
        return chunks.computeIfAbsent(id, k -> new Chunk(k));
    }

    public Chunk getChunkAtWorldPosition(Vector3f worldPosition) {
        ChunkId id = Chunk.getChunkIdAtWorldPosition(worldPosition);
        return getChunk(id);
    }

    public Chunk getOrCreateChunkAtWorldPosition(Vector3f worldPosition) {
        ChunkId id = Chunk.getChunkIdAtWorldPosition(worldPosition);
        return getOrCreateChunk(id);
    }


    public void addBlock(Block block) {
        if (block == null) return;
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(block.getPosition());
        Chunk chunk = getOrCreateChunk(chunkId);
        chunk.addBlock(block);
    }

    public boolean removeBlock(Block blockToRemove) {
        if (blockToRemove == null) return false;
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(blockToRemove.getPosition());
        Chunk chunk = getChunk(chunkId);
        if (chunk != null) {
            boolean removed = chunk.removeBlock(blockToRemove);
            // Optional: if chunk becomes empty, consider removing it from the main 'chunks' map
            // if (chunk.getBlocks().isEmpty()) {
            //     chunks.remove(chunkId);
            // }
            return removed;
        }
        return false;
    }

    public boolean removeBlockAt(Vector3f worldPosition) {
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(worldPosition);
        Chunk chunk = getChunk(chunkId);
        if (chunk != null) {
            // Iterate through blocks in the chunk to find the one at the exact position
            // This is less efficient than passing the Block object itself if available
            Block blockToRemove = null;
            for (Block b : chunk.getBlocks()) {
                if (b.getPosition().distanceSquared(worldPosition) < 0.001f) {
                    blockToRemove = b;
                    break;
                }
            }
            if (blockToRemove != null) {
                return chunk.removeBlock(blockToRemove);
            }
        }
        return false;
    }


    public boolean isBlockAt(Vector3f worldPosition) {
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(worldPosition);
        Chunk chunk = getChunk(chunkId);
        if (chunk != null) {
            for (Block block : chunk.getBlocks()) {
                if (block.getPosition().distanceSquared(worldPosition) < 0.001f) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets all blocks from chunks that could potentially collide with an entity
     * centered at entityPosition with a given bounding box.
     * This typically includes the entity's current chunk and its immediate neighbors.
     * @param entityPosition The center position of the entity.
     * @param entityDimensions The dimensions of the entity's bounding box.
     * @return A list of blocks for collision checking.
     */
    public List<Block> getBlocksForCollision(Vector3f entityPosition, Vector3f entityDimensions) {
        List<Block> relevantBlocks = new ArrayList<>();
        Set<ChunkId> checkedChunkIds = new HashSet<>();

        // Calculate the min and max corners of the entity's AABB to find all chunks it might touch
        Vector3f halfDim = new Vector3f(entityDimensions).mul(0.5f);
        Vector3f entityMinCorner = new Vector3f(entityPosition).sub(halfDim);
        Vector3f entityMaxCorner = new Vector3f(entityPosition).add(halfDim);

        ChunkId minChunkId = Chunk.getChunkIdAtWorldPosition(entityMinCorner);
        ChunkId maxChunkId = Chunk.getChunkIdAtWorldPosition(entityMaxCorner);

        for (int cx = minChunkId.x; cx <= maxChunkId.x; cx++) {
            for (int cy = minChunkId.y; cy <= maxChunkId.y; cy++) {
                for (int cz = minChunkId.z; cz <= maxChunkId.z; cz++) {
                    ChunkId currentChunkId = new ChunkId(cx, cy, cz);
                    // Also check immediate neighbors of these chunks for broader phase
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                ChunkId neighborId = new ChunkId(currentChunkId.x + dx, currentChunkId.y + dy, currentChunkId.z + dz);
                                if (checkedChunkIds.add(neighborId)) { // Avoid re-processing same chunk
                                    Chunk chunk = getChunk(neighborId);
                                    if (chunk != null) {
                                        relevantBlocks.addAll(chunk.getBlocks());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return relevantBlocks;
    }

    /**
     * Gets all blocks within a given radius of chunks around a central chunk.
     * @param centerChunkId The ID of the central chunk.
     * @param radiusInChunks The radius in chunks (e.g., 1 means a 3x3x3 area).
     * @return A list of all blocks in the specified chunk area.
     */
    public List<Block> getBlocksInRadius(ChunkId centerChunkId, int radiusInChunks) {
        List<Block> blocksInRadius = new ArrayList<>();
        for (int dx = -radiusInChunks; dx <= radiusInChunks; dx++) {
            for (int dy = -radiusInChunks; dy <= radiusInChunks; dy++) { // Consider Y-axis for render distance too
                for (int dz = -radiusInChunks; dz <= radiusInChunks; dz++) {
                    ChunkId currentId = new ChunkId(centerChunkId.x + dx, centerChunkId.y + dy, centerChunkId.z + dz);
                    Chunk chunk = getChunk(currentId);
                    if (chunk != null) {
                        blocksInRadius.addAll(chunk.getBlocks());
                    }
                }
            }
        }
        return blocksInRadius;
    }


    /**
     * Gets all currently loaded chunks.
     * @return A new list containing all loaded chunks.
     */
    public List<Chunk> getAllLoadedChunks() {
        return new ArrayList<>(chunks.values());
    }


    public void cleanup() {
        chunks.clear();
    }
}
