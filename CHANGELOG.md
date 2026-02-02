<!--
SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>

SPDX-License-Identifier: CC0-1.0
-->
# Version v1.1.1
This release introduces a complete GUI-based pipeline manager for BraiAnDetect,
enabling batch processing and configuration without writing scripts.

## Enhancements
### New GUI Architecture
 - **BraiAnDetect Pipeline Manager**: Full JavaFX dialog accessible via Extensions menu
 - **Project Preparation tab**: ABBA atlas import, auto-exclude empty regions, classifier training project creation
 - **Cell Detection tab**: Per-channel configuration with ChannelCard components
 - **Batch mode**: Multi-project discovery and processing from a single root folder

### Cell Detection Features
 - Watershed detection with all QuPath parameters exposed in the UI
 - Auto-threshold histogram analysis with "Find Threshold" preview button
 - Default value indicators `[default: xxx]` for all numeric parameters
 - Per-channel object classifiers with global/partial region filtering
 - Pixel classifier support with region-specific measurements
 - Cross-channel co-localization detection configuration

### Project Preparation Features
 - ABBA atlas import with scope selection (single image, project, experiment)
 - Auto-exclude empty regions with channel modes and configurable threshold multiplier
 - Exclusion review dialog with navigation and percentile reporting
 - Classifier training project creation with sample image generation

### Infrastructure
 - YAML-backed configuration with auto-save and debouncing
 - ProjectDiscoveryService for automatic QuPath project discovery
 - Robust error handling and thread-safe JavaFX operations

## Bugs fixed
 - Memory leak: projectProperty listener now properly removed on dialog close
 - Thread safety: Hierarchy selection changes wrapped in FXUtils.runOnApplicationThread
 - YAML parsing: Unknown properties are now skipped for backward compatibility
 - Null handling: Graceful defaults for missing configuration values

## API changes
 - New `gui` package: BraiAnDetectDialog, ChannelCard, ExperimentPane, ExclusionReviewDialog
 - New `config` package: ProjectsConfig, ChannelDetectionsConfig, PixelClassifierConfig, etc.
 - New `runners` package: BraiAnAnalysisRunner, PixelClassifierRunner, AutoExcludeEmptyRegionsRunner, etc.
 - New `utils` package: ProjectDiscoveryService

## Dependency updates
 - No new dependencies required (uses existing QuPath/JavaFX)




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
