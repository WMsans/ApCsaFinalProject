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

        // Iterate through a square area that encompasses the circle/sphere
        for (int dx = -renderDist; dx <= renderDist; dx++) {
            for (int dy = -renderDist; dy <= renderDist; dy++) { // Assuming y-axis distance matters for rendering
                for (int dz = -renderDist; dz <= renderDist; dz++) {
                    // Calculate the squared distance from the player's chunk to the current chunk
                    // Using squared distance avoids a square root calculation, which is more efficient
                    double distanceSq = dx * dx + dy * dy + dz * dz;

                    // Check if the chunk is within the spherical/circular render distance
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
                                shader.setUniform("modelMatrix", modelMatrix);
                                mesh.render();
                                chunksRenderedThisFrame++;
                            }
                        }
                    }
                }
            }
        }
        shader.unbind();
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
    }
}