// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import java.util.Optional;

public class DetectionsCheckConfig {
    private boolean apply = false;
    private String controlChannel = null;

    public boolean getApply() {
        return this.apply;
    }

    public String getControlChannel() {
        return controlChannel;
    }

    public void setApply(boolean apply) {
        this.apply = apply;
    }

    public void setControlChannel(String controlChannel) {
        this.controlChannel = controlChannel;
    }
}
