package Graphics;

import Configuration.Config;
import Physics.CustomAABB; // Import CustomAABB
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
import java.nio.IntBuffer; // Added for AABB EBO
import java.util.List;

public class Renderer {

    private Shader terrainShader;
    private Shader lineShader;
    private Shader crosshairShader;
    private Camera camera;
    private Config config;
    private float gammaValue;

    private ModelRenderer entityRenderer;
    private Skybox skybox;

    private int lineVaoId, lineVboId;
    private int crosshairVaoId, crosshairVboId;

    private Window window;

    // New fields for AABB rendering
    private int aabbVaoId, aabbVboId, aabbEboId;
    private final int AABB_INDEX_COUNT = 24; // 12 lines * 2 indices per line

    public Renderer(Camera camera, Config config, Window window) {
        this.camera = camera;
        this.config = config;
        this.window = window;
        this.gammaValue = config.getGamma();
        this.entityRenderer = new ModelRenderer();
        this.skybox = new Skybox();
        try {
            initTerrainShader();
            initLineShader();
            initCrosshairShader();
            entityRenderer.init();
            skybox.init();
            initAABBRenderer(); // New initialization
        } catch (Exception e) {
            System.err.println("Error initializing renderer:");
            e.printStackTrace();
            System.exit(1);
        }
        initLineBuffers();
        initCrosshairBuffers();
    }

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
        lineShader.createUniform("modelMatrix"); // Added for AABB
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
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 4 * 2 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private void initAABBRenderer() {
        float[] vertices = { // Unit cube vertices (-0.5 to 0.5)
                -0.5f, -0.5f, -0.5f, // 0
                0.5f, -0.5f, -0.5f, // 1
                0.5f,  0.5f, -0.5f, // 2
                -0.5f,  0.5f, -0.5f, // 3
                -0.5f, -0.5f,  0.5f, // 4
                0.5f, -0.5f,  0.5f, // 5
                0.5f,  0.5f,  0.5f, // 6
                -0.5f,  0.5f,  0.5f  // 7
        };

        int[] indices = { // 12 lines for wireframe
                0, 1, 1, 2, 2, 3, 3, 0, // Bottom face
                4, 5, 5, 6, 6, 7, 7, 4, // Top face
                0, 4, 1, 5, 2, 6, 3, 7  // Connecting lines
        };

        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;
        try {
            verticesBuffer = MemoryUtil.memAllocFloat(vertices.length);
            verticesBuffer.put(vertices).flip();

            indicesBuffer = MemoryUtil.memAllocInt(indices.length);
            indicesBuffer.put(indices).flip();

            aabbVaoId = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(aabbVaoId);

            aabbVboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, aabbVboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

            aabbEboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, aabbEboId);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);

            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
            GL20.glEnableVertexAttribArray(0);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
        } finally {
            if (verticesBuffer != null) MemoryUtil.memFree(verticesBuffer);
            if (indicesBuffer != null) MemoryUtil.memFree(indicesBuffer);
        }
    }

    public void renderSkybox() {
        if (skybox != null) {
            skybox.render(this.camera);
        }
    }

    public void renderTerrain(BaseTerrainGenerator terrain, Vector3f playerPosition) {
        camera.updateFrustum();
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
            for (int dy = -renderDist; dy <= renderDist; dy++) {
                for (int dz = -renderDist; dz <= renderDist; dz++) {
                    double distanceSqXZ = dx * dx + dz * dz;
                    double distanceSqY = dy*dy;
                    if (distanceSqXZ <= renderDist * renderDist && distanceSqY <= renderDist * renderDist ) {
                        ChunkId currentChunkId = new ChunkId(playerChunkId.x + dx, playerChunkId.y + dy, playerChunkId.z + dz);
                        Chunk chunkToRender = terrain.getChunk(currentChunkId);
                        if (chunkToRender != null) {
                            ChunkMesh mesh = chunkToRender.getOrCreateMesh();
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

    public void renderEntities(List<Entity> entities, Camera cam, PlayerEntity player) {
        Matrix4f viewMatrix = cam.getViewMatrix();
        Matrix4f projectionMatrix = cam.getProjectionMatrix();

        entityRenderer.getEntityShader().bind();
        entityRenderer.getEntityShader().setUniform("viewMatrix", viewMatrix);
        entityRenderer.getEntityShader().setUniform("projectionMatrix", projectionMatrix);

        for (Entity entity : entities) {
            if (entity.isValid()) {
                Matrix4f entityBaseTransform = entity.getModelMatrix();
                List<ModelComponent> components = entity.getModelComponents();
                if (components.isEmpty() && entity.isValid()) {
                    entity.initializeModels(entityRenderer);
                    components = entity.getModelComponents();
                }

                for (ModelComponent component : components) {
                    if (component.model() != null && component.model().getVaoId() != 0) {
                        if (component.usesEntityShader()) {
                            Matrix4f finalModelMatrix = new Matrix4f(entityBaseTransform).mul(component.localTransform());
                            entityRenderer.getEntityShader().setUniform("modelMatrix", finalModelMatrix);
                            GL30.glBindVertexArray(component.model().getVaoId());
                            GL11.glDrawElements(GL11.GL_TRIANGLES, component.model().getIndexCount(), GL11.GL_UNSIGNED_INT, 0);
                            GL30.glBindVertexArray(0);
                        }
                    }
                }
                // Render AABB if debug mode is enabled
                if (config.isDebugRenderAABBsEnabled()) {
                    CustomAABB worldAABB = entity.getBoundingBoxWorld();
                    renderAABB(worldAABB, viewMatrix, projectionMatrix);
                }
            }
        }
        entityRenderer.getEntityShader().unbind();

        if (player != null && player.getActiveHook() != null && player.getActiveHook().isAttached()) {
            Hook currentPlayersHook = player.getActiveHook();
            Vector3f hookActualAttachPoint = currentPlayersHook.getPosition();
            if (hookActualAttachPoint != null) {
                Vector3f camLeft = camera.getRightDirection(true).mul(-0.2f);
                Vector3f camUp = new Vector3f();
                camera.getForwardDirection(true).cross(camera.getRightDirection(true), camUp);
                camUp.normalize();
                Vector3f camDown = new Vector3f(camUp).mul(0.3f);
                Vector3f camForwardOffset = camera.getForwardDirection(true).mul(0.5f);
                Vector3f lineStartPos = new Vector3f(camera.getPosition())
                        .add(camLeft)
                        .add(camDown)
                        .add(camForwardOffset);
                renderLine(lineStartPos, hookActualAttachPoint, new Vector3f(0.8f, 0.8f, 0.8f), viewMatrix, projectionMatrix);
            }
        }
    }

    private void renderAABB(CustomAABB aabb, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (aabbVaoId == 0) return;

        lineShader.bind();
        lineShader.setUniform("projectionMatrix", projectionMatrix);
        lineShader.setUniform("viewMatrix", viewMatrix);

        Vector3f center = new Vector3f();
        aabb.getCenter(center); // Gets the center of the AABB

        Matrix4f modelMatrix = new Matrix4f()
                .translate(center)
                .scale(aabb.getWidth(), aabb.getHeight(), aabb.getDepth());

        lineShader.setUniform("modelMatrix", modelMatrix);
        lineShader.setUniform("lineColor", new Vector3f(0.0f, 1.0f, 0.0f)); // Green for AABBs

        GL30.glBindVertexArray(aabbVaoId);
        GL11.glDrawElements(GL11.GL_LINES, AABB_INDEX_COUNT, GL11.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(0);

        lineShader.unbind();
    }

    private void renderLine(Vector3f start, Vector3f end, Vector3f color, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        lineShader.bind();
        lineShader.setUniform("projectionMatrix", projectionMatrix);
        lineShader.setUniform("viewMatrix", viewMatrix);
        lineShader.setUniform("modelMatrix", new Matrix4f().identity()); // Lines don't need separate model matrix here
        lineShader.setUniform("lineColor", color);

        FloatBuffer lineVertices = MemoryUtil.memAllocFloat(6);
        lineVertices.put(start.x).put(start.y).put(start.z);
        lineVertices.put(end.x).put(end.y).put(end.z);
        lineVertices.flip();

        GL30.glBindVertexArray(lineVaoId);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVboId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, lineVertices);

        float originalLineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GL11.glLineWidth(config.getHookLineWidth());
        GL11.glDrawArrays(GL11.GL_LINES, 0, 2);
        GL11.glLineWidth(originalLineWidth);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        MemoryUtil.memFree(lineVertices);
        lineShader.unbind();
    }

    public void renderCrosshair() {
        float crosshairSize = 10.0f;
        float screenCenterX = window.getWidth() / 2.0f;
        float screenCenterY = window.getHeight() / 2.0f;

        crosshairShader.bind();
        Matrix4f orthoProjection = new Matrix4f().ortho(0.0f, window.getWidth(), window.getHeight(), 0.0f, -1.0f, 1.0f);
        crosshairShader.setUniform("projection", orthoProjection);
        crosshairShader.setUniform("crosshairColor", new Vector3f(1.0f, 1.0f, 1.0f));

        FloatBuffer crosshairVertices = MemoryUtil.memAllocFloat(4 * 2);
        crosshairVertices.put(screenCenterX - crosshairSize).put(screenCenterY);
        crosshairVertices.put(screenCenterX + crosshairSize).put(screenCenterY);
        crosshairVertices.put(screenCenterX).put(screenCenterY - crosshairSize);
        crosshairVertices.put(screenCenterX).put(screenCenterY + crosshairSize);
        crosshairVertices.flip();

        GL30.glBindVertexArray(crosshairVaoId);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, crosshairVboId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, crosshairVertices);
        GL11.glLineWidth(2.0f);
        GL11.glDrawArrays(GL11.GL_LINES, 0, 4);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        MemoryUtil.memFree(crosshairVertices);
        crosshairShader.unbind();
    }

    public void cleanup() {
        if (terrainShader != null) terrainShader.cleanup();
        if (lineShader != null) lineShader.cleanup();
        if (crosshairShader != null) crosshairShader.cleanup();
        if (entityRenderer != null) entityRenderer.cleanup();
        if (skybox != null) skybox.cleanup();

        if (lineVaoId != 0) GL30.glDeleteVertexArrays(lineVaoId);
        if (lineVboId != 0) GL15.glDeleteBuffers(lineVboId);
        if (crosshairVaoId != 0) GL30.glDeleteVertexArrays(crosshairVaoId);
        if (crosshairVboId != 0) GL15.glDeleteBuffers(crosshairVboId);

        // Cleanup AABB resources
        if (aabbVaoId != 0) GL30.glDeleteVertexArrays(aabbVaoId);
        if (aabbVboId != 0) GL15.glDeleteBuffers(aabbVboId);
        if (aabbEboId != 0) GL15.glDeleteBuffers(aabbEboId);
    }

    public ModelRenderer getEntityRenderer() {
        return entityRenderer;
    }
}