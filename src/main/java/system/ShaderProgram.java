package system;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

public class ShaderProgram {
    private final static Set<ShaderProgram> PROGRAMS_TO_CLEANUP = new HashSet<>();

    private final int programId;
    private final Set<Shader> attachedShaders = new HashSet<>();

    private Map<String, Integer> uniformLocations;

    public ShaderProgram(boolean autoCleanup) {
        programId = glCreateProgram();
        if (programId == 0) throw new RuntimeException("Could not create Shader");
        System.out.println("Created shader program " + programId);

        if (autoCleanup) PROGRAMS_TO_CLEANUP.add(this);
    }

    public void link() {
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Error linking Shader code: " + glGetProgramInfoLog(programId, 1024));

        System.out.println("Linked shader program " + programId);

        // Detach and delete shaders after linking
        for (Shader shader : attachedShaders) {
            detachShader(shader.getId());
            shader.delete();
        }
        attachedShaders.clear();
    }

    public void use() {
        glUseProgram(programId);
    }

    public void attachShader(Shader shader) {
        glAttachShader(programId, shader.getId());
        attachedShaders.add(shader);
        System.out.println("Attached shader " + shader.getId() + " to program " + programId);
    }

    private void detachShader(int shaderId) {
        glDetachShader(programId, shaderId);
    }

    public void delete() {
        glDeleteProgram(programId);
        System.out.println("Deleted shader program " + programId);
    }

    public int getUniformLocation(String name) {
        if (uniformLocations == null) uniformLocations = new HashMap<>();

        Integer location = uniformLocations.get(name);
        if (location == null) {
            location = glGetUniformLocation(programId, name);
            if (location == -1) System.err.println("Could not find uniform variable '" + name + "'");
            uniformLocations.put(name, location);
        }

        return location;
    }

    public void setUniform(String name, float value) {
        // Remember to use the program before setting uniforms
        glUniform1f(getUniformLocation(name), value);
    }

    public void setUniform(String name, float value1, float value2) {
        // Remember to use the program before setting uniforms
        glUniform2f(getUniformLocation(name), value1, value2);
    }

    public static ShaderProgram createComputeProgramFromFile(String file) {
        ShaderProgram computeProgram = new ShaderProgram(true);
        Shader shader = Shader.createFromFile(file, GL_COMPUTE_SHADER);
        computeProgram.attachShader(shader);
        computeProgram.link();

        return computeProgram;
    }

    public static ShaderProgram createComputeProgramFromSource(String source) {
        ShaderProgram computeProgram = new ShaderProgram(true);
        Shader shader = Shader.createFromSource(source, GL_COMPUTE_SHADER);
        computeProgram.attachShader(shader);
        computeProgram.link();

        return computeProgram;
    }

    public static void cleanupAll() {
        System.out.println("Cleaning up " + PROGRAMS_TO_CLEANUP.size() + " shader programs");
        for (ShaderProgram program : PROGRAMS_TO_CLEANUP)
            program.delete();
    }
}