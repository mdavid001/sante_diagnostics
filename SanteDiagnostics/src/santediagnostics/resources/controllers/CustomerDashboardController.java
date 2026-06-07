package santediagnostics.resources.controllers;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import santediagnostics.Session;
import santediagnostics.User;

/**
 * Controller for customer-dashboard.fxml.
 *
 * Handles:
 *  - Welcome header with user name and avatar initials.
 *  - Four navigation cards (Browse Tests, My Results, Active Tests, Profile).
 *  - Active Tests table with live countdown timers.
 *
 * TODO for teammates:
 *  - Replace placeholder data in loadData() with real DB queries
 *    using Task<> so the UI thread isn't blocked.
 */
public class CustomerDashboardController implements Initializable {

    /* ========== FXML bindings ========== */
    @FXML private Label welcomeLabel;
    @FXML private Label avatarInitials;

    @FXML private TableView<ActiveTest> activeTestsTable;
    @FXML private TableColumn<ActiveTest, String> colTestName;
    @FXML private TableColumn<ActiveTest, String> colOrderDate;
    @FXML private TableColumn<ActiveTest, String> colPaymentStatus;
    @FXML private TableColumn<ActiveTest, String> colSampleStatus;
    @FXML private TableColumn<ActiveTest, String> colCountdown;

    /** Timer that ticks every second to refresh countdowns. */
    private Timeline countdownTimeline;

    /* ================================================================
       INITIALIZATION
       ================================================================ */

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        User user = Session.getInstance().getCurrentUser();
        welcomeLabel.setText("Welcome " + user.getFullName());
        avatarInitials.setText(getInitials(user));

        setupActiveTestsTable();
        loadData();
        startCountdownTimer();
    }

    /* ================================================================
       TABLE SETUP
       ================================================================ */

    private void setupActiveTestsTable() {
        colTestName.setCellValueFactory(new PropertyValueFactory<>("testName"));
        colOrderDate.setCellValueFactory(new PropertyValueFactory<>("orderDate"));

        // Payment status with colored pill
        colPaymentStatus.setCellValueFactory(new PropertyValueFactory<>("paymentStatus"));
        colPaymentStatus.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            {
                pill.getStyleClass().add("status-pill");
            }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                } else {
                    pill.setText(status);
                    pill.getStyleClass().removeAll("pill-paid", "pill-unpaid");
                    pill.getStyleClass().add(
                        "Paid".equalsIgnoreCase(status) ? "pill-paid" : "pill-unpaid"
                    );
                    setGraphic(pill);
                }
            }
        });

        // Sample status with colored pill
        colSampleStatus.setCellValueFactory(new PropertyValueFactory<>("sampleStatus"));
        colSampleStatus.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            {
                pill.getStyleClass().add("status-pill");
            }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                } else {
                    pill.setText(status);
                    pill.getStyleClass().removeAll(
                        "pill-collected", "pill-processing", "pill-validating", "pill-pending"
                    );
                    switch (status.toLowerCase()) {
                        case "collected":  pill.getStyleClass().add("pill-collected"); break;
                        case "processing": pill.getStyleClass().add("pill-processing"); break;
                        case "validating": pill.getStyleClass().add("pill-validating"); break;
                        default:           pill.getStyleClass().add("pill-pending"); break;
                    }
                    setGraphic(pill);
                }
            }
        });

        // Countdown column — refreshed every second by the timeline
        colCountdown.setCellValueFactory(cd -> cd.getValue().countdownProperty());
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

    /* ================================================================
       DATA LOADING
       TODO: Replace with real DB queries wrapped in Task<>.
       ================================================================ */

    private void loadData() {
        /*
         * Replace this with a Task<> that queries the DB, e.g.:
         *
         *   Task<List<ActiveTest>> task = new Task<>() {
         *       @Override protected List<ActiveTest> call() {
         *           return YourDAO.getActiveTestsForUser(
         *               Session.getInstance().getCurrentUser().getId()
         *           );
         *       }
         *   };
         *   task.setOnSucceeded(e -> {
         *       activeTestsTable.setItems(
         *           FXCollections.observableArrayList(task.getValue())
         *       );
         *   });
         *   new Thread(task).start();
         */

        activeTestsTable.setItems(FXCollections.observableArrayList());
    }

    /* ================================================================
       COUNTDOWN TIMER
       Ticks every second, recalculates remaining time for each row.
       The TableView auto-refreshes because countdownProperty is observable.
       ================================================================ */

    private void startCountdownTimer() {
        countdownTimeline = new Timeline(new KeyFrame(
            javafx.util.Duration.seconds(1),
            event -> {
                for (ActiveTest test : activeTestsTable.getItems()) {
                    test.refreshCountdown();
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
       Each fires the matching sidebar nav button so MainLayoutController
       handles the page swap and highlights the active nav item.
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
        /*
         * TODO: This could navigate to a dedicated active-tests page,
         * or simply scroll to / highlight the table below. For now
         * it's a no-op since the table is already visible on this page.
         */
    }

    @FXML
    private void handleProfile(MouseEvent event) {
        /*
         * TODO: Navigate to a profile/settings page once it exists.
         * For now you could load a placeholder or show a dialog.
         */
    }

    /**
     * Looks up a sidebar nav button by its fx:id selector
     * and fires it, so MainLayoutController handles the switch.
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
       MODEL CLASS
       ================================================================ */

    /**
     * Represents an active (in-progress) test request.
     * The countdown property updates every second via the timeline.
     */
    public static class ActiveTest {
        private final SimpleStringProperty testName;
        private final SimpleStringProperty orderDate;
        private final SimpleStringProperty paymentStatus;
        private final SimpleStringProperty sampleStatus;
        private final SimpleStringProperty countdown;
        private final LocalDateTime estimatedReady;

        private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

        public ActiveTest(String testName, LocalDateTime orderDate,
                          String paymentStatus, String sampleStatus,
                          LocalDateTime estimatedReady) {
            this.testName = new SimpleStringProperty(testName);
            this.orderDate = new SimpleStringProperty(orderDate.format(FMT));
            this.paymentStatus = new SimpleStringProperty(paymentStatus);
            this.sampleStatus = new SimpleStringProperty(sampleStatus);
            this.estimatedReady = estimatedReady;
            this.countdown = new SimpleStringProperty();
            refreshCountdown();
        }

        public void refreshCountdown() {
            Duration remaining = Duration.between(LocalDateTime.now(), estimatedReady);
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
        public String getOrderDate()     { return orderDate.get(); }
        public String getPaymentStatus() { return paymentStatus.get(); }
        public String getSampleStatus()  { return sampleStatus.get(); }
        public String getCountdown()     { return countdown.get(); }
        public StringProperty countdownProperty() { return countdown; }
    }
}
