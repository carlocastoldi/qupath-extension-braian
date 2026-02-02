// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.gui;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import qupath.ext.braian.config.AutoThresholdParmameters;
import qupath.ext.braian.config.ChannelClassifierConfig;
import qupath.ext.braian.config.ChannelDetectionsConfig;
import qupath.ext.braian.config.PixelClassifierConfig;
import qupath.ext.braian.config.WatershedCellDetectionConfig;
import qupath.ext.braian.ImageChannelTools;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * UI component representing a single channel configuration.
 * <p>
 * This card binds controls to a {@link ChannelDetectionsConfig} and emits
 * callbacks when the
 * configuration changes.
 */
public class ChannelCard extends VBox {
    private final ChannelDetectionsConfig config;
    private final WatershedCellDetectionConfig params;
    private final Stage owner;
    private final Supplier<Path> configRootSupplier;
    private final Supplier<Path> projectDirSupplier;
    private final Runnable onConfigChanged;
    private final Runnable onChannelNameChanged;
    private final BooleanSupplier isUpdatingSupplier;
    private final Supplier<ImageData<?>> imageDataSupplier;
    private final VBox classifierList = new VBox(6);
    private final VBox pixelClassifierList = new VBox(6);
    private final List<ChannelClassifierConfig> classifiers;
    private final List<PixelClassifierConfig> pixelClassifiers;
    private Runnable onRemove = () -> {
    };

    private static final String HELP_URL_THRESHOLD = "https://silvalab.codeberg.page/BraiAn/image-analysis/#:~:text=Automatic%20threshold";

    private static final String TOOLTIP_PIXEL_SIZE = "Choose pixel size at which detection will be performed - higher values are likely to be faster, but may be less accurate; set <= 0 to use the full image resolution";
    private static final String TOOLTIP_BACKGROUND_RADIUS = "Radius for background estimation, should be > the largest nucleus radius, or <= 0 to turn off background subtraction";
    private static final String TOOLTIP_BACKGROUND_RECONSTRUCTION = "Use opening-by-reconstruction for background estimation (default is 'true'). Opening by reconstruction tends to give a 'better' background estimate, because it incorporates more information across the image tile used for cell detection. However, in some cases (e.g. images with prominent folds, background staining, or other artefacts) this can cause problems, with the background estimate varying substantially between tiles. Opening by reconstruction was always used in QuPath before v0.4.0, but now it is optional.";
    private static final String TOOLTIP_MEDIAN_RADIUS = "Radius of median filter used to reduce image texture (optional)";
    private static final String TOOLTIP_SIGMA = "Sigma value for Gaussian filter used to reduce noise; increasing the value stops nuclei being fragmented, but may reduce the accuracy of boundaries";
    private static final String TOOLTIP_MIN_AREA = "Detected nuclei with an area < minimum area will be discarded";
    private static final String TOOLTIP_MAX_AREA = "Detected nuclei with an area > maximum area will be discarded";
    private static final String TOOLTIP_THRESHOLD = "Intensity threshold - detected nuclei must have a mean intensity >= threshold";
    private static final String TOOLTIP_HIST_RESOLUTION = "Resolution level at which the histogram is computed";
    private static final String TOOLTIP_HIST_SMOOTH = "Size of the window used by the moving average to smooth the histogram";
    private static final String TOOLTIP_HIST_PEAK = "Amount of prominence from the surrounding values in the histogram for a local maximum to be considered a 'peak'";
    private static final String TOOLTIP_HIST_NPEAK = "n-th peak to use as threshold (starts from 1)";
    private static final String TOOLTIP_WATERSHED = "Split merged detected nuclei based on shape ('roundness')";
    private static final String TOOLTIP_CELL_EXPANSION = "Amount by which to expand detected nuclei to approximate the full cell area";
    private static final String TOOLTIP_INCLUDE_NUCLEI = "If cell expansion is used, optionally include/exclude the nuclei within the detected cells";
    private static final String TOOLTIP_SMOOTH_BOUNDARIES = "Smooth the detected nucleus/cell boundaries";
    private static final String TOOLTIP_MAKE_MEASUREMENTS = "Add default shape & intensity measurements during detection";
    private static final String TOOLTIP_CLASSIFIER_REGIONS = "List of the annotation names for which the classifier is applied";
    private static final String TOOLTIP_PIXEL_CLASSIFIER_REGIONS = "List of atlas regions for which the pixel classifier is applied";

    private static final String BADGE_GLOBAL_TEXT = "🌍 Global";
    private static final String BADGE_PARTIAL_TEXT = "🎯 Partial";
    private static final String BADGE_GLOBAL_STYLE = "-fx-background-color: #E6F4EA; -fx-text-fill: #137333; -fx-background-radius: 8; -fx-padding: 2 8; -fx-font-size: 10px; -fx-font-weight: bold;";
    private static final String BADGE_PARTIAL_STYLE = "-fx-background-color: #E8F0FE; -fx-text-fill: #1A73E8; -fx-background-radius: 8; -fx-padding: 2 8; -fx-font-size: 10px; -fx-font-weight: bold;";

    /**
     * Creates a channel card.
     *
     * @param config               the per-channel configuration to edit
     * @param availableChannels    list of channel names that can be selected
     * @param owner                owning stage used for dialogs
     * @param configRootSupplier   supplier for the directory containing
     *                             configuration resources
     * @param projectDirSupplier   supplier for the current project directory
     * @param onConfigChanged      callback invoked when configuration is changed
     * @param onChannelNameChanged callback invoked when the channel name is changed
     * @param isUpdatingSupplier   supplier indicating whether UI is being updated
     *                             programmatically
     * @param imageDataSupplier    supplier for the current ImageData
     */
    public ChannelCard(ChannelDetectionsConfig config,
            List<String> availableChannels,
            Stage owner,
            Supplier<Path> configRootSupplier,
            Supplier<Path> projectDirSupplier,
            Runnable onConfigChanged,
            Runnable onChannelNameChanged,
            BooleanSupplier isUpdatingSupplier,
            Supplier<ImageData<?>> imageDataSupplier) {
        this.config = config;
        this.params = config.getParameters();
        this.owner = owner;
        this.configRootSupplier = configRootSupplier;
        this.projectDirSupplier = projectDirSupplier;
        this.onConfigChanged = onConfigChanged;
        this.onChannelNameChanged = onChannelNameChanged;
        this.isUpdatingSupplier = isUpdatingSupplier;
        this.imageDataSupplier = imageDataSupplier;
        this.classifiers = new ArrayList<>(Optional.ofNullable(config.getClassifiers()).orElse(List.of()));
        this.pixelClassifiers = new ArrayList<>(Optional.ofNullable(config.getPixelClassifiers()).orElse(List.of()));

        setSpacing(12);
        setPadding(new Insets(12));

        ComboBox<String> channelName = new ComboBox<>(FXCollections.observableArrayList(availableChannels));
        channelName.setEditable(true);
        channelName.setPromptText("Channel name");
        channelName.setValue(config.getName());
        channelName.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            String name = value != null ? value.trim() : null;
            if (name != null && name.isEmpty()) {
                name = null;
            }
            config.setName(name);
            if (name != null) {
                config.setClassifiers(new ArrayList<>(classifiers));
            }
            notifyChannelNameChanged();
            notifyConfigChanged();
        });

        Spinner<Integer> channelIdSpinner = createIntegerSpinner(1, 16, config.getInputChannelID(), 1);
        channelIdSpinner.setPrefWidth(60);
        channelIdSpinner.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            config.setInputChannelID(value);
            notifyConfigChanged();
        });

        Button removeButton = new Button("Remove");
        removeButton.setOnAction(event -> onRemove.run());

        HBox header = new HBox(8, new Label("Source Ch"), channelIdSpinner, new Label("-> Name"), channelName,
                removeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(channelName, Priority.ALWAYS);

        CheckBox enableCellDetection = new CheckBox("Enable Cell Detection");
        enableCellDetection.setSelected(config.isEnableCellDetection());
        enableCellDetection.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            config.setEnableCellDetection(selected);
            notifyConfigChanged();
        });

        CheckBox enablePixelClassification = new CheckBox("Enable Pixel Classification");
        enablePixelClassification.setSelected(config.isEnablePixelClassification());
        enablePixelClassification.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            config.setEnablePixelClassification(selected);
            notifyConfigChanged();
        });

        ToggleGroup thresholdGroup = new ToggleGroup();
        RadioButton autoThreshold = new RadioButton("Auto");
        RadioButton manualThreshold = new RadioButton("Manual");
        autoThreshold.setToggleGroup(thresholdGroup);
        manualThreshold.setToggleGroup(thresholdGroup);

        Spinner<Double> thresholdSpinner = createDoubleSpinner(0, 65535, 1, params.getThreshold());
        thresholdSpinner.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setThreshold(value);
            notifyConfigChanged();
        });
        addTooltip(thresholdSpinner, TOOLTIP_THRESHOLD);

        Label manualDefault = new Label("[default: 100]");
        manualDefault.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");
        HBox thresholdControlBox = new HBox(8, thresholdSpinner, manualDefault);
        thresholdControlBox.setAlignment(Pos.CENTER_LEFT);
        VBox manualBox = new VBox(6, new Label("Threshold"), thresholdControlBox);

        VBox autoBox = buildAutoThresholdBox();

        boolean autoEnabled = params.getHistogramThreshold() != null;
        autoThreshold.setSelected(autoEnabled);
        manualThreshold.setSelected(!autoEnabled);

        autoBox.managedProperty().bind(autoThreshold.selectedProperty());
        autoBox.visibleProperty().bind(autoThreshold.selectedProperty());
        manualBox.managedProperty().bind(manualThreshold.selectedProperty());
        manualBox.visibleProperty().bind(manualThreshold.selectedProperty());

        autoThreshold.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            if (selected) {
                ensureAutoThreshold();
            } else {
                params.setHistogramThreshold(null);
            }
            notifyConfigChanged();
        });

        Hyperlink thresholdHelp = buildHelpLink(HELP_URL_THRESHOLD);
        HBox thresholdMode = new HBox(12, new Label("Threshold"), autoThreshold, manualThreshold, thresholdHelp);
        thresholdMode.setAlignment(Pos.CENTER_LEFT);

        GridPane standardGrid = new GridPane();
        standardGrid.setHgap(12);
        standardGrid.setVgap(8);
        standardGrid.add(thresholdMode, 0, 0, 2, 1);
        standardGrid.add(manualBox, 0, 1, 2, 1);
        standardGrid.add(autoBox, 0, 2, 2, 1);

        Spinner<Double> pixelSize = createDoubleSpinner(0.1, 10.0, 0.1, params.getRequestedPixelSizeMicrons());
        pixelSize.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setRequestedPixelSizeMicrons(value);
            notifyConfigChanged();
        });
        addTooltip(pixelSize, TOOLTIP_PIXEL_SIZE);
        addGridRowWithDefault(standardGrid, 0, 3, "Pixel size (µm)", "0.5", pixelSize);

        Spinner<Double> minArea = createDoubleSpinner(0.0, 5000.0, 1.0, params.getMinAreaMicrons());
        minArea.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setMinAreaMicrons(value);
            notifyConfigChanged();
        });
        addTooltip(minArea, TOOLTIP_MIN_AREA);
        Spinner<Double> maxArea = createDoubleSpinner(0.0, 10000.0, 1.0, params.getMaxAreaMicrons());
        maxArea.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setMaxAreaMicrons(value);
            notifyConfigChanged();
        });
        addTooltip(maxArea, TOOLTIP_MAX_AREA);
        addGridRowWithDefault(standardGrid, 0, 4, "Min area (µm²)", "10", minArea);
        addGridRowWithDefault(standardGrid, 0, 5, "Max area (µm²)", "400", maxArea);

        VBox standardSection = new VBox(8, new Label("Standard parameters"), standardGrid);

        TitledPane advancedPane = new TitledPane("Advanced parameters", buildAdvancedSection());
        advancedPane.setExpanded(false);

        TitledPane classifiersPane = new TitledPane("Classifiers", buildClassifierSection(channelName));
        classifiersPane.setExpanded(false);

        VBox cellDetectionSection = new VBox(10, standardSection, advancedPane, classifiersPane);
        cellDetectionSection.managedProperty().bind(enableCellDetection.selectedProperty());
        cellDetectionSection.visibleProperty().bind(enableCellDetection.selectedProperty());

        TitledPane pixelClassifierPane = new TitledPane("Pixel Classifiers", buildPixelClassifierSection(channelName));
        pixelClassifierPane.setExpanded(false);
        pixelClassifierPane.managedProperty().bind(enablePixelClassification.selectedProperty());
        pixelClassifierPane.visibleProperty().bind(enablePixelClassification.selectedProperty());

        HBox modeCheckboxes = new HBox(24, enableCellDetection, enablePixelClassification);
        modeCheckboxes.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(header, modeCheckboxes, cellDetectionSection, pixelClassifierPane);
    }

    /**
     * Sets the callback invoked when the user clicks the remove button.
     *
     * @param onRemove callback invoked when removing the channel; if null a no-op
     *                 is used
     */
    public void setOnRemove(Runnable onRemove) {
        this.onRemove = Objects.requireNonNullElse(onRemove, () -> {
        });
    }

    private void addGridRow(GridPane grid, int col, int row, String label, Node control) {
        Label lbl = new Label(label);
        grid.add(lbl, col, row);
        grid.add(control, col + 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private void addGridRowWithDefault(GridPane grid, int col, int row, String label, String defaultVal, Node control) {
        Label lbl = new Label(label);
        Label defLabel = new Label("[default: " + defaultVal + "]");
        defLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");
        HBox controlBox = new HBox(8, control, defLabel);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        grid.add(lbl, col, row);
        grid.add(controlBox, col + 1, row);
        GridPane.setHgrow(controlBox, Priority.ALWAYS);
    }

    private void addTooltip(Node node, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(360);
        Tooltip.install(node, tooltip);
    }

    private Hyperlink buildHelpLink(String url) {
        Hyperlink link = new Hyperlink("(?)");
        link.setOnAction(event -> QuPathGUI.openInBrowser(url));
        link.setFocusTraversable(false);
        link.setTooltip(new Tooltip("Open documentation"));
        return link;
    }

    private Label buildSectionHeader(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private void updateClassifierBadge(Label badge, List<String> annotations) {
        boolean isGlobal = annotations == null || annotations.isEmpty();
        if (isGlobal) {
            badge.setText(BADGE_GLOBAL_TEXT);
            badge.setStyle(BADGE_GLOBAL_STYLE);
        } else {
            badge.setText(BADGE_PARTIAL_TEXT);
            badge.setStyle(BADGE_PARTIAL_STYLE);
        }
    }

    private Spinner<Double> createDoubleSpinner(double min, double max, double step, double value) {
        Spinner<Double> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, value, step));
        spinner.setEditable(true);
        return spinner;
    }

    private Spinner<Integer> createIntegerSpinner(int min, int max, int value, int step) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value, step));
        spinner.setEditable(true);
        return spinner;
    }

    private AutoThresholdParmameters ensureAutoThreshold() {
        AutoThresholdParmameters paramsAuto = params.getHistogramThreshold();
        if (paramsAuto == null) {
            paramsAuto = new AutoThresholdParmameters();
            params.setHistogramThreshold(paramsAuto);
        }
        return paramsAuto;
    }

    private VBox buildAutoThresholdBox() {
        AutoThresholdParmameters autoParams = params.getHistogramThreshold();
        if (autoParams == null) {
            autoParams = new AutoThresholdParmameters();
        }

        Spinner<Integer> resolutionLevel = createIntegerSpinner(0, 10, autoParams.getResolutionLevel(), 1);
        resolutionLevel.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            ensureAutoThreshold().setResolutionLevel(value);
            notifyConfigChanged();
        });
        addTooltip(resolutionLevel, TOOLTIP_HIST_RESOLUTION);

        Spinner<Integer> smoothWindowSize = createIntegerSpinner(1, 100, autoParams.getSmoothWindowSize(), 1);
        smoothWindowSize.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            ensureAutoThreshold().setSmoothWindowSize(value);
            notifyConfigChanged();
        });
        addTooltip(smoothWindowSize, TOOLTIP_HIST_SMOOTH);

        Spinner<Double> peakProminence = createDoubleSpinner(1, 10000, 10, autoParams.getPeakProminence());
        peakProminence.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            ensureAutoThreshold().setPeakProminence(value);
            notifyConfigChanged();
        });
        addTooltip(peakProminence, TOOLTIP_HIST_PEAK);

        int nPeakValue = Math.max(1, autoParams.getnPeak() + 1);
        Spinner<Integer> nPeak = createIntegerSpinner(1, 10, nPeakValue, 1);
        nPeak.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            ensureAutoThreshold().setnPeak(value);
            notifyConfigChanged();
        });
        addTooltip(nPeak, TOOLTIP_HIST_NPEAK);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        addGridRowWithDefault(grid, 0, 0, "Resolution level", "4", resolutionLevel);
        addGridRowWithDefault(grid, 0, 1, "Smooth window", "15", smoothWindowSize);
        addGridRowWithDefault(grid, 0, 2, "Peak prominence", "100", peakProminence);
        addGridRowWithDefault(grid, 0, 3, "Peak index", "1", nPeak);

        Label thresholdResultLabel = new Label();
        thresholdResultLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1A73E8;");

        Button findThresholdButton = new Button("Find Threshold");
        findThresholdButton.setTooltip(new Tooltip(
                "Calculate the automatic threshold for the current image using the parameters above."));
        findThresholdButton.setOnAction(event -> {
            String channelName = config.getName();
            if (channelName == null || channelName.isBlank()) {
                Dialogs.showErrorMessage("Find Threshold", "Please select a channel name first.");
                return;
            }
            ImageData<?> imageData = imageDataSupplier.get();
            if (imageData == null) {
                Dialogs.showErrorMessage("Find Threshold", "No image is currently open.");
                return;
            }
            AutoThresholdParmameters currentParams = ensureAutoThreshold();
            try {
                @SuppressWarnings("unchecked")
                ImageData<java.awt.image.BufferedImage> typedImageData = (ImageData<java.awt.image.BufferedImage>) imageData;
                ImageChannelTools channel = new ImageChannelTools(channelName, typedImageData);
                int threshold = WatershedCellDetectionConfig.findThreshold(channel, currentParams);
                thresholdResultLabel.setText("Threshold: " + threshold);
            } catch (Exception e) {
                Dialogs.showErrorMessage("Find Threshold",
                        "Could not compute threshold: " + e.getMessage());
                thresholdResultLabel.setText("");
            }
        });

        HBox thresholdRow = new HBox(10, findThresholdButton, thresholdResultLabel);
        thresholdRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(8, new Label("Auto-threshold parameters"), grid, thresholdRow);
    }

    private VBox buildAdvancedSection() {
        CheckBox backgroundReconstruction = new CheckBox("Background by reconstruction");
        backgroundReconstruction.setSelected(params.isBackgroundByReconstruction());
        backgroundReconstruction.selectedProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setBackgroundByReconstruction(value);
            notifyConfigChanged();
        });
        addTooltip(backgroundReconstruction, TOOLTIP_BACKGROUND_RECONSTRUCTION);

        Spinner<Double> backgroundRadius = createDoubleSpinner(0.0, 100.0, 1.0, params.getBackgroundRadiusMicrons());
        backgroundRadius.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setBackgroundRadiusMicrons(value);
            notifyConfigChanged();
        });
        addTooltip(backgroundRadius, TOOLTIP_BACKGROUND_RADIUS);

        Spinner<Double> sigma = createDoubleSpinner(0.0, 5.0, 0.5, params.getSigmaMicrons());
        sigma.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setSigmaMicrons(value);
            notifyConfigChanged();
        });
        addTooltip(sigma, TOOLTIP_SIGMA);

        Spinner<Double> medianRadius = createDoubleSpinner(0.0, 20.0, 0.5, params.getMedianRadiusMicrons());
        medianRadius.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setMedianRadiusMicrons(value);
            notifyConfigChanged();
        });
        addTooltip(medianRadius, TOOLTIP_MEDIAN_RADIUS);

        CheckBox watershed = new CheckBox("Watershed split");
        watershed.setSelected(params.isWatershedPostProcess());
        watershed.selectedProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setWatershedPostProcess(value);
            notifyConfigChanged();
        });
        addTooltip(watershed, TOOLTIP_WATERSHED);

        Spinner<Double> cellExpansion = createDoubleSpinner(0.0, 50.0, 1.0, params.getCellExpansionMicrons());
        cellExpansion.valueProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setCellExpansionMicrons(value);
            notifyConfigChanged();
        });
        addTooltip(cellExpansion, TOOLTIP_CELL_EXPANSION);

        CheckBox includeNuclei = new CheckBox("Include nuclei");
        includeNuclei.setSelected(params.isIncludeNuclei());
        includeNuclei.selectedProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setIncludeNuclei(value);
            notifyConfigChanged();
        });
        addTooltip(includeNuclei, TOOLTIP_INCLUDE_NUCLEI);

        CheckBox smoothBoundaries = new CheckBox("Smooth boundaries");
        smoothBoundaries.setSelected(params.isSmoothBoundaries());
        smoothBoundaries.selectedProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setSmoothBoundaries(value);
            notifyConfigChanged();
        });
        addTooltip(smoothBoundaries, TOOLTIP_SMOOTH_BOUNDARIES);

        CheckBox makeMeasurements = new CheckBox("Make measurements");
        makeMeasurements.setSelected(params.isMakeMeasurements());
        makeMeasurements.selectedProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            params.setMakeMeasurements(value);
            notifyConfigChanged();
        });
        addTooltip(makeMeasurements, TOOLTIP_MAKE_MEASUREMENTS);

        GridPane preProcessingGrid = new GridPane();
        preProcessingGrid.setHgap(12);
        preProcessingGrid.setVgap(8);
        addGridRowWithDefault(preProcessingGrid, 0, 0, "Median radius (µm)", "0", medianRadius);
        addGridRowWithDefault(preProcessingGrid, 0, 1, "Sigma (µm)", "1.5", sigma);
        addGridRowWithDefault(preProcessingGrid, 0, 2, "Background radius (µm)", "8", backgroundRadius);

        VBox detectionLogicBox = new VBox(6, backgroundReconstruction, watershed);

        GridPane geometryGrid = new GridPane();
        geometryGrid.setHgap(12);
        geometryGrid.setVgap(8);
        addGridRowWithDefault(geometryGrid, 0, 0, "Cell expansion (µm)", "5", cellExpansion);

        VBox box = new VBox(10,
                buildSectionHeader("Pre-processing"), new Separator(), preProcessingGrid,
                buildSectionHeader("Detection Logic"), new Separator(), detectionLogicBox,
                buildSectionHeader("Cell Geometry"), new Separator(), geometryGrid, includeNuclei, smoothBoundaries,
                buildSectionHeader("Output"), new Separator(), makeMeasurements);
        return box;
    }

    private VBox buildClassifierSection(ComboBox<String> channelName) {
        classifierList.getChildren().clear();
        for (ChannelClassifierConfig classifier : classifiers) {
            classifierList.getChildren().add(buildClassifierRow(classifier));
        }

        Button addClassifier = new Button("+ Add Classifier");
        addClassifier.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            String name = channelName.getValue();
            return name == null || name.isBlank();
        }, channelName.valueProperty()));
        addClassifier.setOnAction(event -> addClassifierFromChooser());

        VBox section = new VBox(8, classifierList, addClassifier);
        return section;
    }

    private VBox buildPixelClassifierSection(ComboBox<String> channelName) {
        pixelClassifierList.getChildren().clear();
        for (PixelClassifierConfig classifier : pixelClassifiers) {
            pixelClassifierList.getChildren().add(buildPixelClassifierRow(classifier));
        }

        Button addClassifier = new Button("+ Add Pixel Classifier");
        addClassifier.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            String name = channelName.getValue();
            return name == null || name.isBlank();
        }, channelName.valueProperty()));
        addClassifier.setOnAction(event -> addPixelClassifierCard());

        VBox section = new VBox(8, pixelClassifierList, addClassifier);
        return section;
    }

    private Node buildClassifierRow(ChannelClassifierConfig classifier) {
        String baseName = classifier.getName();
        String fileName = baseName == null || baseName.isBlank() ? "(unnamed).json" : baseName + ".json";
        Label nameLabel = new Label(fileName);
        nameLabel.setStyle("-fx-font-weight: bold;");
        nameLabel.setMinWidth(160);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Label badge = new Label();
        updateClassifierBadge(badge, classifier.getAnnotationsToClassify());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button remove = new Button("Remove");
        HBox header = new HBox(8, nameLabel, badge, spacer, remove);
        header.setAlignment(Pos.CENTER_LEFT);

        TextField annotations = new TextField(formatAnnotations(classifier.getAnnotationsToClassify()));
        annotations.setPromptText("Restrict to regions (optional)");
        addTooltip(annotations, TOOLTIP_CLASSIFIER_REGIONS);
        annotations.textProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            List<String> parsed = parseAnnotations(value);
            classifier.setAnnotationsToClassify(parsed.isEmpty() ? null : parsed);
            updateClassifierBadge(badge, classifier.getAnnotationsToClassify());
            config.setClassifiers(new ArrayList<>(classifiers));
            notifyConfigChanged();
        });

        VBox card = new VBox(8, header, annotations);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: -fx-control-inner-background;"
                + "-fx-background-radius: 6;"
                + "-fx-border-color: -fx-box-border;"
                + "-fx-border-radius: 6;");

        remove.setOnAction(event -> {
            classifiers.remove(classifier);
            config.setClassifiers(new ArrayList<>(classifiers));
            classifierList.getChildren().remove(card);
            notifyConfigChanged();
        });

        return card;
    }

    private Node buildPixelClassifierRow(PixelClassifierConfig classifier) {
        TextField classifierField = new TextField();
        classifierField.setPromptText("Select a .json classifier");
        classifierField.setEditable(false);
        classifierField.setText(formatPixelClassifierName(classifier.getClassifierName()));

        Label badge = new Label();
        updateClassifierBadge(badge, classifier.getRegionFilter());

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(event -> choosePixelClassifier(classifier, classifierField));

        HBox classifierRow = new HBox(8, new Label("Classifier"), classifierField, badge, browseButton);
        classifierRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(classifierField, Priority.ALWAYS);

        TextField measurementField = new TextField();
        measurementField.setPromptText("Measurement ID (e.g. red_projections)");
        measurementField.setText(Optional.ofNullable(classifier.getMeasurementId()).orElse(""));
        measurementField.textProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            String trimmed = value != null ? value.trim() : "";
            classifier.setMeasurementId(trimmed.isEmpty() ? null : trimmed);
            notifyConfigChanged();
        });

        TextField regionField = new TextField(formatRegionFilter(classifier.getRegionFilter()));
        regionField.setPromptText("Restrict to regions (optional)");
        addTooltip(regionField, TOOLTIP_PIXEL_CLASSIFIER_REGIONS);
        regionField.textProperty().addListener((obs, oldValue, value) -> {
            if (isUpdatingSupplier.getAsBoolean()) {
                return;
            }
            List<String> parsed = parseRegionFilter(value);
            classifier.setRegionFilter(parsed.isEmpty() ? null : parsed);
            updateClassifierBadge(badge, classifier.getRegionFilter());
            config.setPixelClassifiers(new ArrayList<>(pixelClassifiers));
            notifyConfigChanged();
        });

        Button removeButton = new Button("Remove");

        HBox measurementRow = new HBox(8, new Label("Measurement"), measurementField, removeButton);
        measurementRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(measurementField, Priority.ALWAYS);

        HBox regionRow = new HBox(8, new Label("Region Filter"), regionField);
        regionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(regionField, Priority.ALWAYS);

        VBox card = new VBox(8, classifierRow, measurementRow, regionRow);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: -fx-control-inner-background;"
                + "-fx-background-radius: 6;"
                + "-fx-border-color: -fx-box-border;"
                + "-fx-border-radius: 6;");

        removeButton.setOnAction(event -> {
            pixelClassifiers.remove(classifier);
            config.setPixelClassifiers(new ArrayList<>(pixelClassifiers));
            pixelClassifierList.getChildren().remove(card);
            notifyConfigChanged();
        });

        return card;
    }

    private void addClassifierFromChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select classifier");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("QuPath classifier", "*.json"));
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) {
            return;
        }
        Path selectedPath = selected.toPath();
        Path targetDir = resolveClassifierTargetDir();
        if (targetDir == null) {
            Dialogs.showErrorMessage("BraiAnDetect", "No project directory is available for classifier storage.");
            return;
        }

        if (!isUnderAllowedRoot(selectedPath)) {
            boolean copy = Dialogs.showConfirmDialog(
                    "Copy classifier",
                    "Copy classifier to " + targetDir
                            + "? BraiAn requires classifiers to be stored in the project or its parent folder.");
            if (!copy) {
                return;
            }
            Path targetPath = targetDir.resolve(selected.getName());
            try {
                Files.copy(selectedPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Dialogs.showErrorMessage("BraiAnDetect", "Failed to copy classifier: " + e.getMessage());
                return;
            }
            selectedPath = targetPath;
        }

        ChannelClassifierConfig classifier = new ChannelClassifierConfig();
        classifier.setName(stripJsonExtension(selectedPath.getFileName().toString()));
        classifiers.add(classifier);
        config.setClassifiers(new ArrayList<>(classifiers));
        classifierList.getChildren().add(buildClassifierRow(classifier));
        notifyConfigChanged();
    }

    private void addPixelClassifierCard() {
        PixelClassifierConfig classifier = new PixelClassifierConfig();
        pixelClassifiers.add(classifier);
        config.setPixelClassifiers(new ArrayList<>(pixelClassifiers));
        pixelClassifierList.getChildren().add(buildPixelClassifierRow(classifier));
        notifyConfigChanged();
    }

    private void choosePixelClassifier(PixelClassifierConfig config, TextField classifierField) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select pixel classifier");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("QuPath classifier", "*.json"));
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) {
            return;
        }
        Path selectedPath = selected.toPath();
        Path targetDir = resolveClassifierTargetDir();
        if (targetDir == null) {
            Dialogs.showErrorMessage("BraiAnDetect", "No project directory is available for classifier storage.");
            return;
        }

        if (!isUnderAllowedRoot(selectedPath)) {
            boolean copy = Dialogs.showConfirmDialog(
                    "Copy classifier",
                    "Copy classifier to " + targetDir
                            + "? BraiAn requires classifiers to be stored in the project or its parent folder.");
            if (!copy) {
                return;
            }
            Path targetPath = targetDir.resolve(selected.getName());
            try {
                Files.copy(selectedPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Dialogs.showErrorMessage("BraiAnDetect", "Failed to copy classifier: " + e.getMessage());
                return;
            }
            selectedPath = targetPath;
        }

        String baseName = stripJsonExtension(selectedPath.getFileName().toString());
        config.setClassifierName(baseName);
        classifierField.setText(baseName + ".json");
        notifyConfigChanged();
    }

    private Path resolveClassifierTargetDir() {
        Path configRoot = configRootSupplier.get();
        if (configRoot != null) {
            return configRoot;
        }
        return projectDirSupplier.get();
    }

    private boolean isUnderAllowedRoot(Path path) {
        for (Path root : resolveClassifierRoots()) {
            if (root != null && path.normalize().startsWith(root.normalize())) {
                return true;
            }
        }
        return false;
    }

    private List<Path> resolveClassifierRoots() {
        List<Path> roots = new ArrayList<>();
        Path configRoot = configRootSupplier.get();
        if (configRoot != null) {
            roots.add(configRoot);
        }
        Path projectDir = projectDirSupplier.get();
        if (projectDir != null && !projectDir.equals(configRoot)) {
            roots.add(projectDir);
        }
        return roots;
    }

    private String stripJsonExtension(String fileName) {
        if (fileName.toLowerCase().endsWith(".json")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    private String formatPixelClassifierName(String classifierName) {
        if (classifierName == null || classifierName.isBlank()) {
            return "";
        }
        return classifierName + ".json";
    }

    private List<String> parseAnnotations(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split(",");
        List<String> results = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                results.add(trimmed);
            }
        }
        return results;
    }

    private List<String> parseRegionFilter(String value) {
        return parseAnnotations(value);
    }

    private String formatAnnotations(List<String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return "";
        }
        return String.join(", ", annotations);
    }

    private String formatRegionFilter(List<String> regionFilter) {
        return formatAnnotations(regionFilter);
    }

    private void notifyConfigChanged() {
        if (!isUpdatingSupplier.getAsBoolean()) {
            onConfigChanged.run();
        }
    }

    private void notifyChannelNameChanged() {
        if (!isUpdatingSupplier.getAsBoolean()) {
            onChannelNameChanged.run();
        }
    }
}
