// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.gui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.braian.ExclusionReport;
import qupath.ext.braian.config.ProjectsConfig;
import qupath.ext.braian.runners.ABBAImporterRunner;
import qupath.ext.braian.runners.AutoExcludeEmptyRegionsRunner;
import qupath.ext.braian.runners.BraiAnAnalysisRunner;
import qupath.ext.braian.runners.ClassifierSampleRunner;
import qupath.ext.braian.utils.BraiAn;
import qupath.ext.braian.utils.ProjectDiscoveryService;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.projects.Project;
import qupath.lib.projects.Projects;
import org.yaml.snakeyaml.error.YAMLException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main JavaFX dialog for the BraiAn pipeline manager.
 */
public class BraiAnDetectDialog {
    private static final Logger logger = LoggerFactory.getLogger(BraiAnDetectDialog.class);
    private static final String CONFIG_FILENAME = "BraiAn.yml";

    private final QuPathGUI qupath;
    private final Stage stage;
    private final ChangeListener<Project<BufferedImage>> projectListener = (obs, oldValue, newValue) -> Platform
            .runLater(this::updateConfigForContext);
    private final BooleanProperty batchMode = new SimpleBooleanProperty(false);
    private final BooleanProperty batchReady = new SimpleBooleanProperty(false);
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final TextField batchRootField = new TextField();
    private final BooleanProperty importBatchMode = new SimpleBooleanProperty(false);
    private final TextField importBatchRootField = new TextField();
    private final TextField importAtlasNameField = new TextField();
    private final PauseTransition saveDebounce = new PauseTransition(Duration.millis(500));
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService runExecutor = Executors.newSingleThreadExecutor();
    private final ObservableList<String> channelNames = FXCollections.observableArrayList();
    private final ObservableList<DiscoveredProject> discoveredProjects = FXCollections.observableArrayList();
    private final List<String> availableImageChannels = new ArrayList<>();
    private ProjectsConfig config;
    private Path configPath;
    private ExperimentPane experimentPane;
    private Runnable onClose;

    private static final class DiscoveredProject {
        private final String name;
        private final Path projectFile;
        private final BooleanProperty selected = new SimpleBooleanProperty(true);

        private DiscoveredProject(String name, Path projectFile, boolean selected) {
            this.name = name;
            this.projectFile = projectFile;
            this.selected.set(selected);
        }

        public Path getProjectFile() {
            return projectFile;
        }

        public BooleanProperty selectedProperty() {
            return selected;
        }

        public boolean isSelected() {
            return selected.get();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum InitialTab {
        IMPORT,
        DETECTION
    }

    /**
     * Creates the main BraiAnDetect dialog.
     *
     * @param qupath     the QuPath GUI instance
     * @param initialTab which tab should be selected initially
     */
    public BraiAnDetectDialog(QuPathGUI qupath, InitialTab initialTab) {
        this.qupath = qupath;
        this.stage = new Stage();
        this.stage.setTitle("BraiAnDetect Pipeline Manager");
        this.stage.initOwner(qupath.getStage());
        this.stage.setWidth(600);
        this.stage.setHeight(820);
        this.batchReady.bind(batchRootField.textProperty().isNotEmpty());
        this.stage.setOnHidden(event -> {
            shutdownExecutors();
            if (onClose != null) {
                onClose.run();
            }
        });

        initializeConfig();

        qupath.projectProperty().addListener(projectListener);
        this.stage.setScene(new Scene(buildRoot(initialTab)));
    }

    /**
     * Registers a callback to run when the dialog closes.
     *
     * @param onClose callback invoked after the dialog is hidden
     */
    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    /**
     * Shows the dialog and brings it to the front.
     */
    public void show() {
        this.stage.show();
        this.stage.toFront();
    }

    private void initializeConfig() {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            Dialogs.showErrorMessage("BraiAnDetect", "Open a project before launching the GUI.");
            throw new IllegalStateException("No project open");
        }
        loadConfig(resolveConfigPath());
        ImageData<BufferedImage> imageData = qupath.getViewer() != null ? qupath.getViewer().getImageData() : null;
        if (imageData != null) {
            for (ImageChannel channel : imageData.getServerMetadata().getChannels()) {
                availableImageChannels.add(channel.getName());
            }
        }
    }

    private Parent buildRoot(InitialTab initialTab) {
        TabPane tabPane = new TabPane();
        Tab importTab = buildImportTab();
        Tab experimentTab = buildExperimentTab();

        tabPane.getTabs().addAll(importTab, experimentTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        if (initialTab == InitialTab.DETECTION) {
            tabPane.getSelectionModel().select(experimentTab);
        } else {
            tabPane.getSelectionModel().select(importTab);
        }

        BorderPane root = new BorderPane(tabPane);
        root.setPadding(new Insets(8));
        return root;
    }

    private Tab buildImportTab() {
        VBox container = new VBox(16);
        container.setPadding(new Insets(16));

        ToggleGroup scopeGroup = new ToggleGroup();
        RadioButton currentImageToggle = new RadioButton("Current Image");
        RadioButton currentProjectToggle = new RadioButton("Current Project");
        RadioButton experimentToggle = new RadioButton("Experiment");
        currentImageToggle.setToggleGroup(scopeGroup);
        currentProjectToggle.setToggleGroup(scopeGroup);
        experimentToggle.setToggleGroup(scopeGroup);
        currentProjectToggle.setSelected(true);

        HBox scopeRow = new HBox(16, new Label("Scope:"), currentImageToggle, currentProjectToggle, experimentToggle);
        scopeRow.setAlignment(Pos.CENTER_LEFT);

        currentImageToggle.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (selected) {
                importBatchMode.set(false);
            }
        });
        currentProjectToggle.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (selected) {
                importBatchMode.set(false);
            }
        });
        experimentToggle.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (selected) {
                importBatchMode.set(true);
                refreshDiscoveredProjects(resolveImportBatchRoot());
            }
        });

        HBox batchChooserRow = new HBox(8);
        batchChooserRow.setAlignment(Pos.CENTER_LEFT);
        importBatchRootField.setPromptText("Select a root folder containing QuPath projects");
        importBatchRootField.setEditable(true);
        importBatchRootField.textProperty().addListener((obs, oldValue, value) -> {
            if (importBatchMode.get()) {
                refreshDiscoveredProjects(resolveImportBatchRoot());
            }
        });
        Button browseButton = new Button("Browse");
        browseButton.setOnAction(event -> chooseBatchFolder(stage, importBatchRootField));
        HBox.setHgrow(importBatchRootField, Priority.ALWAYS);
        batchChooserRow.getChildren().addAll(new Label("Projects Folder:"), importBatchRootField, browseButton);
        batchChooserRow.managedProperty().bind(importBatchMode);
        batchChooserRow.visibleProperty().bind(importBatchMode);

        VBox importAtlasPanel = new VBox(10);
        importAtlasPanel.setPadding(new Insets(12));
        importAtlasPanel.setStyle(
                "-fx-border-color: -fx-box-border; -fx-border-radius: 6; -fx-background-radius: 6; -fx-background-color: -fx-control-inner-background;");

        Label importTitle = new Label("Import Atlas");
        importTitle.setStyle("-fx-font-weight: bold;");

        Button importButton = new Button("Import Atlas");
        importButton.setTooltip(new Tooltip("Imports atlas regions into the hierarchy."));
        importButton.disableProperty().bind(running);
        importButton.setOnAction(event -> {
            if (currentImageToggle.isSelected()) {
                handleImportCurrent();
            } else if (currentProjectToggle.isSelected()) {
                handleImportProject();
            } else {
                handleImportExperiment();
            }
        });

        Label atlasNameLabel = new Label("Atlas Name (Auto-detected):");
        importAtlasNameField.setEditable(false);
        importAtlasNameField.setFocusTraversable(false);
        importAtlasNameField.setPromptText("No atlas detected");
        Button refreshAtlasButton = new Button("Refresh");
        refreshAtlasButton.setTooltip(new Tooltip("Re-scan projects for the imported atlas root annotation."));
        refreshAtlasButton.disableProperty().bind(running);
        refreshAtlasButton.setOnAction(event -> refreshAtlasDetection());
        HBox atlasRow = new HBox(8, importAtlasNameField, refreshAtlasButton);
        atlasRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(importAtlasNameField, Priority.ALWAYS);

        Label warningLabel = new Label(
                "Note: Importing atlas annotations will clear all existing objects in the hierarchy.");
        warningLabel.getStyleClass().add("warning");

        importAtlasPanel.getChildren().addAll(importTitle, importButton, atlasNameLabel, atlasRow,
                warningLabel);

        VBox projectListPanel = buildProjectListPanel(importBatchMode);

        VBox autoExcludePanel = buildAutoExcludePanel(currentImageToggle, currentProjectToggle, experimentToggle);

        VBox classifierSamplePanel = buildClassifierTrainingPanel();

        container.getChildren().addAll(scopeRow, batchChooserRow, projectListPanel, importAtlasPanel, autoExcludePanel,
                classifierSamplePanel);
        return new Tab("Project Preparation", container);
    }

    private VBox buildClassifierTrainingPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setStyle(
                "-fx-border-color: -fx-box-border; -fx-border-radius: 6; -fx-background-radius: 6; -fx-background-color: -fx-control-inner-background;");

        Label title = new Label("Classifier Training Sample");
        title.setStyle("-fx-font-weight: bold;");

        Label desc = new Label("Create a new project with random samples from all projects in the experiment.");
        desc.setWrapText(true);

        Spinner<Integer> samplesSpinner = new Spinner<>(1, 50, 3);
        samplesSpinner.setEditable(true);
        samplesSpinner.setPrefWidth(80);
        HBox samplesRow = new HBox(10, new Label("Samples per Project:"), samplesSpinner);
        samplesRow.setAlignment(Pos.CENTER_LEFT);

        TextField projectNameField = new TextField("ClassifierTrainingGrounds");
        HBox nameRow = new HBox(10, new Label("Project Name:"), projectNameField);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(projectNameField, Priority.ALWAYS);

        Button createButton = new Button("Create Training Project");
        createButton.setTooltip(new Tooltip(
                "Creates a new QuPath project in the experiment folder containing sampled images."));
        createButton.disableProperty().bind(running);
        createButton.setOnAction(e -> {
            Path rootPath = resolveImportBatchRoot();
            if (rootPath == null) {
                // If not in experiment mode, prompt for root
                chooseBatchFolder(stage, importBatchRootField); // This updates the field
                rootPath = resolveImportBatchRoot();
            }
            if (rootPath == null) {
                Dialogs.showErrorMessage("Classifier Sample", "Select an experiment root folder first.");
                return;
            }

            final Path experimentRoot = rootPath;
            final int samples = samplesSpinner.getValue();
            final String name = projectNameField.getText();

            if (name == null || name.isBlank()) {
                Dialogs.showErrorMessage("Classifier Sample", "Enter a project name.");
                return;
            }

            runAsync("Create Classifier Project", () -> {
                try {
                    ClassifierSampleRunner.run(qupath, experimentRoot, samples, name);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        });

        Hyperlink docLink = new Hyperlink("Read about Classifier Training (requires cell detection first)");
        docLink.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI(
                        "https://silvalab.codeberg.page/BraiAn/object-classifiers/#:~:text=interface%20(CLI).-,Classifier%20training,-Dataset%20preparation"));
            } catch (Exception ex) {
                logger.error("Failed to open link", ex);
            }
        });

        panel.getChildren().addAll(title, desc, samplesRow, nameRow, createButton, docLink);
        return panel;
    }

    private VBox buildAutoExcludePanel(RadioButton currentImageToggle, RadioButton currentProjectToggle,
            RadioButton experimentToggle) {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setStyle(
                "-fx-border-color: -fx-box-border; -fx-border-radius: 6; -fx-background-radius: 6; -fx-background-color: -fx-control-inner-background;");

        Label title = new Label("Auto-Exclude Empty Regions");
        title.setStyle("-fx-font-weight: bold;");

        ComboBox<String> channelModeSelector = new ComboBox<>(
                FXCollections.observableArrayList("Nuclei Only", "All Channels (Max)"));
        channelModeSelector.setValue("Nuclei Only");
        channelModeSelector.setMaxWidth(Double.MAX_VALUE);
        channelModeSelector.setTooltip(new Tooltip(
                "Nuclei Only: Use only the selected nuclei channel.\nAll Channels (Max): Use the brightest value across all channels."));
        HBox modeRow = new HBox(10, new Label("Channel Mode:"), channelModeSelector);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        List<String> channelOptions = new ArrayList<>();
        if (!availableImageChannels.isEmpty()) {
            channelOptions.addAll(availableImageChannels);
        } else {
            channelOptions.addAll(List.of("Red", "Green", "Blue"));
        }
        ComboBox<String> channelSelector = new ComboBox<>(FXCollections.observableArrayList(channelOptions));
        channelSelector.setPromptText("Select nuclei channel");
        channelSelector.setMaxWidth(Double.MAX_VALUE);
        channelSelector.setTooltip(new Tooltip("Select the channel used for nuclei staining (e.g., DAPI)."));
        if (channelOptions.size() == 1) {
            channelSelector.setValue(channelOptions.get(0));
        }
        HBox nucleiRow = new HBox(10, new Label("Nuclei channel:"), channelSelector);
        nucleiRow.setAlignment(Pos.CENTER_LEFT);
        nucleiRow.visibleProperty().bind(channelModeSelector.valueProperty().isEqualTo("Nuclei Only"));
        nucleiRow.managedProperty().bind(nucleiRow.visibleProperty());

        TextField multiplierField = new TextField("1.0");
        multiplierField.setPrefWidth(60);
        multiplierField.setTooltip(new Tooltip(
                "Controls how many regions are excluded. Lower = stricter (fewer exclusions). Higher = more exclusions."));
        HBox thresholdRow = new HBox(10,
                new Label("Threshold Multiplier:"),
                multiplierField,
                new Label("(0.1 = fewer, 1.0 = more exclusions)"));
        thresholdRow.setAlignment(Pos.CENTER_LEFT);

        Button runButton = new Button("Run Auto-Exclusion");
        runButton.setTooltip(
                new Tooltip("Marks low-intensity regions as excluded based on adaptive Otsu thresholding."));
        runButton.disableProperty().bind(running);
        runButton.setOnAction(e -> {
            boolean useMax = "All Channels (Max)".equals(channelModeSelector.getValue());
            String nucleiChannel = channelSelector.getValue();

            if (!useMax && (nucleiChannel == null || nucleiChannel.isBlank())) {
                Dialogs.showErrorMessage("Auto-Exclude", "Select a nuclei channel.");
                return;
            }

            double multiplier;
            try {
                multiplier = Double.parseDouble(multiplierField.getText());
            } catch (NumberFormatException ex) {
                Dialogs.showErrorMessage("Auto-Exclude",
                        "Invalid threshold multiplier. Please enter a number (e.g., 1.0).");
                return;
            }

            List<String> channelsToProcess;
            if (useMax) {
                channelsToProcess = new ArrayList<>(availableImageChannels);
                if (channelsToProcess.isEmpty()) {
                    channelsToProcess.addAll(List.of("Red", "Green", "Blue"));
                }
            } else {
                channelsToProcess = List.of(nucleiChannel);
            }

            final List<Path> selectedProjectsForExperiment;
            if (experimentToggle.isSelected()) {
                Path rootPath = resolveImportBatchRoot();
                if (rootPath == null) {
                    Dialogs.showErrorMessage("Auto-Exclude",
                            "Select a projects folder before running auto-exclusion for an experiment.");
                    return;
                }
                selectedProjectsForExperiment = getSelectedProjectFiles();
                if (selectedProjectsForExperiment.isEmpty()) {
                    Dialogs.showErrorMessage("Auto-Exclude", "Select at least one project.");
                    return;
                }
            } else {
                selectedProjectsForExperiment = List.of();
            }

            runAsync("Auto-Exclude Empty Regions", () -> {
                List<ExclusionReport> reports;
                if (currentImageToggle.isSelected()) {
                    reports = AutoExcludeEmptyRegionsRunner.runCurrentImage(qupath, channelsToProcess, useMax,
                            multiplier);
                } else if (currentProjectToggle.isSelected()) {
                    reports = AutoExcludeEmptyRegionsRunner.runProject(qupath, channelsToProcess, useMax, multiplier);
                } else {
                    reports = AutoExcludeEmptyRegionsRunner.runBatch(qupath, selectedProjectsForExperiment,
                            channelsToProcess, useMax, multiplier);
                }

                Platform.runLater(() -> new ExclusionReviewDialog(qupath, reports).show());
            });
        });

        panel.getChildren().addAll(title, modeRow, nucleiRow, thresholdRow, runButton);
        return panel;
    }

    private Tab buildExperimentTab() {
        VBox scopeSection = new VBox(12);
        scopeSection.setPadding(new Insets(16, 16, 0, 16));

        HBox scopeRow = new HBox(16);
        scopeRow.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup scopeGroup = new ToggleGroup();
        RadioButton currentProjectToggle = new RadioButton("Current Project");
        RadioButton batchToggle = new RadioButton("Experiment");
        currentProjectToggle.setToggleGroup(scopeGroup);
        batchToggle.setToggleGroup(scopeGroup);
        currentProjectToggle.setSelected(true);
        scopeRow.getChildren().addAll(new Label("Scope:"), currentProjectToggle, batchToggle);

        currentProjectToggle.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (selected) {
                batchMode.set(false);
                updateConfigForContext();
            }
        });
        batchToggle.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (selected) {
                batchMode.set(true);
                updateConfigForContext();
                refreshDiscoveredProjects();
            }
        });

        HBox batchChooserRow = new HBox(8);
        batchChooserRow.setAlignment(Pos.CENTER_LEFT);
        batchRootField.setPromptText("Select a root folder containing QuPath projects");
        batchRootField.setEditable(true);
        batchRootField.textProperty().addListener((obs, oldValue, value) -> {
            if (batchMode.get()) {
                updateConfigForContext();
                refreshDiscoveredProjects();
            }
        });
        Button browseButton = new Button("Browse");
        browseButton.setOnAction(event -> chooseBatchFolder(stage, batchRootField));
        HBox.setHgrow(batchRootField, Priority.ALWAYS);
        batchChooserRow.getChildren().addAll(new Label("Projects Folder:"), batchRootField, browseButton);
        batchChooserRow.managedProperty().bind(batchMode);
        batchChooserRow.visibleProperty().bind(batchMode);

        VBox projectListPanel = buildProjectListPanel(batchMode);
        scopeSection.getChildren().addAll(scopeRow, batchChooserRow, projectListPanel, new Separator());

        this.experimentPane = new ExperimentPane(
                config,
                channelNames,
                availableImageChannels,
                stage,
                batchMode,
                batchReady,
                running,
                this::handlePreview,
                this::handleRun,
                this::handleConfigChanged,
                this::resolveConfigRoot,
                this::resolveProjectDirectory,
                qupath::getProject,
                this::resolveImageData);
        ScrollPane scrollPane = new ScrollPane(experimentPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        BorderPane layout = new BorderPane();
        layout.setTop(scopeSection);
        layout.setCenter(scrollPane);
        return new Tab("Cell Detection", layout);
    }

    private void handleImportCurrent() {
        runAsync("ABBA Import", () -> {
            ABBAImporterRunner.runCurrentImage(qupath);
            Platform.runLater(() -> experimentPane.autoDetectAtlas());
        });
    }

    private void handleImportProject() {
        runAsync("ABBA Import", () -> {
            ABBAImporterRunner.runProject(qupath);
            Platform.runLater(() -> experimentPane.autoDetectAtlas());
        });
    }

    private void handleImportExperiment() {
        Path rootPath = resolveImportBatchRoot();
        if (rootPath == null) {
            chooseBatchFolder(stage, importBatchRootField);
            rootPath = resolveImportBatchRoot();
        }
        if (rootPath == null) {
            Dialogs.showErrorMessage("BraiAnDetect", "Select a projects folder before importing to an experiment.");
            return;
        }

        Map<Path, Boolean> selectedByPath = new HashMap<>();
        for (DiscoveredProject project : discoveredProjects) {
            selectedByPath.put(project.getProjectFile(), project.isSelected());
        }
        discoveredProjects.setAll(buildDiscoveredProjects(rootPath, selectedByPath));
        List<Path> selectedProjects = getSelectedProjectFiles();
        if (selectedProjects.isEmpty()) {
            Dialogs.showErrorMessage("BraiAnDetect", "Select at least one project to import.");
            return;
        }
        runAsync("ABBA Import", () -> ABBAImporterRunner.runBatch(qupath, selectedProjects));
    }

    private void handlePreview() {
        if (!flushConfigNow()) {
            return;
        }
        runAsync("Preview", () -> BraiAnAnalysisRunner.runPreview(qupath));
    }

    private void handleRun() {
        if (!flushConfigNow()) {
            return;
        }
        if (batchMode.get()) {
            Path rootPath = resolveBatchRoot();
            if (rootPath == null) {
                Dialogs.showErrorMessage("BraiAnDetect", "Select a projects folder before running batch detection.");
                return;
            }
            List<Path> selectedProjects = getSelectedProjectFiles();
            if (selectedProjects.isEmpty()) {
                Dialogs.showErrorMessage("BraiAnDetect", "Select at least one project to run detection.");
                return;
            }
            runAsync("Run Batch Detection", () -> BraiAnAnalysisRunner.runBatch(qupath, rootPath, selectedProjects));
        } else {
            runAsync("Run Detection", () -> BraiAnAnalysisRunner.runProject(qupath));
        }
    }

    private VBox buildProjectListPanel(BooleanProperty visibility) {
        ListView<DiscoveredProject> listView = new ListView<>(discoveredProjects);
        listView.setCellFactory(CheckBoxListCell.forListView(DiscoveredProject::selectedProperty));
        listView.setPrefHeight(160);
        listView.managedProperty().bind(visibility);
        listView.visibleProperty().bind(visibility);

        Label label = new Label("Discovered projects");
        label.managedProperty().bind(visibility);
        label.visibleProperty().bind(visibility);

        VBox panel = new VBox(8, label, listView);
        panel.managedProperty().bind(visibility);
        panel.visibleProperty().bind(visibility);
        return panel;
    }

    private void refreshDiscoveredProjects() {
        Path rootPath = resolveBatchRoot();
        refreshDiscoveredProjects(rootPath);
    }

    private void refreshDiscoveredProjects(Path rootPath) {
        if (rootPath == null) {
            discoveredProjects.clear();
            return;
        }

        Map<Path, Boolean> selectedByPath = new HashMap<>();
        for (DiscoveredProject project : discoveredProjects) {
            selectedByPath.put(project.getProjectFile(), project.isSelected());
        }

        runAsync("Discover Projects", () -> {
            List<DiscoveredProject> refreshed = buildDiscoveredProjects(rootPath, selectedByPath);
            Platform.runLater(() -> discoveredProjects.setAll(refreshed));
        });
    }

    private List<DiscoveredProject> buildDiscoveredProjects(Path rootPath, Map<Path, Boolean> selectedByPath) {
        if (rootPath == null) {
            return List.of();
        }
        List<Path> projectFiles = ProjectDiscoveryService.discoverProjectFiles(rootPath);
        List<DiscoveredProject> refreshed = new ArrayList<>();
        for (Path projectFile : projectFiles) {
            String name = projectFile.getParent().getFileName().toString();
            boolean selected = selectedByPath.getOrDefault(projectFile, true);
            refreshed.add(new DiscoveredProject(name, projectFile, selected));
        }
        return refreshed;
    }

    private List<Path> getSelectedProjectFiles() {
        return discoveredProjects.stream()
                .filter(DiscoveredProject::isSelected)
                .map(DiscoveredProject::getProjectFile)
                .toList();
    }

    private boolean flushConfigNow() {
        saveDebounce.stop();
        if (configPath == null) {
            Dialogs.showErrorMessage("BraiAnDetect",
                    "No configuration path is available. Select a valid project or batch folder.");
            return false;
        }
        try {
            writeConfigNow(ProjectsConfig.toYaml(config));
            return true;
        } catch (IOException e) {
            Dialogs.showErrorMessage("BraiAnDetect", "Failed to save BraiAn.yml: " + e.getMessage());
            return false;
        }
    }

    private void scheduleConfigSave() {
        saveDebounce.stop();
        saveDebounce.setOnFinished(event -> saveConfigAsync(ProjectsConfig.toYaml(config)));
        saveDebounce.playFromStart();
    }

    private void saveConfigAsync(String yaml) {
        if (configPath == null) {
            logger.warn("Config path is not available; skipping save.");
            return;
        }
        if (saveExecutor.isShutdown()) {
            return;
        }
        saveExecutor.execute(() -> {
            try {
                writeConfigNow(yaml);
            } catch (IOException e) {
                logger.error("Failed to save config", e);
            }
        });
    }

    private void writeConfigNow(String yaml) throws IOException {
        if (configPath.getParent() != null) {
            Files.createDirectories(configPath.getParent());
        }
        Files.writeString(configPath, yaml, StandardCharsets.UTF_8);
    }

    private void loadConfig(Path path) {
        if (path == null) {
            return;
        }
        ProjectsConfig loadedConfig;
        try {
            if (Files.exists(path)) {
                loadedConfig = ProjectsConfig.read(path);
            } else {
                loadedConfig = new ProjectsConfig();
            }
        } catch (IOException | YAMLException e) {
            Dialogs.showErrorMessage("BraiAnDetect", "Unable to load BraiAn.yml: " + e.getMessage());
            loadedConfig = new ProjectsConfig();
        }
        this.config = loadedConfig;
        this.configPath = path;
        if (experimentPane != null) {
            experimentPane.setConfig(loadedConfig);
        }
        updateAtlasNameFields();
        if (!Files.exists(path)) {
            saveConfigAsync(ProjectsConfig.toYaml(loadedConfig));
        }
    }

    private void updateConfigForContext() {
        Path resolved = resolveConfigPath();
        if (resolved == null) {
            return;
        }
        if (configPath != null && configPath.equals(resolved)) {
            return;
        }
        loadConfig(resolved);
    }

    private void handleConfigChanged() {
        scheduleConfigSave();
        updateAtlasNameFields();
    }

    private void refreshAtlasDetection() {
        if (experimentPane != null) {
            experimentPane.refreshAtlasDetection();
        }
    }

    private void updateAtlasNameFields() {
        if (config == null) {
            importAtlasNameField.setText("");
            return;
        }
        String atlasName = config.getAtlasName();
        importAtlasNameField.setText(atlasName == null ? "" : atlasName);
    }

    private Path resolveConfigPath() {
        if (batchMode.get()) {
            String root = batchRootField.getText();
            if (root == null || root.isBlank()) {
                return null;
            }
            return Path.of(root).resolve(CONFIG_FILENAME);
        }
        Optional<Path> configPathOpt = BraiAn.resolvePathIfPresent(qupath.getProject(), CONFIG_FILENAME);
        if (configPathOpt.isPresent()) {
            return configPathOpt.get();
        }
        Path projectDir = resolveProjectDirectory();
        if (projectDir == null) {
            return null;
        }
        return projectDir.resolve(CONFIG_FILENAME);
    }

    private void chooseBatchFolder(Window owner, TextField targetField) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select QuPath Projects Folder");
        Path projectDir = resolveProjectDirectory();
        if (projectDir != null && Files.exists(projectDir)) {
            chooser.setInitialDirectory(projectDir.toFile());
        }
        var selection = chooser.showDialog(owner);
        if (selection != null) {
            targetField.setText(selection.getAbsolutePath());
        }
    }

    private Path resolveConfigRoot() {
        if (configPath != null) {
            return configPath.getParent();
        }
        return resolveProjectDirectory();
    }

    private Path resolveBatchRoot() {
        String root = batchRootField.getText();
        if (root == null || root.isBlank()) {
            return null;
        }
        Path path = Path.of(root);
        if (!Files.isDirectory(path)) {
            return null;
        }
        return path;
    }

    private Path resolveImportBatchRoot() {
        String root = importBatchRootField.getText();
        if (root == null || root.isBlank()) {
            return null;
        }
        Path path = Path.of(root);
        if (!Files.isDirectory(path)) {
            return null;
        }
        return path;
    }

    private void runAsync(String title, Runnable task) {
        if (running.get() || runExecutor.isShutdown()) {
            return;
        }
        running.set(true);
        runExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.error("{} failed", title, e);
                showError(title, e);
            } finally {
                Platform.runLater(() -> running.set(false));
            }
        });
    }

    private void showError(String title, Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = "Unexpected error. See log for details.";
        }
        String finalMessage = message;
        Platform.runLater(() -> Dialogs.showErrorMessage(title, finalMessage));
    }

    private void shutdownExecutors() {
        saveDebounce.stop();
        qupath.projectProperty().removeListener(projectListener);
        saveExecutor.shutdown();
        runExecutor.shutdown();
    }

    private Path resolveProjectDirectory() {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            return null;
        }
        return Projects.getBaseDirectory(project).toPath();
    }

    private ImageData<BufferedImage> resolveImageData() {
        return qupath.getViewer() != null ? qupath.getViewer().getImageData() : null;
    }
}
