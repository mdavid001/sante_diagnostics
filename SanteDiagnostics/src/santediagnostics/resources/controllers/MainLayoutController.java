package santediagnostics.resources.controllers;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import santediagnostics.SceneManager;
import santediagnostics.Session;
import santediagnostics.User;

/**
 * Controller for main-layout.fxml — the shared app shell.
 *
 * Responsibilities:
 *  1. Read the current user's role from Session and show only
 *     the sidebar nav items that belong to that role.
 *  2. Populate the topbar with the user's name, role, and initials.
 *  3. Auto-load the correct default landing page for the role.
 *  4. Swap content pages into {@code contentArea} when nav items are clicked.
 *
 * Content pages are standalone FXML fragments — they don't know about
 * the sidebar or topbar. Each teammate builds their page in isolation;
 * integration is just adding a nav button + handler here.
 */
public class MainLayoutController implements Initializable {

    /* ========== Sidebar nav buttons ========== */
    @FXML private Button navDashboard;

    // Super Admin only
    @FXML private Button navTestBuilder;
    @FXML private Button navUserManagement;
    @FXML private Button navAuditTrail;

    // Admin + Lab Attendant
    @FXML private Button navRequestQueue;

    // Lab Attendant only
    @FXML private Button navSampleTracking;
    @FXML private Button navResultUpload;

    // Customer only
    @FXML private Button navCatalog;
    @FXML private Button navResultVault;
    @FXML private Button navProfile;
    @FXML private Button navActiveTests;

    /* ========== Topbar ========== */
    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;
    //@FXML private Label userInitialsLabel;
    @FXML private Label roleLabel;

    /* ========== Content region ========== */
    @FXML private StackPane contentArea;

    /** Tracks which nav button is currently highlighted. */
    private Button activeNavButton;

    /* ================================================================
       INITIALIZATION
       ================================================================ */

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        User user = Session.getInstance().getCurrentUser();

        // Populate the topbar user chip
        userNameLabel.setText(user.getFullName());
        userRoleLabel.setText(formatRole(user.getRole()));
        // userInitialsLabel.setText(getInitials(user));

        // Show/hide nav items per role, then load the default page
        if (user.isSuperAdmin()) {
            setupAdminNav();
        } else if (user.isLabAttendant()) {
            setupAttendantNav();
        } else {
            setupCustomerNav();
        }
    }

    /* ================================================================
       ROLE-BASED NAV SETUP
       Each method hides the buttons that don't apply, sets the
       section label, and loads the default landing page.
       ================================================================ */

    private void setupAdminNav() {
        roleLabel.setText("ADMIN");

        // Hide attendant-only and customer-only items
        hideNav(navSampleTracking);
        hideNav(navResultUpload);
        hideNav(navCatalog);
        hideNav(navResultVault);
        hideNav(navProfile);
        hideNav(navActiveTests);

        // Load admin dashboard as default landing page
        setActiveNav(navDashboard);
        setPageTitle("Dashboard", "System overview");
        loadContent("resources/views/admin-dashboard.fxml");
    }

    private void setupAttendantNav() {
        roleLabel.setText("LAB ATTENDANT");

        // Hide admin-only and customer-only items
        hideNav(navTestBuilder);
        hideNav(navAuditTrail);
        hideNav(navCatalog);
        hideNav(navResultVault);
        hideNav(navProfile);
        hideNav(navActiveTests);

        // Load attendant dashboard as default landing page
        setActiveNav(navDashboard);
        setPageTitle("Dashboard", "Lab operations overview");
        loadContent("resources/views/attendant-dashboard.fxml");
    }

    private void setupCustomerNav() {
        roleLabel.setText("CUSTOMER");

        // Hide admin-only and attendant-only items
        hideNav(navTestBuilder);
        hideNav(navUserManagement);
        hideNav(navAuditTrail);
        hideNav(navRequestQueue);
        hideNav(navSampleTracking);
        hideNav(navResultUpload);

        // Load customer dashboard as default landing page
        setActiveNav(navDashboard);
        setPageTitle("Dashboard", "Your test activity at a glance");
        loadContent("resources/views/customer-dashboard.fxml");
    }

    /* ================================================================
       NAV CLICK HANDLERS
       Each handler loads a content page, highlights the active button,
       and updates the topbar title/subtitle.
       ================================================================ */

    @FXML
    private void handleNavDashboard() {
        User user = Session.getInstance().getCurrentUser();
        setActiveNav(navDashboard);

        if (user.isSuperAdmin()) {
            setPageTitle("Dashboard", "System overview");
            loadContent("resources/views/admin-dashboard.fxml");
        } else if (user.isLabAttendant()) {
            setPageTitle("Dashboard", "Lab operations overview");
            loadContent("resources/views/attendant-dashboard.fxml");
        } else {
            setPageTitle("Dashboard", "Your test activity at a glance");
            loadContent("resources/views/customer-dashboard.fxml");
        }
    }

    // ----- Super Admin -----

    @FXML
    private void handleNavTestBuilder() {
        setActiveNav(navTestBuilder);
        setPageTitle("Test Builder", "Define test types, pricing, and turnaround times");
        loadContent("resources/views/test-builder.fxml");
    }

    @FXML
    private void handleNavUserManagement() {
        setActiveNav(navUserManagement);
        setPageTitle("User Management", "Create and manage staff and customer accounts");
        loadContent("resources/views/user-management.fxml");
    }

    @FXML
    private void handleNavAuditTrail() {
        setActiveNav(navAuditTrail);
        setPageTitle("Audit Trail", "Immutable log of all system actions");
        loadContent("resources/views/audit-trail.fxml");
    }

    // ----- Admin + Lab Attendant -----

    @FXML
    private void handleNavRequestQueue() {
        setActiveNav(navRequestQueue);
        setPageTitle("Test Requests", "View and manage incoming test requests");
        loadContent("resources/views/request-queue.fxml");
    }

    // ----- Lab Attendant -----

    @FXML
    private void handleNavSampleTracking() {
        setActiveNav(navSampleTracking);
        setPageTitle("Sample Tracking", "Track samples from collection to validation");
        loadContent("resources/views/sample-tracking.fxml");
    }

    @FXML
    private void handleNavResultUpload() {
        setActiveNav(navResultUpload);
        setPageTitle("Upload Results", "Attach reports and verify test results");
        loadContent("resources/views/result-upload.fxml");
    }

    // ----- Customer -----

    @FXML
    private void handleNavCatalog() {
        setActiveNav(navCatalog);
        setPageTitle("Browse Tests", "View available tests and place an order");
        loadContent("resources/views/test-catalog.fxml");
    }

    @FXML
    private void handleNavResultVault() {
        setActiveNav(navResultVault);
        setPageTitle("My Results", "View and download your test results");
        loadContent("resources/views/result-vault.fxml");
    }
    
    @FXML
    private void handleNavProfile(){
        setActiveNav(navProfile);
        setPageTitle("My Profile", "View your profile details");
        loadContent("resources/views/customer-profile.fxml");
    }
    
    @FXML
    private void handleNavActiveTests(){
        setActiveNav(navActiveTests);
        setPageTitle("Active Tests", "View your active tests details");
        loadContent("resources/views/active-tests.fxml");
    }

    // ----- Logout -----

    @FXML
    private void handleLogout() {
        try {
            Session.getInstance().logout();
            SceneManager.switchTo("resources/views/login.fxml", false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* ================================================================
       HELPER METHODS
       ================================================================ */

    /**
     * Loads an FXML file and places it into the content area.
     * The previous content is replaced entirely.
     *
     * If the target FXML doesn't exist yet (teammate hasn't built it),
     * a placeholder label is shown instead of crashing the app.
     */
    public void loadContent(String fxmlPath) {
        try {
            URL resource = getClass().getResource("/santediagnostics/" + fxmlPath);
            if (resource == null) {
                // Page not built yet — show a friendly placeholder
                Label placeholder = new Label("This page is coming soon.");
                placeholder.setStyle(
                    "-fx-font-size: 16px; -fx-text-fill: #6B7E84; -fx-padding: 40;"
                );
                contentArea.getChildren().setAll(placeholder);
                return;
            }
            Parent page = FXMLLoader.load(resource);
            contentArea.getChildren().setAll(page);
        } catch (IOException e) {
            e.printStackTrace();
            Label errorLabel = new Label("Failed to load page.");
            errorLabel.setStyle(
                "-fx-font-size: 14px; -fx-text-fill: #E5484D; -fx-padding: 40;"
            );
            contentArea.getChildren().setAll(errorLabel);
        }
    }

    /**
     * Highlights the given nav button and un-highlights the previous one.
     * Uses the "nav-active" CSS class defined in styles.css.
     */
    private void setActiveNav(Button button) {
        if (activeNavButton != null) {
            activeNavButton.getStyleClass().remove("nav-active");
        }
        button.getStyleClass().add("nav-active");
        activeNavButton = button;
    }

    /** Updates the topbar page title and subtitle. */
    private void setPageTitle(String title, String subtitle) {
        pageTitleLabel.setText(title);
        pageSubtitleLabel.setText(subtitle);
    }

    /**
     * Fully hides a nav button — invisible AND takes up no space.
     * Both flags are needed; setVisible(false) alone still reserves space.
     */
    private void hideNav(Node node) {
        node.setVisible(false);
        node.setManaged(false);
    }

    /** Turns "super_admin" into "Super Admin", etc. */
    private String formatRole(String role) {
        if (role == null) return "";
        return role.substring(0, 1).toUpperCase()
             + role.substring(1).replace("_", " ");
    }

    /** Returns first-letter initials, e.g. "JD" for John Doe. */
    /* private String getInitials(User user) {
        String first = user.getFirstName();
        String last = user.getLastName();
        String initials = "";
        if (first != null && !first.isEmpty()) initials += first.charAt(0);
        if (last != null && !last.isEmpty()) initials += last.charAt(0);
        return initials.toUpperCase();
    }*/
}
