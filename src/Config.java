import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.joml.Vector3f;

public class Config {
    private Properties properties;

    public Config(String fileName) {
        properties = new Properties();
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                System.err.println("Sorry, unable to find " + fileName);
                // Set default values if config file is not found
                properties.setProperty("gamma", "0.2");
                properties.setProperty("lightPositionX", "100.0");
                properties.setProperty("lightPositionY", "100.0");
                properties.setProperty("lightPositionZ", "100.0");
                return;
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
            // Set default values in case of an error
            properties.setProperty("gamma", "0.2");
            properties.setProperty("lightPositionX", "100.0");
            properties.setProperty("lightPositionY", "100.0");
            properties.setProperty("lightPositionZ", "100.0");
        }
    }

    public float getGamma() {
        return Float.parseFloat(properties.getProperty("gamma", "0.2"));
    }

    public Vector3f getLightPosition() {
        float x = Float.parseFloat(properties.getProperty("lightPositionX", "100.0"));
        float y = Float.parseFloat(properties.getProperty("lightPositionY", "100.0"));
        float z = Float.parseFloat(properties.getProperty("lightPositionZ", "100.0"));
        return new Vector3f(x, y, z);
    }
}