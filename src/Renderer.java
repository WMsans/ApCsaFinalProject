import Configuration.Config;
import World.Terrain.BaseTerrainGenerator;
import World.Terrain.NetherTerrain;
import World.Chunk.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Renderer {

    private Shader shader;
    private Camera camera;
    private Config config;

    private float gammaValue;

    // Cube mesh data and IDs are removed, as this is now handled by ChunkMesh

    public Renderer(Camera camera, Config config) {
        this.camera = camera;
        this.config = config;
        this.gammaValue = config.getGamma();

        try {
            initShader();
            // initCubeMesh(); // Removed
        } catch (Exception e) {
            System.err.println("Error initializing renderer:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void initShader() throws Exception {
        shader = new Shader();
        shader.createVertexShader(Shader.loadResource("/shaders/vertex.glsl"));
        shader.createFragmentShader(Shader.loadResource("/shaders/fragment.glsl"));
        shader.link();

        shader.createUniform("projectionMatrix");
        shader.createUniform("viewMatrix");
        shader.createUniform("modelMatrix");
        shader.createUniform("lightPos");
        shader.createUniform("lightColor");
        shader.createUniform("gamma");
        shader.createUniform("viewPos");
    }

    public void renderTerrain(BaseTerrainGenerator terrain, Vector3f playerPosition) {
        camera.updateFrustum(); // Update frustum planes once per frame

        shader.bind();
        shader.setUniform("projectionMatrix", camera.getProjectionMatrix());
        shader.setUniform("viewMatrix", camera.getViewMatrix());
        shader.setUniform("lightPos", camera.getPosition());
        shader.setUniform("lightColor", new Vector3f(1.0f, 1.0f, 1.0f)); // White light
        shader.setUniform("gamma", gammaValue);
        shader.setUniform("viewPos", camera.getPosition());

        ChunkId playerChunkId = Chunk.getChunkIdAtWorldPosition(playerPosition);
        int renderDist = config.getRenderDistanceInChunks();
        int chunksRendered = 0;

        for (int dx = -renderDist; dx <= renderDist; dx++) {
            for (int dy = -renderDist; dy <= renderDist; dy++) { // Iterate Y chunks as well
                for (int dz = -renderDist; dz <= renderDist; dz++) {
                    ChunkId currentChunkId = new ChunkId(playerChunkId.x + dx, playerChunkId.y + dy, playerChunkId.z + dz);
                    Chunk chunkToRender = terrain.getChunk(currentChunkId); // This will generate if not present

                    if (chunkToRender != null) {
                        // Frustum Culling for the entire chunk
                        if (!camera.isAABBInFrustum(chunkToRender.getAABB())) {
                            continue; // Skip this chunk if it's outside the frustum
                        }

                        ChunkMesh mesh = chunkToRender.getOrCreateMesh();
                        if (mesh != null && mesh.isInitialized()) {
                            // The model matrix will translate the chunk mesh (which is relative to chunk origin)
                            // to its correct world position (the chunk's min corner).
                            Matrix4f modelMatrix = new Matrix4f().translate(chunkToRender.getMinCorner());
                            shader.setUniform("modelMatrix", modelMatrix);

                            mesh.render();
                            chunksRendered++;
                        }
                    }
                }
            }
        }
        // System.out.println("Rendered Chunks: " + chunksRendered); // For debugging
        shader.unbind();
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
    }
}