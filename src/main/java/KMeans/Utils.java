package KMeans;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * [7/29/15:15:57]
 *
 * @author @IOAyman
 */
public class Utils {
    public static BufferedImage loadImage (String path) throws IOException {
        File img = new File(path);
        Path imgpath = img.toPath();

        if (!Files.exists(imgpath))
            throw new FileNotFoundException("Err! file [" + imgpath + "] not found!");
        else if (!Files.isReadable(imgpath) || !Files.isWritable(imgpath))
            throw new IOException("Err! Can't read/write into file [" + imgpath + "]!");

        try {
            return ImageIO.read(img);
        } catch (IOException e) {
            System.err.println(e.toString() + " Image '" + imgpath + "' not found.");
            return null;
        }
    }
}
