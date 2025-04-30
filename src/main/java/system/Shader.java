package system;

import java.io.InputStream;

import static org.lwjgl.opengl.GL43.*;

public class Shader {
    private final int shaderId;
    private final String code;

    private Shader(String string, int type, boolean isFile) {
        shaderId = glCreateShader(type);
        if (shaderId == 0) throw new RuntimeException("Could not create Shader");

        // If string is a file path, read string in the file, else the string is processed as a source code.
        code = isFile ? readString(string) : string;

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

    public static Shader createFromSource(String source, int type) {
        return new Shader(source, type, false);
    }

    public static Shader createFromFile(String source, int type) {
        return new Shader(source, type, true);
    }

    public static String readString(String file) {
        try (InputStream inputStream = Shader.class.getClassLoader().getResourceAsStream(file)) {
            if (inputStream == null) throw new RuntimeException("Could not read Shader file: " + file);
            return new String(inputStream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Could not read Shader file: " + file, e);
        }
    }
}
