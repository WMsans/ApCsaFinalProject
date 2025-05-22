import World.Terrain.*;
import Input.*;
import Configuration.*;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main implements Runnable {

    private Window window;
    private Renderer renderer;
    private BaseTerrainGenerator terrain;
    private Input input;
    private PlayerEntity playerEntity;
    private Config config;

    private final String windowTitle = "LWJGL Minecraft Prototype - Chunk System";
    private final int initialWidth = 1280;
    private final int initialHeight = 720;
    private final TerrainType TERRAIN_GENERATOR_TYPE = TerrainType.NETHER;

    private enum TerrainType {
        NETHER,
        SIMPLE,
        CRYO_PEAK_WILDS
    }

    private BaseTerrainGenerator GetTerrainGenerator() {
        switch (TERRAIN_GENERATOR_TYPE) {
            case NETHER:
                return new NetherTerrain(config); //
            case SIMPLE:
                return new SimpleTerrain(config); //
            case CRYO_PEAK_WILDS:
                return new CryoPeakWildsTerrain(config); //
            default:
                throw new IllegalStateException("Unknown terrain generator type selected.");
        }
    }

    public static void main(String[] args) {
        new Main().run();
    }

    @Override
    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private Vector3f findSafeSpawnLocation() {
        int renderDist = config.getRenderDistanceInChunks(); //
        int chunkSearchRadius = Math.max(1, renderDist / 2); // Search a smaller radius for initial spawn
        Vector3f bestSpawnPoint = null;
        float highestGroundFound = -Float.MAX_VALUE;

        // Pre-generate chunks in the search radius around 0,0,0
        for (int dx = -chunkSearchRadius; dx <= chunkSearchRadius; dx++) {
            for (int dz = -chunkSearchRadius; dz <= chunkSearchRadius; dz++) {
                // Iterate relevant Y chunk layers. Start from a reasonable height.
                // For simplicity, let's assume spawns are generally sought in y=0 to y=max chunk height.
                // Adjust if your world has significant verticality at spawn.
                for (int dy = config.getChunkSizeY() / Chunk.CHUNK_SIZE_Y + 2; dy >= -2; dy--) { // Search a few layers of chunks vertically
                    terrain.getChunk(new ChunkId(dx, dy, dz)); //
                }
            }
        }

        // Search for a spawn point
        // Iterate X and Z world coordinates within the search radius
        for (int x = -chunkSearchRadius * Chunk.CHUNK_SIZE_X; x < chunkSearchRadius * Chunk.CHUNK_SIZE_X; x++) {
            for (int z = -chunkSearchRadius * Chunk.CHUNK_SIZE_Z; z < chunkSearchRadius * Chunk.CHUNK_SIZE_Z; z++) {
                // Scan vertically downwards in this X,Z column
                for (int y = config.getChunkSizeY() + Chunk.CHUNK_SIZE_Y * 2; y >= 0; y--) { // Start high enough
                    Vector3f currentPoint = new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f);
                    if (terrain.isBlockAt(currentPoint)) { //
                        // Found ground at 'currentPoint' (specifically, y is the top of the block)
                        if (y > highestGroundFound) {
                            boolean spaceClear = true;
                            for (int i = 1; i <= 5; i++) { // Check 5 blocks above
                                if (terrain.isBlockAt(new Vector3f(x + 0.5f, y + i + 0.5f, z + 0.5f))) {
                                    spaceClear = false;
                                    break;
                                }
                            }
                            if (spaceClear) {
                                highestGroundFound = y;
                                bestSpawnPoint = new Vector3f(x + 0.5f, y + 1.0f + 0.5f, z + 0.5f);
                            }
                        }
                        break; // Move to next X,Z column
                    }
                }
            }
        }

        if (bestSpawnPoint != null) {
            System.out.println("Found safe spawn at: " + bestSpawnPoint);
            return new Vector3f(bestSpawnPoint.x, bestSpawnPoint.y, bestSpawnPoint.z);
        } else {
            System.err.println("Could not find a safe spawn location. Defaulting to high up.");
            return new Vector3f(0, config.getChunkSizeY() + 100.0f, 0); // Fallback
        }
    }


    private void init() throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        config = new Config("Configuration/config.properties"); //

        window = new Window(windowTitle, initialWidth, initialHeight); //
        window.create(); //
        input = new Input(window.getWindowHandle()); //

        glfwMakeContextCurrent(window.getWindowHandle()); //
        glfwSwapInterval(1); // Enable v-sync //
        glfwShowWindow(window.getWindowHandle()); //
        GL.createCapabilities();

        glClearColor(0.1f, 0.1f, 0.15f, 0.0f); // Background color
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        terrain = GetTerrainGenerator();

        //MODIFICATION START
        Vector3f playerStartPosition = findSafeSpawnLocation();
        //MODIFICATION END
        playerEntity = new PlayerEntity(input, window, terrain, playerStartPosition, config); //

        renderer = new Renderer(playerEntity.getCamera(), config); //
    }

    private void loop() {
        float lastTime = (float) glfwGetTime();
        float deltaTime;

        while (!window.shouldClose()) { //
            float currentTime = (float) glfwGetTime(); // Get current time for game logic
            deltaTime = currentTime - lastTime;
            lastTime = currentTime;

            // Cap delta time to prevent physics issues if game hangs
            if (deltaTime > 0.1f) deltaTime = 0.1f;
            if (deltaTime <= 0) deltaTime = 1.0f / 60.0f; // Avoid zero or negative delta

            input.pollEvents(); // Update input states (e.g., for isKeyPressed) //

            if (input.isKeyPressed(GLFW_KEY_ESCAPE)) { //
                glfwSetWindowShouldClose(window.getWindowHandle(), true); //
            }

            // Pass deltaTime and currentTime to player update
            playerEntity.update(deltaTime, currentTime); //

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.renderTerrain(terrain, playerEntity.getPosition()); //
            window.swapBuffers(); //
            glfwPollEvents(); // Process OS events
        }
    }

    private void cleanup() {
        if (renderer != null) renderer.cleanup(); //
        if (terrain != null) terrain.cleanup(); //

        if (window != null && window.getWindowHandle() != NULL) { //
            glfwFreeCallbacks(window.getWindowHandle()); //
            glfwDestroyWindow(window.getWindowHandle()); //
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) callback.free();
    }
}