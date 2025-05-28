package Configuration;

import World.Chunk.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.joml.Vector3f;

public class Config {
    private Properties properties;

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

    public float gamma;

    public int chunkSizeX;
    public int chunkSizeY;
    public int chunkSizeZ;
    public int renderDistanceInChunks;

    public boolean flyMode;

    public float hookLineWidth;

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

        properties.setProperty("gamma", "0.2");

        properties.setProperty("player.maxSpeed", "5.0");
        properties.setProperty("player.acceleration", "30.0");
        properties.setProperty("player.groundDeceleration", "20.0");
        properties.setProperty("player.airDeceleration", "5.0");
        properties.setProperty("player.jumpUpSpeed", "7.0");
        properties.setProperty("player.maxFallSpeed", "50.0");
        properties.setProperty("player.fallAcceleration", "19.62");
        properties.setProperty("player.jumpEndEarlyGravityModifier", "2.5");
        properties.setProperty("player.coyoteTime", "0.1");
        properties.setProperty("player.jumpBufferTime", "0.1");
        properties.setProperty("player.flySpeed", "12.0");
        properties.setProperty("player.enablePlayerAirRoll", "1");

        properties.setProperty("world.chunkSizeX", "16");
        properties.setProperty("world.chunkSizeY", "16");
        properties.setProperty("world.chunkSizeZ", "16");
        properties.setProperty("world.renderDistanceInChunks", "8");

        properties.setProperty("debug.flyMode", "0");

        properties.setProperty("graphics.hookLineWidth", "2.0");
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
        this.flySpeed = Float.parseFloat(properties.getProperty("player.flySpeed", "12.0"));
        this.enablePlayerAirRoll = Integer.parseInt(properties.getProperty("player.enablePlayerAirRoll")) >= 1;

        this.chunkSizeX = Integer.parseInt(properties.getProperty("world.chunkSizeX"));
        this.chunkSizeY = Integer.parseInt(properties.getProperty("world.chunkSizeY"));
        this.chunkSizeZ = Integer.parseInt(properties.getProperty("world.chunkSizeZ"));
        this.renderDistanceInChunks = Integer.parseInt(properties.getProperty("world.renderDistanceInChunks"));

        this.flyMode = Integer.parseInt(properties.getProperty("debug.flyMode")) >= 1;

        this.hookLineWidth = Float.parseFloat(properties.getProperty("graphics.hookLineWidth", "2.0"));
    }

    public float getGamma() { return gamma; }
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
    public int getChunkSizeX() { return chunkSizeX; }
    public int getChunkSizeY() { return chunkSizeY; }
    public int getChunkSizeZ() { return chunkSizeZ; }
    public int getRenderDistanceInChunks() { return renderDistanceInChunks; }
    public boolean isDebugFlyModeEnabled() { return flyMode; }
    public float getHookLineWidth() { return hookLineWidth; }
}