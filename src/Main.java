import World.Terrain;
import Input.*;
import Configuration.*;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main implements Runnable {

    private Window window;
    private Renderer renderer;
    private Terrain terrain;
    private Input input;
    private PlayerEntity playerEntity;
    private Config config;

    private final String windowTitle = "LWJGL Minecraft Prototype - Chunk System";
    private final int initialWidth = 1280;
    private final int initialHeight = 720;

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

    private void init() throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        config = new Config("Configuration/config.properties");

        window = new Window(windowTitle, initialWidth, initialHeight);
        window.create();
        input = new Input(window.getWindowHandle());

        glfwMakeContextCurrent(window.getWindowHandle());
        glfwSwapInterval(1);
        glfwShowWindow(window.getWindowHandle());
        GL.createCapabilities();

        glClearColor(0.1f, 0.1f, 0.15f, 0.0f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Initialize terrain with desired initial block dimensions and config
        // The terrain generator will now use chunk sizes from config.
        // For example, 64x16x64 blocks total for initial generation.
        terrain = new Terrain(64, config.getChunkSizeY() * 2, 64, config);


        Vector3f playerStartPosition = new Vector3f(0, config.getChunkSizeY() + 20.0f, 0); // Start above generated terrain
        playerEntity = new PlayerEntity(input, window, terrain, playerStartPosition, config);

        renderer = new Renderer(playerEntity.getCamera(), config);
    }

    private void loop() {
        float lastTime = (float) glfwGetTime();
        float deltaTime;

        while (!window.shouldClose()) {
            float currentTime = (float) glfwGetTime();
            deltaTime = currentTime - lastTime;
            lastTime = currentTime;

            if (deltaTime > 0.1f) deltaTime = 0.1f;
            if (deltaTime <= 0) deltaTime = 1.0f / 60.0f;

            input.pollEvents();

            if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
                glfwSetWindowShouldClose(window.getWindowHandle(), true);
            }

            playerEntity.update(deltaTime);

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            // Pass player position to renderer for determining which chunks to render
            renderer.renderTerrain(terrain, playerEntity.getPosition());
            window.swapBuffers();
            glfwPollEvents();
        }
    }

    private void cleanup() {
        if (renderer != null) renderer.cleanup();
        if (terrain != null) terrain.cleanup();

        if (window != null && window.getWindowHandle() != NULL) {
            glfwFreeCallbacks(window.getWindowHandle());
            glfwDestroyWindow(window.getWindowHandle());
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) callback.free();
    }
}
