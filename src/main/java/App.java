import org.lwjgl.BufferUtils;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import system.*;

import java.nio.ByteBuffer;
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
    private static final int NUM_JACOBI_STEPS = 40;
    private static final int NUM_LOCAL_SIZE_X = 16;
    private static final int NUM_LOCAL_SIZE_Y = 16;


    private long window;
    private Texture velocityTextureRead, velocityTextureWrite, divergenceTexture, pressureTexture;
    private ShaderProgram screenProgram, advectProgram, jacobiRGProgram, divergenceProgram,
            projectProgram, copyProgram, jacobiRProgram;
    private List<VAO> vaos;
    private int width, height;
    private float deltaTime;

    public void run(int width, int height, String title) {
        this.width = width;
        this.height = height;

        System.out.println("LWJGL Version:" + Version.getVersion() + "!");

        initGLFW(title);
        GL.createCapabilities();
        initShaders();
        initTextures();
        initBufferObjects();

        loop();
        free();
    }

    private void initGLFW(String title) {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); // the window will not be resizable
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

    private void initTextures() {
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 2 * Float.BYTES);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                float x = (float) i / width;
                float y = (float) j / height;
                float u = (float) Math.PI * (float) Math.cos(Math.PI * y) * (float) Math.sin(Math.PI * x);
                float v = -(float) Math.PI * (float) Math.cos(Math.PI * x) * (float) Math.sin(Math.PI * y);
                buffer.putFloat(u).putFloat(v);
            }
        }
        buffer.flip();

        velocityTextureRead = new Texture(width, height, GL_RG32F, GL_RG, buffer, true);
        velocityTextureWrite = new Texture(width, height, GL_RG32F, GL_RG, null, true);
        divergenceTexture = new Texture(width, height, GL_R32F, GL_RED, null, true);
        pressureTexture = new Texture(width, height, GL_R32F, GL_RED, null, true);
    }

    private void initShaders() {
            Shader screenVertexShader = new Shader("shaders/vertex.glsl", GL_VERTEX_SHADER);
            Shader screenFragmentShader = new Shader("shaders/fragment.glsl", GL_FRAGMENT_SHADER);

            screenProgram = new ShaderProgram(true);
            screenProgram.attachShader(screenVertexShader);
            screenProgram.attachShader(screenFragmentShader);
            screenProgram.link();

            Shader advectShader = new Shader("shaders/advect.glsl", GL_COMPUTE_SHADER);
            advectProgram = new ShaderProgram(true);
            advectProgram.attachShader(advectShader);
            advectProgram.link();

            advectProgram.use();
            advectProgram.setUniform("resolution", width, height);

            Shader jacobiRGShader = new Shader("shaders/jacobiRG.glsl", GL_COMPUTE_SHADER);
            jacobiRGProgram = new ShaderProgram(true);
            jacobiRGProgram.attachShader(jacobiRGShader);
            jacobiRGProgram.link();

            Shader jacobiRShader = new Shader("shaders/jacobiR.glsl", GL_COMPUTE_SHADER);
            jacobiRProgram = new ShaderProgram(true);
            jacobiRProgram.attachShader(jacobiRShader);
            jacobiRProgram.link();

            Shader divergenceShader = new Shader("shaders/divergence.glsl", GL_COMPUTE_SHADER);
            divergenceProgram = new ShaderProgram(true);
            divergenceProgram.attachShader(divergenceShader);
            divergenceProgram.link();

            Shader projectShader = new Shader("shaders/project.glsl", GL_COMPUTE_SHADER);
            projectProgram = new ShaderProgram(true);
            projectProgram.attachShader(projectShader);
            projectProgram.link();

            Shader copyShader = new Shader("shaders/copyRG.glsl", GL_COMPUTE_SHADER);
            copyProgram = new ShaderProgram(true);
            copyProgram.attachShader(copyShader);
            copyProgram.link();
    }

    private void initBufferObjects() {
        vaos = new ArrayList<>();

        // Vertex data for a quad (positions only)
        float[] quadVertices = {
                -0.5f, -0.5f, 0.0f,  // Bottom-left
                0.5f, -0.5f, 0.0f,  // Bottom-right
                0.5f, 0.5f, 0.0f,  // Top-right
                -0.5f, 0.5f, 0.0f   // Top-left
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
        VAO quadVAO = new VAO(quadVertices.length, true, true) {
            @Override
            public void draw() {
                velocityTextureRead.bindToUnit(0);
                super.draw();
            }
        };
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

    private void fluidStep() {
        int numGroupsX = (velocityTextureRead.getWidth() + NUM_LOCAL_SIZE_X - 1) / NUM_LOCAL_SIZE_X;
        int numGroupsY = (velocityTextureRead.getHeight() + NUM_LOCAL_SIZE_Y - 1) / NUM_LOCAL_SIZE_Y;

        // --- Advection ---
        velocityTextureWrite.bindToImageUnit(0, GL_READ_WRITE);
        velocityTextureRead.bindToUnit(0);

        advectProgram.use();
        advectProgram.setUniform("deltaTime", deltaTime);

        glDispatchCompute(numGroupsX, numGroupsY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        // -----------------


        // Copy the data in velocityTextureWrite to velocityTextureRead
        velocityTextureRead.bindToImageUnit(0, GL_READ_WRITE);
        velocityTextureWrite.bindToUnit(0);

        copyProgram.use();
        glDispatchCompute(numGroupsX, numGroupsY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);

        // Swap velocity texture write and read (Ping-Pong)
        Texture temp = velocityTextureRead;
        velocityTextureRead = velocityTextureWrite;
        velocityTextureWrite = temp;

        velocityTextureWrite.bindToImageUnit(0, GL_READ_WRITE);
        velocityTextureRead.bindToUnit(0);


        // --- Diffusion ---
        velocityTextureWrite.bindToImageUnit(0, GL_READ_WRITE);
        velocityTextureRead.bindToUnit(0);

        jacobiRGProgram.use();
        float diffusionRate = 0.0001f;
        float a = deltaTime * diffusionRate;
        float b = 1f / (4f + a);
        jacobiRGProgram.setUniform("a", a);
        jacobiRGProgram.setUniform("b", b);

        for (int i = 0; i < NUM_JACOBI_STEPS; i++) {
            glDispatchCompute(numGroupsX, numGroupsY, 1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        }
        // -----------------

        // --- Divergence ---
        divergenceTexture.bindToImageUnit(0, GL_WRITE_ONLY);

        divergenceProgram.use();
        float gridScale = 0.5f / (1.0f / width);
        divergenceProgram.setUniform("gridScale", gridScale);

        glDispatchCompute(numGroupsX, numGroupsY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        // ------------------

        // --- Pressure Solve ---
        divergenceTexture.bindToImageUnit(0, GL_WRITE_ONLY);
        pressureTexture.bindToUnit(0);

        jacobiRProgram.use();
        jacobiRProgram.setUniform("a", a);
        jacobiRProgram.setUniform("b", b);

        for (int i = 0; i < NUM_JACOBI_STEPS; i++) {
            glDispatchCompute(numGroupsX, numGroupsY, 1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        }
        // ----------------------

        // --- Projection ---
        pressureTexture.bindToUnit(0);
        velocityTextureWrite.bindToImageUnit(0, GL_READ_WRITE);

        projectProgram.use();
        projectProgram.setUniform("gridScale", gridScale);

        glDispatchCompute(numGroupsX, numGroupsY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
        // ------------------
    }

    private void loop() {
        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            long loopStartTime = System.currentTimeMillis();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            // Perform the fluid simulation step
            fluidStep();

            // Render the models for rasterization
            screenProgram.use();
            for (VAO vao : vaos) {
                vao.bind();
                vao.draw();
            }

            glfwSwapBuffers(window); // swap the color buffers

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();

            deltaTime = (float) (System.currentTimeMillis() - loopStartTime) / 1000.0f;
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