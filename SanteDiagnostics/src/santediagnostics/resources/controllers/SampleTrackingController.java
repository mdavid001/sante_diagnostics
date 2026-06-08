package santediagnostics.resources.controllers;

import javafx.scene.layout.HBox;
import java.net.URL;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import santediagnostics.Sample;
import santediagnostics.SampleService;
import santediagnostics.Session;
import santediagnostics.TestRequest;
import santediagnostics.TestRequestService;
import santediagnostics.User;

/**
 * Controller for sample-tracking.fxml (Lab Attendant).
 *
 * Shows every paid request as a row in the sample pipeline:
 *   awaiting collection -> collected -> processing -> processed
 * A request that's paid but has no sample yet appears as "awaiting collection"
 * with a Collect button; once a sample exists, the row reflects the sample's
 * status with the next-step button (Start processing / Mark processed).
 *
 * All DB work runs on background threads via Task<>.
 */
public class SampleTrackingController implements Initializable {

    @FXML private javafx.scene.control.TextField searchField;
    @FXML private javafx.scene.control.ToggleButton filterAll;
    @FXML private javafx.scene.control.ToggleButton filterAwaiting;
    @FXML private javafx.scene.control.ToggleButton filterCollected;
    @FXML private javafx.scene.control.ToggleButton filterProcessing;
    @FXML private javafx.scene.control.ToggleButton filterProcessed;
    @FXML private Label countLabel;
    @FXML private Button refreshBtn;

    @FXML private TableView<SampleRow> sampleTable;
    @FXML private TableColumn<SampleRow, String> colRequestId;
    @FXML private TableColumn<SampleRow, String> colCustomer;
    @FXML private TableColumn<SampleRow, String> colTest;
    @FXML private TableColumn<SampleRow, String> colStage;
    @FXML private TableColumn<SampleRow, String> colUpdated;
    @FXML private TableColumn<SampleRow, Void> colAction;

    @FXML private StackPane overlayPane;
    @FXML private StackPane collectPopup;
    @FXML private Label collectMessage;
    @FXML private TextArea collectNotes;

    private final SampleService sampleService = new SampleService();
    private final TestRequestService requestService = new TestRequestService();

    /** "all" | "awaiting" | "collected" | "processing" | "processed". */
    private String currentFilter = "all";

    /** All rows loaded from the DB; chip + search filter against this. */
    private final List<SampleRow> masterRows = new ArrayList<>();

    /** The request awaiting collection that's pending in the collect popup. */
    private SampleRow pendingCollect;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM, HH:mm");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        setupActionColumn();
        loadSamples();
    }

    /* ================================================================
       COLUMNS
       ================================================================ */

    private void setupColumns() {
        colRequestId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customer"));
        colTest.setCellValueFactory(new PropertyValueFactory<>("test"));
        colUpdated.setCellValueFactory(new PropertyValueFactory<>("updated"));

        colStage.setCellValueFactory(new PropertyValueFactory<>("stageLabel"));
        colStage.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("status-pill"); }
            @Override
            protected void updateItem(String stage, boolean empty) {
                super.updateItem(stage, empty);
                if (empty || stage == null) {
                    setGraphic(null);
                    return;
                }
                pill.setText(stage);
                pill.getStyleClass().removeAll(
                    "pill-pending", "pill-collected", "pill-processing", "pill-paid");
                switch (stage) {
                    case "Awaiting collection": pill.getStyleClass().add("pill-pending"); break;
                    case "Collected":           pill.getStyleClass().add("pill-collected"); break;
                    case "Processing":          pill.getStyleClass().add("pill-processing"); break;
                    case "Processed":           pill.getStyleClass().add("pill-paid"); break;
                    default:                    pill.getStyleClass().add("pill-pending");
                }
                setGraphic(pill);
            }
        });
    }

    /** Action column shows the forward button plus a "Back" button where allowed. */
    /** Action column shows the forward button plus a "Back" button where allowed. */
    private void setupActionColumn() {
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            private final Button backBtn = new Button("↺ Back");
            private final HBox box = new HBox(8);

            {
                btn.getStyleClass().add("table-action-btn");
                backBtn.getStyleClass().add("back-action-btn");
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                btn.setOnAction(e -> {
                    SampleRow row = getTableView().getItems().get(getIndex());
                    if (row != null) onAction(row);
                });

                backBtn.setOnAction(e -> {
                    SampleRow row = getTableView().getItems().get(getIndex());
                    if (row != null) onMoveBack(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableView().getItems().isEmpty()) {
                    setGraphic(null);
                    return;
                }

                SampleRow row = getTableView().getItems().get(getIndex());
                if (row == null) {
                    setGraphic(null);
                    return;
                }

                box.getChildren().clear();

                switch (row.stage) {
                    case AWAITING:
                        btn.setText("Collect Sample");
                        btn.getStyleClass().removeAll("action-btn-collect", "action-btn-process", "action-btn-complete");
                        btn.getStyleClass().add("action-btn-collect");
                        box.getChildren().add(btn);
                        break;

                    case COLLECTED:
                        btn.setText("Start Processing");
                        btn.getStyleClass().removeAll("action-btn-collect", "action-btn-process", "action-btn-complete");
                        btn.getStyleClass().add("action-btn-process");
                        box.getChildren().add(btn);
                        break;

                    case PROCESSING:
                        btn.setText("Mark Processed");
                        btn.getStyleClass().removeAll("action-btn-collect", "action-btn-process", "action-btn-complete");
                        btn.getStyleClass().add("action-btn-complete");
                        box.getChildren().addAll(btn, backBtn);
                        break;

                    case PROCESSED:
                        backBtn.setText("↺ Revert to Processing");
                        box.getChildren().add(backBtn);
                        break;

                    default:
                        setGraphic(null);
                        return;
                }
                setGraphic(box);
            }
        });
    }

    private void onAction(SampleRow row) {
        if (row == null) return;
        switch (row.stage) {
            case AWAITING:   openCollectPopup(row); break;
            case COLLECTED:  startProcessing(row);  break;
            case PROCESSING: completeProcessing(row); break;
            default: /* processed: nothing forward */
        }
    }

    /** One step back: processed -> processing, or processing -> collected. */
    private void onMoveBack(SampleRow row) {
        if (row == null) return;
        User user = Session.getInstance().getCurrentUser();
        if (user == null) { showError("Session expired. Please log in again."); return; }
        final int sampleId = row.rawSampleId;
        final int staffId = user.getId();
        final boolean fromProcessed = row.stage == Stage.PROCESSED;
        setBusy(true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return fromProcessed
                        ? sampleService.revertToProcessing(sampleId, staffId)
                        : sampleService.revertToCollected(sampleId, staffId);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            if (!Boolean.TRUE.equals(task.getValue())) {
                showError("This sample's status changed; please refresh.");
            }
            loadSamples();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not move the sample back: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });
        new Thread(task).start();
    }

    /* ================================================================
       DATA LOADING
       ================================================================ */

    private void loadSamples() {
        setBusy(true);

        Task<List<SampleRow>> task = new Task<>() {
            @Override
            protected List<SampleRow> call() throws Exception {
                List<SampleRow> rows = new ArrayList<>();

                // 1) Paid requests with no sample yet -> awaiting collection.
                List<TestRequest> paid = requestService.getQueueByPayment(TestRequest.PAYMENT_PAID);
                for (TestRequest r : paid) {
                    Sample existing = sampleService.getByRequest(r.getId());
                    if (existing == null) {
                        rows.add(SampleRow.awaiting(r));
                    }
                }

                // 2) All existing samples -> their current stage.
                List<Sample> samples = sampleService.getWorklist();
                for (Sample s : samples) {
                    rows.add(SampleRow.fromSample(s));
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
            showError("Could not load samples: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    /** Applies the active filter chip AND the search text against masterRows. */
    private void applyView() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();

        List<SampleRow> filtered = new ArrayList<>();
        for (SampleRow row : masterRows) {
            if (!matchesFilter(row)) continue;
            if (!query.isEmpty()) {
                boolean hit = row.getCustomer().toLowerCase().contains(query)
                        || row.getTest().toLowerCase().contains(query)
                        || row.getRequestId().toLowerCase().contains(query);
                if (!hit) continue;
            }
            filtered.add(row);
        }

        sampleTable.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " sample" : " samples"));
    }

    @FXML
    private void handleSearch() {
        applyView();
    }

    private boolean matchesFilter(SampleRow row) {
        switch (currentFilter) {
            case "awaiting":   return row.stage == Stage.AWAITING;
            case "collected":  return row.stage == Stage.COLLECTED;
            case "processing": return row.stage == Stage.PROCESSING;
            case "processed":  return row.stage == Stage.PROCESSED;
            default:           return true;
        }
    }

    /* ================================================================
       ACTIONS
       ================================================================ */

    private void openCollectPopup(SampleRow row) {
        pendingCollect = row;
        collectNotes.clear();
        collectMessage.setText("Record sample collection for request "
                + row.getRequestId() + " from " + row.getCustomer()
                + " (" + row.getTest() + ")? This starts the result countdown.");
        showPopup(collectPopup);
    }

    @FXML
    private void handleCancelCollect() {
        hidePopup(collectPopup);
        pendingCollect = null;
    }

    @FXML
    private void handleConfirmCollect() {
        hidePopup(collectPopup);
        SampleRow row = pendingCollect;
        pendingCollect = null;
        if (row == null) return;

        User user = Session.getInstance().getCurrentUser();
        if (user == null) { showError("Session expired. Please log in again."); return; }

        final int requestId = row.rawRequestId;
        final int staffId = user.getId();
        final String notes = collectNotes.getText();
        setBusy(true);

        Task<Sample> task = new Task<>() {
            @Override
            protected Sample call() throws Exception {
                return sampleService.collectSample(requestId, staffId, notes);
            }
        };
        task.setOnSucceeded(e -> { setBusy(false); loadSamples(); });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError(ex == null ? "Could not collect sample." : ex.getMessage());
        });
        new Thread(task).start();
    }

    private void startProcessing(SampleRow row) {
        User user = Session.getInstance().getCurrentUser();
        if (user == null) { showError("Session expired. Please log in again."); return; }
        final int sampleId = row.rawSampleId;
        final int staffId = user.getId();
        setBusy(true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return sampleService.startProcessing(sampleId, staffId);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            if (!Boolean.TRUE.equals(task.getValue())) {
                showError("This sample is no longer awaiting processing.");
            }
            loadSamples();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not start processing: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });
        new Thread(task).start();
    }

    private void completeProcessing(SampleRow row) {
        User user = Session.getInstance().getCurrentUser();
        if (user == null) { showError("Session expired. Please log in again."); return; }
        final int sampleId = row.rawSampleId;
        final int staffId = user.getId();
        setBusy(true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return sampleService.completeProcessing(sampleId, staffId);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            if (!Boolean.TRUE.equals(task.getValue())) {
                showError("This sample is not currently processing.");
            }
            loadSamples();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not mark processed: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });
        new Thread(task).start();
    }

    /* ================================================================
       FILTER HANDLERS
       ================================================================ */

    @FXML private void handleFilterAll()        { currentFilter = "all";        applyView(); }
    @FXML private void handleFilterAwaiting()   { currentFilter = "awaiting";   applyView(); }
    @FXML private void handleFilterCollected()  { currentFilter = "collected";  applyView(); }
    @FXML private void handleFilterProcessing() { currentFilter = "processing"; applyView(); }
    @FXML private void handleFilterProcessed()  { currentFilter = "processed";  applyView(); }
    @FXML private void handleRefresh()          { loadSamples(); }

    /* ================================================================
       HELPERS
       ================================================================ */

    private void setBusy(boolean busy) {
        overlayPane.setVisible(busy);
        overlayPane.setManaged(busy);
        refreshBtn.setDisable(busy);
    }

    private void showPopup(StackPane popup) { popup.setVisible(true); popup.setManaged(true); }
    private void hidePopup(StackPane popup) { popup.setVisible(false); popup.setManaged(false); }

    private void showError(String message) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait());
    }

    /* ================================================================
       ROW MODEL
       ================================================================ */

    private enum Stage { AWAITING, COLLECTED, PROCESSING, PROCESSED }

    public static class SampleRow {
        private final Stage stage;
        private final int rawRequestId;
        private final int rawSampleId;   // -1 when no sample yet

        private final SimpleStringProperty requestId;
        private final SimpleStringProperty customer;
        private final SimpleStringProperty test;
        private final SimpleStringProperty stageLabel;
        private final SimpleStringProperty updated;

        private SampleRow(Stage stage, int rawRequestId, int rawSampleId,
                          String customer, String test, String updated) {
            this.stage = stage;
            this.rawRequestId = rawRequestId;
            this.rawSampleId = rawSampleId;
            this.requestId = new SimpleStringProperty("#" + rawRequestId);
            this.customer = new SimpleStringProperty(customer == null ? "" : customer);
            this.test = new SimpleStringProperty(test == null ? "" : test);
            this.stageLabel = new SimpleStringProperty(stageText(stage));
            this.updated = new SimpleStringProperty(updated == null ? "\u2014" : updated);
        }

        static SampleRow awaiting(TestRequest r) {
            return new SampleRow(Stage.AWAITING, r.getId(), -1,
                    r.getCustomerName(), r.getTestName(), null);
        }

        static SampleRow fromSample(Sample s) {
            Stage stage;
            Timestamp ts;
            switch (s.getStatus()) {
                case Sample.COLLECTED:  stage = Stage.COLLECTED;  ts = s.getCollectedAt(); break;
                case Sample.PROCESSING: stage = Stage.PROCESSING; ts = s.getProcessingStartedAt(); break;
                case Sample.PROCESSED:  stage = Stage.PROCESSED;  ts = s.getProcessedAt(); break;
                default:                stage = Stage.AWAITING;   ts = null;
            }
            String when = ts == null ? null : ts.toLocalDateTime().format(FMT);
            return new SampleRow(stage, s.getTestRequestId(), s.getId(),
                    s.getCustomerName(), s.getTestName(), when);
        }

        private static String stageText(Stage s) {
            switch (s) {
                case AWAITING:   return "Awaiting collection";
                case COLLECTED:  return "Collected";
                case PROCESSING: return "Processing";
                case PROCESSED:  return "Processed";
                default:         return "";
            }
        }

        public String getRequestId()  { return requestId.get(); }
        public String getCustomer()   { return customer.get(); }
        public String getTest()       { return test.get(); }
        public String getStageLabel() { return stageLabel.get(); }
        public String getUpdated()    { return updated.get(); }
    }
}
