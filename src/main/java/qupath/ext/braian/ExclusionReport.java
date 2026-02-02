// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Immutable payload used to populate the exclusion review dialog.
 *
 * @param projectFile          the QuPath project file (typically
 *                             {@code project.qpproj}); may be null for the
 *                             current project
 * @param projectName          the project display name; may be null
 * @param imageName            the image entry name (as shown in the QuPath
 *                             project)
 * @param excludedAnnotationId the {@link java.util.UUID} of the exclusion
 *                             annotation
 * @param regionName           the excluded region name; may be null
 * @param percentile           the percentile rank of the excluded region
 */
public record ExclusionReport(
        Path projectFile,
        String projectName,
        String imageName,
        UUID excludedAnnotationId,
        String regionName,
        double percentile) {

    /**
     * @return a compact label combining project and image names
     */
    public String imageLabel() {
        if (projectName != null && !projectName.isBlank()) {
            return projectName + ": " + imageName;
        }
        return imageName;
    }
}
