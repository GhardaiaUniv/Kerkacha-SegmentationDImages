package CORE;

import javax.media.jai.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.image.renderable.ParameterBlock;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

/**
 * This class implements a simple region growing algorithm to segment a black-and-white
 * image in regions.
 * Since the region growing algorithm uses a stack to store pixels, for large images
 * please set the virtual machine stack size to a large value.
 */
public class SimpleRegionGrowing extends ImageProcessingTask {
    // The input image and its dimensions.
    private PlanarImage input;
    private int width, height;
    // A matrix for the pixel values, one for the selected labels, one which indicates
    // whether a pixel already has a label.
    private byte[][] pixels;
    private int[][] labels;
    // The position, i.e. number of estimated algorithm steps we've already done.
    private long position;
    // The number of regions on the (finished) task.
    private int numberOfRegions;
    // Counters for pixels in each region
    private Map<Integer, Integer> count;

    /**
     * The constructor for the class, which will pre-process the original image.
     * Pre-processing includes converting images from colormapped to plain RGB,
     * from RGB to gray and from gray to binary, if required. When we get the
     * binary image, we will apply a filter to remove the salt-and-pepper noise.
     *
     * @param im         the input image.
     * @param preprocess NOT ( is this input img already black/white ? )
     */
    public SimpleRegionGrowing (PlanarImage im, boolean preprocess) {
        // The input image MUST be black-and-white for this implementation. Let's
        // convert from indexed to RGB, RGB to gray and gray to binary if required.
        if (preprocess) input = preprocess(im);
        else input = im;
        Raster inputRaster = input.getData();
        // Create the data structures needed for the algorithm.
        width = input.getWidth();
        height = input.getHeight();
        labels = new int[width][height];
        pixels = new byte[width][height];
        // Fill the data structures.
        for (int h = 0; h < height; h++)
            for (int w = 0; w < width; w++) {
                pixels[w][h] = (byte) inputRaster.getSample(w, h, 0);
                labels[w][h] = -1;
            }
        position = 0;
        count = new TreeMap<Integer, Integer>();
    }

    /*
     * This
       method converts a color image (indexed or not) to a gray image and then to
     * a black-and-white image through thresholding using its histogram. The black-and-white
     * image is filtered with mathematical morphology openings and closings.
     */
    private PlanarImage preprocess (PlanarImage input) {
        // If the source image is color-mapped, convert it to 3-band RGB.
        // I am not considering the possibility of an image with a IndexColorModel with
        // two bands since there is no guarantee that it *is* a bw image.
        if (input.getColorModel() instanceof IndexColorModel) {
            // Retrieve the IndexColorModel
            IndexColorModel icm = (IndexColorModel) input.getColorModel();
            // Cache the number of elements in each band of the colormap.
            int mapSize = icm.getMapSize();
            // Allocate an array for the lookup table data.
            byte[][] lutData = new byte[3][mapSize];
            // Load the lookup table data from the IndexColorModel.
            icm.getReds(lutData[0]);
            icm.getGreens(lutData[1]);
            icm.getBlues(lutData[2]);
            // Create the lookup table object.
            LookupTableJAI lut = new LookupTableJAI(lutData);
            // Replace the original image with the 3-band RGB image.
            input = JAI.create("lookup", input, lut);
        }
        // Should we convert it to gray-level?
        if (input.getNumBands() > 1) {
            // Create a gray-level image with the weighted average of the three bands.
            double[][] matrix = {{0.114, 0.587, 0.299, 0}};
            ParameterBlock pb = new ParameterBlock();
            pb.addSource(input);
            pb.add(matrix);
            input = JAI.create("bandcombine", pb, null);
        }
        // Should we binarize it? I don't know how to check whether an image has only
        // two levels, so let's do it anyway.
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(input);
        pb.add(null); // The ROI
        pb.add(1);
        pb.add(1); // sampling
        pb.add(new int[]{256}); // bins stuff
        pb.add(new double[]{0});
        pb.add(new double[]{256});
        // Calculate the histogram of the image and its Fuzziness Threshold.
        PlanarImage dummyImage = JAI.create("histogram", pb);
        Histogram h = (Histogram) dummyImage.getProperty("histogram");
        double[] thresholds = h.getMinFuzzinessThreshold();
        // Use this threshold to binarize the image.
        pb = new ParameterBlock();
        pb.addSource(input);
        pb.add(thresholds[0]);
        // Creates the thresholded image.
        input = JAI.create("binarize", pb);
        // Let's get rid of some annoying noise and small regions. We will do an closing
        // then an opening on the image.
        // The kernels for the operations.
        float[] kernelMatrix = {0, 0, 0, 0, 0,
                0, 1, 1, 1, 0,
                0, 1, 1, 1, 0,
                0, 1, 1, 1, 0,
                0, 0, 0, 0, 0};
        // Create the kernel using the array.
        KernelJAI kernel = new KernelJAI(5, 5, kernelMatrix);
        // Create a ParameterBlock with that kernel and image.
        ParameterBlock p = new ParameterBlock();
        p.addSource(input);
        p.add(kernel);
        // Dilate the image.
        input = JAI.create("dilate", p, null);
        // Now erode the image with the same kernel.
        p = new ParameterBlock();
        p.addSource(input);
        p.add(kernel);
        input = JAI.create("erode", p, null);
        // Do the opening, which is a erode+dilate.
        p = new ParameterBlock();
        p.addSource(input);
        p.add(kernel);
        input = JAI.create("erode", p, null);
        p = new ParameterBlock();
        p.addSource(input);
        p.add(kernel);
        input = JAI.create("dilate", p, null);
        // Return the pre-processed image.
        return input;
    }

    /**
     * This method performs the bulk of the processing. It runs a classic stack-based
     * region growing algorithm:
     * 1 - Find a pixel which is not labeled. Label it and store its coordinates on a
     * stack.
     * 2 - While there are pixels on the stack, do:
     * 3 - Get a pixel from the stack (the pixel being considered).
     * 4 - Check its neighboors to see if they are unlabeled and close to the
     * considered pixel; if are, label them and store them on the stack.
     * 5 - Repeat from 1) until there are no more pixels on the image.
     */
    public void run () {
        numberOfRegions = 0;
        Stack<Point> mustDo = new Stack<Point>();
        for (int h = 0; h < height; h++)
            for (int w = 0; w < width; w++) {
                position++;
                // Is this pixel unlabeled?
                if (labels[w][h] < 0) {
                    numberOfRegions++;
                    mustDo.add(new Point(w, h));
                    labels[w][h] = numberOfRegions; // label it as one on a new region
                    count.put(numberOfRegions, 1);
                }
                // Check all the pixels on the stack. There may be more than one!
                while (mustDo.size() > 0) {
                    Point thisPoint = mustDo.get(0);
                    mustDo.remove(0);
                    // Check 8-neighborhood
                    for (int th = -1; th <= 1; th++)
                        for (int tw = -1; tw <= 1; tw++) {
                            int rx = thisPoint.x + tw;
                            int ry = thisPoint.y + th;
                            // Skip pixels outside of the image.
                            if ((rx < 0) || (ry < 0) || (ry >= height) || (rx >= width)) continue;
                            if (labels[rx][ry] < 0)
                                if (pixels[rx][ry] == pixels[thisPoint.x][thisPoint.y]) {
                                    mustDo.add(new Point(rx, ry));
                                    labels[rx][ry] = numberOfRegions;
                                    count.put(numberOfRegions, count.get(numberOfRegions) + 1);
                                }
                        } // ended neighbors checking
                } // ended stack scan
            } // ended image scan
        position = width * height;
    }

    /**
     * This method returns the number of regions on the segmetation task. This
     * number may be partial if the task has not finished yet.
     */
    public int getNumberOfRegions () {
        return numberOfRegions;
    }

    /**
     * This method returns the pixel count for a particular region or -1 if the
     * region index is outside of the range.
     */
    public int getPixelCount (int region) {
        Integer c = count.get(region);
        if (c == null) return -1;
        else return c;
    }

    /**
     * This method returns the estimated size (steps) for this task. We estimate it as
     * being the size of the image.
     */
    public long getSize () {
        return width * height;
    }

    /**
     * This method returns the position on the image processing task.
     */
    public long getPosition () {
        return position;
    }

    /**
     * This method returns true if the image processing task has finished.
     */
    public boolean isFinished () {
        return (position == width * height);
    }

    /**
     * This method returns the output image. It may be sort of corrupted if the image
     * processing task is still running.
     */
    public PlanarImage getOutput () {
        // Create a new image based on the labels array.
        int[] imageDataSingleArray = new int[width * height];
        int count = 0;
        for (int h = 0; h < height; h++)
            for (int w = 0; w < width; w++)
                imageDataSingleArray[count++] = labels[w][h];
        // Create a Data Buffer from the values on the single image array.
        DataBufferInt dbuffer = new DataBufferInt(imageDataSingleArray,
                width * height);
        // Create a byte data sample model.
        SampleModel sampleModel =
                RasterFactory.createBandedSampleModel(DataBuffer.TYPE_INT, width, height, 1);
        // Create a compatible ColorModel.
        ColorModel colorModel = PlanarImage.createColorModel(sampleModel);
        // Create a WritableRaster.
        Raster raster = RasterFactory.createWritableRaster(sampleModel, dbuffer, new Point(0, 0));
        // Create a TiledImage using the SampleModel and ColorModel.
        TiledImage tiledImage = new TiledImage(0, 0, width, height, 0, 0, sampleModel, colorModel);
        // Set the data of the tiled image to be the raster.
        tiledImage.setData(raster);
        return tiledImage;
    }

    /**
     * This method returns the (local) input image, i.e. the pre-processed image.
     *
     * @return
     */
    public PlanarImage getInternalImage () {
        return input;
    }

}
