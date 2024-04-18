// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import ij.process.ImageStatistics;

import java.io.IOException;
import java.util.*;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class ChannelHistogram {
    private final long[] values;

    public ChannelHistogram(ImageStatistics stats) throws IOException {
        if(stats.histogram16 != null)
            this.values = Arrays.stream(stats.histogram16).asLongStream().toArray();
        else
            this.values = stats.getHistogram();
    }

    public int[] findHistogramPeaks() {
        return findHistogramPeaks(14, 0); // 0.01
    }

    public int[] findHistogramPeaks(int windowSize, double prominence) {
        // movingAvg is a moving average linear digital filter
        double[] movingAvg = new double[windowSize];
        Arrays.fill(movingAvg, (double) 1/windowSize);
        double[] hist = Arrays.stream(this.values).asDoubleStream().toArray();
        double[] smoothed = zeroPhaseFilter(movingAvg, hist);
        double histogramMax = Arrays.stream(smoothed).max().getAsDouble();
        return findPeaks(smoothed, prominence * histogramMax);
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
