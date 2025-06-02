package Configuration;

import World.Chunk.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.joml.Vector3f;

public class Config {
    private Properties properties;

    // Player Movement
    public float maxSpeed;
    public float acceleration;
    public float groundDeceleration;
    public float airDeceleration;
    public float jumpUpSpeed;
    public float maxFallSpeed;
    public float fallAcceleration;
    public float jumpEndEarlyGravityModifier;
    public float coyoteTime;
    public float jumpBufferTime;
    public float flySpeed;
    public boolean enablePlayerAirRoll;

    // Lighting
    public float gamma;

    // World
    public int chunkSizeX;
    public int chunkSizeY;
    public int chunkSizeZ;
    public int renderDistanceInChunks;

    // Debug
    public boolean flyMode;
    public boolean debugRenderAABBs; // New property

    // Graphics
    public float hookLineWidth;

    // Grid Effect Settings
    public float gridSpacing;
    public float gridLineWidth;
    public float gridIntensity;
    public Vector3f gridColorGround;
    public Vector3f gridColorMountain;
    public float gridTransitionHeight;
    public float gridTransitionRange;


    public Config(String fileName) {
        properties = new Properties();
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                System.err.println("Sorry, unable to find " + fileName + ". Using default values.");
                setDefaultProperties();
            } else {
                properties.load(input);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("IOException loading " + fileName + ". Using default values.");
            setDefaultProperties();
        }
        loadProperties();

        Chunk.setChunkDimensions(chunkSizeX, chunkSizeY, chunkSizeZ);
    }

    private void setDefaultProperties() {
        // Player
        properties.setProperty("gamma", "1.0");
        properties.setProperty("player.maxSpeed", "6.0");
        properties.setProperty("player.acceleration", "40.0");
        properties.setProperty("player.groundDeceleration", "30.0");
        properties.setProperty("player.airDeceleration", "15.0");
        properties.setProperty("player.jumpUpSpeed", "11.0");
        properties.setProperty("player.maxFallSpeed", "40.0");
        properties.setProperty("player.fallAcceleration", "30.0");
        properties.setProperty("player.jumpEndEarlyGravityModifier", "3.0");
        properties.setProperty("player.coyoteTime", "0.1");
        properties.setProperty("player.jumpBufferTime", "0.15");
        properties.setProperty("player.flySpeed", "50.0");
        properties.setProperty("player.enablePlayerAirRoll", "1");

        // World
        properties.setProperty("world.chunkSizeX", "16");
        properties.setProperty("world.chunkSizeY", "16");
        properties.setProperty("world.chunkSizeZ", "16");
        properties.setProperty("world.renderDistanceInChunks", "10");

        // Debug
        properties.setProperty("debug.flyMode", "0");
        properties.setProperty("debug.renderAABBs", "0"); // Default for new property

        // Graphics
        properties.setProperty("graphics.hookLineWidth", "2.5");

        // Grid Effect Default Settings
        properties.setProperty("graphics.gridSpacing", "1.0");
        properties.setProperty("graphics.gridLineWidth", "0.1");
        properties.setProperty("graphics.gridIntensity", "0.75");
        properties.setProperty("graphics.gridColorGroundR", "1.0");
        properties.setProperty("graphics.gridColorGroundG", "0.15");
        properties.setProperty("graphics.gridColorGroundB", "0.6");
        properties.setProperty("graphics.gridColorMountainR", "0.15");
        properties.setProperty("graphics.gridColorMountainG", "0.7");
        properties.setProperty("graphics.gridColorMountainB", "1.0");
        properties.setProperty("graphics.gridTransitionHeight", "45.0");
        properties.setProperty("graphics.gridTransitionRange", "40.0");
    }

    private void loadProperties() {
        this.gamma = Float.parseFloat(properties.getProperty("gamma"));

        this.maxSpeed = Float.parseFloat(properties.getProperty("player.maxSpeed"));
        this.acceleration = Float.parseFloat(properties.getProperty("player.acceleration"));
        this.groundDeceleration = Float.parseFloat(properties.getProperty("player.groundDeceleration"));
        this.airDeceleration = Float.parseFloat(properties.getProperty("player.airDeceleration"));
        this.jumpUpSpeed = Float.parseFloat(properties.getProperty("player.jumpUpSpeed"));
        this.maxFallSpeed = Float.parseFloat(properties.getProperty("player.maxFallSpeed"));
        this.fallAcceleration = Float.parseFloat(properties.getProperty("player.fallAcceleration"));
        this.jumpEndEarlyGravityModifier = Float.parseFloat(properties.getProperty("player.jumpEndEarlyGravityModifier"));
        this.coyoteTime = Float.parseFloat(properties.getProperty("player.coyoteTime"));
        this.jumpBufferTime = Float.parseFloat(properties.getProperty("player.jumpBufferTime"));
        this.flySpeed = Float.parseFloat(properties.getProperty("player.flySpeed"));
        this.enablePlayerAirRoll = Integer.parseInt(properties.getProperty("player.enablePlayerAirRoll")) >= 1;

        this.chunkSizeX = Integer.parseInt(properties.getProperty("world.chunkSizeX"));
        this.chunkSizeY = Integer.parseInt(properties.getProperty("world.chunkSizeY"));
        this.chunkSizeZ = Integer.parseInt(properties.getProperty("world.chunkSizeZ"));
        this.renderDistanceInChunks = Integer.parseInt(properties.getProperty("world.renderDistanceInChunks"));

        this.flyMode = Integer.parseInt(properties.getProperty("debug.flyMode")) >= 1;
        this.debugRenderAABBs = Integer.parseInt(properties.getProperty("debug.renderAABBs", "0")) >= 1; // Load new property with default

        this.hookLineWidth = Float.parseFloat(properties.getProperty("graphics.hookLineWidth"));

        // Load Grid Effect Settings
        this.gridSpacing = Float.parseFloat(properties.getProperty("graphics.gridSpacing"));
        this.gridLineWidth = Float.parseFloat(properties.getProperty("graphics.gridLineWidth"));
        this.gridIntensity = Float.parseFloat(properties.getProperty("graphics.gridIntensity"));
        this.gridColorGround = new Vector3f(
                Float.parseFloat(properties.getProperty("graphics.gridColorGroundR")),
                Float.parseFloat(properties.getProperty("graphics.gridColorGroundG")),
                Float.parseFloat(properties.getProperty("graphics.gridColorGroundB"))
        );
        this.gridColorMountain = new Vector3f(
                Float.parseFloat(properties.getProperty("graphics.gridColorMountainR")),
                Float.parseFloat(properties.getProperty("graphics.gridColorMountainG")),
                Float.parseFloat(properties.getProperty("graphics.gridColorMountainB"))
        );
        this.gridTransitionHeight = Float.parseFloat(properties.getProperty("graphics.gridTransitionHeight"));
        this.gridTransitionRange = Float.parseFloat(properties.getProperty("graphics.gridTransitionRange"));
    }

    // Getters for Player Movement
    public float getMaxSpeed() { return maxSpeed; }
    public float getAcceleration() { return acceleration; }
    public float getGroundDeceleration() { return groundDeceleration; }
    public float getAirDeceleration() { return airDeceleration; }
    public float getJumpUpSpeed() { return jumpUpSpeed; }
    public float getMaxFallSpeed() { return maxFallSpeed; }
    public float getFallAcceleration() { return fallAcceleration; }
    public float getJumpEndEarlyGravityModifier() { return jumpEndEarlyGravityModifier; }
    public float getCoyoteTime() { return coyoteTime; }
    public float getJumpBufferTime() { return jumpBufferTime; }
    public float getFlySpeed() { return flySpeed; }
    public boolean isEnablePlayerAirRoll() { return enablePlayerAirRoll; }

    // Getter for Lighting
    public float getGamma() { return gamma; }

    // Getters for World
    public int getChunkSizeX() { return chunkSizeX; }
    public int getChunkSizeY() { return chunkSizeY; }
    public int getChunkSizeZ() { return chunkSizeZ; }
    public int getRenderDistanceInChunks() { return renderDistanceInChunks; }

    // Getter for Debug
    public boolean isDebugFlyModeEnabled() { return flyMode; }
    public boolean isDebugRenderAABBsEnabled() { return debugRenderAABBs; } // Getter for new property

    // Getter for Graphics
    public float getHookLineWidth() { return hookLineWidth; }

    // Getters for Grid Effect
    public float getGridSpacing() { return gridSpacing; }
    public float getGridLineWidth() { return gridLineWidth; }
    public float getGridIntensity() { return gridIntensity; }
    public Vector3f getGridColorGround() { return gridColorGround; }
    public Vector3f getGridColorMountain() { return gridColorMountain; }
    public float getGridTransitionHeight() { return gridTransitionHeight; }
    public float getGridTransitionRange() { return gridTransitionRange; }
}