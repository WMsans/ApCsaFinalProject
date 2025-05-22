package World.Terrain;

import Configuration.Config;
import World.Block;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public abstract class BaseTerrainGenerator {
    protected final Map<ChunkId, Chunk> chunks;
    protected final Config config;

    public BaseTerrainGenerator(Config config) {
        this.chunks = new HashMap<>();
        this.config = config;
        // Ensure chunk dimensions are set. This might be redundant if Config already does it,
        // but it's safe to have here.
        Chunk.setChunkDimensions(config.getChunkSizeX(), config.getChunkSizeY(), config.getChunkSizeZ());
    }

    // Abstract method to be implemented by subclasses
    protected abstract void generateChunk(ChunkId chunkId);

    public Chunk getChunk(ChunkId id) {
        if (!chunks.containsKey(id)) {
            generateChunk(id);
        }
        return chunks.get(id);
    }

    public Chunk getOrCreateChunk(ChunkId id) {
        return getChunk(id);
    }

    public Chunk getChunkAtWorldPosition(Vector3f worldPosition) {
        ChunkId id = Chunk.getChunkIdAtWorldPosition(worldPosition);
        return getChunk(id);
    }

    public Chunk getOrCreateChunkAtWorldPosition(Vector3f worldPosition) {
        ChunkId id = Chunk.getChunkIdAtWorldPosition(worldPosition);
        return getChunk(id);
    }

    public void addBlock(Block block) {
        if (block == null) return;
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(block.getPosition());
        Chunk chunk = getChunk(chunkId); // Ensures chunk is generated if it doesn't exist
        chunk.addBlock(block);
    }

    public boolean removeBlock(Block blockToRemove) {
        if (blockToRemove == null) return false;
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(blockToRemove.getPosition());
        Chunk chunk = getChunk(chunkId); // Ensures chunk is generated
        if (chunk != null) {
            return chunk.removeBlock(blockToRemove);
        }
        return false;
    }

    public boolean removeBlockAt(Vector3f worldPosition) {
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(worldPosition);
        Chunk chunk = getChunk(chunkId); // Ensures chunk is generated
        if (chunk != null) {
            Block toRemove = null;
            // Iterate over a copy or use an iterator if concurrent modification is an issue
            for (Block b : new java.util.ArrayList<>(chunk.getModifiableBlocks())) {
                if (b.getPosition().distanceSquared(worldPosition) < 0.001f) {
                    toRemove = b;
                    break;
                }
            }
            if (toRemove != null) {
                return chunk.removeBlock(toRemove);
            }
        }
        return false;
    }

    public boolean isBlockAt(Vector3f worldPosition) {
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(worldPosition);
        Chunk chunk = getChunk(chunkId); // Ensures chunk is generated
        if (chunk != null) {
            for (Block block : chunk.getBlocks()) { // Use getBlocks() for read-only access
                if (block.getPosition().distanceSquared(worldPosition) < 0.001f) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Block> getBlocksForCollision(Vector3f entityPosition, Vector3f entityDimensions) {
        List<Block> relevantBlocks = new java.util.ArrayList<>();
        java.util.Set<ChunkId> checkedChunkIds = new java.util.HashSet<>();

        Vector3f halfDim = new Vector3f(entityDimensions).mul(0.5f);
        Vector3f entityMinCorner = new Vector3f(entityPosition).sub(halfDim);
        Vector3f entityMaxCorner = new Vector3f(entityPosition).add(halfDim);

        ChunkId minChunkId = Chunk.getChunkIdAtWorldPosition(entityMinCorner);
        ChunkId maxChunkId = Chunk.getChunkIdAtWorldPosition(entityMaxCorner);

        // Iterate a 3x3x3 or larger area of chunks around the entity
        for (int cx = minChunkId.x - 1; cx <= maxChunkId.x + 1; cx++) {
            for (int cy = minChunkId.y - 1; cy <= maxChunkId.y + 1; cy++) {
                for (int cz = minChunkId.z - 1; cz <= maxChunkId.z + 1; cz++) {
                    ChunkId currentChunkId = new ChunkId(cx, cy, cz);
                    if (checkedChunkIds.add(currentChunkId)) {
                        Chunk chunk = getChunk(currentChunkId); // Ensures chunk generation
                        if (chunk != null) {
                            relevantBlocks.addAll(chunk.getBlocks()); // Use read-only access
                        }
                    }
                }
            }
        }
        return relevantBlocks;
    }

    public List<Block> getBlocksInRadius(ChunkId centerChunkId, int radiusInChunks) {
        List<Block> blocksInRadius = new java.util.ArrayList<>();
        for (int dx = -radiusInChunks; dx <= radiusInChunks; dx++) {
            for (int dy = -radiusInChunks; dy <= radiusInChunks; dy++) { // Consider if Y radius is needed
                for (int dz = -radiusInChunks; dz <= radiusInChunks; dz++) {
                    ChunkId currentId = new ChunkId(centerChunkId.x + dx, centerChunkId.y + dy, centerChunkId.z + dz);
                    Chunk chunk = getChunk(currentId); // Ensures chunk generation
                    if (chunk != null) {
                        blocksInRadius.addAll(chunk.getBlocks()); // Use read-only access
                    }
                }
            }
        }
        return blocksInRadius;
    }

    public List<Chunk> getAllLoadedChunks() {
        return new java.util.ArrayList<>(chunks.values());
    }

    public void cleanup() {
        for (Chunk chunk : chunks.values()) {
            chunk.cleanupMesh();
        }
        chunks.clear();
    }
}