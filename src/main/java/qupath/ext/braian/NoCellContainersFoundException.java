// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

/**
 * Exception thrown when no detection containers can be found in the hierarchy.
 */
public class NoCellContainersFoundException extends Exception {
    /**
     * Creates a new exception for the requested detections implementation.
     *
     * @param clazz detections class that was expected to have containers
     */
    public NoCellContainersFoundException(Class<? extends AbstractDetections> clazz) {
        super("No '"+clazz.getSimpleName()+" was pre-computed in the given image.");
    }
}
