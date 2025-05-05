package system;

import java.util.EnumSet;

/**
 * This class is designed to create shader programs that utilize R, RG, or RGB image units.
 * In OpenGL, image uniforms require explicitly specified formats, which often results in maintaining
 * separate shader files with identical code except for the image format declarations.
 * This class simplifies that process by managing multiple shaders that share the same logic but differ only in image format.
 */

public class MultiProgramManager {
    public enum Formats {R, RG, RGBA}
    private static final String STRING_TO_REPLACE = "REPLACE_ME";

    private final String rawSource;
    private ShaderProgram programR;
    private ShaderProgram programRG;
    private ShaderProgram programRGBA;

    public MultiProgramManager(String file, EnumSet<Formats> formats) {
        rawSource = Shader.readString(file);

        if(formats.contains(Formats.R))
            this.programR = createProgram("r32f");
        if (formats.contains(Formats.RG))
            this.programRG = createProgram("rg32f");
        if (formats.contains(Formats.RGBA))
            this.programRGBA = createProgram("rgba32f");
    }

    public ShaderProgram getProgramR() {
        return programR;
    }

    public ShaderProgram getProgramRG() {
        return programRG;
    }

    public ShaderProgram getProgramRGBA() {
        return programRGBA;
    }

    private ShaderProgram createProgram(String replacement) {
        String source = rawSource.replace(STRING_TO_REPLACE, replacement);
        return ShaderProgram.createComputeProgramFromSource(source);
    }
}
