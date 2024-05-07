// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import qupath.imagej.tools.IJTools;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

class IllegalChannelName extends RuntimeException {
    public IllegalChannelName(String name) {
        super(String.format("Cannot find a channel named '"+name+"'!"));
    }
}

public class ImageChannelTools {
    private final String name;
    private final ImageServer server;
    private final int nChannel;

    /**
     * Creates an {@link ImageChannelTools} from the given channel name and {@link ImageServer}
     * @param name name of the channel
     * @param server image server to which the channel is referring to
     */
    public <T> ImageChannelTools(String name, ImageServer<T> server) {
        this.name = name;
        this.server = server;
        this.nChannel = this.findNChannel();
    }

    private int findNChannel() {
        int nChannel = 0;
        for (ImageChannel ch: this.server.getMetadata().getChannels()) {
            if(ch.getName().equals(this.name))
                return nChannel;
            nChannel++;
        }
        throw new IllegalChannelName(this.name);
    }

    /**
     * @return index of the corresponding channel used by QuPath
     */
    public int getnChannel() {
        return this.nChannel;
    }

    /**
     * Computes the {@link ChannelHistogram} of the current channel at the given resolution.
     * @param resolutionLevel Resolution level, If it's bigger than {@link ImageServer#nResolutions()}-1,
     *                        than it uses the igven n-th resolution.
     * @return the histogram of the given channel
     * @throws IOException when it fails to read the image file to build the histogram
     * @see #getChannelStats(int)
     * @see ImageServer#getDownsampleForResolution(int)
     */
    public ChannelHistogram getHistogram(int resolutionLevel) throws IOException {
        return new ChannelHistogram(this.getChannelStats(resolutionLevel));
    }

    /**
     * Computes the {@link ImageStatistics} of the current channel at the given resolution.
     * @param resolutionLevel Resolution level, If it's bigger than {@link ImageServer#nResolutions()}-1,
     *                        than it uses the igven n-th resolution.
     * @return the statistics of the channel computed by ImageJ at the given resolution
     * @throws IOException when it fails to read the image file
     * @see #getChannelStats(int)
     * @see ImageServer#getDownsampleForResolution(int)
     */
    public ImageStatistics getChannelStats(int resolutionLevel) throws IOException {
        double downsample = this.server.getDownsampleForResolution(Math.min(this.server.nResolutions()-1, resolutionLevel));
        RegionRequest request = RegionRequest.createInstance(this.server, downsample);
        PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(this.server, request);
        CompositeImage ci = (CompositeImage) pathImage.getImage();

        int ijChannel = this.nChannel+1; // ij.CompositeImage uses 1-based channels
        ci.setC(ijChannel);
        ImageProcessor ip = ci.getChannelProcessor();
        ip = ip.duplicate();
        ip.resetRoi();
        ImageStatistics stats = ip.getStats();
        return stats;
    }

    public String getName() {
        return this.name;
    }

    public ChannelDetections getDetections(PathObjectHierarchy hierarchy) {
        return new ChannelDetections(this, hierarchy);
    }
}
