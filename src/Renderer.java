import Configuration.Config;
import World.Terrain.BaseTerrainGenerator;
import World.Chunk.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Renderer {

    private Shader shader;
    private Camera camera;
    private Config config;
    private float gammaValue;

    public Renderer(Camera camera, Config config) {
        this.camera = camera;
        this.config = config;
        this.gammaValue = config.getGamma();
        try {
            initShader();
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
        camera.updateFrustum();

        shader.bind();
        shader.setUniform("projectionMatrix", camera.getProjectionMatrix());
        shader.setUniform("viewMatrix", camera.getViewMatrix());
        shader.setUniform("lightPos", camera.getPosition());
        shader.setUniform("lightColor", new Vector3f(1.0f, 1.0f, 1.0f));
        shader.setUniform("gamma", gammaValue);
        shader.setUniform("viewPos", camera.getPosition());

        ChunkId playerChunkId = Chunk.getChunkIdAtWorldPosition(playerPosition);
        int renderDist = config.getRenderDistanceInChunks();
        int chunksRenderedThisFrame = 0;

        for (int dx = -renderDist; dx <= renderDist; dx++) {
            for (int dy = -renderDist; dy <= renderDist; dy++) {
                for (int dz = -renderDist; dz <= renderDist; dz++) {
                    ChunkId currentChunkId = new ChunkId(playerChunkId.x + dx, playerChunkId.y + dy, playerChunkId.z + dz);

                    // terrain.getChunk() might return null if the chunk is still being generated
                    Chunk chunkToRender = terrain.getChunk(currentChunkId);

                    if (chunkToRender != null) { // Only proceed if chunk is loaded
                        if (!camera.isAABBInFrustum(chunkToRender.getAABB())) {
                            continue;
                        }

                        ChunkMesh mesh = chunkToRender.getOrCreateMesh(); // Mesh should be ready if chunk is in main map
                        if (mesh != null && mesh.isInitialized()) {
                            Matrix4f modelMatrix = new Matrix4f().translate(chunkToRender.getMinCorner());
                            shader.setUniform("modelMatrix", modelMatrix);
                            mesh.render();
                            chunksRenderedThisFrame++;
                        }
                    }
                }
            }
        }
        // Optional: Log how many chunks were actually rendered vs. how many might be loading
        // System.out.println("Rendered Chunks: " + chunksRenderedThisFrame);
        shader.unbind();
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
    }
}