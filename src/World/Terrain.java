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
    private final Random random = new Random();

    private final FastNoiseLite noiseGen_BaseHeight;
    private final FastNoiseLite noiseGen_3DDensity;
    private final FastNoiseLite noiseGen_FloatingIslandPlacement;
    private final FastNoiseLite noiseGen_FloatingIslandShape;
    private final FastNoiseLite noiseGen_SpirePlacement;
    private final FastNoiseLite noiseGen_SpireShape;

    private static final float SEA_LEVEL = 64.0f;
    private static final float BASE_TERRAIN_AMPLITUDE = 70.0f;
    private static final float DENSITY_THRESHOLD_BELOW_SURFACE = -0.1f;
    private static final float DENSITY_THRESHOLD_ABOVE_SURFACE = 0.25f;
    private static final float MIN_ISLAND_ALTITUDE = 100.0f;
    private static final float ISLAND_PLACEMENT_THRESHOLD = 0.65f;
    private static final float ISLAND_DENSITY_THRESHOLD = 0.05f;
    private static final float ISLAND_CORE_HEIGHT_VARIATION = 20.0f;
    private static final float ISLAND_THICKNESS_VARIATION = 15.0f;
    private static final float MIN_SPIRE_BASE_ALTITUDE = 50.0f;
    private static final float MAX_SPIRE_HEIGHT_ABOVE_BASE = 150.0f;
    private static final float SPIRE_PLACEMENT_THRESHOLD = 0.75f; // Note: This is for Distance2, so lower is "more likely"
    private static final float SPIRE_DENSITY_THRESHOLD = 0.4f;
    private static final float SPIRE_RADIUS_FACTOR = 0.15f;


    public Terrain(Config config) {
        this.chunks = new HashMap<>();
        this.config = config;
        Chunk.setChunkDimensions(config.getChunkSizeX(), config.getChunkSizeY(), config.getChunkSizeZ());

        int worldSeed = 1337;

        noiseGen_BaseHeight = new FastNoiseLite(worldSeed);
        noiseGen_BaseHeight.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseGen_BaseHeight.SetFrequency(0.003f);
        noiseGen_BaseHeight.SetFractalType(FastNoiseLite.FractalType.FBm);
        noiseGen_BaseHeight.SetFractalOctaves(5);
        noiseGen_BaseHeight.SetFractalLacunarity(2.0f);
        noiseGen_BaseHeight.SetFractalGain(0.5f);

        noiseGen_3DDensity = new FastNoiseLite(worldSeed + 1);
        noiseGen_3DDensity.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseGen_3DDensity.SetFrequency(0.015f);
        noiseGen_3DDensity.SetFractalType(FastNoiseLite.FractalType.Ridged);
        noiseGen_3DDensity.SetFractalOctaves(4);
        noiseGen_3DDensity.SetFractalLacunarity(2.2f);
        noiseGen_3DDensity.SetFractalGain(0.45f);

        noiseGen_FloatingIslandPlacement = new FastNoiseLite(worldSeed + 2);
        noiseGen_FloatingIslandPlacement.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        noiseGen_FloatingIslandPlacement.SetFrequency(0.0025f);
        noiseGen_FloatingIslandPlacement.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
        noiseGen_FloatingIslandPlacement.SetCellularReturnType(FastNoiseLite.CellularReturnType.CellValue);
        noiseGen_FloatingIslandPlacement.SetCellularJitter(0.8f);

        noiseGen_FloatingIslandShape = new FastNoiseLite(worldSeed + 3);
        noiseGen_FloatingIslandShape.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseGen_FloatingIslandShape.SetFrequency(0.025f);
        noiseGen_FloatingIslandShape.SetFractalType(FastNoiseLite.FractalType.FBm);
        noiseGen_FloatingIslandShape.SetFractalOctaves(3);

        noiseGen_SpirePlacement = new FastNoiseLite(worldSeed + 4);
        noiseGen_SpirePlacement.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        noiseGen_SpirePlacement.SetFrequency(0.008f); // Lower frequency for sparser spires
        noiseGen_SpirePlacement.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
        noiseGen_SpirePlacement.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2); // Distance to nearest point (smaller is closer)
        noiseGen_SpirePlacement.SetCellularJitter(0.6f);


        noiseGen_SpireShape = new FastNoiseLite(worldSeed + 5);
        noiseGen_SpireShape.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        noiseGen_SpireShape.SetFrequency(0.05f);
    }

    private void generateChunk(ChunkId chunkId) {
        Chunk newChunk = new Chunk(chunkId); // This now initializes its own AABB and an empty ChunkMesh
        float worldChunkXBase = (float)chunkId.x * Chunk.CHUNK_SIZE_X;
        float worldChunkYBase = (float)chunkId.y * Chunk.CHUNK_SIZE_Y;
        float worldChunkZBase = (float)chunkId.z * Chunk.CHUNK_SIZE_Z;

        List<Block> tempBlockList = new ArrayList<>(); // Generate blocks into a temporary list first

        for (int lx = 0; lx < Chunk.CHUNK_SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.CHUNK_SIZE_Z; lz++) {
                float worldX = worldChunkXBase + lx + 0.5f;
                float worldZ = worldChunkZBase + lz + 0.5f;

                float baseHeightNoiseVal = noiseGen_BaseHeight.GetNoise(worldX, worldZ);
                float currentSurfaceY = SEA_LEVEL + baseHeightNoiseVal * BASE_TERRAIN_AMPLITUDE;

                float islandPlacementVal = (noiseGen_FloatingIslandPlacement.GetNoise(worldX, worldZ) + 1) / 2f;
                float spirePlacementNoise = noiseGen_SpirePlacement.GetNoise(worldX, worldZ); // Raw Distance2 value

                for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                    float worldY = worldChunkYBase + ly + 0.5f;
                    boolean placeBlock = false;

                    float densityVal3D = noiseGen_3DDensity.GetNoise(worldX, worldY, worldZ);

                    if (worldY < currentSurfaceY) {
                        if (densityVal3D > DENSITY_THRESHOLD_BELOW_SURFACE) {
                            placeBlock = true;
                        }
                    } else {
                        if (densityVal3D > DENSITY_THRESHOLD_ABOVE_SURFACE) {
                            placeBlock = true;
                        }
                    }

                    if (islandPlacementVal > ISLAND_PLACEMENT_THRESHOLD && worldY > MIN_ISLAND_ALTITUDE) {
                        float islandShapeVal = noiseGen_FloatingIslandShape.GetNoise(worldX, worldY, worldZ);
                        float islandCoreBaseY = MIN_ISLAND_ALTITUDE + (islandPlacementVal - ISLAND_PLACEMENT_THRESHOLD) * 50.0f;
                        float islandCoreTopY = islandCoreBaseY + ISLAND_CORE_HEIGHT_VARIATION + baseHeightNoiseVal * ISLAND_THICKNESS_VARIATION;

                        if (worldY > islandCoreBaseY && worldY < islandCoreTopY) {
                            if (islandShapeVal > ISLAND_DENSITY_THRESHOLD) {
                                placeBlock = true;
                            } else if (islandShapeVal < -0.3f && placeBlock){
                                placeBlock = false;
                            }
                        } else if (placeBlock && worldY > MIN_ISLAND_ALTITUDE -10 && worldY < islandCoreBaseY && islandShapeVal < -0.2f) {
                            placeBlock = false;
                        }
                    }

                    // For spirePlacementNoise (Distance2), lower values mean closer to a spire center.
                    // We need to define what "close enough" means. SPIRE_PLACEMENT_THRESHOLD could be e.g., 0.05f
                    // if Distance2 output is typically 0 to 1. Let's adjust its use.
                    // A threshold of 0.75f for Distance2 is very high, it should be low for "close".
                    // Let's assume SPIRE_PLACEMENT_THRESHOLD (e.g., 0.02f for Distance2 output usually in [0,1])
                    // defines "close enough to a spire center".
                    float actualSpirePlacementThreshold = 0.02f; // Example for Distance2 where lower is better

                    if (spirePlacementNoise < actualSpirePlacementThreshold && worldY > MIN_SPIRE_BASE_ALTITUDE && worldY < currentSurfaceY + MAX_SPIRE_HEIGHT_ABOVE_BASE) {
                        float distToSpireCenterApprox = (float)Math.sqrt(spirePlacementNoise / actualSpirePlacementThreshold) * (Chunk.CHUNK_SIZE_X * 0.5f);
                        float normalizedYInSpire = (worldY - MIN_SPIRE_BASE_ALTITUDE) / MAX_SPIRE_HEIGHT_ABOVE_BASE;
                        float spireRadiusAtY = Chunk.CHUNK_SIZE_X * SPIRE_RADIUS_FACTOR * (1.0f - normalizedYInSpire * 0.7f);

                        if (distToSpireCenterApprox < spireRadiusAtY) {
                            float spireBodyNoise = (noiseGen_SpireShape.GetNoise(worldX * 0.5f, worldY * 2.0f, worldZ * 0.5f) +1)/2f;
                            if (spireBodyNoise > SPIRE_DENSITY_THRESHOLD - (normalizedYInSpire * 0.2f) ) {
                                placeBlock = true;
                            }
                        }
                    }


                    if (placeBlock) {
                        Vector3f color;
                        if (worldY < SEA_LEVEL - 20) color = new Vector3f(0.3f, 0.3f, 0.35f);
                        else if (worldY < currentSurfaceY - 1.5f) color = new Vector3f(0.5f, 0.45f, 0.4f);
                        else if (worldY < currentSurfaceY + 0.5f) color = new Vector3f(0.2f, 0.7f, 0.2f);
                        else color = new Vector3f(0.6f, 0.6f, 0.6f);

                        if (islandPlacementVal > ISLAND_PLACEMENT_THRESHOLD && worldY > MIN_ISLAND_ALTITUDE &&
                                noiseGen_FloatingIslandShape.GetNoise(worldX, worldY, worldZ) > ISLAND_DENSITY_THRESHOLD) {
                            color = new Vector3f(0.7f, 0.7f, 0.3f);
                        }
                        // Check again for spire coloration with the corrected understanding of Distance2
                        if (spirePlacementNoise < actualSpirePlacementThreshold && worldY > MIN_SPIRE_BASE_ALTITUDE) {
                            float distToSpireCenterApprox = (float)Math.sqrt(spirePlacementNoise / actualSpirePlacementThreshold) * (Chunk.CHUNK_SIZE_X * 0.5f);
                            float normalizedYInSpire = (worldY - MIN_SPIRE_BASE_ALTITUDE) / MAX_SPIRE_HEIGHT_ABOVE_BASE;
                            if (normalizedYInSpire < 0) normalizedYInSpire = 0; // Clamp
                            if (normalizedYInSpire > 1) normalizedYInSpire = 1; // Clamp
                            float spireRadiusAtY = Chunk.CHUNK_SIZE_X * SPIRE_RADIUS_FACTOR * (1.0f - normalizedYInSpire * 0.7f);
                            if(distToSpireCenterApprox < spireRadiusAtY) color = new Vector3f(0.4f, 0.4f, 0.8f);
                        }

                        tempBlockList.add(new Block(worldX, worldY, worldZ, color));
                    }
                }
            }
        }
        // Add all generated blocks to the chunk, which will trigger one mesh rebuild flag
        for(Block b : tempBlockList) {
            newChunk.addBlock(b); // addBlock in Chunk sets needsMeshRebuild = true
        }
        newChunk.getOrCreateMesh(); // Force initial mesh generation after blocks are added
        chunks.put(chunkId, newChunk);
    }

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
        Chunk chunk = getChunk(chunkId);
        chunk.addBlock(block); // This will flag the chunk for mesh rebuild
    }

    public boolean removeBlock(Block blockToRemove) {
        if (blockToRemove == null) return false;
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(blockToRemove.getPosition());
        Chunk chunk = getChunk(chunkId);
        if (chunk != null) {
            boolean removed = chunk.removeBlock(blockToRemove); // This flags for rebuild
            return removed;
        }
        return false;
    }

    public boolean removeBlockAt(Vector3f worldPosition) {
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(worldPosition);
        Chunk chunk = getChunk(chunkId);
        if (chunk != null) {
            // Iterate over a copy for safe removal, or use an iterator
            Block toRemove = null;
            for (Block b : chunk.getModifiableBlocks()) { // Use modifiable if iterating and removing
                if (b.getPosition().distanceSquared(worldPosition) < 0.001f) {
                    toRemove = b;
                    break;
                }
            }
            if (toRemove != null) {
                return chunk.removeBlock(toRemove); // This flags for rebuild
            }
        }
        return false;
    }

    public boolean isBlockAt(Vector3f worldPosition) {
        ChunkId chunkId = Chunk.getChunkIdAtWorldPosition(worldPosition);
        Chunk chunk = getChunk(chunkId);
        if (chunk != null) {
            // Check against the chunk's block list (which is efficient if few blocks per chunk)
            // For very dense chunks, a spatial data structure within the chunk might be better,
            // but for now, direct iteration is fine given block positions are world absolute.
            for (Block block : chunk.getBlocks()) { // Using getBlocks() which is unmodifiable
                if (block.getPosition().distanceSquared(worldPosition) < 0.001f) {
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

        for (int cx = minChunkId.x - 1; cx <= maxChunkId.x + 1; cx++) {
            for (int cy = minChunkId.y - 1; cy <= maxChunkId.y + 1; cy++) {
                for (int cz = minChunkId.z - 1; cz <= maxChunkId.z + 1; cz++) {
                    ChunkId currentChunkId = new ChunkId(cx, cy, cz);
                    if (checkedChunkIds.add(currentChunkId)) {
                        Chunk chunk = getChunk(currentChunkId);
                        if (chunk != null) {
                            relevantBlocks.addAll(chunk.getBlocks()); // Use unmodifiable list
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
                    Chunk chunk = getChunk(currentId);
                    if (chunk != null) {
                        blocksInRadius.addAll(chunk.getBlocks()); // Use unmodifiable list
                    }
                }
            }
        }
        return blocksInRadius;
    }

    public List<Chunk> getAllLoadedChunks() {
        return new ArrayList<>(chunks.values());
    }

    public void cleanup() {
        for (Chunk chunk : chunks.values()) {
            chunk.cleanupMesh(); // Ensure each chunk's mesh is cleaned
        }
        chunks.clear();
    }
}