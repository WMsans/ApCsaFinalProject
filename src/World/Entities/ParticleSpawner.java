package World.Entities;

import Graphics.Camera;
import World.Terrain.BaseTerrainGenerator;
import org.joml.Vector3f;
import java.util.Random;

public class ParticleSpawner {

    private BaseTerrainGenerator worldTerrain;
    private Camera camera;
    private Random random;

    public ParticleSpawner(BaseTerrainGenerator worldTerrain, Camera camera) {
        this.worldTerrain = worldTerrain;
        this.camera = camera;
        this.random = new Random();
    }

    /**
     * Spawns a single particle with the specified properties.
     */
    public void spawnParticle(Vector3f position, Vector3f initialVelocity, Vector3f endVelocity,
                              float lifespan, float gravityScale, Vector3f color, float size) {
        Particle particle = new Particle(
                worldTerrain, camera, position, initialVelocity, endVelocity,
                lifespan, gravityScale, color, size
        );
        worldTerrain.addEntity(particle);
    }

    /**
     * Spawns a burst of particles from an origin point.
     *
     * @param origin          The center point of the burst.
     * @param count           Number of particles to spawn.
     * @param burstSpeed      Magnitude of the random outward velocity for each particle.
     * @param lifespan        Lifespan for each particle.
     * @param gravityScale    Gravity scale for each particle.
     * @param color           Color for each particle.
     * @param size            Size for each particle.
     * @param baseVelocity    An optional base velocity added to each particle's random burst velocity. Can be null.
     */
    public void spawnBurst(Vector3f origin, int count, float burstSpeed,
                           float lifespan, float gravityScale, Vector3f color, float size,
                           Vector3f baseVelocity) {

        for (int i = 0; i < count; i++) {
            // Generate a random 3D direction
            float theta = random.nextFloat() * 2.0f * (float) Math.PI; // Angle in XY plane
            float phi = (float) Math.acos(2.0f * random.nextFloat() - 1.0f); // Angle from Z axis

            float vx = (float) (Math.sin(phi) * Math.cos(theta));
            float vy = (float) (Math.sin(phi) * Math.sin(theta));
            float vz = (float) Math.cos(phi);

            Vector3f randomDir = new Vector3f(vx, vy, vz);
            if (randomDir.lengthSquared() == 0) { // Avoid zero vector if Math.cos(phi) is exactly 0 and sin(phi) is 0
                randomDir.set(0,1,0); // Default to up
            }
            randomDir.normalize();

            Vector3f particleVelocity = new Vector3f(randomDir).mul(burstSpeed);

            if (baseVelocity != null) {
                particleVelocity.add(baseVelocity);
            }

            // Spawn the individual particle. For gas, endVelocity is null.
            spawnParticle(new Vector3f(origin), particleVelocity, null, lifespan, gravityScale, color, size);
        }
    }
}