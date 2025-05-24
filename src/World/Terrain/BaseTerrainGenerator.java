package World.Terrain;

import Configuration.Config;
import World.Block;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import World.Entities.Entity; // Added import
import World.Entities.PlayerEntity; // Added import for player check

import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CopyOnWriteArrayList; // Added import

public abstract class BaseTerrainGenerator {
    protected final Map<ChunkId, Chunk> chunks;
    protected final Config config;
    protected final List<Entity> entities; // Added to manage entities

    // For asynchronous chunk generation
    private final ExecutorService chunkExecutor;
    private final Map<ChunkId, Future<Chunk>> pendingChunks;
    private final Queue<Chunk> completedChunksQueue;
    private final Set<ChunkId> requestedChunks; // To avoid re-requesting

    public BaseTerrainGenerator(Config config) {
        this.chunks = new ConcurrentHashMap<>(); // Use ConcurrentHashMap for thread safety
        this.config = config;
        this.entities = new CopyOnWriteArrayList<>(); // Initialize entity list
        Chunk.setChunkDimensions(config.getChunkSizeX(), config.getChunkSizeY(), config.getChunkSizeZ());

        // Initialize components for asynchronous generation
        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2); // Example: use half available cores
        this.chunkExecutor = Executors.newFixedThreadPool(numThreads);
        this.pendingChunks = new ConcurrentHashMap<>();
        this.completedChunksQueue = new ConcurrentLinkedQueue<>();
        this.requestedChunks = ConcurrentHashMap.newKeySet();
    }

    // Abstract method to be implemented by subclasses for actual chunk data generation
    // This method will be called by the executor service.
    protected abstract Chunk generateChunkData(ChunkId chunkId);

    /**
     * Retrieves a chunk. If the chunk is not loaded, it may initiate asynchronous loading.
     * Returns null if the chunk is not yet loaded and is being generated.
     *
     * @param id The ID of the chunk to retrieve.
     * @return The Chunk if available, otherwise null.
     */
    public Chunk getChunk(ChunkId id) {
        Chunk chunk = chunks.get(id);
        if (chunk != null) {
            return chunk;
        }

        // If chunk is not loaded, and not already being generated, request it.
        if (!pendingChunks.containsKey(id) && requestedChunks.add(id)) {
            Future<Chunk> future = chunkExecutor.submit(() -> {
                try {
                    Chunk generatedChunk = generateChunkData(id);
                    if (generatedChunk != null) {
                        completedChunksQueue.offer(generatedChunk);
                    }
                    return generatedChunk; // Return the chunk so Future can hold it if needed, though queue is primary
                } catch (Exception e) {
                    System.err.println("Error generating chunk " + id + ": " + e.getMessage());
                    e.printStackTrace();
                    return null; // Return null on error
                } finally {
                    pendingChunks.remove(id); // Remove from pending once task is done (success or fail)
                    // Keep in requestedChunks to avoid re-submission, unless you want retry logic
                }
            });
            pendingChunks.put(id, future);
        }
        return null; // Chunk is not ready yet
    }

    /**
     * Synchronously gets or generates a chunk. This is for critical path like initial spawn.
     * @param id The ID of the chunk to retrieve or generate.
     * @return The Chunk.
     */
    public Chunk getChunkSynchronous(ChunkId id) {
        Chunk chunk = chunks.get(id);
        if (chunk != null) {
            return chunk;
        }

        // If it's pending, wait for it
        Future<Chunk> pendingFuture = pendingChunks.get(id);
        if (pendingFuture != null) {
            try {
                System.out.println("Waiting for pending chunk: " + id);
                chunk = pendingFuture.get(); // This will block
                if (chunk != null && !chunks.containsKey(id)) { // Check if it was added by processCompletedChunks already
                    // Mesh creation should happen on the main thread or a dedicated mesh thread
                    // For now, let's assume mesh is built when added to main 'chunks' map
                    // chunk.getOrCreateMesh(); // Potentially long operation
                    chunks.put(id, chunk); // Add to main map
                }
                return chunk;
            } catch (Exception e) {
                System.err.println("Error waiting for pending chunk " + id + ": " + e.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupt status
                return null;
            }
        }
        chunk = generateChunkData(id);
        if (chunk != null) {
            // chunk.getOrCreateMesh(); // Potentially long operation
            chunks.put(id, chunk);
        }
        return chunk;
    }


    /**
     * Processes chunks that have finished generating in worker threads.
     * This should be called regularly from the main game loop.
     */
    public void processCompletedChunks() {
        Chunk completedChunk;
        while ((completedChunk = completedChunksQueue.poll()) != null) {
            if (!chunks.containsKey(completedChunk.getId())) {
                // The mesh is created here, on the main thread, after data is loaded.
                completedChunk.getOrCreateMesh(); // Ensure mesh is ready before being added to active chunks
                chunks.put(completedChunk.getId(), completedChunk);
            }
        }
    }
    public void unloadDistantChunks(ChunkId playerChunkId, int renderDistance, PlayerEntity playerToKeep) {
        List<ChunkId> toUnload = new ArrayList<>();
        int unloadDistance = renderDistance + 2; // Unload chunks a bit further than render distance

        for (ChunkId loadedChunkId : chunks.keySet()) {
            int deltaX = Math.abs(loadedChunkId.x - playerChunkId.x);
            int deltaY = Math.abs(loadedChunkId.y - playerChunkId.y);
            int deltaZ = Math.abs(loadedChunkId.z - playerChunkId.z);

            if (deltaX > unloadDistance || deltaZ > unloadDistance || deltaY > unloadDistance) {
                toUnload.add(loadedChunkId);
            }
        }

        for (ChunkId idToUnload : toUnload) {
            Chunk chunkToRemove = chunks.remove(idToUnload);
            if (chunkToRemove != null) {
                chunkToRemove.cleanupMesh();
                requestedChunks.remove(idToUnload);
            }
        }

        // Unload entities in unloaded chunks (excluding the player)
        List<Entity> entitiesToRemove = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity == playerToKeep) { // Do not unload the player
                continue;
            }
            ChunkId entityChunkId = entity.getChunkId();
            if (!chunks.containsKey(entityChunkId) && toUnload.contains(entityChunkId)) { // If entity's chunk was unloaded
                entity.kill(); // Mark for removal
                entitiesToRemove.add(entity);
                System.out.println("Unloading entity " + entity.getId() + " as its chunk " + entityChunkId + " was unloaded.");
            }
        }
        entities.removeAll(entitiesToRemove); // Efficiently remove all marked entities
    }


    public Chunk getOrCreateChunk(ChunkId id) {
        return getChunk(id); // Will use the async version
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
        Chunk chunk = chunks.get(chunkId); // Get already loaded chunk
        if (chunk != null) {
            chunk.addBlock(block); // This will set needsMeshRebuild = true
        } else {
            System.err.println("Attempted to add block to a non-loaded chunk: " + chunkId);
        }
    }

    public boolean removeBlock(Block blockToRemove) {
        if (blockToRemove == null) return false;
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(blockToRemove.getPosition());
        Chunk chunk = chunks.get(chunkId);
        if (chunk != null) {
            return chunk.removeBlock(blockToRemove);
        }
        return false;
    }

    public boolean removeBlockAt(Vector3f worldPosition) {
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(worldPosition);
        Chunk chunk = chunks.get(chunkId);
        if (chunk != null) {
            Block toRemove = null;
            // Iterate over a copy if concurrent modification is an issue, or ensure single-threaded access here
            for (Block b : new ArrayList<>(chunk.getModifiableBlocks())) {
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
        Chunk chunk = chunks.get(chunkId); // Check only fully loaded chunks
        if (chunk != null) {
            for (Block block : chunk.getBlocks()) {
                // Ensure block positions are exact or use a small tolerance for floating point comparison
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

        // Iterate an expanded area of chunks: current chunk and immediate neighbors
        for (int cx = minChunkId.x - 1; cx <= maxChunkId.x + 1; cx++) {
            for (int cy = minChunkId.y - 1; cy <= maxChunkId.y + 1; cy++) { // Consider Y dimension for collision
                for (int cz = minChunkId.z - 1; cz <= maxChunkId.z + 1; cz++) {
                    ChunkId currentChunkId = new ChunkId(cx, cy, cz);
                    if (checkedChunkIds.add(currentChunkId)) {
                        Chunk chunk = chunks.get(currentChunkId); // Only use fully loaded chunks
                        if (chunk != null) {
                            relevantBlocks.addAll(chunk.getBlocks());
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
            for (int dy = -radiusInChunks; dy <= radiusInChunks; dy++) { // Consider Y dimension
                for (int dz = -radiusInChunks; dz <= radiusInChunks; dz++) {
                    ChunkId currentId = new ChunkId(centerChunkId.x + dx, centerChunkId.y + dy, centerChunkId.z + dz);
                    Chunk chunk = chunks.get(currentId); // Only use fully loaded chunks
                    if (chunk != null) {
                        blocksInRadius.addAll(chunk.getBlocks());
                    }
                }
            }
        }
        return blocksInRadius;
    }

    public List<Chunk> getAllLoadedChunks() {
        return new java.util.ArrayList<>(chunks.values());
    }

    // Entity Management Methods
    public void addEntity(Entity entity) {
        if (entity != null && !entities.contains(entity)) {
            entities.add(entity);
        }
    }

    public void removeEntity(Entity entity) {
        if (entity != null) {
            entity.kill(); // Mark as invalid
            entities.remove(entity); // Remove from list
        }
    }

    public void updateEntities(float deltaTime, float currentTime) {
        List<Entity> toRemove = null;
        for (Entity entity : entities) {
            if (!entity.isValid()) {
                if (toRemove == null) {
                    toRemove = new ArrayList<>();
                }
                toRemove.add(entity);
            } else {
                entity.update(deltaTime, currentTime);
            }
        }
        if (toRemove != null) {
            entities.removeAll(toRemove);
        }
    }

    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }


    public void cleanup() {
        chunkExecutor.shutdown();
        try {
            if (!chunkExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                chunkExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            chunkExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        for (Chunk chunk : chunks.values()) {
            chunk.cleanupMesh();
        }
        chunks.clear();
        pendingChunks.clear();
        completedChunksQueue.clear();
        requestedChunks.clear();

        for (Entity entity : entities) { // Cleanup entities
            entity.kill();
        }
        entities.clear();
    }
}