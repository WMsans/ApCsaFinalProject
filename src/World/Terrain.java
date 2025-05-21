package World;

import Configuration.Config;
import World.Chunk.*;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public class Terrain {
    private final Map<ChunkId, Chunk> chunks;
    private final Config config;
    private final Random random = new Random(); // For block color variations etc.

    // Noise generators for different terrain features
    private final FastNoiseLite noiseGen_BaseHeight; // For overall large-scale elevation
    private final FastNoiseLite noiseGen_3DDensity;  // For caves, overhangs, general solidity
    private final FastNoiseLite noiseGen_FloatingIslandPlacement; // 2D noise to decide where islands might form
    private final FastNoiseLite noiseGen_FloatingIslandShape;   // 3D noise to shape the islands themselves
    private final FastNoiseLite noiseGen_SpirePlacement; // 2D noise for spire locations
    private final FastNoiseLite noiseGen_SpireShape;    // 3D noise (or stretched 2D) for spire verticality

    // --- Terrain Generation Parameters (Consider moving to Config if more control is needed) ---
    private static final float SEA_LEVEL = 64.0f;
    private static final float BASE_TERRAIN_AMPLITUDE = 70.0f; // Max height variation for base terrain
    private static final float DENSITY_THRESHOLD_BELOW_SURFACE = -0.1f; // Lower value = more solid
    private static final float DENSITY_THRESHOLD_ABOVE_SURFACE = 0.25f; // Higher value = more air, creating overhangs

    private static final float MIN_ISLAND_ALTITUDE = 100.0f;
    private static final float ISLAND_PLACEMENT_THRESHOLD = 0.65f; // Value from noiseGen_FloatingIslandPlacement
    private static final float ISLAND_DENSITY_THRESHOLD = 0.05f;
    private static final float ISLAND_CORE_HEIGHT_VARIATION = 20.0f;
    private static final float ISLAND_THICKNESS_VARIATION = 15.0f;

    private static final float MIN_SPIRE_BASE_ALTITUDE = 50.0f;
    private static final float MAX_SPIRE_HEIGHT_ABOVE_BASE = 150.0f;
    private static final float SPIRE_PLACEMENT_THRESHOLD = 0.75f;
    private static final float SPIRE_DENSITY_THRESHOLD = 0.4f; // For the spire body itself
    private static final float SPIRE_RADIUS_FACTOR = 0.15f; // Spire radius relative to chunk size


    public Terrain(Config config) { // Removed initial block dimensions
        this.chunks = new HashMap<>();
        this.config = config;
        Chunk.setChunkDimensions(config.getChunkSizeX(), config.getChunkSizeY(), config.getChunkSizeZ());

        // Initialize FastNoiseLite instances
        int worldSeed = 1337; // Or get from config

        noiseGen_BaseHeight = new FastNoiseLite(worldSeed);
        noiseGen_BaseHeight.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseGen_BaseHeight.SetFrequency(0.003f); // Very low frequency for large landforms
        noiseGen_BaseHeight.SetFractalType(FastNoiseLite.FractalType.FBm);
        noiseGen_BaseHeight.SetFractalOctaves(5);
        noiseGen_BaseHeight.SetFractalLacunarity(2.0f);
        noiseGen_BaseHeight.SetFractalGain(0.5f);

        noiseGen_3DDensity = new FastNoiseLite(worldSeed + 1);
        noiseGen_3DDensity.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseGen_3DDensity.SetFrequency(0.015f); // Medium frequency for caves/overhangs
        noiseGen_3DDensity.SetFractalType(FastNoiseLite.FractalType.Ridged); // Good for interesting structures
        noiseGen_3DDensity.SetFractalOctaves(4);
        noiseGen_3DDensity.SetFractalLacunarity(2.2f);
        noiseGen_3DDensity.SetFractalGain(0.45f);

        noiseGen_FloatingIslandPlacement = new FastNoiseLite(worldSeed + 2);
        noiseGen_FloatingIslandPlacement.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        noiseGen_FloatingIslandPlacement.SetFrequency(0.0025f); // Very low, for sparse island regions
        noiseGen_FloatingIslandPlacement.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
        noiseGen_FloatingIslandPlacement.SetCellularReturnType(FastNoiseLite.CellularReturnType.CellValue); // Get cell value for regions
        noiseGen_FloatingIslandPlacement.SetCellularJitter(0.8f);

        noiseGen_FloatingIslandShape = new FastNoiseLite(worldSeed + 3);
        noiseGen_FloatingIslandShape.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseGen_FloatingIslandShape.SetFrequency(0.025f); // For island body details
        noiseGen_FloatingIslandShape.SetFractalType(FastNoiseLite.FractalType.FBm);
        noiseGen_FloatingIslandShape.SetFractalOctaves(3);

        noiseGen_SpirePlacement = new FastNoiseLite(worldSeed + 4);
        noiseGen_SpirePlacement.SetNoiseType(FastNoiseLite.NoiseType.Cellular); // Using cellular to get distinct points
        noiseGen_SpirePlacement.SetFrequency(0.008f); // Controls density of spires
        noiseGen_SpirePlacement.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
        noiseGen_SpirePlacement.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2); // Distance to nearest point
        noiseGen_SpirePlacement.SetCellularJitter(0.6f);


        noiseGen_SpireShape = new FastNoiseLite(worldSeed + 5); // For the vertical shape of spires
        noiseGen_SpireShape.SetNoiseType(FastNoiseLite.NoiseType.Perlin); // Perlin can be good for streaky/vertical forms
        noiseGen_SpireShape.SetFrequency(0.05f); // This will be applied in a "stretched" way
    }

    private void generateChunk(ChunkId chunkId) {
        Chunk newChunk = new Chunk(chunkId);
        float worldChunkXBase = (float)chunkId.x * Chunk.CHUNK_SIZE_X;
        float worldChunkYBase = (float)chunkId.y * Chunk.CHUNK_SIZE_Y;
        float worldChunkZBase = (float)chunkId.z * Chunk.CHUNK_SIZE_Z;

        for (int lx = 0; lx < Chunk.CHUNK_SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.CHUNK_SIZE_Z; lz++) {
                float worldX = worldChunkXBase + lx + 0.5f; // Use block center
                float worldZ = worldChunkZBase + lz + 0.5f;

                // --- Layer 1: Base terrain height ---
                float baseHeightNoiseVal = noiseGen_BaseHeight.GetNoise(worldX, worldZ); // Range -1 to 1
                float currentSurfaceY = SEA_LEVEL + baseHeightNoiseVal * BASE_TERRAIN_AMPLITUDE;

                // --- Layer 3: Floating Island Placement Check ---
                float islandPlacementVal = (noiseGen_FloatingIslandPlacement.GetNoise(worldX, worldZ) + 1) / 2f; // Normalize 0-1

                // --- Layer 4: Spire Placement Check ---
                // Distance2 gives low values near points, high values far. We want low values.
                float spirePlacementVal = noiseGen_SpirePlacement.GetNoise(worldX, worldZ);


                for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                    float worldY = worldChunkYBase + ly + 0.5f; // Use block center
                    boolean placeBlock = false;

                    // --- Layer 2: 3D Density ---
                    // Ridged noise is often negative, adjust its range or use abs for interesting effects
                    float densityVal3D = noiseGen_3DDensity.GetNoise(worldX, worldY, worldZ);

                    // --- Primary Terrain Decision (Solid ground, caves, overhangs) ---
                    if (worldY < currentSurfaceY) { // Below calculated surface
                        if (densityVal3D > DENSITY_THRESHOLD_BELOW_SURFACE) {
                            placeBlock = true;
                        }
                    } else { // At or above calculated surface (potential for overhangs)
                        if (densityVal3D > DENSITY_THRESHOLD_ABOVE_SURFACE) {
                            placeBlock = true;
                        }
                    }

                    // --- Layer 3: Floating Islands ---
                    if (islandPlacementVal > ISLAND_PLACEMENT_THRESHOLD && worldY > MIN_ISLAND_ALTITUDE) {
                        float islandShapeVal = noiseGen_FloatingIslandShape.GetNoise(worldX, worldY, worldZ);
                        // Define island core based on a secondary noise layer or a simple distance falloff from placement noise peak
                        float islandCoreBaseY = MIN_ISLAND_ALTITUDE + (islandPlacementVal - ISLAND_PLACEMENT_THRESHOLD) * 50.0f; // Island base height varies
                        float islandCoreTopY = islandCoreBaseY + ISLAND_CORE_HEIGHT_VARIATION + baseHeightNoiseVal * ISLAND_THICKNESS_VARIATION;

                        if (worldY > islandCoreBaseY && worldY < islandCoreTopY) {
                            if (islandShapeVal > ISLAND_DENSITY_THRESHOLD) {
                                placeBlock = true; // Add to or form island block
                            } else if (islandShapeVal < -0.3f && placeBlock){ // Carve from existing island block
                                placeBlock = false;
                            }
                        } else if (placeBlock && worldY > MIN_ISLAND_ALTITUDE -10 && worldY < islandCoreBaseY && islandShapeVal < -0.2f) {
                            // Erode blocks just under an island to make it more "floating"
                            placeBlock = false;
                        }
                    }

                    // --- Layer 4: Tall Spires ---
                    // SpirePlacementVal is low near spire centers (Distance2)
                    if (spirePlacementVal < SPIRE_PLACEMENT_THRESHOLD && worldY > MIN_SPIRE_BASE_ALTITUDE && worldY < currentSurfaceY + MAX_SPIRE_HEIGHT_ABOVE_BASE) {
                        // For spires, we want density to be high along a vertical column, tapering off.
                        // Use distance from the XZ center of the spire "point" (can be approximated)
                        // and a Y-dependent shaping noise.
                        float distToSpireCenterApprox = (float)Math.sqrt(spirePlacementVal / SPIRE_PLACEMENT_THRESHOLD) * (Chunk.CHUNK_SIZE_X * 0.5f); // very rough
                        float normalizedYInSpire = (worldY - MIN_SPIRE_BASE_ALTITUDE) / MAX_SPIRE_HEIGHT_ABOVE_BASE;

                        // Taper radius: spires get thinner higher up
                        float spireRadiusAtY = Chunk.CHUNK_SIZE_X * SPIRE_RADIUS_FACTOR * (1.0f - normalizedYInSpire * 0.7f);

                        if (distToSpireCenterApprox < spireRadiusAtY) {
                            // Additional 3D noise for spire "solidity" or texture
                            float spireBodyNoise = (noiseGen_SpireShape.GetNoise(worldX * 0.5f, worldY * 2.0f, worldZ * 0.5f) +1)/2f; // Stretched Y
                            if (spireBodyNoise > SPIRE_DENSITY_THRESHOLD - (normalizedYInSpire * 0.2f) ) { // Spires can get slightly hollower at top
                                placeBlock = true;
                            }
                        }
                    }


                    if (placeBlock) {
                        Vector3f color;
                        if (worldY < SEA_LEVEL - 20) color = new Vector3f(0.3f, 0.3f, 0.35f); // Deep stone
                        else if (worldY < currentSurfaceY - 1.5f) color = new Vector3f(0.5f, 0.45f, 0.4f); // Stone
                        else if (worldY < currentSurfaceY + 0.5f) color = new Vector3f(0.2f, 0.7f, 0.2f); // Grass
                        else color = new Vector3f(0.6f, 0.6f, 0.6f); // Default stone for overhangs/islands

                        if (islandPlacementVal > ISLAND_PLACEMENT_THRESHOLD && worldY > MIN_ISLAND_ALTITUDE && newChunk.isWorldPositionInChunk(new Vector3f(worldX, worldY, worldZ))) {
                            if(noiseGen_FloatingIslandShape.GetNoise(worldX, worldY, worldZ) > ISLAND_DENSITY_THRESHOLD)
                                color = new Vector3f(0.7f, 0.7f, 0.3f); // Island color
                        }
                        if (spirePlacementVal < SPIRE_PLACEMENT_THRESHOLD && worldY > MIN_SPIRE_BASE_ALTITUDE) {
                            float distToSpireCenterApprox = (float)Math.sqrt(spirePlacementVal / SPIRE_PLACEMENT_THRESHOLD) * (Chunk.CHUNK_SIZE_X * 0.5f);
                            float normalizedYInSpire = (worldY - MIN_SPIRE_BASE_ALTITUDE) / MAX_SPIRE_HEIGHT_ABOVE_BASE;
                            float spireRadiusAtY = Chunk.CHUNK_SIZE_X * SPIRE_RADIUS_FACTOR * (1.0f - normalizedYInSpire * 0.7f);
                            if(distToSpireCenterApprox < spireRadiusAtY) color = new Vector3f(0.4f, 0.4f, 0.8f); // Spire color
                        }

                        newChunk.addBlock(new Block(worldX, worldY, worldZ, color));
                    }
                }
            }
        }
        chunks.put(chunkId, newChunk);
        // System.out.println("Generated chunk: " + chunkId); // For debugging
    }

    public Chunk getChunk(ChunkId id) {
        if (!chunks.containsKey(id)) {
            generateChunk(id); // Generate on demand
        }
        return chunks.get(id);
    }

    public Chunk getOrCreateChunk(ChunkId id) { // May not be needed if getChunk handles generation
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
        Chunk chunk = getChunk(chunkId); // Ensures chunk is generated if it's new area
        chunk.addBlock(block);
    }

    public boolean removeBlock(Block blockToRemove) {
        if (blockToRemove == null) return false;
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(blockToRemove.getPosition());
        Chunk chunk = getChunk(chunkId); // Ensures chunk exists
        if (chunk != null) {
            return chunk.removeBlock(blockToRemove);
        }
        return false;
    }

    public boolean removeBlockAt(Vector3f worldPosition) {
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(worldPosition);
        Chunk chunk = getChunk(chunkId); // Ensures chunk exists
        if (chunk != null) {
            Block blockToRemove = null;
            for (Block b : chunk.getBlocks()) { // Iterate over a copy or use iterator if concurrent modification is an issue
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
        Chunk chunk = getChunk(chunkId); // Ensures chunk is loaded/generated
        if (chunk != null) {
            for (Block block : chunk.getBlocks()) {
                if (block.getPosition().distanceSquared(worldPosition) < 0.001f) { // Using distanceSquared for minor efficiency
                    return true;
                }
            }
        }
        return false;
    }

    public List<Block> getBlocksForCollision(Vector3f entityPosition, Vector3f entityDimensions) {
        List<Block> relevantBlocks = new ArrayList<>();
        Set<ChunkId> checkedChunkIds = new HashSet<>();

        Vector3f halfDim = new Vector3f(entityDimensions).mul(0.5f);
        Vector3f entityMinCorner = new Vector3f(entityPosition).sub(halfDim);
        Vector3f entityMaxCorner = new Vector3f(entityPosition).add(halfDim);

        ChunkId minChunkId = Chunk.getChunkIdAtWorldPosition(entityMinCorner);
        ChunkId maxChunkId = Chunk.getChunkIdAtWorldPosition(entityMaxCorner);

        for (int cx = minChunkId.x - 1; cx <= maxChunkId.x + 1; cx++) { // Iterate one chunk beyond AABB extent
            for (int cy = minChunkId.y - 1; cy <= maxChunkId.y + 1; cy++) {
                for (int cz = minChunkId.z - 1; cz <= maxChunkId.z + 1; cz++) {
                    ChunkId currentChunkId = new ChunkId(cx, cy, cz);
                    if (checkedChunkIds.add(currentChunkId)) {
                        Chunk chunk = getChunk(currentChunkId); // This will trigger generation
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
        List<Block> blocksInRadius = new ArrayList<>();
        for (int dx = -radiusInChunks; dx <= radiusInChunks; dx++) {
            for (int dy = -radiusInChunks; dy <= radiusInChunks; dy++) {
                for (int dz = -radiusInChunks; dz <= radiusInChunks; dz++) {
                    ChunkId currentId = new ChunkId(centerChunkId.x + dx, centerChunkId.y + dy, centerChunkId.z + dz);
                    Chunk chunk = getChunk(currentId); // This will trigger generation
                    if (chunk != null) {
                        blocksInRadius.addAll(chunk.getBlocks());
                    }
                }
            }
        }
        return blocksInRadius;
    }

    public List<Chunk> getAllLoadedChunks() {
        // Be cautious if generation happens on other threads.
        // For now, assuming single-threaded access or that `getChunk` is synchronized/safe.
        return new ArrayList<>(chunks.values());
    }

    public void cleanup() {
        chunks.clear();
    }
}