package World.Entities;

import Graphics.Camera;
import Graphics.EntityModel;
import Graphics.ModelComponent;
import Inventory.Hand;
import World.Block;
import World.Terrain.BaseTerrainGenerator;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;

public class Particle extends Entity {

    private float lifespan; // Total time to live
    private float age;      // Current age
    private Vector3f initialVelocity;
    private Vector3f endVelocity; // For velocity interpolation over lifetime (optional)
    private float gravityScale;
    private Vector3f color;
    private float size;
    protected Camera camera; // Reference to the main camera for billboarding

    public Particle(BaseTerrainGenerator worldTerrain, Camera camera, Vector3f initialPosition,
                    Vector3f initialVelocity, Vector3f endVelocity,
                    float lifespan, float gravityScale, Vector3f color, float size) {
        super(worldTerrain, initialPosition, new Vector3f(size, size, size)); // Bounding box for culling, not physics
        this.camera = camera;
        this.initialVelocity = new Vector3f(initialVelocity);
        this.velocity.set(this.initialVelocity);
        this.endVelocity = (endVelocity != null) ? new Vector3f(endVelocity) : null;
        this.lifespan = lifespan;
        this.gravityScale = gravityScale;
        this.color = new Vector3f(color);
        this.size = size;
        this.age = 0.0f;
        this.skipCollisionProcessing = true; // Particles usually don't collide with terrain
        this.isOnGround = false; // Particles are typically airborne or non-physical
    }

    @Override
    protected void populateModelComponents() {
        if (modelComponents.isEmpty()) {
            // Create a simple quad model for the particle
            EntityModel particleModel = EntityModel.createQuadModel(1.0f, this.color); // Unit size, color set
            modelComponents.add(new ModelComponent(particleModel));
        }
    }

    @Override
    protected void updateLogic(float deltaTime) {
        age += deltaTime;
        if (age >= lifespan) {
            kill();
            return;
        }

        // Optional: Interpolate velocity if endVelocity is set
        if (endVelocity != null) {
            float t = Math.min(age / lifespan, 1.0f);
            velocity.lerp(initialVelocity, t, endVelocity);
        }
    }

    @Override
    protected void applyGravity(float deltaTime) {
        if (skipCollisionProcessing) return; // If they don't collide, gravity might not be needed as standard
        if (gravityScale != 0.0f) {
            velocity.y -= DEFAULT_GRAVITY_ACCELERATION * gravityScale * deltaTime;
            if (velocity.y < -DEFAULT_TERMINAL_VELOCITY) { // Use default or particle-specific terminal vel
                velocity.y = -DEFAULT_TERMINAL_VELOCITY;
            }
        }
    }

    @Override
    public Matrix4f getModelMatrix() {
        Matrix4f billboardMatrix = new Matrix4f();

        // 1. Get camera's view matrix and position
        Matrix4f viewMatrix = camera.getViewMatrix();
        Vector3f camPos = camera.getPosition(); // For cylindrical billboarding if needed, but spherical is more common

        // Spherical Billboarding: Always face the camera's position
        // The rotation should make the particle's local -Z axis point towards the camera.
        // Alternatively, easier: construct matrix to counteract camera rotation.

        // Method 1: Using camera's axes (ensures billboard is perpendicular to view direction)
        // This is often preferred for 3D particles.
        Vector3f lookDir = new Vector3f(position).sub(camPos).normalize();
        Vector3f rightDir = new Vector3f(camera.getRightDirection(true)); // Camera's right
        if(Math.abs(lookDir.dot(rightDir)) > 0.999f) { // if lookDir is parallel to rightDir (edge case)
            rightDir.set(0,0,1).cross(lookDir).normalize(); // Recalculate right if colinear
            if(rightDir.lengthSquared() < 0.001f) rightDir.set(1,0,0); // Further fallback
        }
        Vector3f upDir = new Vector3f(rightDir).cross(lookDir).normalize(); // Particle's up is perpendicular to its right and look

        // Ensure right is truly perpendicular if lookDir changed it
        rightDir.set(lookDir).cross(upDir).normalize();


        billboardMatrix.m00(rightDir.x); billboardMatrix.m01(rightDir.y); billboardMatrix.m02(rightDir.z); billboardMatrix.m03(0);
        billboardMatrix.m10(upDir.x);    billboardMatrix.m11(upDir.y);    billboardMatrix.m12(upDir.z);    billboardMatrix.m13(0);
        billboardMatrix.m20(-lookDir.x); billboardMatrix.m21(-lookDir.y); billboardMatrix.m22(-lookDir.z); billboardMatrix.m23(0); // Pointing -Z of particle towards camera
        billboardMatrix.m30(position.x); billboardMatrix.m31(position.y); billboardMatrix.m32(position.z); billboardMatrix.m33(1);

        // Apply particle's individual roll if you add such a feature
        // if (this.roll != 0.0f) { // Assuming 'roll' is around the lookDir
        //     billboardMatrix.rotate((float)Math.toRadians(this.roll), lookDir.x, lookDir.y, lookDir.z);
        // }


        // Scale the billboard
        billboardMatrix.scale(this.size);

        return billboardMatrix;
    }


    // Particles typically don't interact
    @Override
    public void onBlockInteraction(Block block, Vector3f intersectionPoint, Hand hand) {}
    @Override
    public void onEntityInteraction(Entity target, Hand hand) {}
}