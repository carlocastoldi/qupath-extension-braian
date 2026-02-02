// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

/**
 * Configuration for enforcing detections overlap checks.
 * <p>
 * This is typically used to compute and validate double-positive detections
 * relative to a control channel.
 */
public class DetectionsCheckConfig {
    private boolean apply = false;
    private String controlChannel = null;

    /**
     * @return true if overlap checks must be applied
     */
    public boolean getApply() {
        return this.apply;
    }

    /**
     * @return the control channel name used as reference for overlap checks
     */
    public String getControlChannel() {
        return controlChannel;
    }

    /**
     * @param apply true to enable overlap checks
     */
    public void setApply(boolean apply) {
        this.apply = apply;
    }

    /**
     * @param controlChannel the control channel name used as reference for overlap
     *                       checks
     */
    public void setControlChannel(String controlChannel) {
        this.controlChannel = controlChannel;
    }
}
