package system;

import static org.lwjgl.opengl.GL43.*;

public class Engine {
    private static final int N = 200;
    private static final int JACOBI_ITERATION_COUNT = 40;
    private static final int NUM_LOCAL_SIZE_X = 16;
    private static final int NUM_LOCAL_SIZE_Y = 16;
    public static final int NUM_GROUPS_X = (N + NUM_LOCAL_SIZE_X - 1) / NUM_LOCAL_SIZE_X;
    public static final int NUM_GROUPS_Y = (N + NUM_LOCAL_SIZE_Y - 1) / NUM_LOCAL_SIZE_Y;
    private static ShaderProgram addSourceProgramRG, addSourceProgramR, jacobiProgramR, jacobiProgramRG, advectProgramR,
            advectProgramRG, subtractPressureProgram, divergenceProgram, sourcesFromUIProgram;

    private final PingPongTexture densities;
    private final PingPongTexture velocities;
    private final Texture divergence;
    private final Texture pressure;
    private final Texture userInputTexture;

    public Engine() {
        densities = new PingPongTexture(N + 2, N + 2, GL_R32F, GL_RED, null, true);
        velocities = new PingPongTexture(N + 2, N + 2, GL_RG32F, GL_RG, null, true);
        divergence = new Texture(N + 2, N + 2, GL_R32F, GL_RED, null, true);
        pressure = new Texture(N + 2, N + 2, GL_R32F, GL_RED, null, true);
        userInputTexture = new Texture(N, N, GL_RGBA32F, GL_RGB, null, true);
    }

    /**
     * Clear {@link #densities} and {@link #velocities} textures.
     */
    public void clear() {
        densities.clearData();
        velocities.clearData();
    }

    public Texture getDensityTexture() {
        return densities.getWriteTexture();
    }

    public Texture getVelocityTexture() {
        return velocities.getWriteTexture();
    }

    public Texture getUserInputTexture() {
        return userInputTexture;
    }

    public void step(float deltaTime) {
        getSourcesFromUI();
        userInputTexture.clearData();
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

        ShaderProgram program = readTexture.getFormat() == GL_RG ? jacobiProgramRG : jacobiProgramR;
        program.use();
        float a = deltaTime * rate * N * N;
        float b = 1f / (1 + 4 * a);
        program.setUniform("a", a);
        program.setUniform("b", b);

        for (int i = 0; i < JACOBI_ITERATION_COUNT; i++) {
            glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        }
    }

    private void advect(PingPongTexture texture, float deltaTime) {
        Texture readTexture = texture.getReadTexture();
        Texture writeTexture = texture.getWriteTexture();

        velocities.getReadTexture().bindToUnit(0);
        readTexture.bindToUnit(1);
        writeTexture.bindToImageUnit(0, GL_WRITE_ONLY);

        ShaderProgram program = readTexture.getFormat() == GL_RG ? advectProgramRG : advectProgramR;
        program.use();
        float deltaTime0 = deltaTime * N;
        program.setUniform("deltaTime0", deltaTime0);

        glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void project() {
        solveDivergence();
        solvePressure();
        subtractPressure();
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
        velocities.getWriteTexture().bindToImageUnit(0, GL_WRITE_ONLY);
        pressure.bindToUnit(0);

        subtractPressureProgram.use();
        float h = -0.5f * N;
        subtractPressureProgram.setUniform("h", h);

        glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void getSourcesFromUI() {
        userInputTexture.bindToUnit(0);
        velocities.getReadTexture().bindToImageUnit(1, GL_WRITE_ONLY);
        densities.getReadTexture().bindToImageUnit(0, GL_WRITE_ONLY);
        sourcesFromUIProgram.use();
        glDispatchCompute(NUM_GROUPS_X, NUM_GROUPS_Y, 1);
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
        sourcesFromUIProgram = ShaderProgram.createComputeProgram("shaders/addSourcesFromUI.glsl");
        Texture.initPrograms();
    }
}
