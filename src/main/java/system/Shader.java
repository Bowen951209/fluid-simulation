package system;

import java.io.InputStream;

import static org.lwjgl.opengl.GL43.*;

public class Shader {
    private final int shaderId;
    private final String code;

    public Shader(String file, int type) {
        shaderId = glCreateShader(type);
        if (shaderId == 0) throw new RuntimeException("Could not create Shader");

        // Read the shader code from the file
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(file)) {
            if (inputStream == null) throw new RuntimeException("Could not read Shader file: " + file);
            code = new String(inputStream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Could not read Shader file: " + file, e);
        }

        compile();
        System.out.println("Created shader " + shaderId);
    }

    private void compile() {
        glShaderSource(shaderId, code);
        glCompileShader(shaderId);
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Error compiling Shader code: " + glGetShaderInfoLog(shaderId, 1024));
    }

    public void delete() {
        glDeleteShader(shaderId);
        System.out.println("Deleted shader " + shaderId);
    }

    public int getId() {
        return shaderId;
    }
}
