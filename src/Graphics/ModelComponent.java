package Graphics;

import org.joml.Matrix4f;

public record ModelComponent(EntityModel model, Matrix4f localTransform, boolean usesEntityShader) {
    /**
     * Constructor for a model component with a default identity local transform and uses entity shader.
     * @param model The entity model.
     */
    public ModelComponent(EntityModel model) {
        this(model, new Matrix4f().identity(), true);
    }

    /**
     * Constructor for a model component with a specific local transform and uses entity shader.
     * @param model The entity model.
     * @param localTransform The local transformation matrix for this component.
     */
    public ModelComponent(EntityModel model, Matrix4f localTransform) {
        this(model, localTransform, true);
    }
}