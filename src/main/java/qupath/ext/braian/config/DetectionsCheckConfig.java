// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import java.util.Optional;

/**
 * Configuration for optional cross-channel detections consistency checks.
 */
public class DetectionsCheckConfig {
    private boolean apply = false;
    private String controlChannel = null;

    /**
     * @return whether overlap checks should be applied
     */
    public boolean getApply() {
        return this.apply;
    }

    /**
     * @return channel name used as control, or {@code null} if unspecified
     */
    public String getControlChannel() {
        return controlChannel;
    }

    /**
     * @param apply whether overlap checks should be applied
     */
    public void setApply(boolean apply) {
        this.apply = apply;
    }

    /**
     * @param controlChannel channel name used as control
     */
    public void setControlChannel(String controlChannel) {
        this.controlChannel = controlChannel;
    }
}
