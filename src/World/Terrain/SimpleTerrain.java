package World.Terrain;

import Configuration.Config;
import World.Block;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import World.FastNoiseLite;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class SimpleTerrain extends BaseTerrainGenerator {

    private final FastNoiseLite heightNoise;
    private static final float TERRAIN_BASE_HEIGHT = 30.0f; // Base Y level for terrain
    private static final float TERRAIN_AMPLITUDE = 30.0f;  // Max height variation

    private static final Vector3f COLOR_GRASS = new Vector3f(0.0f, 0.8f, 0.2f); // Green
    private static final Vector3f COLOR_DIRT = new Vector3f(0.5f, 0.35f, 0.2f); // Brown

    public SimpleTerrain(Config config) {
        super(config);

        int worldSeed = config.hashCode(); // Or some other way to get a seed, can be fixed too

        heightNoise = new FastNoiseLite(worldSeed);
        heightNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin); // Using Perlin noise
        heightNoise.SetFrequency(0.01f); // Adjust for desired hill/valley scale
        heightNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        heightNoise.SetFractalOctaves(4);
        heightNoise.SetFractalLacunarity(2.0f);
        heightNoise.SetFractalGain(0.5f);
    }

    @Override
    protected void generateChunk(ChunkId chunkId) {
        Chunk newChunk = new Chunk(chunkId);
        float worldChunkXBase = (float)chunkId.x * Chunk.CHUNK_SIZE_X;
        float worldChunkYBase = (float)chunkId.y * Chunk.CHUNK_SIZE_Y;
        float worldChunkZBase = (float)chunkId.z * Chunk.CHUNK_SIZE_Z;

        List<Block> tempBlockList = new ArrayList<>();

        for (int lx = 0; lx < Chunk.CHUNK_SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.CHUNK_SIZE_Z; lz++) {
                float worldX = worldChunkXBase + lx + 0.5f; // Center of the column
                float worldZ = worldChunkZBase + lz + 0.5f;

                // Get height from 2D Perlin noise
                float heightValue = heightNoise.GetNoise(worldX, worldZ); // Noise is typically -1 to 1
                float surfaceY = TERRAIN_BASE_HEIGHT + heightValue * TERRAIN_AMPLITUDE;

                for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                    float worldY = worldChunkYBase + ly + 0.5f; // Center of the block

                    if (worldY < surfaceY) {
                        Vector3f blockColor;
                        // If this block is the top layer (or very close to it)
                        if (worldY >= surfaceY - 1.0f) {
                            blockColor = COLOR_GRASS;
                        } else {
                            blockColor = COLOR_DIRT;
                        }
                        tempBlockList.add(new Block(worldX, worldY, worldZ, blockColor));
                    }
                }
            }
        }

        for(Block b : tempBlockList) {
            newChunk.addBlock(b);
        }
        newChunk.getOrCreateMesh();
        chunks.put(chunkId, newChunk); // Add to the map in the superclass
    }
}