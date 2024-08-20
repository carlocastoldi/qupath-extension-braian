<!--
SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>

SPDX-License-Identifier: CC0-1.0
-->
# Version v1.0.3-SNAPSHOT
This is a work in progress for the next release.
## Enhancements
## Bugs fixed
## API changes
## Dependency updates


# Version v1.0.2
## Enhancements
 - `ChannelDetections` config now supports applying a `PartialClassifier` (`name: "ALL"`)  that resets any previous applied classification.
 - `BoundingBox.contains()` method added.
 - if a new detection container is added and it intersects with any previous ones, it updates the old detection with the new ones.
 - documentation update
## Bugs fixed
 - fixed a bug where `ChannelDetection` constructor would fail when no prior containers was found.
## API changes
## Dependency updates
