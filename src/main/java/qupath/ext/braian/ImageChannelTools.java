// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import qupath.imagej.tools.IJTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;

class IllegalChannelName extends RuntimeException {
    public IllegalChannelName(String name) {
        super(String.format("Cannot find a channel named '"+name+"'!"));
    }
}

/**
 * Utility helper to access and process a named image channel.
 * <p>
 * This class bridges QuPath channel metadata and ImageJ processors, and is used to compute
 * per-channel histograms/statistics and retrieve existing detections.
 *
 * @see ChannelHistogram
 * @see ChannelDetections
 */
public class ImageChannelTools {
    private final String name;
    private final ImageData<BufferedImage> imageData;
    private final ImageServer<BufferedImage> server;
    private final int nChannel;

    /**
     * Creates an {@link ImageChannelTools} from the given channel name and {@link ImageServer}
     * @param name name of the channel
     * @param server image server to which the channel is referring to
     */
    @Deprecated(since = "1.1.1")
    public ImageChannelTools(String name, ImageServer<BufferedImage> server) {
        this.name = name;
        this.imageData = null;
        this.server = server;
        this.nChannel = this.findNChannel();
    }

    /**
     * Crates an {@link ImageChannelTools} from the given channel name and {@link ImageData}
     * @param name name of the channel
     * @param imageData data of the image to which the channel is referring to
     */
    public ImageChannelTools(String name, ImageData<BufferedImage> imageData) {
        this.name = name;
        this.imageData = imageData;
        this.server = null;
        this.nChannel = this.findNChannel();
    }

    private ImageServer<BufferedImage> getServer() {
        if (this.server != null)
            return this.server;
        return this.imageData.getServer();
    }

    private ImageServerMetadata getMetadata() {
        return this.imageData.getServerMetadata();
    }

    private int findNChannel() {
        int nChannel = 0;
        for (ImageChannel ch: this.getMetadata().getChannels()) {
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
        return new ChannelHistogram(this.name, this.getImageProcessor(resolutionLevel));
    }

    /**
     * Retrieves the corresponding {@link ImageProcessor} of the current channel at the given resolution.
     * @param resolutionLevel Resolution level, If it's bigger than {@link ImageServer#nResolutions()}-1,
     *                        than it uses the given n-th resolution.
     * @return the image channel as processed by ImageJ at the given resolution
     * @throws IOException when it fails to read the image file
     * @see #getChannelStats(int)
     * @see ImageServer#getDownsampleForResolution(int)
     */
    public ImageProcessor getImageProcessor(int resolutionLevel) throws IOException {
        ImageServer<BufferedImage> server = this.getServer();
        double downsample = server.getDownsampleForResolution(Math.min(server.nResolutions()-1, resolutionLevel));
        RegionRequest request = RegionRequest.createInstance(server, downsample);
        PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(server, request);
        ImagePlus image = pathImage.getImage();

        int ijChannel = this.nChannel+1; // ij.ImagePlus uses 1-based channels
        image.setC(ijChannel);
        ImageProcessor ip = image.getChannelProcessor();
        ip = ip.duplicate();
        ip.resetRoi();
        return ip;
    }

    /**
     * Computes the {@link ImageStatistics} of the current channel at the given resolution.
     * @param resolutionLevel Resolution level, If it's bigger than {@link ImageServer#nResolutions()}-1,
     *                        than it uses the given n-th resolution.
     * @return the statistics of the channel computed by ImageJ at the given resolution
     * @throws IOException when it fails to read the image file
     * @see #getChannelStats(int)
     * @see #getImageProcessor(int)
     * @see ImageServer#getDownsampleForResolution(int)
     */
    @Deprecated(since = "1.0.4")
    public ImageStatistics getChannelStats(int resolutionLevel) throws IOException {
        return this.getImageProcessor(resolutionLevel).getStats();
    }

    /**
     * @return the name of the associated image channel
     */
    public String getName() {
        return this.name;
    }

    /**
     * returns an instance of {@link ChannelDetections}, if existing in the current hierarchy
     * @param hierarchy where to find the detections
     * @throws NoCellContainersFoundException if no pre-computed detection was found in the given hierarchy
     * @see ChannelDetections
     */
    public ChannelDetections getDetections(PathObjectHierarchy hierarchy) throws NoCellContainersFoundException {
        return new ChannelDetections(this, hierarchy, null, null);
    }
}
