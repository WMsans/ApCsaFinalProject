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
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 6 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);

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
            for (int dy = -renderDist; dy <= renderDist; dy++) {
                for (int dz = -renderDist; dz <= renderDist; dz++) {
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq <= renderDist * renderDist) {
                        ChunkId currentChunkId = new ChunkId(playerChunkId.x + dx, playerChunkId.y + dy, playerChunkId.z + dz);
                        Chunk chunkToRender = terrain.getChunk(currentChunkId);
                        if (chunkToRender != null) {
                            if (!camera.isAABBInFrustum(chunkToRender.getAABB())) {
                                continue;
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

        for (Entity entity : entities) {
            if (entity.isValid() && entity.getModel() != null && entity.getModel().getVaoId() != 0) {
                if (entity instanceof PlayerEntity && entity.getModel().getVertices() == null) {
                    // continue;
                }
                Matrix4f modelMatrix = entity.getModelMatrix();
                entityRenderer.render(entity.getModel(), modelMatrix, viewMatrix, projectionMatrix);
            }
        }

        if (player != null && player.getActiveHook() != null && player.getActiveHook().isAttached()) {
            Hook currentPlayersHook = player.getActiveHook(); // Use a local var for clarity
            Vector3f hookActualAttachPoint = currentPlayersHook.getPosition();

            if (hookActualAttachPoint != null) {
                // Sanity check: Is the hook's reported attachment point too far from the hook entity's own world position?
                // This might indicate a desync or corruption if they are supposed to be the same when attached.
                // Hook's entity position should be the same as its attachedPoint when it is attached.
                float distanceToEntityPosSq = hookActualAttachPoint.distanceSquared(currentPlayersHook.getPosition());

                // A small tolerance for floating point arithmetic might be okay, but a large difference is suspicious.
                if (distanceToEntityPosSq > 1.0f) { // If difference is more than 1 unit (squared), log warning.
                    System.err.println("Warning: Hook attachedPoint " + hookActualAttachPoint +
                            " is far from Hook entity position " + currentPlayersHook.getPosition() +
                            ". Hook ID: " + currentPlayersHook.getId() + ". This may indicate a bug.");
                    // As a potential safety measure, you could choose not to render the line or use the hook's entity position:
                    // return; // Option 1: Don't render the line if data is suspicious
                    // hookActualAttachPoint = currentPlayersHook.getPosition(); // Option 2: Use entity position as fallback (may hide the root issue)
                }


                Vector3f camLeft = camera.getRightDirection(true).mul(-0.2f);
                Vector3f camUp = new Vector3f();
                camera.getForwardDirection(true).cross(camera.getRightDirection(true), camUp);
                Vector3f camDown = camUp.mul(0.2f);
                Vector3f camForwardOffset = camera.getForwardDirection(true).mul(0.3f);
                Vector3f lineStartPos = new Vector3f(camera.getPosition()).add(camLeft).add(camDown).add(camForwardOffset);

                renderLine(lineStartPos, hookActualAttachPoint, new Vector3f(0.8f, 0.8f, 0.8f), viewMatrix, projectionMatrix);
            }
        }
    }

    private void renderLine(Vector3f start, Vector3f end, Vector3f color, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        lineShader.bind();
        lineShader.setUniform("projectionMatrix", projectionMatrix);
        lineShader.setUniform("viewMatrix", viewMatrix);
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
        if (lineVaoId != 0) {
            GL30.glDeleteVertexArrays(lineVaoId);
        }
        if (lineVboId != 0) {
            GL15.glDeleteBuffers(lineVboId);
        }
    }

    public ModelRenderer getEntityRenderer() {
        return entityRenderer;
    }
}