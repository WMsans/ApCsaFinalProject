package Graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import Physics.CustomAABB;

public class Frustum {

    private final Plane[] planes = new Plane[6];

    public Frustum() {
        for (int i = 0; i < 6; i++) {
            planes[i] = new Plane();
        }
    }

    public void update(Matrix4f viewProjectionMatrix) {
        // Extract planes from the combined view-projection matrix

        // Left plane
        planes[0].normal.x = viewProjectionMatrix.m30() + viewProjectionMatrix.m00();
        planes[0].normal.y = viewProjectionMatrix.m31() + viewProjectionMatrix.m01();
        planes[0].normal.z = viewProjectionMatrix.m32() + viewProjectionMatrix.m02();
        planes[0].distance = viewProjectionMatrix.m33() + viewProjectionMatrix.m03();

        // Right plane
        planes[1].normal.x = viewProjectionMatrix.m30() - viewProjectionMatrix.m00();
        planes[1].normal.y = viewProjectionMatrix.m31() - viewProjectionMatrix.m01();
        planes[1].normal.z = viewProjectionMatrix.m32() - viewProjectionMatrix.m02();
        planes[1].distance = viewProjectionMatrix.m33() - viewProjectionMatrix.m03();

        // Bottom plane
        planes[2].normal.x = viewProjectionMatrix.m30() + viewProjectionMatrix.m10();
        planes[2].normal.y = viewProjectionMatrix.m31() + viewProjectionMatrix.m11();
        planes[2].normal.z = viewProjectionMatrix.m32() + viewProjectionMatrix.m12();
        planes[2].distance = viewProjectionMatrix.m33() + viewProjectionMatrix.m13();

        // Top plane
        planes[3].normal.x = viewProjectionMatrix.m30() - viewProjectionMatrix.m10();
        planes[3].normal.y = viewProjectionMatrix.m31() - viewProjectionMatrix.m11();
        planes[3].normal.z = viewProjectionMatrix.m32() - viewProjectionMatrix.m12();
        planes[3].distance = viewProjectionMatrix.m33() - viewProjectionMatrix.m13();

        // Near plane
        planes[4].normal.x = viewProjectionMatrix.m30() + viewProjectionMatrix.m20();
        planes[4].normal.y = viewProjectionMatrix.m31() + viewProjectionMatrix.m21();
        planes[4].normal.z = viewProjectionMatrix.m32() + viewProjectionMatrix.m22();
        planes[4].distance = viewProjectionMatrix.m33() + viewProjectionMatrix.m23();

        // Far plane
        planes[5].normal.x = viewProjectionMatrix.m30() - viewProjectionMatrix.m20();
        planes[5].normal.y = viewProjectionMatrix.m31() - viewProjectionMatrix.m21();
        planes[5].normal.z = viewProjectionMatrix.m32() - viewProjectionMatrix.m22();
        planes[5].distance = viewProjectionMatrix.m33() - viewProjectionMatrix.m23();

        // Normalize the planes
        for (int i = 0; i < 6; i++) {
            planes[i].normalize();
        }
    }

    public boolean isAABBInside(CustomAABB aabb) {
        for (int i = 0; i < 6; i++) {
            Plane p = planes[i];
            // Find the vertex of the AABB that is furthest in the direction of the plane's normal
            Vector3f furthestVertex = new Vector3f();
            furthestVertex.x = p.normal.x > 0 ? aabb.max.x : aabb.min.x;
            furthestVertex.y = p.normal.y > 0 ? aabb.max.y : aabb.min.y;
            furthestVertex.z = p.normal.z > 0 ? aabb.max.z : aabb.min.z;

            if (p.getSignedDistanceToPoint(furthestVertex) < 0) {
                return false; // AABB is outside this plane
            }
        }
        return true; // AABB is inside all planes
    }

    public boolean isPointInside(Vector3f point) {
        for (int i = 0; i < 6; i++) {
            if (planes[i].getSignedDistanceToPoint(point) < 0) {
                return false;
            }
        }
        return true;
    }

    private static class Plane {
        Vector3f normal = new Vector3f();
        float distance = 0f; // Distance from origin

        void normalize() {
            float length = normal.length();
            if (length > 0.00001f) {
                normal.div(length);
                distance /= length;
            }
        }

        float getSignedDistanceToPoint(Vector3f point) {
            return normal.dot(point) + distance;
        }
    }
}