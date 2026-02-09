// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.util.*;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static qupath.ext.braian.BraiAnExtension.logger;

public class ChannelHistogram {
    private static int retrieveBitDepth(ImageStatistics stats) {
        if(stats.histogram16 != null)
            return 16;
        else
            return 8;
    }

    private static long[] getLongHistogram(ImageStatistics stats) {
        if(stats.histogram16 != null)
            return Arrays.stream(stats.histogram16).asLongStream().toArray();
        else
            return stats.getHistogram();
    }

    private final String channelName;
    private final int bitDepth;
    private final long[] values;

    private ChannelHistogram(String channelName, int bitDepth, long[] histogram) {
        this.channelName = channelName;
        this.bitDepth = bitDepth;
        if(bitDepth == 16)
            this.values = new long[65536];
        else
            this.values = new long[256];
        // this way, if histogram is shorter than bitDepth, it fills the rest of the values with zeros.
        // see https://forum.image.sc/t/braian-qupath-scripting-error/108774
        System.arraycopy(histogram, 0, this.values, 0, histogram.length);
    }

    /**
     * Constructs the channel histogram from an ImageJ image.
     * @param channelName the name of the QuPath channel associated to this histogram
     * @param image the processor used by ImageJ to represent a given image channel
     */
    public ChannelHistogram(String channelName, ImageProcessor image) {
        this(channelName, image.getBitDepth(), getLongHistogram(image.getStats()));
    }

    /**
     * Constructs the channel histogram from the {@link ImageStatistics} object
     * @param channelName the name of the QuPath channel associated to this histogram
     * @param stats the statistics representing a given image channel
     * @see ChannelHistogram(String, ImageProcessor)
     */
    @Deprecated(since = "1.0.4")
    public ChannelHistogram(String channelName, ImageStatistics stats) {
        this(channelName, retrieveBitDepth(stats), getLongHistogram(stats));
    }

    /**
     * @return the bit depth of the image on which the histogram was computed
     */
    public int getBitDepth() {
        return this.bitDepth;
    }

    /**
     * @return the name of the channel from which this histogram was built
     */
    public String getChannelName() {
        return this.channelName;
    }

    /**
     * @return true if the current histogram is built from a 8-bit image
     */
    public boolean is8bit() {
        return this.bitDepth == 8;
    }

    /**
     * @return true if the current histogram is built from a 16-bit image
     */
    public boolean is16bit() {
        return this.bitDepth == 16;
    }

    public int getMaxValue() {
        if (this.is8bit() || this.is16bit())
            return this.values.length;
        throw new RuntimeException("Unknown maximum value for this histogram");
    }

    /**
     * Computes the Otsu threshold for the current histogram.
     *
     * @return threshold index (in the same intensity scale as this histogram)
     */
    public int otsuThreshold() {
        return otsuThreshold(this.values);
    }

    static int otsuThreshold(long[] histogram) {
        if (histogram == null || histogram.length == 0)
            return 0;

        long total = 0;
        double sum = 0;
        for (int i = 0; i < histogram.length; i++) {
            long h = histogram[i];
            if (h <= 0)
                continue;
            total += h;
            sum += (double) i * (double) h;
        }
        if (total == 0)
            return 0;

        long wB = 0;
        double sumB = 0;
        double maxBetween = -1.0;
        long thresholdSum = 0;
        int thresholdCount = 0;

        for (int i = 0; i < histogram.length; i++) {
            long h = histogram[i];
            if (h > 0) {
                wB += h;
                sumB += (double) i * (double) h;
            }

            long wF = total - wB;
            if (wB == 0 || wF == 0)
                continue;

            double mB = sumB / (double) wB;
            double mF = (sum - sumB) / (double) wF;
            double between = (double) wB * (double) wF * (mB - mF) * (mB - mF);

            if (between > maxBetween) {
                maxBetween = between;
                thresholdSum = i;
                thresholdCount = 1;
            } else if (between == maxBetween) {
                thresholdSum += i;
                thresholdCount++;
            }
        }

        if (thresholdCount <= 0)
            return 0;

        return (int) Math.round((double) thresholdSum / (double) thresholdCount);
    }

    /**
     * Smooths the ChannelHistogram and find the color values that appear the most.
     * <p>
     * It applies {@link #findHistogramPeaks(int, double)} with <code>windowSize=15</code>
     * and <code>prominence=100</code>
     * @return an array of the color values
     */
    public int[] findHistogramPeaks() {
        return findHistogramPeaks(15, 100);
    }

    /**
     * Smooths the ChannelHistogram and find the color values that appear the most
     * @param windowSize the size of the kernel used for smoothing the histogram
     * @param prominence the threshold used to define whether a local maximum is a peak or not
     * @return an array of the color values
     * @see #findPeaks(double[], double)
     * @see #zeroPhaseFilter(double[], double[])
     */
    public int[] findHistogramPeaks(int windowSize, double prominence) {
        if (windowSize%2 == 0)
            logger.warn("For better results, choose a window of odd size!");
        // movingAvg is a moving average linear digital filter
        double[] movingAvg = new double[windowSize];
        Arrays.fill(movingAvg, (double) 1/windowSize);
        double[] hist = Arrays.stream(this.values).asDoubleStream().toArray();
        double[] smoothed = zeroPhaseFilter(movingAvg, hist);
        return findPeaks(smoothed, prominence);
        // double histogramMax = Arrays.stream(smoothed).max().getAsDouble();
        // return findPeaks(smoothed, prominence * histogramMax);
    }

    /**
     * Applies Applies a linear digital filter twice, once forward and once backwards.
     * The combined filter has zero phase and a filter order twice that of the original.
     * It handles the signal's edges by padding data with zeros.
     * @param f the filter
     * @param xs the data to be filtered
     * @return the filtered output with the same shape as x.
     */
    public static double[] zeroPhaseFilter(double[] f, double[] xs) {
        // forward filtering
        double[] forwardFilteredData = convolute(f, xs);
        // backward filtering on reversed data
        reverse(forwardFilteredData);
        double[] backwardFilteredData = convolute(f, forwardFilteredData);
        // reverse the data back to original order
        reverse(backwardFilteredData);
        return backwardFilteredData;
    }

    /**
     * Convolutes a kernel to a signal. It handles the signal's edges by padding signal with zeros.
     *
     * @param kernel:    kernel to apply
     * @param signal: signal on which the kernel is applied
     * @return the filtered signal as a double array
     */
    private static double[] convolute(double[] kernel, double[] signal) {
        int padSize = Math.floorDiv(kernel.length, 2);

        double[] paddedInputData = DoubleStream.concat(
                DoubleStream.generate(() -> 0).limit(padSize),          // DoubleStream.range(0,padSize).mapToObj(i -> signal[padSize-i]),
                DoubleStream.concat(
                        Arrays.stream(signal),
                        DoubleStream.generate(() -> 0).limit(padSize))  // DoubleStream.range(0,padSize).mapToObj(i -> signal[inputSize-padSize-i]))
        ).toArray();

        return IntStream.range(kernel.length-1, paddedInputData.length)
                .mapToDouble(i -> IntStream.range(0, kernel.length)
                        .mapToDouble(j -> paddedInputData[i-j] * kernel[j])
                        .sum())
                .toArray();
    }

    static void reverse(double[] a) {
        double temp;
        for (int i = 0; i < a.length / 2; i++) {
            temp = a[i];
            a[i] = a[a.length - i - 1];
            a[a.length - i - 1] = temp;
        }
    }

    /**
     * Finds the local maxima that peak above the nearby data
     * @param x the data
     * @param prominence the threshold above which a local maximum is considered a peak
     * @return the positions of the peaks inside x
     */
    public static int[] findPeaks(double[] x, double prominence) {
        int[] peaks = localMaxima(x);
        return Arrays.stream(peaks)
                .filter(peak -> peakProminence(x, peak) >= prominence)
                .toArray();
    }

    private static int[] localMaxima(double[] x) {
        List<Integer> midpoints = new ArrayList<>();
        int i = 1;                      // Pointer to current sample, first one can't be maxima
        int iMax = x.length - 1;        // Last sample can't be maxima
        while (i < iMax) {
            // Test if previous sample is smaller
            if (x[i - 1] < x[i]) {
                int iAhead = i + 1;     // Index to look ahead of current sample

                // Find next sample that is unequal to x[i]
                while (iAhead < iMax && x[iAhead] == x[i])
                    iAhead += 1;

                // Maxima is found if next unequal sample is smaller than x[i]
                if (x[iAhead] < x[i]) {
                    midpoints.add((i + iAhead - 1) / 2); // intdiv
                    // Skip samples that can 't be maximum
                    i = iAhead;
                }
            }
            i += 1;
        }
        return midpoints.stream().mapToInt(n->n).toArray();
    }

    private static double peakProminence(double[] x, int peak) {
        // Find the left base in interval [iMin, peak]
        double leftMin = IntStream.range(0, peak)
                .mapToObj(i -> x[peak-i]) // reverse stream
                .takeWhile(elem -> elem <= x[peak])
                .min(Double::compare)
                .get();
        double rightMin = Arrays.stream(x, peak, x.length)
                .takeWhile(elem -> elem <= x[peak])
                .min()
                .getAsDouble();

        return x[peak] - Math.max(leftMin, rightMin);
    }
}
