#version 330 core
out vec4 FragColor;

in vec3 FragPos_world;
in vec3 Normal_world;
in vec3 VertexColor_FS; // Renamed from blockColor, now comes from vertex shader

uniform vec3 lightPos;     // Light position in world space
uniform vec3 lightColor;
uniform vec3 viewPos;      // Camera position in world space
uniform float gamma;

void main() {
    // Ambient
    float ambientStrength = 0.25; // Slightly higher ambient for better general illumination
    vec3 ambient = ambientStrength * lightColor;

    // Diffuse
    vec3 norm = normalize(Normal_world);
    vec3 lightDir = normalize(lightPos - FragPos_world);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // Specular (optional, can be toned down or removed for Minecraft-like aesthetic)
    float specularStrength = 0.2;
    vec3 viewDir = normalize(viewPos - FragPos_world);
    vec3 reflectDir = reflect(-lightDir, norm);
    // Make specular highlight smaller and less intense
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 8); // Smaller exponent for broader highlight
    vec3 specular = specularStrength * spec * lightColor;

    // Combine lighting and apply the block's own color
    vec3 lightingEffect = ambient + diffuse + specular;
    vec3 result = lightingEffect * VertexColor_FS; // Modulate lighting by vertex color

    // Apply gamma correction
    result = pow(result, vec3(1.0/gamma));
    FragColor = vec4(result, 1.0);
}