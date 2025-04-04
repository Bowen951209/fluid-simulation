import org.joml.Math;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class AppSwing extends JFrame {
    private final int n = 200;
    private final int iterationCount = 20;
    private float[][] prevDensities = new float[n + 2][n + 2];
    private float[][] densities = new float[n + 2][n + 2];
    private final float[][] divergence = new float[n + 2][n + 2];
    private final float[][] pressure = new float[n + 2][n + 2];

    private float[][] prevVelocitiesX = new float[n + 2][n + 2];
    private float[][] prevVelocitiesY = new float[n + 2][n + 2];
    private float[][] velocitiesX = new float[n + 2][n + 2];
    private float[][] velocitiesY = new float[n + 2][n + 2];
    private float deltaTime;
    private boolean isLeftMouseButtonDown;
    private Point lastMousePos;

    public AppSwing() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent evt) {
                // Exit on ESCAPE key press
                if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                    System.exit(0);
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    isLeftMouseButtonDown = true;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastMousePos = null;

                if (e.getButton() == MouseEvent.BUTTON1) {
                    isLeftMouseButtonDown = false;
                }
            }
        });

        // Add the fluid panel as the canvas
        FluidPanel fluidPanel = new FluidPanel(densities, n);
        add(fluidPanel);

        pack();
        setResizable(false);
        setVisible(true);

        // Simulation thread
        new Thread(() -> {
            while (true) {
                long startTime = System.currentTimeMillis();
                getSourcesFromUI();

                velocityStep();
                densityStep();
                clearSource(prevDensities);
                clearSource(prevVelocitiesX);
                clearSource(prevVelocitiesY);
                fluidPanel.repaint();

                long elapsedTime = System.currentTimeMillis() - startTime;
                deltaTime = (float) elapsedTime / 1000.0f;

                // Show elapsed time and FPS in the title bar
                setTitle("Fluid Simulation - Elapsed Time: " + elapsedTime + " ms, FPS: " + (1000.0f / deltaTime));
            }
        }).start();
    }

    private void getSourcesFromUI() {
        Point mousePos = getMousePosition();
        if (!isLeftMouseButtonDown || mousePos == null) return;

        // Convert mouse coordinates to grid coordinates
        int gridX = Math.clamp(1, n, (mousePos.x - getInsets().left) * n / (getWidth() - getInsets().left - getInsets().right));
        int gridY = Math.clamp(1, n, (mousePos.y - getInsets().top) * n / (getHeight() - getInsets().top - getInsets().bottom));

        // Add source at the mouse position
        int halfSize = 5;
        for (int i = -halfSize; i <= halfSize; i++) {
            for (int j = -halfSize; j <= halfSize; j++) {
                int sourceX = Math.clamp(1, n, gridX + i);
                int sourceY = Math.clamp(1, n, gridY + j);
                prevDensities[sourceX][sourceY] = 10f;
            }
        }

        // Add velocity source at the mouse position
        if (lastMousePos != null) {
            for (int i = -halfSize; i <= halfSize; i++) {
                for (int j = -halfSize; j <= halfSize; j++) {
                    int sourceX = Math.clamp(1, n, gridX + i);
                    int sourceY = Math.clamp(1, n, gridY + j);

                    prevVelocitiesX[sourceX][sourceY] = mousePos.x - lastMousePos.x;
                    prevVelocitiesY[sourceX][sourceY] = mousePos.y - lastMousePos.y;
                }
            }
        }

        lastMousePos = mousePos;
    }

    private void addSource(float[][] arr, float[][] source) {
        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr[0].length; j++) {
                arr[i][j] += deltaTime * source[i][j];
            }
        }
    }

    private void clearSource(float[][] source) {
        for (int i = 0; i < source.length; i++) {
            for (int j = 0; j < source[0].length; j++) {
                source[i][j] = 0;
            }
        }
    }


    private void diffuse(float[][] arr, float[][] arrPrev, float rate, int bound) {
        float a = deltaTime * rate * n * n;

        for (int k = 0; k < iterationCount; k++) {
            for (int i = 1; i <= n; i++) {
                for (int j = 1; j <= n; j++) {
                    float left = arr[i - 1][j];
                    float right = arr[i + 1][j];
                    float up = arr[i][j - 1];
                    float down = arr[i][j + 1];
                    arr[i][j] = (arrPrev[i][j] + a * (left + right + up + down)) / (1 + 4 * a);
                }
            }
            setBound(bound, arr);
        }
    }

    private void advect(float[][] arr, int bound) {
        float deltaTime0 = deltaTime * n;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= n; j++) {
                float x = Math.clamp(0.5f, n + 0.5f, i - deltaTime0 * velocitiesX[i][j]);
                float y = Math.clamp(0.5f, n + 0.5f, j - deltaTime0 * velocitiesY[i][j]);

                int i0 = (int) x;
                int i1 = i0 + 1;
                int j0 = (int) y;
                int j1 = j0 + 1;

                float s1 = x - i0;
                float s0 = 1 - s1;
                float t1 = y - j0;
                float t0 = 1 - t1;

                arr[i][j] = s0 * (t0 * arr[i0][j0] + t1 * arr[i0][j1]) +
                        s1 * (t0 * arr[i1][j0] + t1 * arr[i1][j1]);
            }
        }
        setBound(bound, arr);
    }

    private void project() {
        float h = 1.0f / n;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= n; j++) {
                divergence[i][j] =
                        -0.5f * h * (velocitiesX[i + 1][j] - velocitiesX[i - 1][j] +
                                velocitiesY[i][j + 1] - velocitiesY[i][j - 1]);

                pressure[i][j] = 0;
            }
        }
        setBound(0, divergence);
        setBound(0, pressure);

        for (int k = 0; k < iterationCount; k++) {
            for (int i = 1; i <= n; i++) {
                for (int j = 1; j <= n; j++) {
                    pressure[i][j] = (pressure[i - 1][j] + pressure[i + 1][j] +
                            pressure[i][j - 1] + pressure[i][j + 1] + divergence[i][j]) / 4;
                }
            }
            setBound(0, pressure);
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= n; j++) {
                velocitiesX[i][j] -= 0.5f * (pressure[i + 1][j] - pressure[i - 1][j]) / h;
                velocitiesY[i][j] -= 0.5f * (pressure[i][j + 1] - pressure[i][j - 1]) / h;
            }
        }
        setBound(1, velocitiesX);
        setBound(2, velocitiesY);
    }

    private void setBound(int bound, float[][] arr) {
        for (int i = 1; i <= n; i++) {
            arr[0][i] = bound == 1 ? -arr[1][i] : arr[1][i];
            arr[n + 1][i] = bound == 1 ? -arr[n][i] : arr[n][i];
            arr[i][0] = bound == 2 ? -arr[i][1] : arr[i][1];
            arr[i][n + 1] = bound == 2 ? -arr[i][n] : arr[i][n];
        }
        arr[0][0] = 0.5f * (arr[1][0] + arr[0][1]);
        arr[0][n + 1] = 0.5f * (arr[1][n + 1] + arr[0][n]);
        arr[n + 1][0] = 0.5f * (arr[n][0] + arr[n + 1][1]);
        arr[n + 1][n + 1] = 0.5f * (arr[n][n + 1] + arr[n + 1][n]);
    }

    private void densityStep() {
        addSource(densities, prevDensities);

        // swap and diffuse
        float[][] temp = prevDensities;
        prevDensities = densities;
        densities = temp;

        float diffusionRate = 0.1f;
        diffuse(densities, prevDensities, diffusionRate, 0);

        // swap and advect
        temp = prevDensities;
        prevDensities = densities;
        densities = temp;

        advect(densities, 0);
    }

    private void velocityStep() {
        addSource(velocitiesX, prevVelocitiesX);
        addSource(velocitiesY, prevVelocitiesY);

        // swap and diffuse
        float[][] temp = prevVelocitiesX;
        prevVelocitiesX = velocitiesX;
        velocitiesX = temp;

        temp = prevVelocitiesY;
        prevVelocitiesY = velocitiesY;
        velocitiesY = temp;

        float viscosity = 0.01f;
        diffuse(velocitiesX, prevVelocitiesX, viscosity, 1);
        diffuse(velocitiesY, prevVelocitiesY, viscosity, 2);

        project();

        // swap and advect
        temp = prevVelocitiesX;
        prevVelocitiesX = velocitiesX;
        velocitiesX = temp;

        temp = prevVelocitiesY;
        prevVelocitiesY = velocitiesY;
        velocitiesY = temp;

        advect(velocitiesX, 1);
        advect(velocitiesY, 2);


        project();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AppSwing::new);
    }

    static class FluidPanel extends JPanel {
        private final float[][] densities;
        private final int n;

        public FluidPanel(float[][] densities, int n) {
            this.densities = densities;
            this.n = n;
            setPreferredSize(new Dimension(n, n));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, n, n);

            for (int i = 1; i <= n; i++) {
                for (int j = 1; j <= n; j++) {
                    float value = Math.clamp(0f, 1f, densities[i][j]);
                    g2d.setColor(new Color(value, value, value));
                    g2d.fillRect(i, j, 1, 1);
                }
            }
        }
    }
}
