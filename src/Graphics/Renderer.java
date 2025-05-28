package Graphics;

import Configuration.Config;
import World.Terrain.BaseTerrainGenerator;
import World.Chunk.*;
import World.Entities.Entity;
import World.Entities.PlayerEntity;
import World.Entities.Hook; // Keep for hook line rendering logic
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
    private Camera camera;
    private Config config;
    private float gammaValue;

    private ModelRenderer entityRenderer;

    private int lineVaoId, lineVboId;

    public Renderer(Camera camera, Config config) {
        this.camera = camera;
        this.config = config;
        this.gammaValue = config.getGamma();
        this.entityRenderer = new ModelRenderer();
        try {
            initTerrainShader();
            initLineShader();
            entityRenderer.init();
        } catch (Exception e) {
            System.err.println("Error initializing renderer:");
            e.printStackTrace();
            System.exit(1);
        }
        initLineBuffers();
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

    private void initLineBuffers() {
        lineVaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(lineVaoId);

        lineVboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVboId);
        // Allocate buffer size for 2 vertices (start and end point of a line), each with 3 floats (x,y,z)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 2 * 3 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }


    public void renderTerrain(BaseTerrainGenerator terrain, Vector3f playerPosition) {
        camera.updateFrustum();

        terrainShader.bind();
        terrainShader.setUniform("projectionMatrix", camera.getProjectionMatrix());
        terrainShader.setUniform("viewMatrix", camera.getViewMatrix());
        terrainShader.setUniform("lightPos", camera.getPosition());
        terrainShader.setUniform("lightColor", new Vector3f(1.0f, 1.0f, 1.0f));
        terrainShader.setUniform("gamma", gammaValue);
        terrainShader.setUniform("viewPos", camera.getPosition());

        ChunkId playerChunkId = Chunk.getChunkIdAtWorldPosition(playerPosition);
        int renderDist = config.getRenderDistanceInChunks();

        for (int dx = -renderDist; dx <= renderDist; dx++) {
            for (int dy = -renderDist; dy <= renderDist; dy++) { // Adjusted to match player's Y chunk for initial culling pass
                for (int dz = -renderDist; dz <= renderDist; dz++) {
                    double distanceSqXZ = dx * dx + dz * dz; // Check XZ distance primarily for render distance
                    double distanceSqY = dy*dy; // Check Y distance separately or include in main check

                    if (distanceSqXZ <= renderDist * renderDist && distanceSqY <= renderDist * renderDist ) { // Example: include Y in distance check
                        ChunkId currentChunkId = new ChunkId(playerChunkId.x + dx, playerChunkId.y + dy, playerChunkId.z + dz);
                        Chunk chunkToRender = terrain.getChunk(currentChunkId); // Use getChunk for async loading
                        if (chunkToRender != null) {
                            if (!camera.isAABBInFrustum(chunkToRender.getAABB())) {
                                // continue; // Frustum culling is currently not fully implemented/effective
                            }
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
        // Set other shader-wide uniforms if any (e.g. lighting for entities)

        for (Entity entity : entities) {
            if (entity.isValid()) {
                Matrix4f entityBaseTransform = entity.getModelMatrix();
                List<ModelComponent> components = entity.getModelComponents();

                if (components.isEmpty() && entity.isValid()) { // If components list is empty but entity is valid, try to initialize them.
                    entity.initializeModels(entityRenderer); // This will call populate and then build meshes.
                    components = entity.getModelComponents(); // Re-fetch components
                }

                for (ModelComponent component : components) {
                    if (component.model() != null && component.model().getVaoId() != 0) {
                        if (component.usesEntityShader()) {
                            Matrix4f finalModelMatrix = new Matrix4f(entityBaseTransform).mul(component.localTransform());
                            entityRenderer.getEntityShader().setUniform("modelMatrix", finalModelMatrix);
                            // entityRenderer.render(component.model(), finalModelMatrix, viewMatrix, projectionMatrix); // Old way
                            GL30.glBindVertexArray(component.model().getVaoId());
                            GL11.glDrawElements(GL11.GL_TRIANGLES, component.model().getIndexCount(), GL11.GL_UNSIGNED_INT, 0);
                            GL30.glBindVertexArray(0);
                        } else {
                            // Logic for models using a different shader (e.g. a custom shader for ChromeSentinel's lights)
                            // For now, assume all use entityShader
                        }
                    }
                }
            }
        }
        entityRenderer.getEntityShader().unbind();

        // Hook line rendering (remains the same)
        if (player != null && player.getActiveHook() != null && player.getActiveHook().isAttached()) {
            Hook currentPlayersHook = player.getActiveHook();
            Vector3f hookActualAttachPoint = currentPlayersHook.getPosition(); // Use getAttachedPoint for the line end

            if (hookActualAttachPoint != null) {
                Vector3f camLeft = camera.getRightDirection(true).mul(-0.2f);
                Vector3f camUp = new Vector3f();
                camera.getForwardDirection(true).cross(camera.getRightDirection(true), camUp); // Get camera's relative up
                camUp.normalize(); // Ensure it's a unit vector
                Vector3f camDown = new Vector3f(camUp).mul(0.2f); // Use the calculated up for down
                Vector3f camForwardOffset = camera.getForwardDirection(true).mul(0.3f);
                Vector3f lineStartPos = new Vector3f(camera.getPosition()).add(camLeft).add(camDown).add(camForwardOffset);

                renderLine(lineStartPos, hookActualAttachPoint, new Vector3f(0.8f, 0.8f, 0.8f), viewMatrix, projectionMatrix);
            }
        }
    }

    private void renderLine(Vector3f start, Vector3f end, Vector3f color, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        lineShader.bind();
        lineShader.setUniform("projectionMatrix", projectionMatrix);
        lineShader.setUniform("viewMatrix", viewMatrix); // Model matrix is identity for lines in world space
        lineShader.setUniform("lineColor", color);

        FloatBuffer lineVertices = MemoryUtil.memAllocFloat(6);
        lineVertices.put(start.x).put(start.y).put(start.z);
        lineVertices.put(end.x).put(end.y).put(end.z);
        lineVertices.flip();

        GL30.glBindVertexArray(lineVaoId);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVboId);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, lineVertices); // Update buffer data

        float originalLineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GL11.glLineWidth(config.getHookLineWidth()); // Set desired line width

        GL11.glDrawArrays(GL11.GL_LINES, 0, 2); // Draw 2 vertices to make a line

        GL11.glLineWidth(originalLineWidth); // Reset to original line width

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        MemoryUtil.memFree(lineVertices);
        lineShader.unbind();
    }


    public void cleanup() {
        if (terrainShader != null) {
            terrainShader.cleanup();
        }
        if (lineShader != null) {
            lineShader.cleanup();
        }
        if (entityRenderer != null) {
            entityRenderer.cleanup();
        }
        if (lineVaoId != 0) GL30.glDeleteVertexArrays(lineVaoId);
        if (lineVboId != 0) GL15.glDeleteBuffers(lineVboId);
    }

    public ModelRenderer getEntityRenderer() {
        return entityRenderer;
    }
}