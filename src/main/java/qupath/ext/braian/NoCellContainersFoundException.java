// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

/**
 * Exception thrown when no detection container annotations can be found for a given detections implementation.
 *
 * @see AbstractDetections
 */
public class NoCellContainersFoundException extends Exception {
    /**
     * Creates an exception for a missing detections container.
     *
     * @param clazz the detections class that could not be found in the hierarchy
     */
    public NoCellContainersFoundException(Class<? extends AbstractDetections> clazz) {
        super("No '"+clazz.getSimpleName()+" was pre-computed in the given image.");
    }
}
