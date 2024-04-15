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
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;

class IllegalChannelName extends RuntimeException {
    public IllegalChannelName(String name) {
        super(String.format("Cannot find a channel named '{}'!", name));
    }
}

public class ImageChannelTools {
    public String name;
    private ImageServer<BufferedImage> server;
    private int nChannel;

    public ImageChannelTools(String name, ImageServer<BufferedImage> server) {
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

    private ImageStatistics getChannelStats(int downsampleLevel) throws IOException {
        double downsample = this.server.getDownsampleForResolution(Math.min(this.server.nResolutions()-1, downsampleLevel));
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

    public int getnChannel() {
        return this.nChannel;
    }

    public ChannelHistogram getHistogram(int downsample) throws IOException {
        return new ChannelHistogram(this.getChannelStats(downsample));
    }
}
