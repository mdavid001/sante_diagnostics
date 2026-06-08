package santediagnostics.resources.controllers;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import santediagnostics.Result;
import santediagnostics.ResultService;
import santediagnostics.Sample;
import santediagnostics.SampleService;
import santediagnostics.Session;
import santediagnostics.TestRequest;
import santediagnostics.TestRequestDao;
import santediagnostics.User;

/**
 * Controller for customer-dashboard.fxml.
 *
 * Handles:
 *  - Welcome header with user name and avatar initials.
 *  - Four navigation cards (Browse Tests, My Results, Active Tests, Profile).
 *  - Active Tests table with live countdown timers.
 *  - Past Results table showing completed test results.
 */
public class CustomerDashboardController implements Initializable {

    /* ========== FXML bindings ========== */
    @FXML private Label welcomeLabel;
    @FXML private Label avatarInitials;

    @FXML private TableView<TestRequest> activeTestsTable;
    @FXML private TableColumn<TestRequest, String> colTestName;
    @FXML private TableColumn<TestRequest, String> colOrderDate;
    @FXML private TableColumn<TestRequest, String> colPaymentStatus;
    @FXML private TableColumn<TestRequest, String> colSampleStatus;
    @FXML private TableColumn<TestRequest, String> colCountdown;

    @FXML private TableView<Result> pastResultsTable;
    @FXML private TableColumn<Result, String> colResultTestName;
    @FXML private TableColumn<Result, String> colResultDate;
    @FXML private TableColumn<Result, String> colResultValue;
    @FXML private TableColumn<Result, String> colResultFiles;

    /** Timer that ticks every second to refresh countdowns. */
    private Timeline countdownTimeline;
    
    /** Stores active tests with their countdown values for live updates */
    private final List<TestRequestCountdown> activeTestsWithCountdown = new java.util.ArrayList<>();

    private final TestRequestDao requestDao = new TestRequestDao();
    private final ResultService resultService = new ResultService();
    private final SampleService sampleService = new SampleService();
    
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("dd MMM yyyy");

    /* ================================================================
       INITIALIZATION
       ================================================================ */

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        User user = Session.getInstance().getCurrentUser();
        welcomeLabel.setText("Welcome " + user.getFullName());
        avatarInitials.setText(getInitials(user));

        setupActiveTestsTable();
        setupPastResultsTable();
        loadActiveTests();
        loadPastResults();
        startCountdownTimer();
    }

    /* ================================================================
       TABLE SETUP
       ================================================================ */

    private void setupActiveTestsTable() {
        // Test Name column
        colTestName.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getTestName()));
        
        // Order Date column - using getOrderDate? Actually TestRequest doesn't have getOrderDate
        // We'll use a placeholder or get from DB. For now, show "Pending" if no date
        colOrderDate.setCellValueFactory(cellData -> 
            new SimpleStringProperty(getOrderDateForRequest(cellData.getValue())));

        // Payment status with colored pill
        colPaymentStatus.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getPaymentStatus()));
        colPaymentStatus.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("status-pill"); }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    String displayStatus = "Paid".equalsIgnoreCase(status) ? "Paid" : "Unpaid";
                    pill.setText(displayStatus);
                    pill.getStyleClass().removeAll("pill-paid", "pill-unpaid");
                    pill.getStyleClass().add(
                        "Paid".equalsIgnoreCase(status) ? "pill-paid" : "pill-unpaid"
                    );
                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        // Sample status with colored pill
        colSampleStatus.setCellValueFactory(cellData -> 
            new SimpleStringProperty(getSampleStatusForRequest(cellData.getValue())));
        colSampleStatus.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("status-pill"); }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    pill.setText(status);
                    pill.getStyleClass().removeAll(
                        "pill-collected", "pill-processing", "pill-pending"
                    );
                    switch (status.toLowerCase()) {
                        case "collected":  pill.getStyleClass().add("pill-collected"); break;
                        case "processing": pill.getStyleClass().add("pill-processing"); break;
                        case "processed":  pill.getStyleClass().add("pill-validating"); break;
                        default:           pill.getStyleClass().add("pill-pending"); break;
                    }
                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        // Countdown column
        colCountdown.setCellValueFactory(cellData -> {
            for (TestRequestCountdown wrapper : activeTestsWithCountdown) {
                if (wrapper.getRequestId() == cellData.getValue().getId()) {
                    return wrapper.countdownProperty();
                }
            }
            return new SimpleStringProperty("--");
        });
        colCountdown.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(value);
                    if (value.startsWith("0h") || value.equals("Ready!")) {
                        setStyle("-fx-text-fill: #E5484D; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #11242A; -fx-font-weight: bold;");
                    }
                }
            }
        });
    }

    private void setupPastResultsTable() {
        // Test Name column
        colResultTestName.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getTestName()));
        
        // Result Date column (using verifiedAt)
        colResultDate.setCellValueFactory(cellData -> {
            if (cellData.getValue().getVerifiedAt() != null) {
                return new SimpleStringProperty(
                    cellData.getValue().getVerifiedAt().toLocalDateTime().format(DATE_FORMATTER)
                );
            }
            return new SimpleStringProperty("");
        });
        
        // Result Value column - show numeric or text value
        colResultValue.setCellValueFactory(cellData -> {
            Result result = cellData.getValue();
            if (result.getValueNumeric() != null) {
                return new SimpleStringProperty(result.getValueNumeric().toString());
            } else if (result.getValueText() != null && !result.getValueText().isEmpty()) {
                return new SimpleStringProperty(result.getValueText());
            }
            return new SimpleStringProperty("--");
        });
        
        // Files column
        colResultFiles.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().hasFile() ? "📎 View (" + cellData.getValue().getFileCount() + ")" : "--"));
        colResultFiles.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            {
                viewBtn.getStyleClass().add("link-button");
                viewBtn.setOnAction(e -> {
                    Result result = getTableView().getItems().get(getIndex());
                    handleViewResultFile(result);
                });
            }
            
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || "--".equals(value)) {
                    setGraphic(null);
                    setText(value != null ? value : "");
                } else {
                    setGraphic(viewBtn);
                    setText(null);
                }
            }
        });
    }

    /* ================================================================
       DATA LOADING
       ================================================================ */

    private void loadActiveTests() {
        int userId = Session.getInstance().getCurrentUser().getId();

        Task<List<TestRequest>> task = new Task<>() {
            @Override
            protected List<TestRequest> call() throws Exception {
                // Get all requests for this customer
                List<TestRequest> allRequests = requestDao.findByCustomer(userId);
                if (allRequests == null) return new java.util.ArrayList<>();
                // Filter to only active ones (not completed or cancelled)
                return allRequests.stream()
                    .filter(r -> !TestRequest.STATUS_COMPLETED.equals(r.getRequestStatus()))
                    .filter(r -> !TestRequest.STATUS_CANCELLED.equals(r.getRequestStatus()))
                    .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(e -> {
            List<TestRequest> activeTests = task.getValue();
            activeTestsWithCountdown.clear();
            for (TestRequest request : activeTests) {
                activeTestsWithCountdown.add(new TestRequestCountdown(request));
            }
            activeTestsTable.setItems(FXCollections.observableArrayList(activeTests));
        });
        
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            activeTestsTable.setItems(FXCollections.observableArrayList());
        });
        
        new Thread(task).start();
    }

    private void loadPastResults() {
        int userId = Session.getInstance().getCurrentUser().getId();

        Task<List<Result>> task = new Task<>() {
            @Override
            protected List<Result> call() throws Exception {
                // Get verified results for this customer
                return resultService.getCustomerResults(userId);
            }
        };

        task.setOnSucceeded(e -> {
            pastResultsTable.setItems(FXCollections.observableArrayList(task.getValue()));
        });
        
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            pastResultsTable.setItems(FXCollections.observableArrayList());
        });
        
        new Thread(task).start();
    }

    /**
     * Helper method to get order date for a test request.
     * Since TestRequest doesn't have an order date field, we'll use created_at.
     * Note: You may need to add created_at to TestRequest if not present.
     */
    private String getOrderDateForRequest(TestRequest request) {
        // For now, return a placeholder
        // If you add a created_at field to TestRequest, use that
        return "Ordered";
    }

    /**
     * Helper method to get sample status for a test request.
     */
    private String getSampleStatusForRequest(TestRequest request) {
        try {
            Sample sample = sampleService.getByRequest(request.getId());
            if (sample != null && sample.getStatus() != null) {
                String status = sample.getStatus();
                switch (status) {
                    case Sample.COLLECTED: return "Collected";
                    case Sample.PROCESSING: return "Processing";
                    case Sample.PROCESSED: return "Processed";
                    default: return "Pending";
                }
            }
        } catch (Exception e) {
            // No sample found yet
        }
        return "Pending";
    }

    /**
     * Handles viewing a result file.
     */
    private void handleViewResultFile(Result result) {
        if (result.hasFile() && result.getFiles() != null && !result.getFiles().isEmpty()) {
            // TODO: Open file viewer or download dialog
            System.out.println("Viewing result file for: " + result.getTestName());
            System.out.println("Files: " + result.getFiles().size());
        }
    }

    /* ================================================================
       COUNTDOWN TIMER
       ================================================================ */

    private void startCountdownTimer() {
        countdownTimeline = new Timeline(new KeyFrame(
            javafx.util.Duration.seconds(1),
            event -> {
                for (TestRequestCountdown wrapper : activeTestsWithCountdown) {
                    wrapper.refreshCountdown();
                }
            }
        ));
        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    public void stopCountdownTimer() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
    }

    /* ================================================================
       CARD CLICK HANDLERS
       ================================================================ */

    @FXML
    private void handleBrowseTests(MouseEvent event) {
        fireSidebarButton("#navCatalog");
    }

    @FXML
    private void handleMyResults(MouseEvent event) {
        fireSidebarButton("#navResultVault");
    }

    @FXML
    private void handleActiveTests(MouseEvent event) {
        // Scroll to table or navigate to dedicated page
        // Currently the table is visible on this dashboard
    }

    @FXML
    private void handleProfile(MouseEvent event) {
        fireSidebarButton("#navProfile");
    }

    /**
     * Looks up a sidebar nav button by its fx:id selector and fires it.
     */
    private void fireSidebarButton(String selector) {
        Button btn = (Button) welcomeLabel.getScene().lookup(selector);
        if (btn != null) btn.fire();
    }

    /* ================================================================
       HELPERS
       ================================================================ */

    private String getInitials(User user) {
        String first = user.getFirstName();
        String last = user.getLastName();
        String initials = "";
        if (first != null && !first.isEmpty()) initials += first.charAt(0);
        if (last != null && !last.isEmpty()) initials += last.charAt(0);
        return initials.toUpperCase();
    }

    /* ================================================================
       HELPER CLASS FOR COUNTDOWN
       ================================================================ */

    /**
     * Wrapper class to hold a TestRequest with a live countdown property.
     */
    private static class TestRequestCountdown {
        private final int requestId;
        private final LocalDateTime expectedReadyAt;
        private final javafx.beans.property.SimpleStringProperty countdown;
        
        TestRequestCountdown(TestRequest request) {
            this.requestId = request.getId();
            this.expectedReadyAt = request.getExpectedReadyAt() != null 
                ? request.getExpectedReadyAt().toLocalDateTime() 
                : null;
            this.countdown = new javafx.beans.property.SimpleStringProperty();
            refreshCountdown();
        }
        
        int getRequestId() { return requestId; }
        
        javafx.beans.property.StringProperty countdownProperty() { return countdown; }
        
        void refreshCountdown() {
            if (expectedReadyAt == null) {
                countdown.set("--");
                return;
            }
            
            Duration remaining = Duration.between(LocalDateTime.now(), expectedReadyAt);
            if (remaining.isNegative() || remaining.isZero()) {
                countdown.set("Ready!");
            } else {
                long hours = remaining.toHours();
                long mins = remaining.toMinutesPart();
                long secs = remaining.toSecondsPart();
                countdown.set(String.format("%dh %02dm %02ds", hours, mins, secs));
            }
        }
    }
}