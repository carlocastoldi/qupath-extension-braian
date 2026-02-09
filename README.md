<!--
SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>

SPDX-License-Identifier: CC0-1.0
-->
# BraiAnDetect - BraiAn's QuPath Extension
[![Javadoc](https://img.shields.io/badge/JavaDoc-Online-green)](https://carlocastoldi.github.io/qupath-extension-braian/docs/)
[![coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/carlocastoldi/qupath-extension-braian/badges/.github/badges/jacoco.json)](https://carlocastoldi.github.io/qupath-extension-braian/coverage/)
[![branches coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/carlocastoldi/qupath-extension-braian/badges/.github/badges/branches.json)](https://carlocastoldi.github.io/qupath-extension-braian/coverage/)

BraiAnDetect extends QuPath's functionalities for image analysis of serial brain sections across many animals. It is designed for multichannel cell segmentation across large and variable datasets and ensures consistency in image analysis across large datasets. This module leverages QuPath's built-in algorithms to provide a multi-channel, whole-brain optimised object detection pipeline. BraiAnDetect features options for refining signal quantification, including machine learning-based object classification, region specific cell segmentation, multiple marker co-expression algorithms and an interface for selective exclusion of damaged tissue portions.
It works best if coupled with:
* [`qupath-extension-abba`](https://github.com/biop/qupath-extension-abba): for importing brain atlas annotations from [ABBA](https://go.epfl.ch/abba)
* [`BraiAnalyze`](https://silvalab.codeberg.page/BraiAn/): the associated python library for whole-brain analysis and visualization

YSK: BraiAn's names stands for _**Brai**n **An**alysis_.

I suggest you to listen to "[Brianstorm](https://en.wikipedia.org/wiki/Brianstorm)" by Arctic Monkey while working with BraiAn ;)


## Features

This extension helps you manage image analysis across multiple QuPath projects ensuring consistency. In particular, it is designed to perform batch analysis across many QuPath projects in an automated manner. Typically, in whole-brain datasets, one brain = one QuPath project and BraiAnDetect makes sure the exact same analysis parameters are consistently applied across different projects.
It was first developed to work with [ABBA](https://go.epfl.ch/abba), but can be used for other purposes as well.
Its core idea is to move the input image analysis parameters used to analyse multiple QuPath projects of the same cohort/experiment _outside_ of scripts' code (in a [YAML](https://en.wikipedia.org/wiki/YAML) configuration file, see below). This allows having a reproducible configuration that can be shared, backed up and ran after long periods of time.

The extensions exposes a proper library [API](https://carlocastoldi.github.io/qupath-extension-braian/docs/). Here are some examples. It allows you to:

- multi-channel automatic object segmentation (e.g. [cell detections](https://carlocastoldi.github.io/qupath-extension-braian/docs/qupath/ext/braian/AbstractDetections.html))
- machine-learning-based [object classification](https://carlocastoldi.github.io/qupath-extension-braian/docs/qupath/ext/braian/PartialClassifier.html) (e.g. apply custom classifiers on different detection types).
- co-localization analysis (i.e. _quickly_ find all detections that are double—or triple/multiple—positive, through [`BoundingBoxHierarchy`](https://carlocastoldi.github.io/qupath-extension-braian/docs/qupath/ext/braian/BoundingBoxHierarchy.html))
- fine tune image analysis using [channel histograms](https://carlocastoldi.github.io/qupath-extension-braian/docs/qupath/ext/braian/ChannelHistogram.html)
- tag certain brain regions to be excluded from further analysis due to tissue, imaging or alignment problems
- export to file the quantification results (number of detections/double+ found in each brain region)
- export to file a list of regions flagged to be excluded

Where to start from, though? Reading [this script](https://github.com/carlocastoldi/qupath-extension-braian/blob/master/src/main/resources/scripts/compute_classify_overlap_export_exclude_detections.groovy) and the associated [config file](https://github.com/carlocastoldi/qupath-extension-braian/blob/master/BraiAn.yml) is a good start!

## GUI Usage

BraiAnDetect now includes a full **Pipeline Manager** GUI, accessible via `Extensions > BraiAn` in QuPath. This allows you to configure and run the entire BraiAn pipeline without writing any scripts.

### Project Preparation Tab
- **ABBA Import**: Import brain atlas annotations from ABBA with scope selection (single image, current project, or entire experiment batch).
- **Auto-Exclude Empty Regions**: Automatically flag brain regions with low signal intensity for exclusion based on channel modes and a configurable threshold multiplier.
- **Review Exclusions**: Interactive table to navigate and review excluded regions with percentile statistics.
- **Classifier Training**: Generate sample images for training object classifiers.

### Cell Detection Tab
- **Per-Channel Configuration**: Add multiple detection channels, each with its own parameters.
- **Watershed Detection**: All QuPath cell detection parameters are exposed with inline default value indicators (e.g., `[default: 0.5]`).
- **Auto-Threshold**: Histogram-based threshold calculation with a "Find Threshold" preview button.
- **Object Classifiers**: Apply machine-learning classifiers globally or to specific brain regions.
- **Pixel Classifiers**: Run pixel classifiers with region-specific measurement output.
- **Co-localization**: Configure cross-channel overlap detection.

### Batch Mode
Enable batch mode to discover and process multiple QuPath projects from a root folder simultaneously.



## Citing

If you use BraiAn in your work, please cite the paper below, currently in pre-print:

> [!IMPORTANT]
> Chiaruttini, N., Castoldi, C. et al. **ABBA, a novel tool for whole-brain mapping, reveals brain-wide differences in immediate early genes induction following learning**. _bioRxiv_ (2024).
> [https://doi.org/10.1101/2024.09.06.611625](https://doi.org/10.1101/2024.09.06.611625)

## Installation
### Using QuPath _Catalogs_
This is only possible in newer QuPath versions (0.6.0, or newer):

+ Open QuPath extension manager by clicking on `Extensions > Manage extensions`;
+ Click on `Manage extension catalogs`;
+ Paste the following URL and click the `Add` button: `https://github.com/carlocastoldi/qupath-extension-braian-catalog`;
+ In the extension manager, click on the `+` symbol next to BraiAn extension.

This new installation method for third-party extensions was introduced in QuPath to help users keep them up to date.

### Manual
You can download the [latest](https://github.com/carlocastoldi/qupath-extension-braian/releases/latest) release from the the official GitHub page of the project named `qupath-extension-braian-<VERSION>.jar`. Generally, there is no need to download `-javadoc.jar` and `-sources.jar`.\
Later you can drag the downloaded file onto QuPath, restart and be good to go!

Up until QuPath 0.5.1 (included), new extension releases are notified by QuPath on startups. You'll then be able to update them through QuPath's extension manager with one click.\
From QuPath 0.6.+, extensions installed manually no longer receive updates.

## Contributing

We decided to publish the BraiAn pipeline (i.e. this BraiAnDetect extension and the twin [BraiAnalyze python library](https://codeberg.org/SilvaLab/BraiAn)) with the most libre licence possible because we find maximum value in learning from others and sharing our own—small—knowledge.

We, developers in neuroscience, are islands that often work alone and frequently end up reinventing the wheel rather then spending time finding, pickup up and adapting the work that somebody, from the other side of the world, did with no intention to publish. For this reason we spent a great amount of our personal time making the modules as usable, extensible, and long-lasting as we could. And yet, we know it could be better, that there could be bugs, unforeseen scenarios and missing features.

For this reason we hope that, _if you find our work useful_, you will find time and will to contribute back upstream with issues, PRs, documentation, tests, feature requests... _Any_ activity makes us happy!

## Building

You can build the BraiAnDetect extension from source with:

```bash
./gradlew clean build
```

The built `.jar` extension file will be under `build/libs`.
