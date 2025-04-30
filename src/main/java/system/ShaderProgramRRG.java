package system;

/**
 * This class is designed to create shader programs that utilize both RG and R image units.
 * In OpenGL, image uniforms require explicitly specified formats, which often results in maintaining
 * two separate shader files with identical code except for the image format declarations.
 * This class simplifies that process by managing two shaders that share the same logic but differ only in image format.
 */

public class ShaderProgramRRG {
    private static final String STRING_TO_REPLACE = "REPLACE_ME";

    private final ShaderProgram programR;
    private final ShaderProgram programRG;

    public ShaderProgramRRG(String file) {
        String rawSource = Shader.readString(file);
        String rSource = rawSource.replace(STRING_TO_REPLACE, "r32f");
        String rgSource = rawSource.replace(STRING_TO_REPLACE, "rg32f");

        this.programR = ShaderProgram.createComputeProgramFromSource(rSource);
        this.programRG = ShaderProgram.createComputeProgramFromSource(rgSource);
    }

    public ShaderProgram getProgramR() {
        return programR;
    }

    public ShaderProgram getProgramRG() {
        return programRG;
    }
}
