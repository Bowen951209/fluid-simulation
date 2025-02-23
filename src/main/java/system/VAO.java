package system;

import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL30.*;

public class VAO {
    private static final Set<VAO> VAOS_TO_CLEANUP = new HashSet<>();

    private final int vaoId;
    private final int vertexCount;
    private final boolean isIndexed;

    public VAO(int vertexCount, boolean isIndexed, boolean autoCleanup) {
        this.vertexCount = vertexCount;
        this.isIndexed = isIndexed;
        this.vaoId = glGenVertexArrays();
        if (autoCleanup) VAOS_TO_CLEANUP.add(this);

        System.out.println("Created VAO " + vaoId);
    }

    public void bind() {
        glBindVertexArray(vaoId);
    }

    public void draw() {
        if (isIndexed)
            glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        else
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
    }

    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        System.out.println("Deleted VAO " + vaoId);
    }

    public static void cleanupAll() {
        System.out.println("Cleaning up " + VAOS_TO_CLEANUP.size() + " VAOs");
        for (VAO vao : VAOS_TO_CLEANUP)
            vao.cleanup();
    }
}