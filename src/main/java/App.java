import org.lwjgl.BufferUtils;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import system.*;

import java.nio.FloatBuffer;
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
    private ShaderProgram screenProgram;
    private FloatBuffer userInputBuffer;
    private List<VAO> vaos;
    private int width, height, lastMouseX, lastMouseY;
    private float deltaTime;
    private Engine engine;

    public void run(int width, int height, String title) {
        this.width = width;
        this.height = height;

        System.out.println("LWJGL Version:" + Version.getVersion() + "!");

        initGLFW(title);
        GL.createCapabilities();
        initShaders();
        initTextures();
        initBufferObjects();
        Engine.init();
        engine = new Engine();

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

        // Setup a mouse callback.
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_RELEASE) {
                return;
            }

            userInputBuffer.clear();
            for (int i = 0; i < userInputBuffer.capacity(); i++) {
                userInputBuffer.put(0);
            }


            // Mouse dragging logic:
            ypos = height - ypos; // Invert Y coordinate
            int index = (int) (ypos * width + xpos) * 3;

            float velocityX = (float) (xpos - lastMouseX) * 10f;
            float velocityY = (float) (ypos - lastMouseY) * 10f;


            int halfSize = 2;
            for (int i = -halfSize; i <= halfSize; i++) {
                for (int j = -halfSize; j <= halfSize; j++) {
                    int offsetIndex = index + (i * width + j) * 3;
                    if (offsetIndex < 0 || offsetIndex >= userInputBuffer.capacity() - 3) {
                        continue; // Out of bounds
                    }
                    userInputBuffer.put(offsetIndex, velocityX);
                    userInputBuffer.put(offsetIndex + 1, velocityY);
                    userInputBuffer.put(offsetIndex + 2, 3f); // density
                }
            }
            lastMouseX = (int) xpos;
            lastMouseY = (int) ypos;
            userInputBuffer.flip();
            engine.getUserInputTexture().putData(userInputBuffer);
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
        // For the user input texture
        userInputBuffer = BufferUtils.createFloatBuffer(width * height * 3);
    }

    private void initShaders() {
        Shader screenVertexShader = new Shader("shaders/vertex.glsl", GL_VERTEX_SHADER);
        Shader screenFragmentShader = new Shader("shaders/fragment.glsl", GL_FRAGMENT_SHADER);

        screenProgram = new ShaderProgram(true);
        screenProgram.attachShader(screenVertexShader);
        screenProgram.attachShader(screenFragmentShader);
        screenProgram.link();
    }

    private void initBufferObjects() {
        vaos = new ArrayList<>();

        // Vertex data for a quad (positions only)
        float[] quadVertices = {
                -1f, -1f, 0.0f,  // Bottom-left
                1f, -1f, 0.0f,  // Bottom-right
                1f, 1f, 0.0f,  // Top-right
                -1f, 1f, 0.0f   // Top-left
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
//                engine.getVelocityTexture().bindToUnit(0);
                engine.getDensityTexture().bindToUnit(0);
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

    private void loop() {
        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            long loopStartTime = System.currentTimeMillis();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            // Perform the fluid simulation step
            engine.step(deltaTime);

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
        MemoryUtil.memFree(userInputBuffer);

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
        new App().run(200, 200, "Fluid Simulation");
    }
}