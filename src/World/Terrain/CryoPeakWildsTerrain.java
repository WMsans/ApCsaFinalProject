package World.Terrain;

import Configuration.Config;
import World.Block;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import World.FastNoiseLite;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CryoPeakWildsTerrain extends BaseTerrainGenerator {

    private final FastNoiseLite noiseGen_BaseSmooth;
    private final FastNoiseLite noiseGen_SpikeWorley; // 2D Cellular (Worley) noise for spike distance

    // Terrain Base
    private static final float TERRAIN_BASE_Y_LEVEL = 60.0f;
    private static final float TERRAIN_SMOOTH_AMPLITUDE = 15.0f;

    // Ice Spikes (using 2D Worley noise for heightmap)
    private static final float SPIKE_WORLEY_FREQUENCY = 0.05f; // Higher frequency = more, denser spikes
    private static final float SPIKE_WORLEY_JITTER = 0.75f;    // Makes spike placement less grid-like
    private static final float SPIKE_WORLEY_MAX_HEIGHT = 180.0f; // Max height of a spike at its center
    private static final float SPIKE_WORLEY_RADIUS = 10.0f;     // Max horizontal radius of a spike's base
    private static final float SPIKE_TAPER_EXPONENT_WORLEY = 1.5f; // Controls spike sharpness (1.0=cone, >1.0 sharper)


    // Colors
    private static final Vector3f COLOR_BASE_GROUND = new Vector3f(0.8f, 0.85f, 0.95f); // Light icy blue/white
    private static final Vector3f COLOR_ICE_SPIKE_MAIN = new Vector3f(0.65f, 0.8f, 0.98f);
    private static final Vector3f COLOR_SNOW_CAP = new Vector3f(0.95f, 0.98f, 1.0f);


    public CryoPeakWildsTerrain(Config config) {
        super(config);
        int worldSeed = new Random().nextInt();

        noiseGen_BaseSmooth = new FastNoiseLite(worldSeed);
        noiseGen_BaseSmooth.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseGen_BaseSmooth.SetFrequency(0.005f);
        noiseGen_BaseSmooth.SetFractalType(FastNoiseLite.FractalType.FBm);
        noiseGen_BaseSmooth.SetFractalOctaves(3);

        noiseGen_SpikeWorley = new FastNoiseLite(worldSeed + 1);
        noiseGen_SpikeWorley.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        noiseGen_SpikeWorley.SetFrequency(SPIKE_WORLEY_FREQUENCY);
        noiseGen_SpikeWorley.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq); // Using squared distance initially
        noiseGen_SpikeWorley.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance); // Distance to closest point
        noiseGen_SpikeWorley.SetCellularJitter(SPIKE_WORLEY_JITTER);
        // Note: The 'Distance' output from FastNoiseLite with EuclideanSq will be squared distance.
        // We'll take the square root later if needed, or adjust SPIKE_WORLEY_RADIUS to be squared.
        // For simplicity, let's assume Euclidean for now with the Distance return type.
        noiseGen_SpikeWorley.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
    }

    @Override
    protected Chunk generateChunkData(ChunkId chunkId) {
        Chunk newChunk = new Chunk(chunkId);
        float worldChunkXBase = (float) chunkId.x * Chunk.CHUNK_SIZE_X;
        float worldChunkYBase = (float) chunkId.y * Chunk.CHUNK_SIZE_Y;
        float worldChunkZBase = (float) chunkId.z * Chunk.CHUNK_SIZE_Z;

        List<Block> tempBlockList = new ArrayList<>();

        for (int lx = 0; lx < Chunk.CHUNK_SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.CHUNK_SIZE_Z; lz++) {
                float worldX = worldChunkXBase + lx + 0.5f;
                float worldZ = worldChunkZBase + lz + 0.5f;

                // --- Base Smooth Terrain Height ---
                float baseSmoothNoise = noiseGen_BaseSmooth.GetNoise(worldX, worldZ);
                float currentSmoothSurfaceY = TERRAIN_BASE_Y_LEVEL + baseSmoothNoise * TERRAIN_SMOOTH_AMPLITUDE;

                // --- Spike Calculation using Worley Distance ---
                float worleyDist = noiseGen_SpikeWorley.GetNoise(worldX, worldZ);
                // The raw output of 'Distance' can vary. For FastNoiseLite, it's often normalized or related to frequency.
                // Let's assume 'worleyDist' is the actual distance.
                // If EuclideanSq was used, you'd do: worleyDist = (float)Math.sqrt(worleyDist);

                float spikeHeightContribution = 0.0f;
                if (worleyDist < SPIKE_WORLEY_RADIUS) {
                    // Calculate a factor from 1 (at center) to 0 (at radius edge)
                    float normalizedDistanceFactor = 1.0f - (worleyDist / SPIKE_WORLEY_RADIUS);
                    // Apply taper exponent
                    spikeHeightContribution = SPIKE_WORLEY_MAX_HEIGHT * (float) Math.pow(normalizedDistanceFactor, SPIKE_TAPER_EXPONENT_WORLEY);
                    spikeHeightContribution = Math.max(0, spikeHeightContribution); // Ensure non-negative
                }

                float totalSurfaceY = currentSmoothSurfaceY + spikeHeightContribution;

                for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                    float worldY = worldChunkYBase + ly + 0.5f;
                    boolean placeBlock = false;
                    Vector3f blockColor = COLOR_BASE_GROUND;

                    if (worldY < currentSmoothSurfaceY) {
                        // Part of the smooth base terrain
                        placeBlock = true;
                        blockColor = COLOR_BASE_GROUND;
                    } else if (worldY < totalSurfaceY && spikeHeightContribution > 0.1f) {
                        // Part of a Worley-generated spike if contribution is significant
                        placeBlock = true;

                        // Determine color for the spike block
                        float normalizedHeightInSpike = (worldY - currentSmoothSurfaceY) / Math.max(1.0f, spikeHeightContribution);
                        if (normalizedHeightInSpike > 0.85f && spikeHeightContribution > SPIKE_WORLEY_MAX_HEIGHT * 0.4f) { // Snow on upper parts of taller spikes
                            blockColor = COLOR_SNOW_CAP;
                        } else {
                            blockColor = COLOR_ICE_SPIKE_MAIN;
                        }
                    }

                    if (placeBlock) {
                        tempBlockList.add(new Block(worldX, worldY, worldZ, blockColor));
                    }
                }
            }
        }

        for (Block b : tempBlockList) {
            newChunk.addBlock(b);
        }
        return newChunk;
    }
}