package Physics;

import org.joml.Vector2f;
import org.joml.Vector3f;

public class CustomAABB {
    public final Vector3f min;
    public final Vector3f max;

    /**
     * Creates an AABB from minimum and maximum corner points.
     * @param minX minimum x coordinate
     * @param minY minimum y coordinate
     * @param minZ minimum z coordinate
     * @param maxX maximum x coordinate
     * @param maxY maximum y coordinate
     * @param maxZ maximum z coordinate
     */
    public CustomAABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.min = new Vector3f(minX, minY, minZ);
        this.max = new Vector3f(maxX, maxY, maxZ);
    }

    /**
     * Creates an AABB from minimum and maximum corner points.
     * @param min The minimum corner vector.
     * @param max The maximum corner vector.
     */
    public CustomAABB(Vector3f min, Vector3f max) {
        this.min = new Vector3f(min); // Create copies
        this.max = new Vector3f(max);
    }

    /**
     * Creates an AABB centered at a position with given dimensions.
     * @param center The center of the AABB.
     * @param dimensions The full width, height, and depth of the AABB.
     */
    public static CustomAABB fromCenterAndDimensions(Vector3f center, Vector3f dimensions) {
        Vector3f halfDim = new Vector3f(dimensions).mul(0.5f);
        return new CustomAABB(
                new Vector3f(center).sub(halfDim),
                new Vector3f(center).add(halfDim)
        );
    }

    /**
     * Creates an AABB for a block (1x1x1) centered at the given position.
     * @param blockCenterPosition The center position of the block.
     * @return A new CustomAABB for the block.
     */
    public static CustomAABB forBlock(Vector3f blockCenterPosition) {
        return new CustomAABB(
                blockCenterPosition.x - 0.5f, blockCenterPosition.y - 0.5f, blockCenterPosition.z - 0.5f,
                blockCenterPosition.x + 0.5f, blockCenterPosition.y + 0.5f, blockCenterPosition.z + 0.5f
        );
    }


    /**
     * Checks if this AABB intersects with another AABB.
     * @param other The other AABB to test against.
     * @return true if they intersect, false otherwise.
     */
    public boolean testAABB(CustomAABB other) {
        if (this.max.x < other.min.x || this.min.x > other.max.x) return false;
        if (this.max.y < other.min.y || this.min.y > other.max.y) return false;
        if (this.max.z < other.min.z || this.min.z > other.max.z) return false;
        return true;
    }

    /**
     * Intersects a ray with this AABB.
     * Uses the Slab method.
     * @param rayOrigin The origin of the ray.
     * @param rayDirection The direction of the ray (does not need to be normalized).
     * @param resultNearFar A Vector2f to store the near (x) and far (y) intersection distances.
     * If no intersection, values are not guaranteed.
     * @return true if the ray intersects the AABB, false otherwise.
     */
    public boolean intersectRay(Vector3f rayOrigin, Vector3f rayDirection, Vector2f resultNearFar) {
        float tMinX, tMaxX, tMinY, tMaxY, tMinZ, tMaxZ;

        // X slab
        if (Math.abs(rayDirection.x) < 1e-6f) { // Ray is parallel to X-planes
            if (rayOrigin.x < min.x || rayOrigin.x > max.x) return false; // Origin not between slabs
            tMinX = Float.NEGATIVE_INFINITY;
            tMaxX = Float.POSITIVE_INFINITY;
        } else {
            tMinX = (min.x - rayOrigin.x) / rayDirection.x;
            tMaxX = (max.x - rayOrigin.x) / rayDirection.x;
            if (tMinX > tMaxX) { float temp = tMinX; tMinX = tMaxX; tMaxX = temp; }
        }

        // Y slab
        if (Math.abs(rayDirection.y) < 1e-6f) { // Ray is parallel to Y-planes
            if (rayOrigin.y < min.y || rayOrigin.y > max.y) return false;
            tMinY = Float.NEGATIVE_INFINITY;
            tMaxY = Float.POSITIVE_INFINITY;
        } else {
            tMinY = (min.y - rayOrigin.y) / rayDirection.y;
            tMaxY = (max.y - rayOrigin.y) / rayDirection.y;
            if (tMinY > tMaxY) { float temp = tMinY; tMinY = tMaxY; tMaxY = temp; }
        }

        // Z slab
        if (Math.abs(rayDirection.z) < 1e-6f) { // Ray is parallel to Z-planes
            if (rayOrigin.z < min.z || rayOrigin.z > max.z) return false;
            tMinZ = Float.NEGATIVE_INFINITY;
            tMaxZ = Float.POSITIVE_INFINITY;
        } else {
            tMinZ = (min.z - rayOrigin.z) / rayDirection.z;
            tMaxZ = (max.z - rayOrigin.z) / rayDirection.z;
            if (tMinZ > tMaxZ) { float temp = tMinZ; tMinZ = tMaxZ; tMaxZ = temp; }
        }

        float tNear = Math.max(Math.max(tMinX, tMinY), tMinZ);
        float tFar = Math.min(Math.min(tMaxX, tMaxY), tMaxZ);

        if (tNear > tFar || tFar < 0) {
            return false; // No intersection or intersection is behind the ray origin
        }

        resultNearFar.x = tNear; // Near intersection distance
        resultNearFar.y = tFar;  // Far intersection distance
        return true;
    }

    /**
     * Creates a new AABB that is translated by the given offset.
     * @param offset The vector to translate by.
     * @return A new, translated AABB.
     */
    public CustomAABB translate(Vector3f offset) {
        return new CustomAABB(new Vector3f(min).add(offset), new Vector3f(max).add(offset));
    }

    public float getWidth() { return max.x - min.x; }
    public float getHeight() { return max.y - min.y; }
    public float getDepth() { return max.z - min.z; }

    public Vector3f getCenter(Vector3f dest) {
        return dest.set(min).add(max).mul(0.5f);
    }
}
