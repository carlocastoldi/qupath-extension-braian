// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

public class ChannelHistogramTest {

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

    List<Double> applyTransformation(List<Double> inputSignal, int filterSize) {
        // Pad the input signal with zeros at the beginning and end
        List<Double> paddedInputSignal = new java.util.ArrayList<>(Collections.nCopies(filterSize - 1, 0.0));
        paddedInputSignal.addAll(inputSignal);
        paddedInputSignal.addAll(Collections.nCopies(filterSize - 1, 0.0));
        return paddedInputSignal;
    }

    @Test
    void zeroPhaseFilterBasic() {
        List<Double> filter = Arrays.asList(0., 0., 1., 0.);
        List<Double> inputSignal = new java.util.ArrayList<>(IntStream.range(0, 12).mapToDouble(d -> d).boxed().toList());
        Collections.reverse(inputSignal);

        List<Double> output = ChannelHistogram.zeroPhaseFilter(filter, inputSignal);

        assertArrayEquals(inputSignal.toArray(), output.toArray());
    }

}