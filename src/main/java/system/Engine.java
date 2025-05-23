package system;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.EnumSet;

import static org.lwjgl.opengl.GL43.*;

public class Engine {
    public static final int NUM_LOCAL_SIZE_X = 16;
    public static final int NUM_LOCAL_SIZE_Y = 16;
    private static final int JACOBI_ITERATION_COUNT = 40;
    private static ShaderProgram subtractPressureProgram, divergenceProgram, setBoundProgram;
    private static MultiProgramManager addSourceProgramMgr, jacobiProgramMgr, advectProgramMgr;

    public final int nX;
    public final int nY;
    public final int textureWidth;
    public final int textureHeight;
    public final int numGroupsX;
    public final int numGroupsY;
    public final int setBoundNumGroups;
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

    public Engine(int nX, int nY) {
        this.nX = nX;
        this.nY = nY;
        this.textureWidth = nX + 2;
        this.textureHeight = nY + 2;
        this.numGroupsX = getNumGroupsX(textureWidth);
        this.numGroupsY = getNumGroupsY(textureHeight);
        this.setBoundNumGroups = getNumGroupsX(2 * (textureWidth + textureHeight) - 4);
        this.densities = new PingPongTexture(textureWidth, textureHeight, GL_R32F, GL_RED, null, true);
        this.velocities = new PingPongTexture(textureWidth, textureHeight, GL_RG32F, GL_RG, null, true);
        this.divergence = new Texture(textureWidth, textureHeight, GL_R32F, GL_RED, null, true);
        this.pressure = new Texture(textureWidth, textureHeight, GL_R32F, GL_RED, null, true);
        this.userInputVelocityArray = new float[textureWidth][textureHeight][2];
        this.userInputVelocityBuffer = BufferUtils.createFloatBuffer(textureWidth * textureHeight * 2);
        this.userInputDensityArray = new float[textureWidth][textureHeight];
        this.userInputDensityBuffer = BufferUtils.createFloatBuffer(textureWidth * textureHeight);
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

                if (x < 0 || x >= textureWidth || y < 0 || y >= textureHeight) {
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

        boolean isVelocity = readTexture.getFormat() == GL_RG;
        ShaderProgram program = isVelocity ? addSourceProgramMgr.getProgramRG() : addSourceProgramMgr.getProgramR();
        program.use();
        program.setUniform("deltaTime", deltaTime);

        glDispatchCompute(numGroupsX, numGroupsY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void diffuse(PingPongTexture texture, float rate, float deltaTime) {
        Texture readTexture = texture.getReadTexture();
        Texture writeTexture = texture.getWriteTexture();

        readTexture.bindToUnit(0);
        writeTexture.bindToImageUnit(0, GL_READ_WRITE);

        boolean isVelocity = readTexture.getFormat() == GL_RG;
        ShaderProgram program = isVelocity ? jacobiProgramMgr.getProgramRG() : jacobiProgramMgr.getProgramR();
        program.use();
        float a = deltaTime * rate * nX * nY;
        float b = 1f / (1 + 4 * a);
        program.setUniform("a", a);
        program.setUniform("b", b);

        for (int i = 0; i < JACOBI_ITERATION_COUNT; i++) {
            glDispatchCompute(numGroupsX, numGroupsY, 1);
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
        ShaderProgram program = isVelocity ? advectProgramMgr.getProgramRG() : advectProgramMgr.getProgramR();
        program.use();
        float deltaTime0 = deltaTime * nX;
        program.setUniform("deltaTime0", deltaTime0);

        glDispatchCompute(numGroupsX, numGroupsY, 1);
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
        float h = -0.5f / nX;
        divergenceProgram.setUniform("h", h);

        glDispatchCompute(numGroupsX, numGroupsY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void solvePressure() {
        divergence.bindToUnit(0);
        pressure.bindToImageUnit(0, GL_READ_WRITE);


        ShaderProgram program = jacobiProgramMgr.getProgramR();
        program.use();
        program.setUniform("a", 1f);
        program.setUniform("b", 0.25f);

        for (int i = 0; i < JACOBI_ITERATION_COUNT; i++) {
            glDispatchCompute(numGroupsX, numGroupsY, 1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        }
    }

    private void subtractPressure() {
        pressure.bindToUnit(0);

        subtractPressureProgram.use();
        float h = -0.5f * nX;
        subtractPressureProgram.setUniform("h", h);

        glDispatchCompute(numGroupsX, numGroupsY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void getSourcesFromUI() {
        // Reset buffers.
        userInputVelocityBuffer.clear();
        userInputDensityBuffer.clear();

        // Copy user input data to buffers.
        for (int i = 0; i < textureHeight; i++) {
            for (int j = 0; j < textureWidth; j++) {
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
        for (int i = 0; i < textureWidth; i++) {
            for (int j = 0; j < textureHeight; j++) {
                userInputVelocityArray[i][j][0] = 0;
                userInputVelocityArray[i][j][1] = 0;
                userInputDensityArray[i][j] = 0;
            }
        }
    }

    private void setBound() {
        setBoundProgram.use();
        glDispatchCompute(setBoundNumGroups, 1, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void clearSources() {
        densities.clearRead();
        velocities.clearRead();
        pressure.clearData();
    }

    public static void init() {
        var flag = EnumSet.of(MultiProgramManager.Formats.R, MultiProgramManager.Formats.RG);
        addSourceProgramMgr = new MultiProgramManager("shaders/addSource.glsl", flag);
        jacobiProgramMgr = new MultiProgramManager("shaders/jacobi.glsl", flag);
        advectProgramMgr = new MultiProgramManager("shaders/advect.glsl", flag);
        subtractPressureProgram = ShaderProgram.createComputeProgramFromFile("shaders/subtractPressure.glsl");
        divergenceProgram = ShaderProgram.createComputeProgramFromFile("shaders/divergence.glsl");
        setBoundProgram = ShaderProgram.createComputeProgramFromFile("shaders/setBound.glsl");
    }

    private static int getNumGroupsX(int size) {
        return (size + NUM_LOCAL_SIZE_X - 1) / NUM_LOCAL_SIZE_X;
    }

    private static int getNumGroupsY(int size) {
        return (size + NUM_LOCAL_SIZE_Y - 1) / NUM_LOCAL_SIZE_Y;
    }
}
