package Graphics;

import Configuration.Config;
import World.Terrain.BaseTerrainGenerator;
import World.Chunk.*;
import World.Entities.Entity; // Added
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List; // Added

public class Renderer {

    private Shader terrainShader; // Renamed from 'shader' for clarity
    private Camera camera;
    private Config config;
    private float gammaValue;

    private ModelRenderer entityRenderer; // Added

    public Renderer(Camera camera, Config config) {
        this.camera = camera;
        this.config = config;
        this.gammaValue = config.getGamma();
        this.entityRenderer = new ModelRenderer(); // Added
        try {
            initTerrainShader();
            entityRenderer.init(); // Added: Initialize entity renderer and its shader
        } catch (Exception e) {
            System.err.println("Error initializing renderer:");
            e.printStackTrace();
            System.exit(1);
        }
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

    public void renderTerrain(BaseTerrainGenerator terrain, Vector3f playerPosition) {
        camera.updateFrustum();

        terrainShader.bind();
        terrainShader.setUniform("projectionMatrix", camera.getProjectionMatrix());
        terrainShader.setUniform("viewMatrix", camera.getViewMatrix());
        terrainShader.setUniform("lightPos", camera.getPosition()); // Light source at camera for now
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

    public void renderEntities(List<Entity> entities, Camera cam) {
        Matrix4f viewMatrix = cam.getViewMatrix();
        Matrix4f projectionMatrix = cam.getProjectionMatrix();

        for (Entity entity : entities) {
            if (entity.isValid() && entity.getModel() != null && entity.getModel().getVaoId() != 0) {
                Matrix4f modelMatrix = entity.getModelMatrix();
                entityRenderer.render(entity.getModel(), modelMatrix, viewMatrix, projectionMatrix);
            }
        }
    }


    public void cleanup() {
        if (terrainShader != null) {
            terrainShader.cleanup();
        }
        if (entityRenderer != null) { // Added
            entityRenderer.cleanup();
        }
    }

    // Added getter for ModelRenderer to allow Main to initialize entity models
    public ModelRenderer getEntityRenderer() {
        return entityRenderer;
    }
}