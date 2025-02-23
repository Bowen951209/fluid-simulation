package system;

import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL43.*;

public class Texture {
    private static final Set<Texture> TEXTURES_TO_CLEANUP = new HashSet<>();

    private final int textureId;
    private final int width;
    private final int height;

    public Texture(int width, int height, boolean autoCleanup) {
        this.width = width;
        this.height = height;
        this.textureId = glGenTextures();

        bind();

        // Set texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Put blank texture data
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);

        if (autoCleanup) TEXTURES_TO_CLEANUP.add(this);

        System.out.println("Created texture " + textureId);
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
        System.out.println("Deleted texture " + textureId);
    }

    public int getId() {
        return textureId;
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