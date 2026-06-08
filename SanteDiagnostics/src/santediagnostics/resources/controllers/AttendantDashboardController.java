package santediagnostics.resources.controllers;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import santediagnostics.ResultService;
import santediagnostics.Sample;
import santediagnostics.SampleService;
import santediagnostics.Session;
import santediagnostics.TestRequest;
import santediagnostics.TestRequestService;
import santediagnostics.User;

/**
 * Controller for attendant-dashboard.fxml (Lab Attendant landing page).
 *
 * Mirrors the customer dashboard: dark welcome header, a 2x2 action-card grid
 * whose cards fire the matching sidebar nav buttons, and a workload stat row
 * (payments to confirm, samples to process, results to verify) loaded on a
 * background thread.
 */
public class AttendantDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label avatarInitials;

    @FXML private Label pendingPaymentsValue;
    @FXML private Label samplesToProcessValue;
    @FXML private Label resultsToUploadValue;

    private final TestRequestService requestService = new TestRequestService();
    private final SampleService sampleService = new SampleService();
    private final ResultService resultService = new ResultService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        User user = Session.getInstance().getCurrentUser();
        welcomeLabel.setText("Welcome " + user.getFullName());
        avatarInitials.setText(getInitials(user));
        loadStats();
    }

    /* ================================================================
       STATS  (background thread)
       ================================================================ */

    private void loadStats() {
        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() throws Exception {
                int payments = requestService.getQueueByPayment(
                        TestRequest.PAYMENT_UNPAID).size();

                int samples = sampleService.getByStatus(Sample.COLLECTED).size()
                        + sampleService.getByStatus(Sample.PROCESSING).size();

                int results = resultService.getVerificationQueue().size();

                return new int[]{payments, samples, results};
            }
        };

        task.setOnSucceeded(e -> {
            int[] c = task.getValue();
            pendingPaymentsValue.setText(String.valueOf(c[0]));
            samplesToProcessValue.setText(String.valueOf(c[1]));
            resultsToUploadValue.setText(String.valueOf(c[2]));
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("Attendant stats failed: "
                    + (ex == null ? "unknown" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    /* ================================================================
       CARD HANDLERS  -> fire the matching sidebar nav button
       ================================================================ */

    /* ================================================================
       STAT CARD HANDLERS  -> navigate to the matching section
       ================================================================ */

    @FXML
    private void handleStatPayments(MouseEvent event) {
        fireSidebarButton("#navRequestQueue");
    }

    @FXML
    private void handleStatSamples(MouseEvent event) {
        fireSidebarButton("#navSampleTracking");
    }

    @FXML
    private void handleStatResults(MouseEvent event) {
        fireSidebarButton("#navResultUpload");
    }

    @FXML
    private void handleRequestQueue(MouseEvent event) {
        fireSidebarButton("#navRequestQueue");
    }

    @FXML
    private void handleSampleTracking(MouseEvent event) {
        fireSidebarButton("#navSampleTracking");
    }

    @FXML
    private void handleResultUpload(MouseEvent event) {
        fireSidebarButton("#navResultUpload");
    }

    @FXML
    private void handleCatalog(MouseEvent event) {
        fireSidebarButton("#navCatalog");
    }

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
}
