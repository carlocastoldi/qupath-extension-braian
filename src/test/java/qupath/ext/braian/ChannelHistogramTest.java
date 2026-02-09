// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ChannelHistogramTest {

    @Test
    void otsuThreshold_shouldFallBetweenBimodalPeaks_8bit() {
        long[] hist = new long[256];
        hist[20] = 10_000;
        hist[200] = 10_000;

        int t = ChannelHistogram.otsuThreshold(hist);
        assertTrue(t > 20 && t < 200, "Expected threshold between peaks, got " + t);
    }

    @Test
    void otsuThreshold_shouldFallBetweenBimodalPeaks_16bit() {
        long[] hist = new long[65536];
        hist[1_000] = 10_000;
        hist[60_000] = 10_000;

        int t = ChannelHistogram.otsuThreshold(hist);
        assertTrue(t > 1_000 && t < 60_000, "Expected threshold between peaks, got " + t);
    }

    @Test
    void otsuThreshold_allZeroHistogram_returnsZero() {
        long[] hist = new long[256];
        assertEquals(0, ChannelHistogram.otsuThreshold(hist));
    }

    @Test
        // test behavior for signal without local maxima
    void constant() {
        // PREPARE
        double[] xs = Arrays.stream(new double[10]).map(x -> 1).toArray();
        // EXECUTE
        int[] peaks = ChannelHistogram.findPeaks(xs, 0); // prominence=0 -> no prominence check
        // CHECK
        assertEquals(0, peaks.length);
    }

    @Test
        // test plateau size condition for peaks
    void plateauSize() {
        double[] plateauSizes = {1, 2, 3, 4, 8, 20, 111};
        double[] xs = DoubleStream.concat(
                DoubleStream.of(0),
                Arrays.stream(plateauSizes)
                        .flatMap(size -> DoubleStream.concat(
                                DoubleStream.generate(() -> size).limit((int) size),
                                DoubleStream.of(0)
                        ))
        ).toArray();

        int[] peaks = ChannelHistogram.findPeaks(xs, 0);
        assertArrayEquals(new int[]{1, 3, 7, 11, 18, 33, 100}, peaks);
    }

    DoubleStream linspace(int start, int stop, int num) {
        return LongStream.range(0, num)
                .mapToDouble(i -> start+((double) (stop-start)/(num-1)*i));
    }

    IntStream range(int start, int stop, int step) {
        return IntStream.range(0, (stop-start)/step).map(i -> start+(i*step));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
        // test prominence condition for peaks
    void prominenceCondition(double prominence) {
        double[] xs = linspace(0, 10, 100).toArray();
        int[] peaksAll = range(1, 99, 2).toArray();
        PrimitiveIterator.OfDouble offset = linspace(1, 10, peaksAll.length).iterator();
        Arrays.stream(peaksAll).forEach(peak -> xs[peak] += offset.hasNext() ? offset.next() : null);
        int[] peaksTrue = Arrays.stream(peaksAll).filter(peak -> (xs[peak] - xs[peak+1]) >= prominence).toArray();
        // Function<Double, IntStream> getTruePeaks = (prominence) -> {
        //     return Arrays.stream(peaksAll).filter(peak -> (xs[peak] - xs[peak+1]) >= prominence);
        // };
        assertArrayEquals(peaksTrue, ChannelHistogram.findPeaks(xs, prominence));
        double[] xsRev = IntStream.range(0, xs.length).mapToDouble(i -> xs[xs.length-1-i]).toArray();
        int[] peaksTrueRev = IntStream.range(0, peaksTrue.length).map(i -> {
            int peak = peaksTrue[peaksTrue.length-1-i];
            return xs.length-1-peak;
        }).toArray();
        assertArrayEquals(peaksTrueRev, ChannelHistogram.findPeaks(xsRev, prominence));
    }

    @Test
    void zeroPhaseFilterBasic() {
        double[] inputSignal = IntStream.range(0, 12).mapToDouble(d -> d).toArray();
        double[] output;

        output = ChannelHistogram.zeroPhaseFilter(new double[]{0., 0., 1., 0., 0.}, inputSignal);
        assertArrayEquals(inputSignal, output);

        output = ChannelHistogram.zeroPhaseFilter(new double[]{0., 1., 0.}, inputSignal);
        assertArrayEquals(inputSignal, output);

        output = ChannelHistogram.zeroPhaseFilter(new double[]{1.}, inputSignal);
        assertArrayEquals(inputSignal, output);
    }

    @Test
    void zeroPhaseFilter_movingAverage() {
        int numSamples = 10000;
        int cleanSignalFrequency = 2;
        int samplingRate = 2000;
        int noiseLevel = 2;
        int windowSize = 15;

        double[] cleanSignal = IntStream.range(0, numSamples).
                mapToDouble(i -> {
                    double t = (double) i/samplingRate;
                    return Math.sin(2 * Math.PI * cleanSignalFrequency * t);
                })
                .toArray();
        double[] noisySignal = IntStream.range(0,numSamples)
                .mapToDouble(i -> {
                    if (i < windowSize || i > numSamples-windowSize)
                        return cleanSignal[i];
                    double spike = noiseLevel *(i % (windowSize/2) == 0 ? 1 : 0) * Math.pow(-1, i);
                    return cleanSignal[i]+spike;
                }).toArray();

        double[] movingAvg = new double[windowSize];
        Arrays.fill(movingAvg, (double) 1/windowSize);
        double[] cleanedSignal = ChannelHistogram.zeroPhaseFilter(movingAvg, noisySignal);

        assertEquals(IntStream.range(0,cleanSignal.length)
                .mapToDouble(i -> Math.abs(noisySignal[i]-cleanSignal[i]))
                .max().getAsDouble(), noiseLevel);
        assertArrayEquals(cleanSignal, cleanedSignal, 0.1);
    }

    @ParameterizedTest
    @MethodSource("readHistogram")
    void shouldGetDataBit(int[] histogram) {
        int windowSize = 15; // if it's even, it fails
        double[] movingAvg = new double[windowSize];
        Arrays.fill(movingAvg, (double) 1/windowSize);
        double[] hist = Arrays.stream(histogram).asDoubleStream().toArray();
        double[] smoothed = ChannelHistogram.zeroPhaseFilter(movingAvg, hist);

        // System.out.println(histogram);
        assertEquals(hist.length, smoothed.length);
    }

    static Stream<Arguments> readHistogram() {
        try {
            return Stream.of(
                    Arguments.of(readIntArrayFromFile("/histogram1.txt"))
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int[] readIntArrayFromFile(String fileName) throws IOException {
        List<Integer> list = new ArrayList<>();
        String line;
        InputStream inputStream = ChannelHistogramTest.class.getResourceAsStream(fileName);
        assert inputStream != null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        // BufferedReader reader = new BufferedReader(new FileReader(filePath));
        while ((line = reader.readLine()) != null)
            list.add(Integer.parseInt(line.trim()));
        return list.stream().mapToInt(i -> i).toArray();
    }

}
