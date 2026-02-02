// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.braian.gui.BraiAnDetectDialog;
import qupath.ext.braian.gui.ExclusionReviewDialog;
import qupath.ext.braian.runners.AutoExcludeEmptyRegionsRunner;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Entry point for the BraiAn QuPath extension.
 * <p>
 * This class registers menu items and bundled scripts in the QuPath GUI.
 */
public class BraiAnExtension implements QuPathExtension {

    private static final String menuPosition = "Extensions>BraiAn";

    static final Logger logger = LoggerFactory.getLogger(BraiAnExtension.class);

    /**
     * @return the logger used by the extension to print messages
     */
    public static Logger getLogger() {
        return logger;
    }

    /**
     * integrate the extension in QuPath GUI
     * 
     * @param qupath the istance of QuPath to modify
     */
    @Override
    public void installExtension(QuPathGUI qupath) {
        this.addCommands(qupath);
        this.addHelpScripts(qupath);
    }

    private void addCommands(QuPathGUI qupath) {
        var importAction = ActionTools.createAction(
                () -> {
                    try {
                        new BraiAnDetectDialog(qupath, BraiAnDetectDialog.InitialTab.IMPORT).show();
                    } catch (IllegalStateException e) {
                        logger.warn("BraiAnDetect GUI not opened: {}", e.getMessage());
                    }
                },
                "Project Preparation");

        var detectionAction = ActionTools.createAction(
                () -> {
                    try {
                        new BraiAnDetectDialog(qupath, BraiAnDetectDialog.InitialTab.DETECTION).show();
                    } catch (IllegalStateException e) {
                        logger.warn("BraiAnDetect GUI not opened: {}", e.getMessage());
                    }
                },
                "Cell Detection");

        var showExclusions = ActionTools.createAction(
                () -> {
                    if (qupath.getProject() == null) {
                        logger.error("No project is currently open!");
                        return;
                    }
                    var reports = AutoExcludeEmptyRegionsRunner.getExcludedRegionsCurrentProject(qupath);
                    new ExclusionReviewDialog(qupath, reports).show();
                },
                "Show regions currently excluded");
        MenuTools.addMenuItems(
                qupath.getMenu(BraiAnExtension.menuPosition, true),
                importAction,
                detectionAction,
                showExclusions);
    }

    private void addHelpScripts(QuPathGUI qupath) {
        File extensionJar = getExtensionJarPath();
        if (extensionJar == null) {
            logger.warn("Could not find the '{}' JAR file!", getClass().getSimpleName());
            return;
        }
        String[] scripts = listJarDirectory(extensionJar,
                (dir, file) -> dir.toString().equals("scripts") && file.endsWith(".groovy"));

        for (String scriptPath : scripts)
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(scriptPath)) {
                if (stream == null) {
                    logger.warn("Missing script resource: {}", scriptPath);
                    continue;
                }
                String scriptName = new File(scriptPath).getName();
                String script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                MenuTools.addMenuItems(
                        qupath.getMenu(BraiAnExtension.menuPosition + ">Scripts", true),
                        new Action(scriptName, e -> openScript(qupath, scriptName, script)));
            } catch (Exception e) {
                logger.error(e.getLocalizedMessage(), e);
            }
    }

    private File getExtensionJarPath() {
        File extensionJar = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
        assert extensionJar.toString().endsWith(".jar");
        return extensionJar;
    }

    private String[] listJarDirectory(File jarPath, FilenameFilter filter) {
        Enumeration<JarEntry> internalFiles = null;
        try (JarFile jar = new JarFile(URLDecoder.decode(jarPath.toString(), StandardCharsets.UTF_8))) {
            internalFiles = jar.entries(); // gives ALL internalFiles in jar
            List<String> selectedResources = new ArrayList<>();
            while (internalFiles.hasMoreElements()) {
                String internalPath = internalFiles.nextElement().getName();
                if (internalPath.endsWith("/"))
                    continue;
                File internalFilePath = new File(internalPath);
                if (filter.accept(internalFilePath.getParentFile(), internalFilePath.getName()))
                    selectedResources.add(internalPath);
            }
            return selectedResources.toArray(new String[0]);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return new String[0];
        }
    }

    private static void openScript(QuPathGUI qupath, String scriptName, String scriptCode) {
        var editor = qupath.getScriptEditor();
        if (editor == null) {
            logger.error("No script editor is available!");
            return;
        }
        qupath.getScriptEditor().showScript(scriptName, scriptCode);
    }

    /**
     * @return the name of the extension
     */
    @Override
    public String getName() {
        return "BraiAn extension";
    }

    /**
     * @return the description of the extension
     */
    @Override
    public String getDescription() {
        return "A collection of tools for whole-brain data quantification and extraction";
    }

    /**
     * @return the minimum QuPath version required by this extension
     */
    @Override
    public Version getQuPathVersion() {
        return Version.parse("0.6.0");
    }
}
