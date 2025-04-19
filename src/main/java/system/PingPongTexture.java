package system;

import java.nio.ByteBuffer;

public class PingPongTexture {

    private Texture readTexture;
    private Texture writeTexture;

    public PingPongTexture(int width, int height, int internalFormat, int format, ByteBuffer data, boolean autoCleanup) {
        readTexture = new Texture(width, height, internalFormat, format, data, autoCleanup);
        writeTexture = new Texture(width, height, internalFormat, format, data, autoCleanup);
    }

    public void swapReadWrite() {
        Texture temp = readTexture;
        readTexture = writeTexture;
        writeTexture = temp;
    }

    public void clearRead() {
        readTexture.clearData();
    }

    public void clearWrite() {
        writeTexture.clearData();
    }

    public void clearData() {
        clearRead();
        clearWrite();
    }

    public Texture getReadTexture() {
        return readTexture;
    }

    public Texture getWriteTexture() {
        return writeTexture;
    }
}
