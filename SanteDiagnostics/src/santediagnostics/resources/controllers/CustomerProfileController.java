package santediagnostics.resources.controllers;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.mindrot.jbcrypt.BCrypt;
import santediagnostics.AuthService;
import santediagnostics.DatabaseConnection;
import santediagnostics.Session;
import santediagnostics.User;

/**
 * Controller for customer-profile.fxml.
 * Lets users view their info, edit their name, and change their password.
 */
public class CustomerProfileController implements Initializable {

    /* Profile header */
    @FXML private Label avatarInitials;
    @FXML private Label headerNameLabel;
    @FXML private Label headerEmailLabel;
    @FXML private Label headerRoleLabel;

    /* Edit profile fields */
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private Label profileMessage;

    /* Change password fields */
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordMessage;

    private final AuthService authService = new AuthService();

    /* ================================================================
       INITIALIZATION
       ================================================================ */

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        User user = Session.getInstance().getCurrentUser();

        // Header
        avatarInitials.setText(getInitials(user));
        headerNameLabel.setText(user.getFullName());
        headerEmailLabel.setText(user.getEmail());
        headerRoleLabel.setText(capitalise(user.getRole().replace("_", " ")));

        // Pre-fill edit fields
        firstNameField.setText(user.getFirstName());
        lastNameField.setText(user.getLastName());
        emailField.setText(user.getEmail());
    }

    /* ================================================================
       SAVE PROFILE (name update)
       ================================================================ */

    @FXML
    private void handleSaveProfile() {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();

        if (firstName.isEmpty() || lastName.isEmpty()) {
            showMessage(profileMessage, "Please fill in both name fields.", true);
            return;
        }

        User user = Session.getInstance().getCurrentUser();

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                String sql = "UPDATE users SET first_name = ?, last_name = ? WHERE id = ?";
                try (Connection conn = DatabaseConnection.getConnect();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, firstName);
                    stmt.setString(2, lastName);
                    stmt.setInt(3, user.getId());
                    return stmt.executeUpdate() > 0;
                }
            }
        };

        task.setOnSucceeded(e -> {
            if (task.getValue()) {
                // Update the session so the rest of the app reflects the change
                user.setFirstName(firstName);
                user.setLastName(lastName);

                // Update the header on this page
                headerNameLabel.setText(user.getFullName());
                avatarInitials.setText(getInitials(user));

                // Update the topbar user chip
                Label topbarName = (Label) headerNameLabel.getScene().lookup("#userNameLabel");
                Label topbarInitials = (Label) headerNameLabel.getScene().lookup("#userInitialsLabel");
                if (topbarName != null) topbarName.setText(user.getFullName());
                if (topbarInitials != null) topbarInitials.setText(getInitials(user));

                showMessage(profileMessage, "Profile updated successfully!", false);
            } else {
                showMessage(profileMessage, "Failed to update profile.", true);
            }
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            showMessage(profileMessage, "An error occurred. Please try again.", true);
        });

        new Thread(task).start();
    }

    /* ================================================================
       CHANGE PASSWORD
       ================================================================ */

    @FXML
    private void handleChangePassword() {
        String current = currentPasswordField.getText();
        String newPass = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            showMessage(passwordMessage, "Please fill in all password fields.", true);
            return;
        }

        if (newPass.length() < 6) {
            showMessage(passwordMessage, "New password must be at least 6 characters.", true);
            return;
        }

        if (!newPass.equals(confirm)) {
            showMessage(passwordMessage, "New passwords do not match.", true);
            return;
        }

        User user = Session.getInstance().getCurrentUser();

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // 1. Verify current password
                String sql = "SELECT password FROM users WHERE id = ?";
                String storedHash;
                try (Connection conn = DatabaseConnection.getConnect();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, user.getId());
                    ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) return "User not found.";
                    storedHash = rs.getString("password");
                }

                if (!BCrypt.checkpw(current, storedHash)) {
                    return "Current password is incorrect.";
                }

                // 2. Update to new password
                boolean updated = authService.changePassword(user.getId(), newPass);
                return updated ? "SUCCESS" : "Failed to update password.";
            }
        };

        task.setOnSucceeded(e -> {
            String result = task.getValue();
            if ("SUCCESS".equals(result)) {
                showMessage(passwordMessage, "Password changed successfully!", false);
                currentPasswordField.clear();
                newPasswordField.clear();
                confirmPasswordField.clear();
            } else {
                showMessage(passwordMessage, result, true);
            }
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            showMessage(passwordMessage, "An error occurred. Please try again.", true);
        });

        new Thread(task).start();
    }

    /* ================================================================
       HELPERS
       ================================================================ */

    private void showMessage(Label label, String text, boolean isError) {
        label.setText(text);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: " +
            (isError ? "#E5484D" : "#3D9A38") + ";");
    }

    private String getInitials(User user) {
        String initials = "";
        if (user.getFirstName() != null && !user.getFirstName().isEmpty())
            initials += user.getFirstName().charAt(0);
        if (user.getLastName() != null && !user.getLastName().isEmpty())
            initials += user.getLastName().charAt(0);
        return initials.toUpperCase();
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
