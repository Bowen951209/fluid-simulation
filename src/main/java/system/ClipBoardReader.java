package system;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;

public class ClipBoardReader {
    public static String getClipboardString() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read clipboard data", e);
        }
    }
}
