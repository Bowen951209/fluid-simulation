package system;

import java.awt.*;
import java.awt.image.BufferedImage;

public class FontRenderer {
    private final float[][] targetArray;
    private final BufferedImage bufferedImage;
    private final int width;
    private final int height;
    private final Graphics2D g2d;

    public FontRenderer(Font font, float[][] targetArray) {
        this.targetArray = targetArray;
        this.width = targetArray.length;
        this.height = targetArray[0].length;
        System.out.println("FontRenderer: " + width + "x" + height);
        this.bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        this.g2d = bufferedImage.createGraphics();

        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setFont(font);
    }

    public void renderToArray(String text, int x, int y) {
        // Fill black background
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);

        // Set color to white for text
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, x, y);

        // Copy the buffered image to the FloatBuffer
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int pixel = bufferedImage.getRGB(i, j) & 0x00FFFFFF; // black is 0, white is 1
                targetArray[i][height - 1 - j] = 0.0000001f * pixel;
            }
        }
    }

    public void dispose() {
        g2d.dispose();
    }
}
