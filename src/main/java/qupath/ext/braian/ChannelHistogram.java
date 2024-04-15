// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import ij.process.ImageStatistics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class ChannelHistogram {
    private final List<Long> values;

    public ChannelHistogram(ImageStatistics stats) throws IOException {
        long[] hist;
        if(stats.histogram16 != null)
            hist = Arrays.stream(stats.histogram16).asLongStream().toArray();
        else
            hist = stats.getHistogram();
        this.values = Arrays.stream(hist).boxed().toList();
    }

    public int[] findHistogramPeaks() {
        return findHistogramPeaks(14, 0); // 0.01
    }

    public int[] findHistogramPeaks(int windowSize, double prominence) {
        List<Double> b = new ArrayList<>(Collections.nCopies(windowSize, (double) 1 / windowSize));
        double[] smoothed = zeroPhaseFilter(b, this.values).stream()
                .map(Math::round).mapToDouble(d->d).toArray();
        // long[] smoothed = zeroPhaseFilter(b, this.values).stream()
        //        .map(Math::round).mapToLong(l->l).toArray();
        double histogramMax = Arrays.stream(smoothed).max().getAsDouble();
        return findPeaks(smoothed, prominence * histogramMax);
    }

    public static List<Double> zeroPhaseFilter(List<? extends Number> b, List<? extends Number> xs) {
        // forward filtering
        List<Double> forwardFilteredData = filter(b, xs);
        // backward filtering on reversed data
        Collections.reverse(forwardFilteredData);
        List<Double> backwardFilteredData = filter(b, forwardFilteredData);
        // reverse the data back to original order
        Collections.reverse(backwardFilteredData);
        return backwardFilteredData;
    }

    private static List<Double> filter(List<? extends Number> filter, List<? extends Number> inputData) {
        int filterSize = filter.size();
        int inputSize = inputData.size();

        List<Double> output = new ArrayList<>(inputSize);

        for (int i = 0; i < inputSize; i++) {
            double sum = 0.0;
            for (int j = 0; j < filterSize; j++) {
                int index = i - j;
                double inputValue = index >= 0 ? inputData.get(index).doubleValue() : 0.0;
                sum += filter.get(j).doubleValue() * inputValue;
            }
            output.add(sum);
        }
        return output;
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
