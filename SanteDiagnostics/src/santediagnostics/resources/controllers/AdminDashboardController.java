package santediagnostics.resources.controllers;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import santediagnostics.Session;
import santediagnostics.TestRequest;
import santediagnostics.TestRequestService;
import santediagnostics.TestService;
import santediagnostics.User;
import santediagnostics.UserService;

/**
 * Controller for admin-dashboard.fxml (Super Admin landing page).
 *
 * Mirrors the customer dashboard: dark welcome header, a 2x2 action-card grid
 * whose cards fire the matching sidebar nav buttons, and an overview stat row
 * (total users, active tests, open requests) loaded on a background thread.
 */
public class AdminDashboardController implements Initializable {

    @FXML private Label welcomeLabel;
    @FXML private Label avatarInitials;

    @FXML private Label totalUsersValue;
    @FXML private Label totalTestsValue;
    @FXML private Label openRequestsValue;

    private final UserService userService = new UserService();
    private final TestService testService = new TestService();
    private final TestRequestService requestService = new TestRequestService();

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
        final int myId = Session.getInstance().getCurrentUser().getId();

        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() throws Exception {
                int users = userService.listAll(myId).size() + 1; // +1 to count self
                int tests = testService.listAvailable().size();

                int open = 0;
                List<TestRequest> all = requestService.getQueue();
                for (TestRequest r : all) {
                    if (!TestRequest.STATUS_COMPLETED.equals(r.getRequestStatus())
                            && !TestRequest.STATUS_CANCELLED.equals(r.getRequestStatus())) {
                        open++;
                    }
                }
                return new int[]{users, tests, open};
            }
        };

        task.setOnSucceeded(e -> {
            int[] c = task.getValue();
            totalUsersValue.setText(String.valueOf(c[0]));
            totalTestsValue.setText(String.valueOf(c[1]));
            openRequestsValue.setText(String.valueOf(c[2]));
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("Admin stats failed: "
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
    private void handleStatUsers(MouseEvent event) {
        fireSidebarButton("#navUserManagement");
    }

    @FXML
    private void handleStatTests(MouseEvent event) {
        fireSidebarButton("#navTestBuilder");
    }

    @FXML
    private void handleStatRequests(MouseEvent event) {
        fireSidebarButton("#navRequestQueue");
    }

    @FXML
    private void handleUserManagement(MouseEvent event) {
        fireSidebarButton("#navUserManagement");
    }

    @FXML
    private void handleTestBuilder(MouseEvent event) {
        fireSidebarButton("#navTestBuilder");
    }

    @FXML
    private void handleAuditTrail(MouseEvent event) {
        fireSidebarButton("#navAuditTrail");
    }

    @FXML
    private void handleRequestQueue(MouseEvent event) {
        fireSidebarButton("#navRequestQueue");
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