package system;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL43.*;

public class Engine {
    public static final int N = 200;
    public static final int TEXTURE_SIZE = N + 2;
    public static final int NUM_LOCAL_SIZE_X = 16;
    public static final int NUM_LOCAL_SIZE_Y = 16;
    public static final int NUM_GROUPS_X = getNumGroupsX(TEXTURE_SIZE);
    public static final int NUM_GROUPS_Y = getNumGroupsY(TEXTURE_SIZE);
    public static final int SET_BOUND_NUM_GROUPS = getNumGroupsX(4 * N - 4);
    private static final int JACOBI_ITERATION_COUNT = 40;
    private static ShaderProgram addSourceProgramRG, addSourceProgramR, jacobiProgramR, jacobiProgramRG, advectProgramR,
            advectProgramRG, subtractPressureProgram, divergenceProgram, setBoundProgram;

    private final PingPongTexture densities;
    private final PingPongTexture velocities;
    private final Texture divergence;
    private final Texture pressure;
    private final FloatBuffer userInputVelocityBuffer;
    private final FloatBuffer userInputDensityBuffer;
    private final float[][][] userInputVelocityArray;
    private final float[][] userInputDensityArray;

    private int lastMouseX;
    private int lastMouseY;

    /**
     * Whether {@link #clear()} method was ever called.
     */
    private boolean hasCleared;

    public Engine() {
        densities = new PingPongTexture(TEXTURE_SIZE, TEXTURE_SIZE, GL_R32F, GL_RED, null, true);
        velocities = new PingPongTexture(TEXTURE_SIZE, TEXTURE_SIZE, GL_RG32F, GL_RG, null, true);
        divergence = new Texture(TEXTURE_SIZE, TEXTURE_SIZE, GL_R32F, GL_RED, null, true);
        pressure = new Texture(TEXTURE_SIZE, TEXTURE_SIZE, GL_R32F, GL_RED, null, true);
        userInputVelocityArray = new float[TEXTURE_SIZE][TEXTURE_SIZE][2];
        userInputVelocityBuffer = BufferUtils.createFloatBuffer(TEXTURE_SIZE * TEXTURE_SIZE * 2);
        userInputDensityArray = new float[TEXTURE_SIZE][TEXTURE_SIZE];
        userInputDensityBuffer = BufferUtils.createFloatBuffer(TEXTURE_SIZE * TEXTURE_SIZE);
    }

    /**
     * Clear {@link #densities} and {@link #velocities} textures.
     */
    public void clear() {
        densities.clearData();
        velocities.clearData();

        hasCleared = true;
    }

    public void userInput(int mouseX, int mouseY) {
        float velocityX = (float) (mouseX - lastMouseX) * 10;
        float velocityY = (float) (mouseY - lastMouseY) * 10;

        int halfSize = 2;
        for (int i = -halfSize; i <= halfSize; i++) {
            for (int j = -halfSize; j <= halfSize; j++) {
                int x = mouseX + i;
                int y = mouseY + j;

                if (x < 0 || x >= TEXTURE_SIZE || y < 0 || y >= TEXTURE_SIZE) {
                    continue; // Skip out-of-bounds coordinates
                }

                userInputVelocityArray[x][y][0] = velocityX;
                userInputVelocityArray[x][y][1] = velocityY;
                userInputDensityArray[x][y] = 1.0f;
            }
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public Texture getDensityTexture() {
        return densities.getWriteTexture();
    }

    public Texture getVelocityTexture() {
        return velocities.getWriteTexture();
    }

    public boolean hasCleared() {
        return hasCleared;
    }

    public void step(float deltaTime) {
        getSourcesFromUI();
        velocityStep(deltaTime);
        densityStep(deltaTime);

        clearSources();
    }

    private void densityStep(float deltaTime) {
        addSource(densities, deltaTime);
        densities.swapReadWrite();
        diffuse(densities, 0.0001f, deltaTime);
        densities.swapReadWrite();
        advect(densities, deltaTime);
    }

    private void velocityStep(float deltaTime) {
        addSource(velocities, deltaTime);
        velocities.swapReadWrite();
        diffuse(velocities, 0.1f, deltaTime);
        project();
        velocities.getWriteTexture().copyFrom(velocities.getReadTexture());
        velocities.swapReadWrite();
        advect(velocities, deltaTime);
        project();
    }

    private void addSource(PingPongTexture texture, float deltaTime) {
        Texture readTexture = texture.getReadTexture();
        Texture writeTexture = texture.getWriteTexture();

        readTexture.bindToUnit(0);
        writeTexture.bindToImageUnit(0, GL_READ_WRITE);

        ShaderProgram program = readTexture.getFormat() == GL_RG ? addSourceProgramRG : addSourceProgramR;
        program.use();
        program.setUniform("deltaTime", deltaTime);

        glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void diffuse(PingPongTexture texture, float rate, float deltaTime) {
        Texture readTexture = texture.getReadTexture();
        Texture writeTexture = texture.getWriteTexture();

        readTexture.bindToUnit(0);
        writeTexture.bindToImageUnit(0, GL_READ_WRITE);

        boolean isVelocity = readTexture.getFormat() == GL_RG;
        ShaderProgram program = isVelocity ? jacobiProgramRG : jacobiProgramR;
        program.use();
        float a = deltaTime * rate * N * N;
        float b = 1f / (1 + 4 * a);
        program.setUniform("a", a);
        program.setUniform("b", b);

        for (int i = 0; i < JACOBI_ITERATION_COUNT; i++) {
            glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

            if (isVelocity) setBound();
        }
    }

    private void advect(PingPongTexture texture, float deltaTime) {
        Texture readTexture = texture.getReadTexture();
        Texture writeTexture = texture.getWriteTexture();

        velocities.getReadTexture().bindToUnit(0);
        readTexture.bindToUnit(1);
        writeTexture.bindToImageUnit(0, GL_READ_WRITE);

        boolean isVelocity = readTexture.getFormat() == GL_RG;
        ShaderProgram program = isVelocity ? advectProgramRG : advectProgramR;
        program.use();
        float deltaTime0 = deltaTime * N;
        program.setUniform("deltaTime0", deltaTime0);

        glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        if (isVelocity) setBound();
    }

    private void project() {
        solveDivergence();
        solvePressure();
        velocities.getWriteTexture().bindToImageUnit(0, GL_READ_WRITE);
        subtractPressure();
        setBound();
    }

    private void solveDivergence() {
        velocities.getWriteTexture().bindToUnit(0);
        divergence.bindToImageUnit(0, GL_WRITE_ONLY);


        divergenceProgram.use();
        float h = -0.5f / N;
        divergenceProgram.setUniform("h", h);

        glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void solvePressure() {
        divergence.bindToUnit(0);
        pressure.bindToImageUnit(0, GL_READ_WRITE);


        jacobiProgramR.use();
        jacobiProgramR.setUniform("a", 1f);
        jacobiProgramR.setUniform("b", 0.25f);

        for (int i = 0; i < JACOBI_ITERATION_COUNT; i++) {
            glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        }
    }

    private void subtractPressure() {
        pressure.bindToUnit(0);

        subtractPressureProgram.use();
        float h = -0.5f * N;
        subtractPressureProgram.setUniform("h", h);

        glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void getSourcesFromUI() {
        // Reset buffers.
        userInputVelocityBuffer.clear();
        userInputDensityBuffer.clear();

        // Copy user input data to buffers.
        for (int i = 0; i < TEXTURE_SIZE; i++) {
            for (int j = 0; j < TEXTURE_SIZE; j++) {
                // Notice that the order of the array is inverted.
                userInputVelocityBuffer.put(userInputVelocityArray[j][i]);
                userInputDensityBuffer.put(userInputDensityArray[j][i]);
            }
        }
        userInputVelocityBuffer.flip();
        userInputDensityBuffer.flip();

        // Copy data to textures.
        velocities.getReadTexture().putData(userInputVelocityBuffer);
        densities.getReadTexture().putData(userInputDensityBuffer);

        // Clear arrays.
        for (int i = 0; i < TEXTURE_SIZE; i++) {
            for (int j = 0; j < TEXTURE_SIZE; j++) {
                userInputVelocityArray[i][j][0] = 0;
                userInputVelocityArray[i][j][1] = 0;
                userInputDensityArray[i][j] = 0;
            }
        }
    }

    private void setBound() {
        setBoundProgram.use();
        glDispatchCompute(SET_BOUND_NUM_GROUPS, 1, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void clearSources() {
        densities.clearRead();
        velocities.clearRead();
        pressure.clearData();
    }

    public static void init() {
        addSourceProgramR = ShaderProgram.createComputeProgram("shaders/addSourceR.glsl");
        addSourceProgramRG = ShaderProgram.createComputeProgram("shaders/addSourceRG.glsl");
        jacobiProgramR = ShaderProgram.createComputeProgram("shaders/jacobiR.glsl");
        jacobiProgramRG = ShaderProgram.createComputeProgram("shaders/jacobiRG.glsl");
        advectProgramR = ShaderProgram.createComputeProgram("shaders/advectR.glsl");
        advectProgramRG = ShaderProgram.createComputeProgram("shaders/advectRG.glsl");
        subtractPressureProgram = ShaderProgram.createComputeProgram("shaders/subtractPressure.glsl");
        divergenceProgram = ShaderProgram.createComputeProgram("shaders/divergence.glsl");
        setBoundProgram = ShaderProgram.createComputeProgram("shaders/setBound.glsl");
    }

    private static int getNumGroupsX(int size) {
        return (size + NUM_LOCAL_SIZE_X - 1) / NUM_LOCAL_SIZE_X;
    }

    private static int getNumGroupsY(int size) {
        return (size + NUM_LOCAL_SIZE_Y - 1) / NUM_LOCAL_SIZE_Y;
    }
}
