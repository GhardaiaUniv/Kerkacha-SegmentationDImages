package KMeans;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;

import static KMeans.Utils.loadImage;
import static KMeans.Utils.saveImage;

public class KMeans {

    public static final int MODE_CONTINUOUS = 0x01;
    public static final int MODE_ITERATIVE = 0x02;
    private static final String USAGE = "\nUsage:\t$ java KMeans  MODE  ClusterCount  /input/path  output/path\n\n" +
            "ClusterCount:\t0-255\n" +
            "MODE\t\t:\t-i (interactive) | -c (continuous)\n\n";

    private Cluster[] clusters;


    // Constructor
    public KMeans () {
    }

    public static void main (String[] args) throws IOException {
        // Check args
        if (args.length != 4) {
            System.out.println(USAGE);
            // Exit with -1 error signal
            System.exit(-1);
        }

        // Parse arguments
        int mode = MODE_CONTINUOUS;     // Default case
        switch (args[0].toLowerCase()) {
            case "-i":
                mode = MODE_ITERATIVE;
                break;
            case "-c":
                mode = MODE_CONTINUOUS;
                break;
            default:
                System.err.println("Err! Unknown mode ... Using default (MODE_CONTINUOUS)");
                break;
        }
        int clusterCount = 0;  // clusterCount
        try {
            clusterCount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid argument [ClusterCount]! Please enter a valid integer value.");
            System.exit(-1);
        }
        if (!(clusterCount >= 0 && clusterCount < 256)) {
            System.err.println("ClusterCount should be in the interval 0-255");
            System.exit(-1);
        }
        String src = args[2];   // image.in
        String target = args[3];   // image.out

        // call the function to actually start the clustering
        BufferedImage clusteredImg = new KMeans().calculate(loadImage(src), clusterCount, mode);
        // save the resulting image
        saveImage(clusteredImg, target);
    }

    public BufferedImage calculate (BufferedImage image, int ClusterCount, int mode) {
        // timer.init()
        long start = System.currentTimeMillis();

        // Get dimensions
        int width = image.getWidth();
        int height = image.getHeight();

        // Create Clusters
        clusters = createClusters(image, ClusterCount);
        // create cluster lookup table
        int[] cLookupTable = new int[width * height];
        Arrays.fill(cLookupTable, -1);

        // at first loop all pixels will move their clusters
        boolean pixelChangedCluster = true;

        // loop until all clusters are stable!
        int loops = 0;
        while (pixelChangedCluster) {
            pixelChangedCluster = false;
            loops++;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = image.getRGB(x, y);
                    Cluster cluster = findMinimalCluster(pixel);
                    if (cLookupTable[width * y + x] != cluster.getId()) {
                        // cluster changed
                        if (mode == MODE_CONTINUOUS) {
                            if (cLookupTable[width * y + x] != -1) {
                                // remove from possible previousthen
                                // cluster
                                clusters[cLookupTable[width * y + x]].removePixel(pixel);
                            }

                            cluster.addPixel(pixel);
                        }

                        pixelChangedCluster = true;


                        cLookupTable[width * y + x] = cluster.getId();
                    }
                }
            }
            if (mode == MODE_ITERATIVE) {
                // update clusters
                for (Cluster cluster : clusters) {
                    cluster.clear();
                }
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int clusterId = cLookupTable[width * y + x];
                        // add pixels to cluster
                        clusters[clusterId].addPixel(image.getRGB(x, y));
                    }
                }
            }

        }   // EndWhile

        // create result image
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int clusterId = cLookupTable[width * y + x];
                result.setRGB(x, y, clusters[clusterId].getRGB());
            }
        }

        // timer.end()
        long end = System.currentTimeMillis();
//        System.out.println("Clustered to " + ClusterCount
//                + " clusters in " + loops
//                + " loops in " + (end - start) + " ms.");
        System.out.printf("DONE in %dms !  Clustered to %d clusters, in %d loops.",
                (end - start), ClusterCount, loops);
        return result;
    }

    public Cluster[] createClusters (BufferedImage image, int ClusterCount) {
        // Here the clusters are taken with specific steps,
        // so the result looks always same with same image.
        // You can randomize the cluster centers, if you like.
        Cluster[] result = new Cluster[ClusterCount];
        int x = 0;
        int y = 0;
        int dx = image.getWidth() / ClusterCount;
        int dy = image.getHeight() / ClusterCount;
        for (int i = 0; i < ClusterCount; i++) {
            result[i] = new Cluster(i, image.getRGB(x, y));
            x += dx;
            y += dy;
        }
        return result;
    }

    public Cluster findMinimalCluster (int rgb) {
        Cluster cluster = null;
        int min = Integer.MAX_VALUE;
        for (Cluster cluster1 : clusters) {
            int distance = cluster1.distance(rgb);
            if (distance < min) {
                min = distance;
                cluster = cluster1;
            }
        }
        return cluster;
    }
}
