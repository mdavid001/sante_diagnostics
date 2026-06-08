package santediagnostics.resources.controllers;

import santediagnostics.SceneManager;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import santediagnostics.Result;
import santediagnostics.ResultService;
import santediagnostics.Sample;
import santediagnostics.SampleService;
import santediagnostics.Session;
import santediagnostics.Test;
import santediagnostics.TestRequest;
import santediagnostics.TestRequestService;
import santediagnostics.TestService;
import santediagnostics.User;

/**
 * Controller for result-upload.fxml (Lab Attendant).
 *
 * Two kinds of rows:
 *   - "Awaiting result": a processed sample whose request has no result yet.
 *     The Enter Result action opens a popup that adapts to the test's format
 *     (numeric field / text area / file attachment).
 *   - "Pending verification": a result already uploaded but not verified.
 *     The Edit action allows modifications before verification.
 *     The Verify action releases it to the customer and completes the request.
 *
 * Uploaded files are copied into a local "result_files" folder; the stored
 * path points there.
 */
public class ResultUploadController implements Initializable {

    @FXML private TextField searchField;
    @FXML private javafx.scene.control.ToggleButton filterAll;
    @FXML private javafx.scene.control.ToggleButton filterAwaiting;
    @FXML private javafx.scene.control.ToggleButton filterPending;
    @FXML private Label countLabel;
    @FXML private Button refreshBtn;

    @FXML private TableView<ResultRow> resultTable;
    @FXML private TableColumn<ResultRow, String> colRequestId;
    @FXML private TableColumn<ResultRow, String> colCustomer;
    @FXML private TableColumn<ResultRow, String> colTest;
    @FXML private TableColumn<ResultRow, String> colFormat;
    @FXML private TableColumn<ResultRow, String> colFile;  // NEW
    @FXML private TableColumn<ResultRow, String> colStage;
    @FXML private TableColumn<ResultRow, Void> colAction;

    @FXML private StackPane overlayPane;

    /* result-entry popup */
    @FXML private StackPane resultPopup;
    @FXML private Label resultPopupTitle;
    @FXML private Label resultPopupSub;
    @FXML private VBox numericBox;
    @FXML private TextField numericField;
    @FXML private VBox textBox;
    @FXML private Label textBoxLabel;
    @FXML private TextArea textField;
    @FXML private VBox fileBox;
    @FXML private Button chooseFileBtn;
    @FXML private Label chosenFileLabel;
    @FXML private Label resultError;

    /* verify popup */
    @FXML private StackPane verifyPopup;
    @FXML private Label verifyMessage;
    
    @FXML
    private void handleBackToDashboard() {
        try {
            SceneManager.switchTo("/santediagnostics/resources/views/attendant-dashboard.fxml", false);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not return to dashboard: " + e.getMessage());
        }
    }
    private final ResultService resultService = new ResultService();
    private final SampleService sampleService = new SampleService();
    private final TestRequestService requestService = new TestRequestService();
    private final TestService testService = new TestService();

    /** "all" | "awaiting" | "pending". */
    private String currentFilter = "all";

    private final List<ResultRow> masterRows = new ArrayList<>();

    /** Row currently being acted on in a popup. */
    private ResultRow pendingRow;
    /** File chosen in the entry popup (pdf/image formats). */
    private File chosenFile;

    private static final String FILE_DIR = "result_files";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        setupActionColumn();
        loadQueue();
    }

    /* ================================================================
       COLUMNS
       ================================================================ */

    private void setupColumns() {
        colRequestId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customer"));
        colTest.setCellValueFactory(new PropertyValueFactory<>("test"));
        colFormat.setCellValueFactory(new PropertyValueFactory<>("format"));
        
        // NEW: File column
        colFile.setCellValueFactory(new PropertyValueFactory<>("fileInfo"));
        colFile.setCellFactory(col -> new TableCell<>() {
            private final Label fileLabel = new Label();
            { 
                fileLabel.getStyleClass().add("muted-label");
                fileLabel.setStyle("-fx-font-size: 11px;");
            }
            @Override
            protected void updateItem(String fileInfo, boolean empty) {
                super.updateItem(fileInfo, empty);
                if (empty || fileInfo == null || fileInfo.isEmpty()) {
                    setGraphic(null);
                    return;
                }
                fileLabel.setText(fileInfo);
                setGraphic(fileLabel);
            }
        });

        colStage.setCellValueFactory(new PropertyValueFactory<>("stageLabel"));
        colStage.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("status-pill"); }
            @Override
            protected void updateItem(String stage, boolean empty) {
                super.updateItem(stage, empty);
                if (empty || stage == null) { setGraphic(null); return; }
                pill.setText(stage);
                pill.getStyleClass().removeAll("pill-pending", "pill-processing");
                pill.getStyleClass().add(
                    "Pending verification".equals(stage) ? "pill-processing" : "pill-pending");
                setGraphic(pill);
            }
        });
    }

    /**
     * Action column shows:
     * - For awaiting results: "Enter result" button
     * - For pending verification: "Edit" and "Verify" buttons
     */
    private void setupActionColumn() {
        colAction.setCellFactory(col -> new TableCell<>() {
            private final HBox box = new HBox(8);
            private final Button editBtn = new Button("✎ Edit");
            private final Button verifyBtn = new Button("✓ Verify");
            private final Button enterBtn = new Button("Enter result");
            
            {
                box.setAlignment(Pos.CENTER_LEFT);
                
                // Style the buttons
                editBtn.getStyleClass().add("back-action-btn");
                verifyBtn.getStyleClass().add("table-action-btn");
                enterBtn.getStyleClass().add("table-action-btn");
                
                // Add tooltips
                editBtn.setTooltip(new Tooltip("Edit result before verification"));
                verifyBtn.setTooltip(new Tooltip("Verify and release to customer"));
                enterBtn.setTooltip(new Tooltip("Enter result for this sample"));
                
                // Set actions
                editBtn.setOnAction(e -> {
                    ResultRow row = getTableView().getItems().get(getIndex());
                    if (row != null) openEditPopup(row);
                });
                
                verifyBtn.setOnAction(e -> {
                    ResultRow row = getTableView().getItems().get(getIndex());
                    if (row != null) openVerifyPopup(row);
                });
                
                enterBtn.setOnAction(e -> {
                    ResultRow row = getTableView().getItems().get(getIndex());
                    if (row != null) openResultPopup(row);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableView().getItems().isEmpty()) {
                    setGraphic(null);
                    return;
                }
                
                ResultRow row = getTableView().getItems().get(getIndex());
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                
                box.getChildren().clear();
                
                if (row.stage == Stage.AWAITING) {
                    // No result yet - only "Enter result"
                    box.getChildren().add(enterBtn);
                } else {
                    // Pending verification - both Edit and Verify
                    box.getChildren().addAll(editBtn, verifyBtn);
                }
                
                setGraphic(box);
            }
        });
    }

    /* ================================================================
       DATA LOADING
       ================================================================ */

    private void loadQueue() {
        setBusy(true);

        Task<List<ResultRow>> task = new Task<>() {
            @Override
            protected List<ResultRow> call() throws Exception {
                List<ResultRow> rows = new ArrayList<>();

                // 1) Processed samples whose request has no result yet.
                List<Sample> processed = sampleService.getByStatus(Sample.PROCESSED);
                for (Sample s : processed) {
                    Result existing = resultService.getByRequest(s.getTestRequestId());
                    if (existing == null) {
                        // No result exists yet - show "Awaiting result"
                        Test test = lookupTest(s.getTestRequestId());
                        String format = test == null ? "" : test.getResultFormat();
                        rows.add(ResultRow.awaiting(s, format));
                    } else if (!existing.isVerified()) {
                        // Result exists and is pending, AND sample is still PROCESSED - show it
                        rows.add(ResultRow.pending(existing));
                    }
                    // If result is verified, it's already completed - don't show
                }

                // 2) Also get pending results from the database directly
                // This ensures we catch any pending results that might exist
                List<Result> pending = resultService.getVerificationQueue();
                for (Result r : pending) {
                    // Only add if the sample is actually PROCESSED (double-check)
                    Sample sample = sampleService.getByRequest(r.getTestRequestId());
                    if (sample != null && Sample.PROCESSED.equals(sample.getStatus())) {
                        // Check if already added (avoid duplicates)
                        boolean alreadyAdded = false;
                        for (ResultRow row : rows) {
                            if (row.rawResultId == r.getId()) {
                                alreadyAdded = true;
                                break;
                            }
                        }
                        if (!alreadyAdded) {
                            rows.add(ResultRow.pending(r));
                        }
                    }
                    // If sample is not PROCESSED (was reverted), skip adding it
                }

                return rows;
            }
        };

        task.setOnSucceeded(e -> {
            masterRows.clear();
            masterRows.addAll(task.getValue());
            applyView();
            setBusy(false);
        });

        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not load the queue: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });

        new Thread(task).start();
    }
    
    /** Looks up the test behind a request so we know its result format. */
    private Test lookupTest(int requestId) throws Exception {
        TestRequest req = null;
        for (TestRequest r : requestService.getQueue()) {
            if (r.getId() == requestId) { req = r; break; }
        }
        if (req == null) return null;
        return testService.getTest(req.getTestId());
    }

    private void applyView() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        List<ResultRow> filtered = new ArrayList<>();
        for (ResultRow row : masterRows) {
            if (!matchesFilter(row)) continue;
            if (!query.isEmpty()) {
                boolean hit = row.getCustomer().toLowerCase().contains(query)
                        || row.getTest().toLowerCase().contains(query)
                        || row.getRequestId().toLowerCase().contains(query);
                if (!hit) continue;
            }
            filtered.add(row);
        }
        resultTable.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " item" : " items"));
    }

    private boolean matchesFilter(ResultRow row) {
        switch (currentFilter) {
            case "awaiting": return row.stage == Stage.AWAITING;
            case "pending":  return row.stage == Stage.PENDING;
            default:         return true;
        }
    }

    /* ================================================================
       RESULT ENTRY POPUP (for new results)
       ================================================================ */

    private void openResultPopup(ResultRow row) {
        pendingRow = row;
        chosenFile = null;
        chosenFileLabel.setText("No file chosen");
        chosenFileLabel.setStyle("-fx-text-fill: #6B7E84;");
        numericField.clear();
        textField.clear();
        hideError();

        resultPopupTitle.setText("Enter Result");
        resultPopupSub.setText("Request " + row.getRequestId() + " — "
                + row.getTest() + " for " + row.getCustomer());

        String fmt = row.format == null ? "" : row.format.toLowerCase();
        boolean numeric = Test.FORMAT_NUMERIC.equals(fmt);
        boolean text = Test.FORMAT_TEXT.equals(fmt);

        // Primary input matches the declared format.
        show(numericBox, numeric);
        // Text box: the main input for text tests; an optional interpretation otherwise.
        show(textBox, true);
        textBoxLabel.setText(text ? "Result / interpretation" : "Interpretation (optional)");
        // File box: can be attached to ANY result
        show(fileBox, true);

        showPopup(resultPopup);
    }

    /* ================================================================
       EDIT RESULT POPUP (for pending results)
       ================================================================ */

    private void openEditPopup(ResultRow row) {
        pendingRow = row;
        chosenFile = null;
        chosenFileLabel.setText("No file chosen");
        chosenFileLabel.setStyle("-fx-text-fill: #6B7E84;");
        hideError();
        
        // Load existing result data using getResult() method
        setBusy(true);
        Task<Result> task = new Task<>() {
            @Override
            protected Result call() throws Exception {
                return resultService.getResult(row.rawResultId);
            }
        };
        
        task.setOnSucceeded(e -> {
            Result existing = task.getValue();
            if (existing != null) {
                // Pre-populate fields with existing data
                if (existing.getValueNumeric() != null) {
                    numericField.setText(existing.getValueNumeric().toString());
                } else {
                    numericField.clear();
                }
                
                if (existing.getValueText() != null && !existing.getValueText().isEmpty()) {
                    textField.setText(existing.getValueText());
                } else {
                    textField.clear();
                }
                
                // Show existing file if present
                if (existing.hasFile()) {
                    chosenFileLabel.setText("Current: " + existing.getFirstFileName());
                    chosenFileLabel.setStyle("-fx-text-fill: #2E7D32;");
                } else {
                    chosenFileLabel.setText("No file attached");
                    chosenFileLabel.setStyle("-fx-text-fill: #6B7E84;");
                }
            }
            
            resultPopupTitle.setText("Edit Result");
            resultPopupSub.setText("Request " + row.getRequestId() + " — "
                    + row.getTest() + " for " + row.getCustomer());
            
            String fmt = row.format == null ? "" : row.format.toLowerCase();
            boolean numeric = Test.FORMAT_NUMERIC.equals(fmt);
            boolean text = Test.FORMAT_TEXT.equals(fmt);
            
            show(numericBox, numeric);
            show(textBox, true);
            textBoxLabel.setText(text ? "Result / interpretation" : "Additional interpretation (optional)");
            show(fileBox, true);
            
            showPopup(resultPopup);
            setBusy(false);
        });
        
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not load existing result: " + 
                    (ex == null ? "unknown error" : ex.getMessage()));
        });
        
        new Thread(task).start();
    }

    @FXML
    private void handleChooseFile() {
        if (pendingRow == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose result file");
        String fmt = pendingRow.format == null ? "" : pendingRow.format.toLowerCase();
        
        // Set extension filters based on format
        if (Test.FORMAT_IMAGE.equals(fmt)) {
            chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("PDF files", "*.pdf"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        } else {
            chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF files", "*.pdf"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        }
        
        File f = chooser.showOpenDialog(resultTable.getScene().getWindow());
        if (f != null) {
            chosenFile = f;
            chosenFileLabel.setText("New: " + f.getName());
            chosenFileLabel.setStyle("-fx-text-fill: #16B0A6;");
        }
    }

    @FXML
    private void handleCancelResult() {
        hidePopup(resultPopup);
        pendingRow = null;
        chosenFile = null;
    }

    @FXML
    private void handleSaveResult() {
        if (pendingRow == null) return;
        User user = Session.getInstance().getCurrentUser();
        if (user == null) { 
            showError("Session expired. Please log in again."); 
            return; 
        }

        final ResultRow row = pendingRow;
        final int requestId = row.rawRequestId;
        final int staffId = user.getId();
        final String fmt = row.format == null ? "" : row.format.toLowerCase();
        final boolean isEditing = (row.stage == Stage.PENDING);  // true if editing existing result

        BigDecimal numeric = null;
        String text = textField.getText() == null ? null : textField.getText().trim();
        
        // Clear text if empty
        if (text != null && text.isEmpty()) text = null;

        // Validate based on format
        if (Test.FORMAT_NUMERIC.equals(fmt)) {
            String raw = numericField.getText() == null ? "" : numericField.getText().trim();
            if (raw.isEmpty()) { 
                showInlineError("Please enter a numeric value."); 
                return; 
            }
            try {
                numeric = new BigDecimal(raw);
            } catch (NumberFormatException ex) {
                showInlineError("That is not a valid number."); 
                return;
            }
        } else if (Test.FORMAT_TEXT.equals(fmt)) {
            if (text == null || text.isEmpty()) {
                showInlineError("Please enter the result text."); 
                return;
            }
        }

        final BigDecimal fNumeric = numeric;
        final String fText = text;
        final File fFile = chosenFile;
        
        hidePopup(resultPopup);
        setBusy(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (isEditing) {
                    // Update existing result using uploadResult (it handles updates)
                    resultService.uploadResult(requestId, fNumeric, fText, staffId);
                    
                    // If new file uploaded, attach it
                    if (fFile != null) {
                        String storedPath = copyToStore(fFile);
                        String fileType = fileTypeOf(fFile);
                        resultService.attachFile(row.rawResultId, fileType, storedPath, fFile.getName());
                    }
                } else {
                    // Create new result
                    Result result = resultService.uploadResult(requestId, fNumeric, fText, staffId);
                    
                    if (fFile != null && result != null) {
                        String storedPath = copyToStore(fFile);
                        String fileType = fileTypeOf(fFile);
                        resultService.attachFile(result.getId(), fileType, storedPath, fFile.getName());
                    }
                }
                return null;
            }
        };
        
        task.setOnSucceeded(e -> { 
            setBusy(false); 
            pendingRow = null; 
            chosenFile = null; 
            loadQueue(); 
            showInfo(isEditing ? "Result updated successfully" : "Result saved successfully");
        });
        
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError(ex == null ? "Could not save the result." : ex.getMessage());
        });
        
        new Thread(task).start();
    }

    /** Copies an uploaded file into the local result_files folder, returns its path. */
    private String copyToStore(File source) throws IOException {
        Path dir = Paths.get(FILE_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        String unique = System.currentTimeMillis() + "_" + source.getName();
        Path target = dir.resolve(unique);
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    /** Determines file type based on extension. */
    private String fileTypeOf(File file) {
        if (file == null) return null;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) return "pdf";
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") 
            || name.endsWith(".gif") || name.endsWith(".bmp")) {
            return "image";
        }
        return null;
    }

    /* ================================================================
       VERIFY POPUP
       ================================================================ */

    private void openVerifyPopup(ResultRow row) {
        pendingRow = row;
        verifyMessage.setText("Verify and release the result for request "
                + row.getRequestId() + " (" + row.getCustomer() + ")?\n\n"
                + "Once verified:\n"
                + "• The customer can view the result\n"
                + "• The request is marked as completed\n"
                + "• The result cannot be edited further");
        showPopup(verifyPopup);
    }

    @FXML
    private void handleCancelVerify() {
        hidePopup(verifyPopup);
        pendingRow = null;
    }

    @FXML
    private void handleConfirmVerify() {
        hidePopup(verifyPopup);
        ResultRow row = pendingRow;
        pendingRow = null;
        if (row == null) return;

        User user = Session.getInstance().getCurrentUser();
        if (user == null) { 
            showError("Session expired. Please log in again."); 
            return; 
        }

        final int resultId = row.rawResultId;
        final int staffId = user.getId();
        setBusy(true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return resultService.verifyResult(resultId, staffId);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            if (!Boolean.TRUE.equals(task.getValue())) {
                showError("This result could not be verified. It may already be verified.");
            } else {
                showInfo("Result verified and released to customer");
            }
            loadQueue();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not verify: " + (ex == null ? "unknown error" : ex.getMessage()));
        });
        new Thread(task).start();
    }

    /* ================================================================
       FILTER / SEARCH HANDLERS
       ================================================================ */

    @FXML private void handleFilterAll()      { currentFilter = "all";      applyView(); }
    @FXML private void handleFilterAwaiting() { currentFilter = "awaiting"; applyView(); }
    @FXML private void handleFilterPending()  { currentFilter = "pending";  applyView(); }
    @FXML private void handleRefresh()        { loadQueue(); }
    @FXML private void handleSearch()         { applyView(); }

    /* ================================================================
       HELPERS
       ================================================================ */

    private void setBusy(boolean busy) {
        overlayPane.setVisible(busy);
        overlayPane.setManaged(busy);
        refreshBtn.setDisable(busy);
    }

    private void show(VBox box, boolean visible) {
        box.setVisible(visible);
        box.setManaged(visible);
    }

    private void showPopup(StackPane popup) { 
        popup.setVisible(true); 
        popup.setManaged(true); 
    }
    
    private void hidePopup(StackPane popup) { 
        popup.setVisible(false); 
        popup.setManaged(false); 
    }

    private void showInlineError(String msg) {
        resultError.setText(msg);
        resultError.setVisible(true);
        resultError.setManaged(true);
    }

    private void hideError() {
        resultError.setVisible(false);
        resultError.setManaged(false);
    }

    private void showError(String message) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait());
    }

    private void showInfo(String message) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait());
    }

    /* ================================================================
       ROW MODEL
       ================================================================ */

    private enum Stage { AWAITING, PENDING }

    public static class ResultRow {
        private final Stage stage;
        private final int rawRequestId;
        private final int rawResultId;   // -1 when no result yet
        private final String format;

        private final SimpleStringProperty requestId;
        private final SimpleStringProperty customer;
        private final SimpleStringProperty test;
        private final SimpleStringProperty formatLabel;
        private final SimpleStringProperty fileInfo;   // NEW
        private final SimpleStringProperty stageLabel;

        private ResultRow(Stage stage, int rawRequestId, int rawResultId, String format,
                          String customer, String test, String fileInfo) {
            this.stage = stage;
            this.rawRequestId = rawRequestId;
            this.rawResultId = rawResultId;
            this.format = format;
            this.requestId = new SimpleStringProperty("#" + rawRequestId);
            this.customer = new SimpleStringProperty(customer == null ? "" : customer);
            this.test = new SimpleStringProperty(test == null ? "" : test);
            this.formatLabel = new SimpleStringProperty(prettyFormat(format));
            this.fileInfo = new SimpleStringProperty(fileInfo == null ? "" : fileInfo);
            this.stageLabel = new SimpleStringProperty(
                stage == Stage.AWAITING ? "Awaiting result" : "Pending verification");
        }

        static ResultRow awaiting(Sample s, String format) {
            return new ResultRow(Stage.AWAITING, s.getTestRequestId(), -1, format,
                    s.getCustomerName(), s.getTestName(), null);
        }

        static ResultRow pending(Result r) {
            String fileInfo = null;
            if (r.hasFile()) {
                fileInfo = "📎 " + r.getFirstFileName();
                if (r.getFileCount() > 1) {
                    fileInfo += " (+" + (r.getFileCount() - 1) + " more)";
                }
            }
            return new ResultRow(Stage.PENDING, r.getTestRequestId(), r.getId(),
                    r.getResultFormat(), r.getCustomerName(), r.getTestName(), fileInfo);
        }

        private static String prettyFormat(String f) {
            if (f == null || f.isEmpty()) return "\u2014";
            return Character.toUpperCase(f.charAt(0)) + f.substring(1);
        }

        public String getRequestId()  { return requestId.get(); }
        public String getCustomer()   { return customer.get(); }
        public String getTest()       { return test.get(); }
        public String getFormat()     { return formatLabel.get(); }
        public String getFileInfo()   { return fileInfo.get(); }
        public String getStageLabel() { return stageLabel.get(); }
    }
}