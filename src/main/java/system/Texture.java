package system;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL43.*;

public class Texture {
    private static final Set<Texture> TEXTURES_TO_CLEANUP = new HashSet<>();

    private static ShaderProgram clearRProgram, clearRGProgram, clearRGBProgram, copyRGProgram;
    private final int textureId;
    private final int width;
    private final int height;
    private final int format;
    private final int internalFormat;
    private final int numGroupX;
    private final int numGroupY;

    public Texture(int width, int height, int internalFormat, int format, ByteBuffer data, boolean autoCleanup) {
        this.width = width;
        this.height = height;
        this.internalFormat = internalFormat;
        this.format = format;
        this.textureId = glGenTextures();
        this.numGroupX = (width + Engine.NUM_LOCAL_SIZE_X - 1) / Engine.NUM_LOCAL_SIZE_X;
        this.numGroupY = (height + Engine.NUM_LOCAL_SIZE_Y - 1) / Engine.NUM_LOCAL_SIZE_Y;

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


    /**
     * Puts data into the texture.
     * If data is null, an empty data is put.
     * <br>
     * The internal format and format is already set when the constructor is called.
     * <br>
     * This method sets the texel data type to GL_FLOAT.
     */
    public void putData(FloatBuffer data) {
        glBindTexture(GL_TEXTURE_2D, textureId);

        if (data == null)
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_FLOAT, 0);
        else
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_FLOAT, data);
    }

    /**
     * Clears the texture data using a compute shader.
     */
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
        glDispatchCompute(numGroupX, numGroupY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    public void copyFrom(Texture texture) {
        if (this.format != texture.format)
            throw new IllegalArgumentException("Texture formats do not match: " + this.format + " != " + texture.format);

        if(this.format == GL_RG) {
            copyRGProgram.use();
            texture.bindToUnit(0);
            bindToImageUnit(0, GL_WRITE_ONLY);
            glDispatchCompute(numGroupX, numGroupY, 1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        } else
            throw new IllegalArgumentException("Not supported copying format: " + this.format);
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

    public static void initPrograms() {
        clearRProgram = ShaderProgram.createComputeProgramFromFile("shaders/clearR.glsl");
        clearRGProgram = ShaderProgram.createComputeProgramFromFile("shaders/clearRG.glsl");
        clearRGBProgram = ShaderProgram.createComputeProgramFromFile("shaders/clearRGB.glsl");
        copyRGProgram = ShaderProgram.createComputeProgramFromFile("shaders/copyRG.glsl");
    }
}