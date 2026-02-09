// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.gui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.braian.AtlasManager;
import qupath.ext.braian.ExclusionReport;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dialog for reviewing excluded regions across project entries.
 * <p>
 * This dialog displays a table of {@link ExclusionReport} items, allows
 * navigation to an excluded annotation,
 * and supports restoring regions by removing the corresponding
 * {@link AtlasManager#EXCLUDE_CLASSIFICATION} annotation.
 */
public class ExclusionReviewDialog extends Stage {
    private static final Logger logger = LoggerFactory.getLogger(ExclusionReviewDialog.class);
    private static final DecimalFormat DECIMAL_FMT = new DecimalFormat("0.00");

    private final QuPathGUI qupath;
    private final TableView<ExclusionReport> table = new TableView<>();
    private final ObservableList<ExclusionReport> reports;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new review dialog.
     *
     * @param qupath  the QuPath GUI instance
     * @param reports the exclusion reports to display
     */
    public ExclusionReviewDialog(QuPathGUI qupath, List<ExclusionReport> reports) {
        this.qupath = qupath;
        this.reports = FXCollections.observableArrayList(reports);

        setTitle("Excluded Regions Review");
        initOwner(qupath.getStage());
        setWidth(860);
        setHeight(520);

        table.setItems(this.reports);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ExclusionReport, String> imageCol = new TableColumn<>("Image");
        imageCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().imageLabel()));

        TableColumn<ExclusionReport, String> regionCol = new TableColumn<>("Region Name");
        regionCol.setCellValueFactory(data -> {
            String name = data.getValue().regionName();
            if (name == null || name.isBlank()) {
                name = "(unnamed)";
            }
            return new ReadOnlyStringWrapper(name);
        });

        TableColumn<ExclusionReport, String> percentileCol = new TableColumn<>("Percentile");
        percentileCol.setCellValueFactory(data -> {
            double v = data.getValue().percentile();
            if (Double.isNaN(v)) {
                return new ReadOnlyStringWrapper("n/a");
            }
            return new ReadOnlyStringWrapper(DECIMAL_FMT.format(v) + "%");
        });
        percentileCol.setMaxWidth(120);
        percentileCol.setMinWidth(120);
        percentileCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });

        table.getColumns().addAll(imageCol, regionCol, percentileCol);
        table.setRowFactory(tv -> {
            TableRow<ExclusionReport> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    navigateTo(row.getItem());
                }
            });
            return row;
        });

        Label hint = new Label(
                "Double-click a row to navigate. Use 'Restore Selected' to remove the Exclude annotation.");
        hint.setPadding(new Insets(0, 0, 8, 0));

        Button restore = new Button("Restore Selected");
        restore.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        restore.setOnAction(e -> restoreSelected());

        Button close = new Button("Close");
        close.setOnAction(e -> close());

        HBox buttons = new HBox(10, restore, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setTop(hint);
        root.setCenter(table);
        root.setBottom(buttons);
        BorderPane.setMargin(buttons, new Insets(10, 0, 0, 0));

        setScene(new Scene(root));

        setOnHidden(e -> executor.shutdown());
    }

    private void navigateTo(ExclusionReport report) {
        // Check if we're already viewing this image
        ImageData<BufferedImage> current = qupath.getViewer() != null ? qupath.getViewer().getImageData() : null;
        if (current != null) {
            String currentImageName = current.getServerMetadata().getName();
            boolean sameProject = report.projectFile() == null || isCurrentProject(report.projectFile());
            if (sameProject && currentImageName.equals(report.imageName())) {
                // Already viewing this image, just select and center
                Platform.runLater(() -> selectAndCenter(current, report.excludedAnnotationId()));
                return;
            }
        }

        executor.execute(() -> {
            try {
                LoadedImage loaded = loadImageForReport(report);
                if (loaded == null) {
                    return;
                }
                Platform.runLater(() -> {
                    try {
                        qupath.getViewer().setImageData(loaded.imageData);
                        selectAndCenter(loaded.imageData, report.excludedAnnotationId());
                    } catch (IOException e) {
                        Dialogs.showErrorMessage("Open image", resolveErrorMessage(e));
                    }
                });
            } catch (Exception e) {
                logger.error("Navigation failed", e);
                Platform.runLater(() -> Dialogs.showErrorMessage("Navigate", resolveErrorMessage(e)));
            }
        });
    }

    private void restoreSelected() {
        List<ExclusionReport> selected = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            for (ExclusionReport report : selected) {
                try {
                    restoreOne(report);
                    Platform.runLater(() -> reports.remove(report));
                } catch (Exception e) {
                    logger.error("Restore failed", e);
                    Platform.runLater(() -> Dialogs.showErrorMessage("Restore", resolveErrorMessage(e)));
                    break;
                }
            }
        });
    }

    private void restoreOne(ExclusionReport report) throws IOException {
        ImageData<BufferedImage> current = qupath.getViewer() != null ? qupath.getViewer().getImageData() : null;
        if (current != null) {
            Project<BufferedImage> currentProject = qupath.getProject();
            ProjectImageEntry<BufferedImage> currentEntry = currentProject != null ? currentProject.getEntry(current)
                    : null;
            String currentName = currentEntry != null ? currentEntry.getImageName()
                    : current.getServerMetadata().getName();

            boolean sameProject = report.projectFile() == null || isCurrentProject(report.projectFile());
            if (sameProject && currentName.equals(report.imageName())) {
                boolean removed = removeExcludeById(current.getHierarchy(), report.excludedAnnotationId());
                if (removed && currentEntry != null) {
                    currentEntry.saveImageData(current);
                }
                return;
            }
        }

        LoadedImage loaded = loadImageForReport(report);
        if (loaded == null) {
            throw new IOException("Unable to load image for restore");
        }

        boolean removed = removeExcludeById(loaded.imageData.getHierarchy(), report.excludedAnnotationId());
        if (!removed) {
            return;
        }
        loaded.entry.saveImageData(loaded.imageData);
        try {
            loaded.imageData.getServer().close();
        } catch (Exception e) {
            logger.warn("Failed to close image server after restore: {}", e.getMessage());
        }
        try {
            loaded.project.syncChanges();
        } catch (Exception e) {
            logger.warn("Failed to sync project after restore: {}", e.getMessage());
        }
    }

    private void selectAndCenter(ImageData<BufferedImage> imageData, UUID annotationId) {
        if (imageData == null || annotationId == null) {
            return;
        }
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        PathAnnotationObject target = findAnnotationById(hierarchy, annotationId);
        if (target == null) {
            return;
        }
        hierarchy.getSelectionModel().clearSelection();
        hierarchy.getSelectionModel().setSelectedObject(target);
        var roi = target.getROI();
        if (roi != null && qupath.getViewer() != null) {
            qupath.getViewer().setCenterPixelLocation(roi.getCentroidX(), roi.getCentroidY());
        }
    }

    private static boolean removeExcludeById(PathObjectHierarchy hierarchy, UUID annotationId) {
        PathAnnotationObject target = findAnnotationById(hierarchy, annotationId);
        if (target == null) {
            return false;
        }
        if (target.getPathClass() != AtlasManager.EXCLUDE_CLASSIFICATION) {
            return false;
        }
        hierarchy.removeObject(target, true);
        return true;
    }

    private static PathAnnotationObject findAnnotationById(PathObjectHierarchy hierarchy, UUID id) {
        if (hierarchy == null || id == null) {
            return null;
        }
        for (var ann : hierarchy.getAnnotationObjects()) {
            if (id.equals(ann.getID()) && ann instanceof PathAnnotationObject) {
                return (PathAnnotationObject) ann;
            }
        }
        return null;
    }

    private boolean isCurrentProject(Path projectFile) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            return false;
        }
        Path baseDir = Projects.getBaseDirectory(project).toPath();
        return baseDir != null && projectFile.getParent() != null && baseDir.equals(projectFile.getParent());
    }

    private LoadedImage loadImageForReport(ExclusionReport report) throws IOException {
        if (report.projectFile() == null) {
            // Best effort: use current project
            Project<BufferedImage> project = qupath.getProject();
            if (project == null) {
                return null;
            }
            var entry = findEntryByImageName(project, report.imageName());
            if (entry == null) {
                return null;
            }
            ImageData<BufferedImage> imageData = entry.readImageData();
            return new LoadedImage(project, entry, imageData);
        }

        Project<BufferedImage> project = ProjectIO.loadProject(report.projectFile().toFile(), BufferedImage.class);
        FXUtils.runOnApplicationThread(() -> qupath.setProject(project));

        var entry = findEntryByImageName(project, report.imageName());
        if (entry == null) {
            return null;
        }
        ImageData<BufferedImage> imageData = entry.readImageData();
        return new LoadedImage(project, entry, imageData);
    }

    private static ProjectImageEntry<BufferedImage> findEntryByImageName(Project<BufferedImage> project,
            String imageName) {
        if (project == null || imageName == null) {
            return null;
        }
        for (ProjectImageEntry<BufferedImage> entry : project.getImageList()) {
            if (imageName.equals(entry.getImageName())) {
                return entry;
            }
        }
        return null;
    }

    private static String resolveErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Unexpected error. See log for details.";
        }
        return message;
    }

    private record LoadedImage(Project<BufferedImage> project,
            ProjectImageEntry<BufferedImage> entry,
            ImageData<BufferedImage> imageData) {
    }
}
