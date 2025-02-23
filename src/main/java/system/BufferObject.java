package system;

import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL43.*;

public class BufferObject {
    private static final Set<BufferObject> BUFFER_OBJ_TO_CLEANUP = new HashSet<>();

    private final int id;
    private final int target;

    public BufferObject(int[] data, int target, boolean autoCleanup) {
        this.id = glGenBuffers();
        this.target = target;

        bind();
        glBufferData(target, data, GL_STATIC_DRAW);

        if (autoCleanup) BUFFER_OBJ_TO_CLEANUP.add(this);
        System.out.println("Created buffer object " + id);
    }

    public BufferObject(float[] data, int target, boolean autoCleanup) {
        this.id = glGenBuffers();
        this.target = target;

        bind();
        glBufferData(target, data, GL_STATIC_DRAW);

        if (autoCleanup) BUFFER_OBJ_TO_CLEANUP.add(this);
        System.out.println("Created buffer object " + id);
    }

    public void bind() {
        glBindBuffer(target, id);
    }

    public void cleanup() {
        glDeleteBuffers(id);
        System.out.println("Deleted buffer object " + id);
    }

    public static void cleanupAll() {
        System.out.println("Cleaning up " + BUFFER_OBJ_TO_CLEANUP.size() + " buffer objects");
        for (BufferObject bufferObject : BUFFER_OBJ_TO_CLEANUP)
            bufferObject.cleanup();
    }
}