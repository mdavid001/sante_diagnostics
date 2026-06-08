package santediagnostics.resources.controllers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import santediagnostics.Session;
import santediagnostics.TestRequest;
import santediagnostics.TestRequestDao;

/**
 * Controller for active-tests.fxml (Customer).
 *
 * Lists the customer's in-progress orders (anything not completed or
 * cancelled) with a live per-row countdown to the expected ready time.
 */
public class ActiveTestsController implements Initializable {

    @FXML private TextField searchField;
    @FXML private Label countLabel;

    @FXML private TableView<ActiveTest> activeTable;
    @FXML private TableColumn<ActiveTest, String> colTestName;
    @FXML private TableColumn<ActiveTest, String> colPayment;
    @FXML private TableColumn<ActiveTest, String> colStatus;
    @FXML private TableColumn<ActiveTest, String> colCountdown;

    private Timeline countdownTimeline;
    private final List<ActiveTest> masterRows = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        loadActiveTests();
        startCountdownTimer();
    }

    /* ================================================================
       TABLE SETUP
       ================================================================ */

    private void setupTable() {
        colTestName.setCellValueFactory(new PropertyValueFactory<>("testName"));

        colPayment.setCellValueFactory(new PropertyValueFactory<>("paymentStatus"));
        colPayment.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("status-pill"); }
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); return; }
                pill.setText(capitalise(status));
                pill.getStyleClass().removeAll("pill-paid", "pill-unpaid");
                pill.getStyleClass().add("paid".equals(status) ? "pill-paid" : "pill-unpaid");
                setGraphic(pill);
            }
        });

        colStatus.setCellValueFactory(new PropertyValueFactory<>("requestStatus"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("status-pill"); }
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); return; }
                pill.setText(capitalise(status.replace("_", " ")));
                pill.getStyleClass().removeAll("pill-collected", "pill-processing", "pill-pending", "pill-ready");
                switch (status) {
                    case "in_progress": pill.getStyleClass().add("pill-processing"); break;
                    case "completed":   pill.getStyleClass().add("pill-ready"); break;
                    default:            pill.getStyleClass().add("pill-pending"); break;
                }
                setGraphic(pill);
            }
        });

        colCountdown.setCellValueFactory(cd -> cd.getValue().countdownProperty());
        colCountdown.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setText(null); setStyle(""); return; }
                setText(value);
                if (value.startsWith("0h") || value.equals("Ready!") || value.equals("--")) {
                    setStyle("-fx-text-fill: #E5484D; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #11242A; -fx-font-weight: bold;");
                }
            }
        });
    }

    /* ================================================================
       DATA
       ================================================================ */

    private void loadActiveTests() {
        int userId = Session.getInstance().getCurrentUser().getId();

        Task<List<ActiveTest>> task = new Task<>() {
            @Override
            protected List<ActiveTest> call() throws Exception {
                TestRequestDao dao = new TestRequestDao();
                List<TestRequest> requests = dao.findByCustomer(userId);
                List<ActiveTest> rows = new ArrayList<>();
                for (TestRequest r : requests) {
                    // "Active" = not completed and not cancelled.
                    if ("completed".equals(r.getRequestStatus())
                            || "cancelled".equals(r.getRequestStatus())) {
                        continue;
                    }
                    LocalDateTime expectedReady = r.getExpectedReadyAt() == null
                            ? null : r.getExpectedReadyAt().toLocalDateTime();
                    rows.add(new ActiveTest(
                            r.getTestName(),
                            r.getPaymentStatus(),
                            r.getRequestStatus(),
                            expectedReady));
                }
                return rows;
            }
        };

        task.setOnSucceeded(e -> {
            masterRows.clear();
            masterRows.addAll(task.getValue());
            applySearch();
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
        });
        new Thread(task).start();
    }

    private void applySearch() {
        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        List<ActiveTest> filtered = new ArrayList<>();
        for (ActiveTest t : masterRows) {
            if (q.isEmpty() || t.getTestName().toLowerCase().contains(q)) {
                filtered.add(t);
            }
        }
        activeTable.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " active" : " active"));
    }

    @FXML
    private void handleSearch() {
        applySearch();
    }

    private void startCountdownTimer() {
        countdownTimeline = new Timeline(new KeyFrame(
            javafx.util.Duration.seconds(1),
            event -> {
                for (ActiveTest t : activeTable.getItems()) {
                    t.refreshCountdown();
                }
            }
        ));
        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /* ================================================================
       ROW MODEL
       ================================================================ */

    public static class ActiveTest {
        private final SimpleStringProperty testName;
        private final SimpleStringProperty paymentStatus;
        private final SimpleStringProperty requestStatus;
        private final SimpleStringProperty countdown;
        private final LocalDateTime expectedReady;

        public ActiveTest(String testName, String paymentStatus,
                          String requestStatus, LocalDateTime expectedReady) {
            this.testName = new SimpleStringProperty(testName);
            this.paymentStatus = new SimpleStringProperty(paymentStatus);
            this.requestStatus = new SimpleStringProperty(requestStatus);
            this.expectedReady = expectedReady;
            this.countdown = new SimpleStringProperty();
            refreshCountdown();
        }

        public void refreshCountdown() {
            if (expectedReady == null) {
                countdown.set("--");
                return;
            }
            Duration remaining = Duration.between(LocalDateTime.now(), expectedReady);
            if (remaining.isNegative() || remaining.isZero()) {
                countdown.set("Ready!");
            } else {
                long hours = remaining.toHours();
                long mins = remaining.toMinutesPart();
                long secs = remaining.toSecondsPart();
                countdown.set(String.format("%dh %02dm %02ds", hours, mins, secs));
            }
        }

        public String getTestName()      { return testName.get(); }
        public String getPaymentStatus() { return paymentStatus.get(); }
        public String getRequestStatus() { return requestStatus.get(); }
        public String getCountdown()     { return countdown.get(); }
        public StringProperty countdownProperty() { return countdown; }
    }
}
