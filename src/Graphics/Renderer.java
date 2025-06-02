package Graphics;

import Configuration.Config;
import World.Terrain.BaseTerrainGenerator;
import World.Chunk.*;
import World.Entities.Entity;
import World.Entities.PlayerEntity;
import World.Entities.Hook;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

public class Renderer {

    private Shader terrainShader;
    private Shader lineShader;
    private Shader crosshairShader;
    private Camera camera;
    private Config config;
    private float gammaValue;

    private ModelRenderer entityRenderer;
    private Skybox skybox; // Added Skybox

    private int lineVaoId, lineVboId;
    private int crosshairVaoId, crosshairVboId;

    private Window window;

    public Renderer(Camera camera, Config config, Window window) {
        this.camera = camera;
        this.config = config;
        this.window = window;
        this.gammaValue = config.getGamma();
        this.entityRenderer = new ModelRenderer();
        this.skybox = new Skybox(); // Instantiate Skybox
        try {
            initTerrainShader();
            initLineShader();
            initCrosshairShader();
            entityRenderer.init();
            skybox.init(); // Initialize Skybox
        } catch (Exception e) {
            System.err.println("Error initializing renderer:");
            e.printStackTrace();
            System.exit(1);
        }
        initLineBuffers();
        initCrosshairBuffers();
    }

    // ... (initTerrainShader, initLineShader, initCrosshairShader, initLineBuffers, initCrosshairBuffers remain the same)
    private void initTerrainShader() throws Exception {
        terrainShader = new Shader();
        terrainShader.createVertexShader(Shader.loadResource("/shaders/vertex.glsl"));
        terrainShader.createFragmentShader(Shader.loadResource("/shaders/fragment.glsl"));
        terrainShader.link();
        terrainShader.createUniform("projectionMatrix");
        terrainShader.createUniform("viewMatrix");
        terrainShader.createUniform("modelMatrix");
        terrainShader.createUniform("lightPos");
        terrainShader.createUniform("lightColor");
        terrainShader.createUniform("gamma");
        terrainShader.createUniform("viewPos");

        // Grid uniforms
        terrainShader.createUniform("gridSpacing");
        terrainShader.createUniform("gridLineWidth");
        terrainShader.createUniform("gridIntensity");
        terrainShader.createUniform("gridColorGround");
        terrainShader.createUniform("gridColorMountain");
        terrainShader.createUniform("gridTransitionHeight");
        terrainShader.createUniform("gridTransitionRange");
    }

    private void initLineShader() throws Exception {
        lineShader = new Shader();
        lineShader.createVertexShader(Shader.loadResource("/shaders/line_vertex.glsl"));
        lineShader.createFragmentShader(Shader.loadResource("/shaders/line_fragment.glsl"));
        lineShader.link();
        lineShader.createUniform("projectionMatrix");
        lineShader.createUniform("viewMatrix");
        lineShader.createUniform("lineColor");
    }

    private void initCrosshairShader() throws Exception {
        crosshairShader = new Shader();
        crosshairShader.createVertexShader(Shader.loadResource("/shaders/crosshair_vertex.glsl"));
        crosshairShader.createFragmentShader(Shader.loadResource("/shaders/crosshair_fragment.glsl"));
        crosshairShader.link();
        crosshairShader.createUniform("projection");
        crosshairShader.createUniform("crosshairColor");
    }


    private void initLineBuffers() {
        lineVaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(lineVaoId);

        lineVboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVboId);
        // Allocate for 2 vertices, each with 3 float components (x,y,z)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 2 * 3 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private void initCrosshairBuffers() {
        crosshairVaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(crosshairVaoId);

        crosshairVboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, crosshairVboId);
        // 4 vertices (2 for horizontal, 2 for vertical line), each 2 floats (x,y)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 4 * 2 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);

        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }


    public void renderSkybox() { // Renamed to not take camera as skybox uses the main camera
        if (skybox != null) {
            skybox.render(this.camera);
        }
    }


    public void renderTerrain(BaseTerrainGenerator terrain, Vector3f playerPosition) {
        camera.updateFrustum(); // It's good to update frustum once per frame if culling is used

        terrainShader.bind();
        terrainShader.setUniform("projectionMatrix", camera.getProjectionMatrix());
        terrainShader.setUniform("viewMatrix", camera.getViewMatrix());
        terrainShader.setUniform("lightPos", camera.getPosition());
        terrainShader.setUniform("lightColor", new Vector3f(1.0f, 1.0f, 1.0f));
        terrainShader.setUniform("gamma", this.gammaValue);
        terrainShader.setUniform("viewPos", camera.getPosition());

        terrainShader.setUniform("gridSpacing", config.getGridSpacing());
        terrainShader.setUniform("gridLineWidth", config.getGridLineWidth());
        terrainShader.setUniform("gridIntensity", config.getGridIntensity());
        terrainShader.setUniform("gridColorGround", config.getGridColorGround());
        terrainShader.setUniform("gridColorMountain", config.getGridColorMountain());
        terrainShader.setUniform("gridTransitionHeight", config.getGridTransitionHeight());
        terrainShader.setUniform("gridTransitionRange", config.getGridTransitionRange());


        ChunkId playerChunkId = Chunk.getChunkIdAtWorldPosition(playerPosition);
        int renderDist = config.getRenderDistanceInChunks();

        for (int dx = -renderDist; dx <= renderDist; dx++) {
            for (int dy = -renderDist; dy <= renderDist; dy++) { // Keep Y for potential vertical chunks
                for (int dz = -renderDist; dz <= renderDist; dz++) {
                    double distanceSqXZ = dx * dx + dz * dz;
                    // Consider Y distance if your render distance is truly spherical/cubical
                    double distanceSqY = dy*dy;

                    if (distanceSqXZ <= renderDist * renderDist && distanceSqY <= renderDist * renderDist ) {
                        ChunkId currentChunkId = new ChunkId(playerChunkId.x + dx, playerChunkId.y + dy, playerChunkId.z + dz);
                        Chunk chunkToRender = terrain.getChunk(currentChunkId); // getChunk handles async loading
                        if (chunkToRender != null) {
                            // Optional: Frustum culling. Can be expensive.
                            // if (!camera.isAABBInFrustum(chunkToRender.getAABB())) {
                            //    continue;
                            // }
                            ChunkMesh mesh = chunkToRender.getOrCreateMesh(); // Ensures mesh is built if needed
                            if (mesh != null && mesh.isInitialized()) {
                                Matrix4f modelMatrix = new Matrix4f().translate(chunkToRender.getMinCorner());
                                terrainShader.setUniform("modelMatrix", modelMatrix);
                                mesh.render();
                            }
                        }
                    }
                }
            }
        }
        terrainShader.unbind();
    }
    // ... (renderEntities, renderLine, renderCrosshair remain the same)

    public void renderEntities(List<Entity> entities, Camera cam, PlayerEntity player) {
        Matrix4f viewMatrix = cam.getViewMatrix();
        Matrix4f projectionMatrix = cam.getProjectionMatrix();

        entityRenderer.getEntityShader().bind();
        entityRenderer.getEntityShader().setUniform("viewMatrix", viewMatrix);
        entityRenderer.getEntityShader().setUniform("projectionMatrix", projectionMatrix);
        // TODO: Set lighting uniforms if your entity shader needs them.
        // entityRenderer.getEntityShader().setUniform("lightPos", cam.getPosition());
        // entityRenderer.getEntityShader().setUniform("lightColor", new Vector3f(1.0f, 1.0f, 1.0f));
        // entityRenderer.getEntityShader().setUniform("gamma", this.gammaValue);
        // entityRenderer.getEntityShader().setUniform("viewPos", cam.getPosition());


        for (Entity entity : entities) {
            if (entity.isValid()) {
                Matrix4f entityBaseTransform = entity.getModelMatrix(); // Gets position, rotation, scale of the entity root
                List<ModelComponent> components = entity.getModelComponents();

                // Ensure models are initialized (VAO/VBO built)
                if (components.isEmpty() && entity.isValid()) { // Check if populate was called
                    entity.initializeModels(entityRenderer);
                    components = entity.getModelComponents(); // Re-fetch after potential initialization
                }


                for (ModelComponent component : components) {
                    if (component.model() != null && component.model().getVaoId() != 0) {
                        // If this specific component should use the main entity shader
                        if (component.usesEntityShader()) {
                            // Combine entity's base transform with the component's local transform
                            Matrix4f finalModelMatrix = new Matrix4f(entityBaseTransform).mul(component.localTransform());
                            entityRenderer.getEntityShader().setUniform("modelMatrix", finalModelMatrix);

                            GL30.glBindVertexArray(component.model().getVaoId());
                            GL11.glDrawElements(GL11.GL_TRIANGLES, component.model().getIndexCount(), GL11.GL_UNSIGNED_INT, 0);
                            GL30.glBindVertexArray(0);
                        } else {
                            // Here you could add logic to use a different shader for this component
                            // e.g., if (component.shader() != null) component.shader().bind()... etc.
                        }
                    }
                }
            }
        }
        entityRenderer.getEntityShader().unbind();

        // Render hook line for the player
        if (player != null && player.getActiveHook() != null && player.getActiveHook().isAttached()) {
            Hook currentPlayersHook = player.getActiveHook();
            Vector3f hookActualAttachPoint = currentPlayersHook.getPosition(); // The hook's position is its attachment point

            if (hookActualAttachPoint != null) {
                // Calculate a visually appropriate start point for the line from the player's view
                // Example: slightly to the right and below the camera's center, and a bit forward
                Vector3f camLeft = camera.getRightDirection(true).mul(-0.2f); // Offset to the left
                Vector3f camUp = new Vector3f();
                camera.getForwardDirection(true).cross(camera.getRightDirection(true), camUp);
                camUp.normalize();
                Vector3f camDown = new Vector3f(camUp).mul(0.3f); // Offset downwards
                Vector3f camForwardOffset = camera.getForwardDirection(true).mul(0.5f); // Offset forwards from camera eye

                Vector3f lineStartPos = new Vector3f(camera.getPosition())
                        .add(camLeft)
                        .add(camDown)
                        .add(camForwardOffset);

                renderLine(lineStartPos, hookActualAttachPoint, new Vector3f(0.8f, 0.8f, 0.8f), viewMatrix, projectionMatrix);
            }
        }
    }

    private void renderLine(Vector3f start, Vector3f end, Vector3f color, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        lineShader.bind();
        lineShader.setUniform("projectionMatrix", projectionMatrix);
        lineShader.setUniform("viewMatrix", viewMatrix);
        lineShader.setUniform("lineColor", color);

        FloatBuffer lineVertices = MemoryUtil.memAllocFloat(6); // 2 vertices * 3 floats
        lineVertices.put(start.x).put(start.y).put(start.z);
        lineVertices.put(end.x).put(end.y).put(end.z);
        lineVertices.flip();

        GL30.glBindVertexArray(lineVaoId);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVboId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, lineVertices); // Update existing buffer

        // Store original line width and set desired width
        float originalLineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GL11.glLineWidth(config.getHookLineWidth()); // Use config for line width

        GL11.glDrawArrays(GL11.GL_LINES, 0, 2); // Draw 2 vertices

        // Restore original line width
        GL11.glLineWidth(originalLineWidth);


        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); // Unbind VBO
        GL30.glBindVertexArray(0); // Unbind VAO
        MemoryUtil.memFree(lineVertices);
        lineShader.unbind();
    }
    public void renderCrosshair() {
        float crosshairSize = 10.0f; // Size in screen pixels
        float screenCenterX = window.getWidth() / 2.0f;
        float screenCenterY = window.getHeight() / 2.0f;

        crosshairShader.bind();

        // Orthographic projection for 2D rendering
        Matrix4f orthoProjection = new Matrix4f().ortho(0.0f, window.getWidth(), window.getHeight(), 0.0f, -1.0f, 1.0f);
        crosshairShader.setUniform("projection", orthoProjection);
        crosshairShader.setUniform("crosshairColor", new Vector3f(1.0f, 1.0f, 1.0f)); // White crosshair

        FloatBuffer crosshairVertices = MemoryUtil.memAllocFloat(4 * 2); // 4 points, 2 coords each
        // Horizontal line
        crosshairVertices.put(screenCenterX - crosshairSize).put(screenCenterY);
        crosshairVertices.put(screenCenterX + crosshairSize).put(screenCenterY);
        // Vertical line
        crosshairVertices.put(screenCenterX).put(screenCenterY - crosshairSize);
        crosshairVertices.put(screenCenterX).put(screenCenterY + crosshairSize);
        crosshairVertices.flip();

        GL30.glBindVertexArray(crosshairVaoId);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, crosshairVboId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, crosshairVertices);

        GL11.glLineWidth(2.0f); // Set line width for the crosshair
        GL11.glDrawArrays(GL11.GL_LINES, 0, 4); // Draw 2 lines (4 vertices)

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        MemoryUtil.memFree(crosshairVertices);
        crosshairShader.unbind();
    }

    public void cleanup() {
        if (terrainShader != null) terrainShader.cleanup();
        if (lineShader != null) lineShader.cleanup();
        if (crosshairShader != null) crosshairShader.cleanup(); // Cleanup crosshair shader
        if (entityRenderer != null) entityRenderer.cleanup();
        if (skybox != null) skybox.cleanup(); // Cleanup Skybox

        if (lineVaoId != 0) GL30.glDeleteVertexArrays(lineVaoId);
        if (lineVboId != 0) GL15.glDeleteBuffers(lineVboId);
        if (crosshairVaoId != 0) GL30.glDeleteVertexArrays(crosshairVaoId); // Cleanup crosshair VAO
        if (crosshairVboId != 0) GL15.glDeleteBuffers(crosshairVboId);   // Cleanup crosshair VBO
    }

    public ModelRenderer getEntityRenderer() {
        return entityRenderer;
    }
}