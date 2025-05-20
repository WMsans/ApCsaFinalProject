package World;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator; // Added for safe removal
import java.util.Random;

public class Terrain {
    private List<Block> blocks;
    private final Random random = new Random(); // Keep for potential future use, though Main will handle random color now

    /**
     * Creates a terrain.
     * @param width Number of blocks along X.
     * @param heightLayers Number of block layers along Y (e.g., 1 for flat terrain).
     * @param depth Number of blocks along Z.
     */
    public Terrain(int width, int heightLayers, int depth) {
        blocks = new ArrayList<>();
        generateTerrain(width, heightLayers, depth);
    }

    private void generateTerrain(int width, int heightLayers, int depth) {
        float offsetX = -width / 2.0f + 0.5f;
        float offsetZ = -depth / 2.0f + 0.5f;

        for (int y = 0; y < heightLayers; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    float blockX = offsetX + x;
                    float blockY = (float)y;
                    float blockZ = offsetZ + z;

                    Vector3f color;
                    if (y == heightLayers -1 ) {
                        if ((x + z) % 2 == 0) {
                            color = new Vector3f(0.2f, 0.8f, 0.2f);
                        } else {
                            color = new Vector3f(0.15f, 0.6f, 0.15f);
                        }
                    } else {
                        color = new Vector3f(0.5f, 0.35f, 0.2f);
                    }
                    blocks.add(new Block(blockX, blockY, blockZ, color));
                }
            }
        }
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    /**
     * Adds a block to the terrain.
     * @param block The block to add.
     */
    public void addBlock(Block block) {
        if (block != null && !isBlockAt(block.getPosition())) {
            blocks.add(block);
        }
    }

    /**
     * Removes a specific block instance from the terrain.
     * @param block The block to remove.
     * @return true if the block was found and removed, false otherwise.
     */
    public boolean removeBlock(Block block) {
        return blocks.remove(block);
    }

    /**
     * Removes a block at the specified exact coordinates.
     * Iterates through blocks to find a match.
     * @param position The exact center position of the block to remove.
     * @return true if a block was found and removed, false otherwise.
     */
    public boolean removeBlockAt(Vector3f position) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block b = iterator.next();
            // Using a small epsilon for float comparison, or check if positions are very close
            if (b.getPosition().distanceSquared(position) < 0.001f) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }


    /**
     * Checks if there is a block at the given position.
     * Compares with a small tolerance due to potential floating point inaccuracies.
     * @param position The position to check (should be the center of a block).
     * @return True if a block exists at the position, false otherwise.
     */
    public boolean isBlockAt(Vector3f position) {
        for (Block block : blocks) {
            // Check if block.getPosition() is very close to the given position
            if (block.getPosition().distanceSquared(position) < 0.001f) { // Using distanceSquared for efficiency
                return true;
            }
        }
        return false;
    }

    public void cleanup() {
        blocks.clear();
    }
}
