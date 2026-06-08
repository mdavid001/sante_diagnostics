package santediagnostics.resources.controllers;

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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import santediagnostics.Audit;
import santediagnostics.AuditService;

/**
 * Controller for audit-trail.fxml (Super Admin).
 *
 * Read-only viewer over the immutable audit log. There are deliberately NO
 * edit or delete actions here: the audit_log table is protected at the
 * database level (a trigger blocks UPDATE and DELETE), so the log cannot be
 * altered from anywhere in the system. This page only reads and displays it.
 */
public class AuditTrailController implements Initializable {

    @FXML private javafx.scene.control.TextField searchField;
    @FXML private javafx.scene.control.ToggleButton filterAll;
    @FXML private javafx.scene.control.ToggleButton filterTests;
    @FXML private javafx.scene.control.ToggleButton filterRequests;
    @FXML private javafx.scene.control.ToggleButton filterSamples;
    @FXML private javafx.scene.control.ToggleButton filterResults;
    @FXML private Label countLabel;
    @FXML private Button refreshBtn;

    @FXML private TableView<AuditRow> auditTable;
    @FXML private TableColumn<AuditRow, String> colTime;
    @FXML private TableColumn<AuditRow, String> colActor;
    @FXML private TableColumn<AuditRow, String> colAction;
    @FXML private TableColumn<AuditRow, String> colEntity;
    @FXML private TableColumn<AuditRow, String> colDetails;

    @FXML private StackPane overlayPane;

    private final AuditService auditService = new AuditService();

    /** "all" | "test" | "test_request" | "sample" | "result". */
    private String currentFilter = "all";

    private final List<AuditRow> masterRows = new ArrayList<>();

    private static final int LOAD_LIMIT = 500;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        loadAudit();
    }

    private void setupColumns() {
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colActor.setCellValueFactory(new PropertyValueFactory<>("actor"));
        colAction.setCellValueFactory(new PropertyValueFactory<>("action"));
        colEntity.setCellValueFactory(new PropertyValueFactory<>("entity"));
        colDetails.setCellValueFactory(new PropertyValueFactory<>("details"));
    }

    private void loadAudit() {
        setBusy(true);

        Task<List<AuditRow>> task = new Task<>() {
            @Override
            protected List<AuditRow> call() throws Exception {
                List<Audit> entries = auditService.getRecent(LOAD_LIMIT);
                List<AuditRow> rows = new ArrayList<>();
                for (Audit a : entries) {
                    rows.add(new AuditRow(a));
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
            showError("Could not load the audit log: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    private void applyView() {
        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        List<AuditRow> filtered = new ArrayList<>();
        for (AuditRow r : masterRows) {
            if (!"all".equals(currentFilter) && !currentFilter.equals(r.rawEntityType)) {
                continue;
            }
            if (!q.isEmpty()) {
                boolean hit = r.getActor().toLowerCase().contains(q)
                        || r.getAction().toLowerCase().contains(q)
                        || r.getEntity().toLowerCase().contains(q)
                        || r.getDetails().toLowerCase().contains(q);
                if (!hit) continue;
            }
            filtered.add(r);
        }
        auditTable.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " entry" : " entries"));
    }

    @FXML private void handleSearch()         { applyView(); }
    @FXML private void handleFilterAll()      { currentFilter = "all";          applyView(); }
    @FXML private void handleFilterTests()    { currentFilter = "test";         applyView(); }
    @FXML private void handleFilterRequests() { currentFilter = "test_request"; applyView(); }
    @FXML private void handleFilterSamples()  { currentFilter = "sample";       applyView(); }
    @FXML private void handleFilterResults()  { currentFilter = "result";       applyView(); }
    @FXML private void handleRefresh()        { loadAudit(); }

    private void setBusy(boolean busy) {
        overlayPane.setVisible(busy);
        overlayPane.setManaged(busy);
        refreshBtn.setDisable(busy);
    }

    private void showError(String message) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait());
    }

    /* ================================================================
       ROW MODEL
       ================================================================ */

    public static class AuditRow {
        private final String rawEntityType;
        private final SimpleStringProperty time;
        private final SimpleStringProperty actor;
        private final SimpleStringProperty action;
        private final SimpleStringProperty entity;
        private final SimpleStringProperty details;

        private static final DateTimeFormatter FMT =
                DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss");

        public AuditRow(Audit a) {
            this.rawEntityType = a.getEntityType() == null ? "" : a.getEntityType();

            Timestamp ts = a.getCreatedAt();
            this.time = new SimpleStringProperty(
                    ts == null ? "\u2014" : ts.toLocalDateTime().format(FMT));

            String who = a.getActorName();
            if (who == null || who.trim().isEmpty()) {
                who = a.getActorUserId() == null ? "System" : ("User #" + a.getActorUserId());
            }
            this.actor = new SimpleStringProperty(who);

            this.action = new SimpleStringProperty(prettyAction(a.getAction()));
            this.entity = new SimpleStringProperty(prettyEntity(a.getEntityType(), a.getEntityId()));
            this.details = new SimpleStringProperty(a.getDetails() == null ? "" : a.getDetails());
        }

        private static String prettyAction(String action) {
            if (action == null || action.isEmpty()) return "";
            // e.g. PAYMENT_CONFIRMED -> "Payment confirmed"
            String lower = action.replace('_', ' ').toLowerCase();
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        private static String prettyEntity(String type, Integer id) {
            if (type == null || type.isEmpty()) return "\u2014";
            String label;
            switch (type) {
                case "test":         label = "Test"; break;
                case "test_request": label = "Request"; break;
                case "sample":       label = "Sample"; break;
                case "result":       label = "Result"; break;
                default:             label = Character.toUpperCase(type.charAt(0)) + type.substring(1);
            }
            return id == null ? label : (label + " #" + id);
        }

        public String getTime()    { return time.get(); }
        public String getActor()   { return actor.get(); }
        public String getAction()  { return action.get(); }
        public String getEntity()  { return entity.get(); }
        public String getDetails() { return details.get(); }
    }
}
