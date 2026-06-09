package santediagnostics.resources.controllers;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import santediagnostics.Result;
import santediagnostics.ResultDao;
import santediagnostics.ResultFile;
import santediagnostics.ResultFileDao;
import santediagnostics.Session;
import santediagnostics.User;

/**
 * Controller for result-vault.fxml.
 *
 * Download: copies the file from server path to user's chosen location.
 */
public class ResultVaultController implements Initializable {

    @FXML private TextField searchField;
    @FXML private Label resultCountLabel;
    @FXML private VBox resultCardsPane;

    /* Viewer popup */
    @FXML private StackPane viewerPopup;
    @FXML private FontAwesomeIconView viewerIcon;
    @FXML private Label viewerTestName;
    @FXML private Label viewerDate;
    @FXML private VBox viewerValueBox;
    @FXML private Label viewerValueLabel;
    @FXML private StackPane imagePreviewBox;
    @FXML private ImageView resultImageView;
    @FXML private VBox pdfPreviewBox;
    @FXML private HBox viewerActionRow;
    @FXML private Button viewerOpenBtn;
    @FXML private Button viewerDownloadBtn;

    private List<ResultCardData> allResultCards;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /* ================================================================
       INITIALIZATION
       ================================================================ */

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterCards(newVal));
        loadResults();
    }

    /* ================================================================
       TABLE SETUP
       ================================================================ */

    private void loadResults() {
        int userId = Session.getInstance().getCurrentUser().getId();

        Task<List<Result>> task = new Task<>() {
            @Override
            protected List<Result> call() throws Exception {
                ResultDao dao = new ResultDao();
                List<Result> results = dao.findVerifiedByCustomer(userId);

                // For each result, fetch its attached files
                ResultFileDao fileDao = new ResultFileDao();
                for (Result r : results) {
                    r.setFiles(fileDao.findByResult(r.getId()));
                }
                return results;
            }
        };

        task.setOnSucceeded(e -> {
            List<Result> results = task.getValue();
            allResultCards = new ArrayList<>();

            for (Result r : results) {
                // Determine display value
                String value;
                if ("numeric".equals(r.getResultFormat()) && r.getValueNumeric() != null) {
                    value = r.getValueNumeric().toPlainString();
                } else if (r.getValueText() != null) {
                    value = r.getValueText();
                } else if (!r.getFiles().isEmpty()) {
                    value = r.getFiles().get(0).getOriginalFilename();
                } else {
                    value = "Result available";
                }

                // Get file path if any
                String filePath = r.getFiles().isEmpty() ? null : r.getFiles().get(0).getFilePath();

                allResultCards.add(new ResultCardData(
                    r.getId(), r.getTestName(), r.getResultFormat(),
                    r.getStatus(), value,
                    r.getUploadedAt().toLocalDateTime(), filePath
                ));
            }

            buildCards(allResultCards);
            resultCountLabel.setText(allResultCards.size() + " results");
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            resultCountLabel.setText("Error loading results");
        });

        new Thread(task).start();
    }

    /* ================================================================
       LOAD RECENT TEST ORDERS (bottom table)
       Uses TestRequestDao.findByCustomer — all orders for this customer.
       ================================================================ */

    private void buildCards(List<ResultCardData> results) {
        resultCardsPane.getChildren().clear();
        for (ResultCardData result : results) {
            resultCardsPane.getChildren().add(createResultCard(result));
        }
    }

    private VBox createResultCard(ResultCardData result) {
        String iconName;
        switch (result.format) {
            case "pdf":     iconName = "FILE_PDF_ALT"; break;
            case "image":   iconName = "FILE_IMAGE_ALT"; break;
            case "numeric": iconName = "CALCULATOR"; break;
            default:        iconName = "FILE_TEXT"; break;
        }
        FontAwesomeIconView icon = new FontAwesomeIconView();
        icon.setGlyphName(iconName);
        icon.setSize("20");
        icon.setFill(javafx.scene.paint.Color.web("#16B0A6"));

        Label nameLabel = new Label(result.testName);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #11242A;");
        Label dateLabel = new Label(result.uploadedAt.format(FMT));
        dateLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7E84;");
        VBox nameBox = new VBox(2, nameLabel, dateLabel);

        Label statusPill = new Label(capitalise(result.status));
        statusPill.getStyleClass().add("status-pill");
        statusPill.setStyle("-fx-background-color: #E3F5E1; -fx-text-fill: #3D9A38;");

        Label formatPill = new Label(result.format.toUpperCase());
        formatPill.getStyleClass().add("status-pill");
        formatPill.setStyle("-fx-background-color: #E6F7F6; -fx-text-fill: #16B0A6;");

        Button viewBtn = new Button("View");
        viewBtn.getStyleClass().add("table-action-btn");
        viewBtn.setOnAction(e -> openViewer(result));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(icon, nameBox, spacer, formatPill, statusPill, viewBtn);

        VBox card = new VBox();
        card.setPadding(new Insets(14, 22, 14, 22));
        card.getChildren().add(row);
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; " +
            "-fx-border-color: #D4DEDE; -fx-border-radius: 12; -fx-border-width: 1.2; -fx-cursor: hand;"
        );
        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; " +
            "-fx-border-color: #16B0A6; -fx-border-radius: 12; -fx-border-width: 1.2; -fx-cursor: hand;"
        ));
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12; " +
            "-fx-border-color: #D4DEDE; -fx-border-radius: 12; -fx-border-width: 1.2; -fx-cursor: hand;"
        ));
        card.setOnMouseClicked(e -> openViewer(result));

        return card;
    }

    /* ================================================================
       SEARCH / FILTER
       ================================================================ */

    private void filterCards(String query) {
        if (allResultCards == null) return;
        if (query == null || query.isEmpty()) {
            buildCards(allResultCards);
            resultCountLabel.setText(allResultCards.size() + " results");
            return;
        }
        String lower = query.toLowerCase();
        List<ResultCardData> filtered = new ArrayList<>();
        for (ResultCardData r : allResultCards) {
            if (r.testName.toLowerCase().contains(lower)) filtered.add(r);
        }
        buildCards(filtered);
        resultCountLabel.setText(filtered.size() + " results");
    }

    /* ================================================================
       RESULT VIEWER POPUP
       ================================================================ */

    private void openViewer(ResultCardData result) {
        viewerTestName.setText(result.testName);
        viewerDate.setText("Completed: " + result.uploadedAt.format(FMT));

        // Reset every conditional region; only the relevant one is shown.
        setShown(viewerValueBox,    false);
        setShown(imagePreviewBox,   false);
        setShown(pdfPreviewBox,     false);
        setShown(viewerActionRow,   false);
        resultImageView.setImage(null);

        viewerDownloadBtn.setUserData(result);
        viewerOpenBtn.setUserData(result);

        switch (result.format) {
            case "image":
                viewerIcon.setGlyphName("FILE_IMAGE_ALT");
                if (result.filePath != null && new File(result.filePath).exists()) {
                    resultImageView.setImage(
                            new Image(new File(result.filePath).toURI().toString()));
                    setShown(imagePreviewBox, true);
                    // Hide the "Open PDF" half of the action row for image results;
                    // keep just the secondary Download.
                    setShown(viewerActionRow, true);
                    viewerOpenBtn.setVisible(false);
                    viewerOpenBtn.setManaged(false);
                } else {
                    viewerValueLabel.setText("Image is unavailable.");
                    setShown(viewerValueBox, true);
                }
                break;

            case "pdf":
                viewerIcon.setGlyphName("FILE_PDF_ALT");
                if (result.filePath != null && new File(result.filePath).exists()) {
                    setShown(pdfPreviewBox, true);
                    setShown(viewerActionRow, true);
                    viewerOpenBtn.setVisible(true);
                    viewerOpenBtn.setManaged(true);
                } else {
                    viewerValueLabel.setText("PDF is unavailable.");
                    setShown(viewerValueBox, true);
                }
                break;

            case "numeric":
                viewerIcon.setGlyphName("CALCULATOR");
                viewerValueLabel.setText(result.value != null ? result.value : "No result data");
                setShown(viewerValueBox, true);
                break;

            default:
                viewerIcon.setGlyphName("FILE_TEXT");
                viewerValueLabel.setText(result.value != null ? result.value : "No result data");
                setShown(viewerValueBox, true);
                break;
        }

        viewerPopup.setVisible(true);
        viewerPopup.setManaged(true);
    }

    private void setShown(javafx.scene.Node node, boolean shown) {
        if (node == null) return;
        node.setVisible(shown);
        node.setManaged(shown);
    }

    @FXML
    private void handleCloseViewer() {
        viewerPopup.setVisible(false);
        viewerPopup.setManaged(false);
        resultImageView.setImage(null);   // free the image
    }

    /**
     * Opens the result file using the OS default viewer (PDFs open in the
     * system PDF reader). Falls back to a helpful error if the platform
     * does not support Desktop.open().
     */
    @FXML
    private void handleOpenInSystem() {
        ResultCardData result = (ResultCardData) viewerOpenBtn.getUserData();
        if (result == null || result.filePath == null) return;
        File f = new File(result.filePath);
        if (!f.exists()) {
            showError("The file is no longer available on the server.");
            return;
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(f);
            } else {
                showError("Your system cannot open files directly. "
                        + "Please use Download instead.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Could not open the file: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, javafx.scene.control.ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /* ================================================================
       DOWNLOAD — copies the file from the server path to the
       user's chosen location on their system.
       ================================================================ */

    @FXML
    private void handleDownload() {
        ResultCardData result = (ResultCardData) viewerDownloadBtn.getUserData();
        if (result == null || result.filePath == null) {
            showError("There is no downloadable file for this result.");
            return;
        }
        File source = new File(result.filePath);
        if (!source.exists()) {
            showError("The file is no longer available on the server.");
            return;
        }

        // Let the user choose where to save
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Result Report");

        // Suggested name: prefer the original filename (carries the real
        // extension). Fall back to "<test>.<ext-from-source>".
        if (result.value != null && !result.value.isEmpty() && result.value.contains(".")) {
            chooser.setInitialFileName(result.value);
        } else {
            String name = result.filePath;
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 ? name.substring(dot) : "";
            chooser.setInitialFileName(result.testName.replace(" ", "_") + ext);
        }

        File saveLocation = chooser.showSaveDialog(viewerPopup.getScene().getWindow());
        if (saveLocation == null) return; // user cancelled

        // Copy file on a background thread
        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Path source = Path.of(result.filePath);
                Path target = saveLocation.toPath();
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return null;
            }
        };

        downloadTask.setOnSucceeded(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Download Complete");
            alert.setHeaderText(null);
            alert.setContentText("Result saved to:\n" + saveLocation.getAbsolutePath());
            alert.showAndWait();
        });

        downloadTask.setOnFailed(e -> {
            downloadTask.getException().printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Download Failed");
            alert.setHeaderText(null);
            alert.setContentText("Could not save the file. Please try again.");
            alert.showAndWait();
        });

        new Thread(downloadTask).start();
    }

    /* ================================================================
       HELPERS
       ================================================================ */

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /* ================================================================
       MODEL CLASSES
       ================================================================ */

    /** Display data for a verified result card. */
    public static class ResultCardData {
        public final int id;
        public final String testName;
        public final String format;
        public final String status;
        public final String value;
        public final LocalDateTime uploadedAt;
        public final String filePath;

        public ResultCardData(int id, String testName, String format,
                              String status, String value,
                              LocalDateTime uploadedAt, String filePath) {
            this.id = id;
            this.testName = testName;
            this.format = format;
            this.status = status;
            this.value = value;
            this.uploadedAt = uploadedAt;
            this.filePath = filePath;
        }
    }

}
