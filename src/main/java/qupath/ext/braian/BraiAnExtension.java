// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;

public class BraiAnExtension implements QuPathExtension, GitHubProject {

    static final Logger logger = LoggerFactory.getLogger(BraiAnExtension.class);

    public static Logger getLogger() {
        return logger;
    }

    @Override
    public GitHubRepo getRepository() {
        return GitHubRepo.create("BraiAn extension", "carlocastoldi", "qupath-extension-braian");
    }

    @Override
    public void installExtension(QuPathGUI qupath) {

    }

    @Override
    public String getName() {
        return "BraiAn extension";
    }

    @Override
    public String getDescription() {
        return "A collection of tools for whole-brain data extraction";
    }
}