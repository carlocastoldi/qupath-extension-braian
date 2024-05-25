// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

public class NoCellContainersFoundException extends Exception {
    public NoCellContainersFoundException(Class<? extends AbstractDetections> clazz) {
        super("No '"+clazz.getSimpleName()+" was pre-computed in the given image.");
    }
}
