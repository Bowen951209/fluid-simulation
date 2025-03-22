package system;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL43.*;

public class Texture {
    private static final Set<Texture> TEXTURES_TO_CLEANUP = new HashSet<>();

    private final int textureId;
    private final int width;
    private final int height;
    private final int internalFormat;
    private final int format;

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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public static void cleanupAll() {
        System.out.println("Cleaning up " + TEXTURES_TO_CLEANUP.size() + " textures");
        for (Texture texture : TEXTURES_TO_CLEANUP)
            texture.cleanup();

        TEXTURES_TO_CLEANUP.clear();
    }
}