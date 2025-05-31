#version 330 core
out vec4 FragColor;

in vec3 FragPos; // World position from vertex shader
in vec3 Normal;
in vec3 v_color; // Original block color from vertex shader (now dark base color)

uniform vec3 lightPos;
uniform vec3 lightColor;
uniform float gamma;
uniform vec3 viewPos; // Camera's world position

// Grid uniforms
uniform float gridSpacing;
uniform float gridLineWidth; // As a fraction of gridSpacing (e.g., 0.1 for 10% thick line)
uniform float gridIntensity;
uniform vec3 gridColorGround;   // e.g., Pink for lower areas
uniform vec3 gridColorMountain; // e.g., Blue for higher areas
uniform float gridTransitionHeight; // Y-value world coordinate for grid color transition mid-point
uniform float gridTransitionRange;  // How spread out the transition is

void main()
{
    // Lighting calculations (simplified Blinn-Phong)
    vec3 ambient = 0.2 * v_color; // Ambient color based on the block's dark color

    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor * v_color; // Diffuse reflects the block's dark color

    vec3 viewDir = normalize(viewPos - FragPos);
    vec3 halfwayDir = normalize(lightDir + viewDir);
    float spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);
    vec3 specular = spec * lightColor * vec3(0.3); // Specular highlights

    vec3 baseLitColor = ambient + diffuse + specular;

    // Grid calculation (anti-aliased lines)

    // w_pixels: line half-width in terms of screen pixels.
    vec2 w_pixels = (gridSpacing * gridLineWidth * 0.5) / fwidth(FragPos.xz);

    // d_world: distance from the center of the grid lines in world units.
    // Ranges from 0 (on the line) to 0.5 * gridSpacing (center of cell).
    vec2 d_world = abs(fract(FragPos.xz/gridSpacing + 0.5) - 0.5) * gridSpacing;

    // d_pixels: convert d_world to pixel units.
    vec2 d_pixels = d_world / fwidth(FragPos.xz);

    // Calculate gridLines.
    // smoothstep(edge0, edge1, x) will be 1 if x <= edge0 and 0 if x >= edge1 (since edge1 < edge0 here).
    // We want gridLines to be 1 ON the line (d_pixels is small) and 0 OFF the line (d_pixels is large).
    // w_pixels is the line half-width in pixels. The line itself is roughly where d_pixels < w_pixels.
    // The transition happens over 1 pixel.
    vec2 gridLines = smoothstep(w_pixels, w_pixels - vec2(1.0), d_pixels);
    float gridFactor = max(gridLines.x, gridLines.y);


    // Determine grid color based on height
    vec3 currentGridColor;
    float transitionFactor = smoothstep(
        gridTransitionHeight - gridTransitionRange * 0.5,
        gridTransitionHeight + gridTransitionRange * 0.5,
        FragPos.y
    );
    currentGridColor = mix(gridColorGround, gridColorMountain, transitionFactor);

    // Mix base lit color with grid color
    vec3 finalColor = mix(baseLitColor, currentGridColor, gridFactor * gridIntensity);

    // Apply gamma correction
    FragColor = vec4(pow(finalColor, vec3(1.0/gamma)), 1.0);
}