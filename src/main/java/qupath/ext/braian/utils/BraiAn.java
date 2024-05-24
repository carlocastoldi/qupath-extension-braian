// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.utils;

import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Projects;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static qupath.lib.scripting.QP.getProject;

public class BraiAn {
    public static Path resolvePath(String fileName) throws FileNotFoundException {
        Path projectPath = Projects.getBaseDirectory(getProject()).toPath();
        Path projectParentDirectoryPath = projectPath.getParent();
        Path[] resolutionOrder = {projectPath, projectParentDirectoryPath};
        for (Path path: resolutionOrder) {
            Path filePath = path.resolve(fileName);
            if (Files.exists(filePath))
                return filePath;
        }
        throw new FileNotFoundException("Can't find the specified file: '"+fileName+"'");
    }

    public static void populatePathClassGUI(PathClass... toAdd) {
        List<PathClass> visibleClasses = new ArrayList<>(getProject().getPathClasses());
        List<PathClass> missingClasses = Arrays.stream(toAdd)
                .filter(classification -> !visibleClasses.contains(classification))
                .toList();
        visibleClasses.addAll(missingClasses);
        var qupathGUI = QuPathGUI.getInstance();
        if (qupathGUI != null)
            FXUtils.runOnApplicationThread(() ->
                qupathGUI.getAvailablePathClasses().setAll(visibleClasses)
            );
    }

    public static <T> String join(Collection<T> c, String delimiter) {
        if (c.isEmpty())
            return "";
        StringBuilder classesStr = new StringBuilder();
        List<T> l = c instanceof List ? (List<T>) c : new ArrayList<>(c);
        for (int i = 0; i < l.size()-1; i++) {
            T o = l.get(i);
            classesStr.append(o).append(delimiter);
        }
        classesStr.append(l.get(l.size()-1));
        return classesStr.toString();
    }
}
