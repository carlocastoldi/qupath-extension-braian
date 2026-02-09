// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.gui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import qupath.ext.braian.config.AutoThresholdParmameters;
import qupath.ext.braian.config.ChannelDetectionsConfig;
import qupath.ext.braian.config.DetectionsCheckConfig;
import qupath.ext.braian.config.ProjectsConfig;
import qupath.ext.braian.config.WatershedCellDetectionConfig;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Main pane used to configure and run the BraiAn analysis pipeline.
 * <p>
 * This component is responsible for editing {@link ProjectsConfig}, including
 * global settings,
 * per-channel parameters, and execution controls.
 */
public class ExperimentPane extends VBox {
    private final ObservableList<String> channelNames;
    private final List<String> availableImageChannels;
    private final Stage owner;
    private final BooleanProperty batchMode;
    private final BooleanProperty batchReady;
    private final BooleanProperty running;
    private final Runnable onPreview;
    private final Runnable onRun;
    private final Runnable onConfigChanged;
    private final Supplier<Path> configRootSupplier;
    private final Supplier<Path> projectDirSupplier;
    private final Supplier<Project<BufferedImage>> projectSupplier;
    private final Supplier<ImageData<?>> imageDataSupplier;
    private final VBox channelStack = new VBox(12);
    private final TextField classForDetectionsField = new TextField();
    private final TextField atlasNameField = new TextField();
    private final CheckBox detectionsCheckBox = new CheckBox("Enforce Co-localization");
    private final ComboBox<String> controlChannelCombo = new ComboBox<>();
    private final Button addChannelButton = new Button("+ Add Channel");
    private final BooleanProperty hasCellDetection = new SimpleBooleanProperty(false);
    private final BooleanProperty hasPixelClassification = new SimpleBooleanProperty(false);
    private ProjectsConfig config;
    private boolean isUpdating = false;

    private static final String HELP_URL_CROSS_CHANNEL = "https://silvalab.codeberg.page/BraiAn/image-analysis/#:~:text=Find%20co%2Dlabelled%20detections";
    private static final String DEFAULT_ATLAS = "allen_mouse_10um_java";
    private static final String ROOT_ANNOTATION_NAME = "Root";

    /**
     * Creates the experiment pane.
     *
     * @param config                 the initial configuration
     * @param channelNames           observable list of available channel names
     * @param availableImageChannels available image channel names from the current
     *                               image
     * @param owner                  owning stage used for dialogs
     * @param batchMode              whether the analysis runs in batch mode
     * @param batchReady             whether batch mode has a valid project
     *                               selection
     * @param running                indicates whether the pipeline is currently
     *                               running
     * @param onPreview              callback to run a preview on the current image
     * @param onRun                  callback to run the pipeline
     * @param onConfigChanged        callback invoked when configuration is changed
     * @param configRootSupplier     supplier for the directory containing
     *                               configuration resources
     * @param projectDirSupplier     supplier for the current project directory
     * @param projectSupplier        supplier for the current QuPath project
     * @param imageDataSupplier      supplier for the current {@link ImageData}
     */
    public ExperimentPane(ProjectsConfig config,
            ObservableList<String> channelNames,
            List<String> availableImageChannels,
            Stage owner,
            BooleanProperty batchMode,
            BooleanProperty batchReady,
            BooleanProperty running,
            Runnable onPreview,
            Runnable onRun,
            Runnable onConfigChanged,
            Supplier<Path> configRootSupplier,
            Supplier<Path> projectDirSupplier,
            Supplier<Project<BufferedImage>> projectSupplier,
            Supplier<ImageData<?>> imageDataSupplier) {
        this.channelNames = channelNames;
        this.availableImageChannels = availableImageChannels;
        this.owner = owner;
        this.batchMode = batchMode;
        this.batchReady = batchReady;
        this.running = running;
        this.onPreview = Objects.requireNonNullElse(onPreview, () -> {
        });
        this.onRun = Objects.requireNonNullElse(onRun, () -> {
        });
        this.onConfigChanged = Objects.requireNonNullElse(onConfigChanged, () -> {
        });
        this.config = config;
        this.configRootSupplier = configRootSupplier;
        this.projectDirSupplier = projectDirSupplier;
        this.projectSupplier = projectSupplier;
        this.imageDataSupplier = imageDataSupplier;

        setSpacing(16);
        setPadding(new Insets(16));

        getChildren().addAll(
                buildGlobalSection(),
                new Separator(),
                buildChannelSection(),
                new Separator(),
                buildCommandBar());

        ensureChannelListMutable();
        refreshFromConfig();
    }

    /**
     * Replaces the underlying configuration object and refreshes the UI.
     *
     * @param config the new configuration
     */
    public void setConfig(ProjectsConfig config) {
        this.config = config;
        ensureChannelListMutable();
        refreshFromConfig();
    }

    private VBox buildGlobalSection() {
        VBox section = new VBox(8);
        Label header = new Label("Global Detection Settings");
        classForDetectionsField.setPromptText("Restrict detection to this annotation class (optional)");
        classForDetectionsField.textProperty().addListener((obs, oldValue, value) -> {
            if (isUpdating) {
                return;
            }
            String trimmed = value != null ? value.trim() : "";
            config.setClassForDetections(trimmed.isEmpty() ? null : trimmed);
            notifyConfigChanged();
        });

        atlasNameField.setPromptText("Atlas name (e.g. allen_mouse_10um_java)");
        atlasNameField.textProperty().addListener((obs, oldValue, value) -> {
            if (isUpdating) {
                return;
            }
            String trimmed = value != null ? value.trim() : "";
            config.setAtlasName(trimmed.isEmpty() ? DEFAULT_ATLAS : trimmed);
            notifyConfigChanged();
        });

        // Auto-detect atlas from hierarchy if not already set or default
        autoDetectAtlas();

        HBox detectionsCheckRow = new HBox(12);
        detectionsCheckRow.setAlignment(Pos.CENTER_LEFT);
        detectionsCheckBox.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (isUpdating) {
                return;
            }
            config.getDetectionsCheck().setApply(selected);
            notifyConfigChanged();
        });
        detectionsCheckBox.disableProperty().bind(hasCellDetection.not());
        controlChannelCombo.setItems(channelNames);
        controlChannelCombo.setPromptText("Control channel");
        controlChannelCombo.disableProperty()
                .bind(Bindings.or(
                        hasCellDetection.not(),
                        Bindings.or(detectionsCheckBox.selectedProperty().not(), Bindings.isEmpty(channelNames))));
        controlChannelCombo.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdating) {
                return;
            }
            config.getDetectionsCheck().setControlChannel(value);
            notifyConfigChanged();
        });
        detectionsCheckRow.getChildren().addAll(detectionsCheckBox, controlChannelCombo);

        HBox crossChannelRow = new HBox(6);
        crossChannelRow.setAlignment(Pos.CENTER_LEFT);
        Label crossChannelLabel = new Label("Cross-channel logic");
        Hyperlink crossChannelHelp = buildHelpLink(HELP_URL_CROSS_CHANNEL);
        crossChannelRow.getChildren().addAll(crossChannelLabel, crossChannelHelp);

        Button refreshAtlasButton = new Button("Refresh");
        refreshAtlasButton.setOnAction(event -> refreshAtlasDetection());
        refreshAtlasButton.disableProperty().bind(running);
        HBox atlasRow = new HBox(8, atlasNameField, refreshAtlasButton);
        atlasRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(atlasNameField, Priority.ALWAYS);

        section.getChildren().addAll(header,
                new Label("Region Filter:"), classForDetectionsField,
                new Label("Atlas Name (Auto-detected):"), atlasRow,
                crossChannelRow, detectionsCheckRow);
        return section;
    }

    private VBox buildChannelSection() {
        VBox section = new VBox(12);
        Label header = new Label("Channel Configuration");
        channelStack.setSpacing(12);
        addChannelButton.setOnAction(event -> addChannelCard());
        addChannelButton.disableProperty().bind(running);
        section.getChildren().addAll(header, channelStack, addChannelButton);
        return section;
    }

    private HBox buildCommandBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_RIGHT);
        Button previewButton = new Button("Preview on Current Image");
        Button runButton = new Button("Run Detection");
        runButton.textProperty().bind(Bindings.when(batchMode).then("Run Batch Detection").otherwise("Run Detection"));
        var noModesEnabled = hasCellDetection.not().and(hasPixelClassification.not());
        var missingInputs = noModesEnabled;
        var batchMissing = batchMode.and(batchReady.not());
        previewButton.disableProperty().bind(running.or(missingInputs));
        runButton.disableProperty().bind(running.or(missingInputs).or(batchMissing));
        previewButton.setOnAction(event -> onPreview.run());
        runButton.setOnAction(event -> onRun.run());
        HBox.setHgrow(runButton, Priority.NEVER);
        bar.getChildren().addAll(previewButton, runButton);
        return bar;
    }

    private void refreshFromConfig() {
        isUpdating = true;
        classForDetectionsField.setText(Optional.ofNullable(config.getClassForDetections()).orElse(""));
        atlasNameField.setText(Optional.ofNullable(config.getAtlasName()).orElse(DEFAULT_ATLAS));
        DetectionsCheckConfig detectionsCheck = config.getDetectionsCheck();
        detectionsCheckBox.setSelected(detectionsCheck.getApply());
        rebuildChannelCards();
        refreshChannelNames();
        syncControlChannelSelection();
        updateModeAvailability();
        isUpdating = false;
    }

    private void rebuildChannelCards() {
        channelStack.getChildren().clear();
        for (ChannelDetectionsConfig channelConfig : config.getChannelDetections()) {
            addChannelCard(channelConfig);
        }
    }

    private void addChannelCard() {
        ChannelDetectionsConfig channelConfig = new ChannelDetectionsConfig();
        WatershedCellDetectionConfig params = channelConfig.getParameters();
        params.setRequestedPixelSizeMicrons(1.0);
        params.setHistogramThreshold(new AutoThresholdParmameters());
        List<ChannelDetectionsConfig> configs = new ArrayList<>(config.getChannelDetections());
        configs.add(channelConfig);
        config.setChannelDetections(configs);
        addChannelCard(channelConfig);
        refreshChannelNames();
        notifyConfigChanged();
    }

    private void addChannelCard(ChannelDetectionsConfig channelConfig) {
        ChannelCard card = new ChannelCard(
                channelConfig,
                availableImageChannels,
                owner,
                configRootSupplier,
                projectDirSupplier,
                this::notifyConfigChanged,
                this::refreshChannelNames,
                () -> isUpdating,
                imageDataSupplier);
        card.setOnRemove(() -> removeChannelCard(card, channelConfig));
        channelStack.getChildren().add(card);
    }

    private void removeChannelCard(ChannelCard card, ChannelDetectionsConfig channelConfig) {
        List<ChannelDetectionsConfig> configs = new ArrayList<>(config.getChannelDetections());
        configs.remove(channelConfig);
        config.setChannelDetections(configs);
        channelStack.getChildren().remove(card);
        refreshChannelNames();
        notifyConfigChanged();
    }

    private void refreshChannelNames() {
        List<String> names = config.getChannelDetections().stream()
                .filter(ChannelDetectionsConfig::isEnableCellDetection)
                .map(ChannelDetectionsConfig::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        channelNames.setAll(names);
        syncControlChannelSelection();
    }

    private void syncControlChannelSelection() {
        String configured = config.getDetectionsCheck().getControlChannel();
        if (configured != null && channelNames.contains(configured)) {
            controlChannelCombo.setValue(configured);
            return;
        }
        if (!channelNames.isEmpty() && config.getDetectionsCheck().getApply()) {
            controlChannelCombo.setValue(channelNames.get(0));
            config.getDetectionsCheck().setControlChannel(controlChannelCombo.getValue());
            notifyConfigChanged();
            return;
        }
        controlChannelCombo.setValue(null);
    }

    private void ensureChannelListMutable() {
        if (config.getChannelDetections() == null) {
            config.setChannelDetections(new ArrayList<>());
        } else {
            try {
                config.getChannelDetections().add(new ChannelDetectionsConfig());
                config.getChannelDetections().remove(config.getChannelDetections().size() - 1);
            } catch (UnsupportedOperationException ex) {
                config.setChannelDetections(new ArrayList<>(config.getChannelDetections()));
            }
        }
    }

    private void notifyConfigChanged() {
        if (!isUpdating) {
            refreshChannelNames();
            onConfigChanged.run();
            updateModeAvailability();
        }
    }

    private Hyperlink buildHelpLink(String url) {
        Hyperlink link = new Hyperlink("(?)");
        link.setOnAction(event -> QuPathGUI.openInBrowser(url));
        link.setFocusTraversable(false);
        return link;
    }

    private void updateModeAvailability() {
        boolean cellEnabled = config.getChannelDetections().stream()
                .anyMatch(channel -> channel.isEnableCellDetection()
                        && channel.getName() != null
                        && !channel.getName().isBlank());
        boolean pixelEnabled = config.getChannelDetections().stream()
                .anyMatch(channel -> channel.isEnablePixelClassification()
                        && channel.getPixelClassifiers() != null
                        && !channel.getPixelClassifiers().isEmpty()
                        && channel.getName() != null
                        && !channel.getName().isBlank());
        hasCellDetection.set(cellEnabled);
        hasPixelClassification.set(pixelEnabled);
        if (!cellEnabled && detectionsCheckBox.isSelected()) {
            detectionsCheckBox.setSelected(false);
        }
    }

    /**
     * Refreshes atlas auto-detection, ignoring any currently set atlas name.
     */
    public void refreshAtlasDetection() {
        detectAtlas(true);
    }

    /**
     * Tries to detect the atlas name from the current image, falling back to
     * scanning project entries for an imported atlas root annotation.
     */
    public void autoDetectAtlas() {
        detectAtlas(false);
    }

    private void detectAtlas(boolean force) {
        if (!force && !shouldAutoDetect()) {
            return;
        }

        String detected = findAtlasFromImage(imageDataSupplier.get());
        if (detected != null) {
            if (Platform.isFxApplicationThread()) {
                applyDetectedAtlas(detected, force);
            } else {
                Platform.runLater(() -> applyDetectedAtlas(detected, force));
            }
            return;
        }

        Project<BufferedImage> project = projectSupplier.get();
        if (project == null) {
            return;
        }

        if (Platform.isFxApplicationThread()) {
            Thread worker = new Thread(() -> {
                String found = findAtlasFromProject(project);
                if (found != null) {
                    Platform.runLater(() -> applyDetectedAtlas(found, force));
                }
            }, "braian-atlas-detect");
            worker.setDaemon(true);
            worker.start();
            return;
        }

        String found = findAtlasFromProject(project);
        if (found != null) {
            if (Platform.isFxApplicationThread()) {
                applyDetectedAtlas(found, force);
            } else {
                Platform.runLater(() -> applyDetectedAtlas(found, force));
            }
        }
    }

    private boolean shouldAutoDetect() {
        String current = config.getAtlasName();
        if (current == null || current.isBlank()) {
            return true;
        }
        if (DEFAULT_ATLAS.equals(current)) {
            return true;
        }
        return "image".equalsIgnoreCase(current);
    }

    private void applyDetectedAtlas(String detected, boolean force) {
        if (detected == null || detected.isBlank()) {
            return;
        }
        if (!force && !shouldAutoDetect()) {
            return;
        }
        String current = config.getAtlasName();
        if (detected.equals(current)) {
            return;
        }
        boolean previousUpdating = isUpdating;
        isUpdating = true;
        atlasNameField.setText(detected);
        isUpdating = previousUpdating;
        config.setAtlasName(detected);
        notifyConfigChanged();
    }

    private String findAtlasFromImage(ImageData<?> imageData) {
        if (imageData == null) {
            return null;
        }
        for (var annotation : imageData.getHierarchy().getAnnotationObjects()) {
            if (!(annotation instanceof PathAnnotationObject)) {
                continue;
            }
            String name = annotation.getName();
            // Use exact case-sensitive match for "Root" to align with AtlasManager.search()
            if (!ROOT_ANNOTATION_NAME.equals(name)) {
                continue;
            }
            if (annotation.getPathClass() == null) {
                continue;
            }
            String atlasName = annotation.getPathClass().toString();
            if (atlasName != null && !atlasName.isBlank()) {
                return atlasName;
            }
        }
        return null;
    }

    private String findAtlasFromProject(Project<BufferedImage> project) {
        if (project == null) {
            return null;
        }
        for (ProjectImageEntry<BufferedImage> entry : project.getImageList()) {
            ImageData<BufferedImage> imageData;
            try {
                imageData = entry.readImageData();
            } catch (IOException e) {
                continue;
            }
            try {
                String detected = findAtlasFromImage(imageData);
                if (detected != null) {
                    return detected;
                }
            } finally {
                try {
                    imageData.getServer().close();
                } catch (Exception e) {
                    // Best effort cleanup.
                }
            }
        }
        return null;
    }
}
