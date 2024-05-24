<!--
SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>

SPDX-License-Identifier: CC0-1.0
-->
# QuPath BraiAn extension
[![Javadoc](https://img.shields.io/badge/JavaDoc-Online-green)](https://carlocastoldi.github.io/qupath-extension-braian/docs/)
[![coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/carlocastoldi/qupath-extension-braian/badges/.github/badges/jacoco.json)](https://carlocastoldi.github.io/qupath-extension-braian/coverage/)
[![branches coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/carlocastoldi/qupath-extension-braian/badges/.github/badges/branches.json)](https://carlocastoldi.github.io/qupath-extension-braian/coverage/)

Extends QuPath's functionalities for whole-brain data extraction. It works best if coupled with:
* [`qupath-extension-abba`](https://github.com/biop/qupath-extension-abba): for importing importing brain atlas annotations from [ABBA](https://abba-documentation.readthedocs.io/en/latest/)
* [`braian`](https://codeberg.org/SilvaLab/BraiAn): a python library for whole-brain analysis

YSK: BraiAn's names stands for _**Brai**n **An**alysis_.

I suggest you to listen to "[Brianstorm](https://en.wikipedia.org/wiki/Brianstorm)" by Arctic Monkey while working with BraiAn ;)


## Features

This extension helps you managing multiple QuPath projects with the same parameters.
It was first developed to work with [ABBA](https://abba-documentation.readthedocs.io/en/latest/), but can be used for other purposes as well.
Its core idea is to move _outside_ of scripts' code the input parameters used to analyse multiple QP projects of the same cohort/experiment.
This allows to have a reproducible configuration that can be shared, backed up and ran after long.

Apart from that, the extensions is a proper [library](https://carlocastoldi.github.io/qupath-extension-braian/docs/), and among all allows to:
- work with image channel histograms
- compute and manage detections separately for each image channel
- apply different classifiers on different subsets of detections
- find _quickly_ all detections that are double (or triple/multiple) positive
- exports to file the number of detections/double+ found in each brain region
- exports to file a list of regions that have to be excluded from further analysis

Where to start from, though? Reading [this script](https://github.com/carlocastoldi/qupath-extension-braian/blob/master/src/main/resources/scripts/compute_classify_overlap_export_exclude_detections.groovy) and the associated [config file](https://github.com/carlocastoldi/qupath-extension-braian/blob/master/BraiAn.yml) is a good start!

## Citing

**Soon.**

Keep an eye on this or [ABBA](https://github.com/biop/qupath-extension-abba)'s repositories! _ETA_: June 2024

## Contributing

I decided to publish BraiAn with the most libre licence possible, because I find maximum value in learning from others and sharing my (small) knowledge.

We, developers in research, are all small islands that often work alone and find themselves reinventing the wheel. For this reason I spent a great amount of my personal time making this extension as usable, extensible, and long-lasting as I could.
However, I know that are bugs, unforeseen scenarios and missing features.

For this reason I hope that if you find m work useful, you will find time and will to contribute back upstream with issues, PRs, documentation, tests, feature requests... I am open to _anything_!

## Building

You can build the QuPath BraiAn extension from source with:

```bash
./gradlew clean build
```

The built `.jar` extension file will be under `build/libs`.
