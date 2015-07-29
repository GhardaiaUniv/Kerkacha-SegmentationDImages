package KMeans;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    public static void saveImage (BufferedImage image, String target) {
        try {
            Path file = Files.createFile(Paths.get(target));
            ImageIO.write(image, "png", file.toFile());
        } catch (NullPointerException e) {
            System.err.println("Err! Could not initiate the output file\nExiting");
            System.exit(-1);
        } catch (Exception e) {
            System.err.println(e.toString() + " Image '" + target + "' saving failed.\nExiting");
            System.exit(-1);
        }
    }
}
