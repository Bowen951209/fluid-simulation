package system;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL43.*;

public class Texture {
    private static final Set<Texture> TEXTURES_TO_CLEANUP = new HashSet<>();

    private static ShaderProgram clearRProgram, clearRGProgram, clearRGBProgram;
    private final int textureId;
    private final int width;
    private final int height;
    private final int format;
    private final int internalFormat;

    public Texture(int width, int height, int internalFormat, int format, ByteBuffer data, boolean autoCleanup) {
        this.width = width;
        this.height = height;
        this.internalFormat = internalFormat;
        this.format = format;
        this.textureId = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, textureId);

        // Set texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        putData(data);

        if (autoCleanup) TEXTURES_TO_CLEANUP.add(this);

        System.out.println("Created texture " + textureId);
    }

    /**
     * Puts data into the texture.
     * If data is null, an empty data is put.
     * <br>
     * The internal format and format is already set when the constructor is called.
     * <br>
     * This method sets the texel data type to GL_UNSIGNED_BYTE.
     */
    public void putData(ByteBuffer data) {
        glBindTexture(GL_TEXTURE_2D, textureId);

        if (data == null)
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_UNSIGNED_BYTE, 0);
        else
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_UNSIGNED_BYTE, data);
    }

    public void putData(FloatBuffer data) {
        glBindTexture(GL_TEXTURE_2D, textureId);

        if (data == null)
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_FLOAT, 0);
        else
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_FLOAT, data);
    }

    public void clearData() {
        ShaderProgram program;
        if (format == GL_RG) {
            program = clearRGProgram;
        } else if (format == GL_RED) {
            program = clearRProgram;
        } else if (format == GL_RGB) {
            program = clearRGBProgram;
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }

        program.use();
        bindToImageUnit(0, GL_WRITE_ONLY);
        glDispatchCompute(Engine.NUM_GROUPS_X, Engine.NUM_GROUPS_Y, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    public void bindToUnit(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /**
     * Binds the texture to an image unit for use in compute shaders.
     */
    public void bindToImageUnit(int unit, int access) {
        glBindImageTexture(unit, textureId, 0, false, 0, access, internalFormat);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
        System.out.println("Deleted texture " + textureId);
    }

    public int getFormat() {
        return format;
    }

    public static void cleanupAll() {
        System.out.println("Cleaning up " + TEXTURES_TO_CLEANUP.size() + " textures");
        for (Texture texture : TEXTURES_TO_CLEANUP)
            texture.cleanup();

        TEXTURES_TO_CLEANUP.clear();
    }

    public static void initClearPrograms() {
        initClearRProgram();
        initClearRGProgram();
        initClearRGBProgram();
    }

    private static void initClearRProgram() {
        clearRProgram = new ShaderProgram(true);
        Shader shader = new Shader("shaders/clearR.glsl", GL_COMPUTE_SHADER);
        clearRProgram.attachShader(shader);
        clearRProgram.link();
    }

    private static void initClearRGProgram() {
        clearRGProgram = new ShaderProgram(true);
        Shader shader = new Shader("shaders/clearRG.glsl", GL_COMPUTE_SHADER);
        clearRGProgram.attachShader(shader);
        clearRGProgram.link();
    }

    private static void initClearRGBProgram() {
        clearRGBProgram = new ShaderProgram(true);
        Shader shader = new Shader("shaders/clearRGB.glsl", GL_COMPUTE_SHADER);
        clearRGBProgram.attachShader(shader);
        clearRGBProgram.link();
    }
}