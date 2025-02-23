import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import system.*;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class App {
    private long window;
    private Texture quadTexture;
    private ShaderProgram screenProgram, computeProgram;
    private List<VAO> vaos;

    public void run(int width, int height, String title) {
        System.out.println("LWJGL Version:" + Version.getVersion() + "!");

        initGLFW(width, height, title);
        GL.createCapabilities();
        initShaders();
        initTextures(width, height);
        initBufferObjects();

        loop();
        free();
    }

    private void initGLFW(int width, int height, String title) {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);

        // Create the window
        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            assert vidmode != null;
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);
    }

    private void initTextures(int width, int height) {
        quadTexture = new Texture(width, height, true);
        glActiveTexture(GL_TEXTURE0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT, NULL);
        glBindImageTexture(0, quadTexture.getId(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA32F);

        int samplerLocation = screenProgram.getUniformLocation("texSampler");
        screenProgram.use();
        glUniform1i(samplerLocation, 0);
    }

    private void initShaders() {
        Shader screenVertexShader = new Shader("shaders/vertex.glsl", GL_VERTEX_SHADER);
        Shader screenFragmentShader = new Shader("shaders/fragment.glsl", GL_FRAGMENT_SHADER);

        screenProgram = new ShaderProgram(true);
        screenProgram.attachShader(screenVertexShader);
        screenProgram.attachShader(screenFragmentShader);
        screenProgram.link();


        Shader computeShader = new Shader("shaders/compute.glsl", GL_COMPUTE_SHADER);
        computeProgram = new ShaderProgram(true);
        computeProgram.attachShader(computeShader);
        computeProgram.link();
    }

    private void initBufferObjects() {
        vaos = new ArrayList<>();

        // Vertex data for a quad (positions only)
        float[] quadVertices = {
                -0.5f, -0.5f, 0.0f,  // Bottom-left
                0.5f, -0.5f, 0.0f,  // Bottom-right
                0.5f,  0.5f, 0.0f,  // Top-right
                -0.5f,  0.5f, 0.0f   // Top-left
        };

        // Index data for the quad (two triangles)
        int[] quadIndices = {
                0, 1, 2,  // First triangle
                2, 3, 0   // Second triangle
        };

        // Texture coordinates for the quad
        float[] quadTexCoords = {
                0.0f, 0.0f,  // Bottom-left
                1.0f, 0.0f,  // Bottom-right
                1.0f, 1.0f,  // Top-right
                0.0f, 1.0f   // Top-left
        };

        // Initialize objects
        VAO quadVAO = new VAO(quadVertices.length, true, true);
        vaos.add(quadVAO);
        BufferObject quadVBO = new BufferObject(quadVertices, GL_ARRAY_BUFFER, true);
        BufferObject quadIBO = new BufferObject(quadIndices, GL_ELEMENT_ARRAY_BUFFER, true);
        BufferObject quadTBO = new BufferObject(quadTexCoords, GL_ARRAY_BUFFER, true);

        // Configure vertex attributes
        quadVAO.bind();

        quadVBO.bind();
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        quadTBO.bind();
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(1);

        quadIBO.bind();  // Bind IBO for indexed drawing
    }

    private void loop() {
        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            computeProgram.use();
            int localSizeX = 16, localSizeY = 16; // this is set in the shader
            int numGroupsX = (quadTexture.getWidth() + localSizeX - 1) / localSizeX;
            int numGroupsY = (quadTexture.getHeight() + localSizeY - 1) / localSizeY;

            // The dispatch call. This takes most of the time.
            glDispatchCompute(numGroupsX, numGroupsY, 1); // Dispatch the work groups

            // Ensure all work has completed.
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT); // Ensure the write to image is visible to subsequent operations

            screenProgram.use();
            for (VAO vao : vaos) {
                vao.bind();
                vao.draw();
            }

            glfwSwapBuffers(window); // swap the color buffers

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();
        }
    }

    private void free() {
        Texture.cleanupAll();
        ShaderProgram.cleanupAll();
        BufferObject.cleanupAll();
        VAO.cleanupAll();

        glfwFreeCallbacks(window);
        System.out.println("GLFW Callbacks freed!");

        glfwDestroyWindow(window);
        System.out.println("GLFW Window destroyed!");

        glfwTerminate();
        System.out.println("GLFW Terminated!");

        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
        System.out.println("GLFW Error Callback freed!");
    }

    public static void main(String[] args) {
        new App().run(300, 300, "Fluid Simulation");
    }
}