<!--
SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>

SPDX-License-Identifier: CC0-1.0
-->
# QuPath BraiAn extension
[![Javadoc](https://img.shields.io/badge/JavaDoc-Online-green)](https://carlocastoldi.github.io/qupath-extension-braian/docs/)
[![coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/carlocastoldi/qupath-extension-braian/badges/.github/badges/jacoco.json)](https://carlocastoldi.github.io/qupath-extension-braian/coverage/)
[![branches coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/carlocastoldi/qupath-extension-braian/badges/.github/badges/branches.json)](https://carlocastoldi.github.io/qupath-extension-braian/coverage/)

Extends QuPath's functionalities for whole-brain data extraction. It works best if coupled with:
* [`qupath-extension-abba`](https://github.com/biop/qupath-extension-abba): for importing importing brain atlas annotations from [ABBA](https://go.epfl.ch/abba)
* [`braian`](https://codeberg.org/SilvaLab/BraiAn): the associated python library for whole-brain analysis and visualization

YSK: BraiAn's names stands for _**Brai**n **An**alysis_.

I suggest you to listen to "[Brianstorm](https://en.wikipedia.org/wiki/Brianstorm)" by Arctic Monkey while working with BraiAn ;)


## Features

This extension helps you managing multiple QuPath projects with the same parameters.
It was first developed to work with [ABBA](https://go.epfl.ch/abba), but can be used for other purposes as well.
Its core idea is to move _outside_ of scripts' code the input parameters used to analyse multiple QP projects of the same cohort/experiment.
This allows to have a reproducible configuration that can be shared, backed up and ran after long periods of time.

The extensions exposes a proper library [API](https://carlocastoldi.github.io/qupath-extension-braian/docs/) and, among all, it allows to:
- work with image channel histograms, thanks to `ChannelHistogram.class`
- compute and manage detections separately for each image channel, thanks to `AbstractDetections.class`
- apply different classifiers on different subsets of detections, thanks to `PartialClassifier.class`
- _quickly_ find all detections that are double—or triple/multiple—positive, thanks to `BoundingBoxHierarchy.class`
- tag certain brain regions to be excluded from further analysis due to tissue, imaging or alignment problems
- export to file the number of detections/double+ found in each brain region
- export to file a list of regions flagged to be excluded

Where to start from, though? Reading [this script](https://github.com/carlocastoldi/qupath-extension-braian/blob/master/src/main/resources/scripts/compute_classify_overlap_export_exclude_detections.groovy) and the associated [config file](https://github.com/carlocastoldi/qupath-extension-braian/blob/master/BraiAn.yml) is a good start!

## Citing

**Soon.**

Keep an eye on this or [ABBA](https://github.com/biop/qupath-extension-abba)'s repositories!\
_ETA_: June 2024

## Contributing

I decided to publish the BraiAn pipeline (i.e. this QuPath extension and the subsequent [python libray](https://codeberg.org/SilvaLab/BraiAn)) with the most libre licence possible because I find maximum value in learning from others
and sharing my own—small—knowledge.

We, developers in neuroscience, are islands that often work alone and frequently end up reinventing the wheel rather 
then spending time finding, pickup up and adapting the work that somebody on the other side of the world did with no
intention to publish. For this reason I spent a great amount of my personal time making this extension as
usable, extensible, and long-lasting as I could. And yet, I know it could be better, that there could be bugs,
unforeseen scenarios and missing features.

For this reason I hope that if you find my work useful, you will find time and will to contribute back upstream with
issues, PRs, documentation, tests, feature requests... _Any_ activity makes me happy!

## Building

You can build the QuPath BraiAn extension from source with:

```bash
./gradlew clean build
```

The built `.jar` extension file will be under `build/libs`.
