<!--
SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>

SPDX-License-Identifier: CC0-1.0
-->

# QuPath BraiAn extension

Extends QuPath's functionalities for whole-brain data extraction. It works best if coupled with:
* [`qupath-extension-abba`](https://github.com/biop/qupath-extension-abba): for importing importing brain atlas annotations from [ABBA](https://biop.github.io/ijp-imagetoatlas/)
* [`braian`](https://codeberg.org/SilvaLab/BraiAn): a python library for whole-brain analysis

YSK: BraiAn's names stands for _**Brai**n **An**alysis_.

We suggest you to listen to "[Brianstorm](https://en.wikipedia.org/wiki/Brianstorm)" by Arctic Monkey while working with BraiAn ;)


## Features
TODO

## Citing

Soon.

Keep an eye on this or [ABBA](https://github.com/biop/qupath-extension-abba)'s repositories! _ETA_: June 2024

## Building

You can build the QuPath BraiAn extension from source with:

```bash
./gradlew clean build
```

The built `.jar` extension file will be under `build/libs`. 
