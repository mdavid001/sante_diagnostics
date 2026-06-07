package santediagnostics.resources.controllers;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import santediagnostics.AuthService;
import santediagnostics.SceneManager;
import santediagnostics.Session;
import santediagnostics.User;

/**
 * Drives change-password.fxml. Reached only when a staff-created account logs
 * in for the first time (must_change_password = true). The user cannot proceed
 * to their dashboard until they set a new password.
 */
public class ChangePasswordController implements Initializable {

    @FXML private Label subtitleLabel;
    @FXML private PasswordField newPasswordField;
    @FXML private TextField newVisibleField;
    @FXML private FontAwesomeIconView newEyeIcon;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField confirmVisibleField;
    @FXML private FontAwesomeIconView confirmEyeIcon;
    @FXML private Label errorLabel;
    @FXML private Button saveBtn;
    @FXML private StackPane overlayPane;

    private boolean showNew;
    private boolean showConfirm;

    private final AuthService auth = new AuthService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        overlayPane.setVisible(false);
        errorLabel.setVisible(false);

        User user = Session.getInstance().getCurrentUser();
        if (user != null) {
            subtitleLabel.setText("Welcome " + user.getFirstName()
                    + ". For your security, please set a new password before continuing.");
        }

        newPasswordField.textProperty().bindBidirectional(newVisibleField.textProperty());
        confirmPasswordField.textProperty().bindBidirectional(confirmVisibleField.textProperty());

        saveBtn.disableProperty().bind(
                newPasswordField.textProperty().isEmpty()
                        .or(confirmPasswordField.textProperty().isEmpty())
        );

        confirmPasswordField.textProperty().addListener((obs, oldV, newV) -> {
            if (!newPasswordField.getText().equals(newV)) {
                errorLabel.setText("Passwords do not match");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
            }
        });
    }

    @FXML
    private void handleSave(ActionEvent event) {
        String newPass = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (newPass.length() < 6) {
            errorLabel.setText("Password must be at least 6 characters");
            errorLabel.setVisible(true);
            return;
        }
        if (!newPass.equals(confirm)) {
            errorLabel.setText("Passwords do not match");
            errorLabel.setVisible(true);
            return;
        }

        User user = Session.getInstance().getCurrentUser();
        if (user == null) {
            showError("Session expired. Please log in again.");
            return;
        }

        overlayPane.setVisible(true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return auth.changePassword(user.getId(), newPass);
            }
        };

        task.setOnSucceeded(e -> {
            overlayPane.setVisible(false);
            if (Boolean.TRUE.equals(task.getValue())) {
                user.setMustChangePassword(false);
                try {
                    routeToDashboard(user);
                } catch (IOException ex) {
                    Logger.getLogger(ChangePasswordController.class.getName())
                            .log(Level.SEVERE, null, ex);
                    showError("Password updated, but the dashboard failed to open. "
                            + "Please log in again.");
                }
            } else {
                showError("Could not update password. Please try again.");
            }
        });

        task.setOnFailed(e -> {
            overlayPane.setVisible(false);
            showError("Could not update password: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void routeToDashboard(User user) throws IOException {
        
        SceneManager.switchTo("resources/views/main-layout.fxml", true); // works through MainController
        
    /*    String target;
        if (user.isSuperAdmin()) {
            target = "resources/views/admin-dashboard.fxml";
        } else if (user.isLabAttendant()) {
            target = "resources/views/attendant-dashboard.fxml";
        } else {
            target = "resources/views/customer-dashboard.fxml";
        }
        SceneManager.switchTo(target, true);
    */
    }

    @FXML
    private void handleShowNew() {
        showNew = !showNew;
        toggle(showNew, newPasswordField, newVisibleField, newEyeIcon);
    }

    @FXML
    private void handleShowConfirm() {
        showConfirm = !showConfirm;
        toggle(showConfirm, confirmPasswordField, confirmVisibleField, confirmEyeIcon);
    }

    private void toggle(boolean show, PasswordField masked,
                        TextField visible, FontAwesomeIconView icon) {
        masked.setVisible(!show);
        masked.setManaged(!show);
        visible.setVisible(show);
        visible.setManaged(show);
        icon.setGlyphName(show ? FontAwesomeIcon.EYE_SLASH.name()
                : FontAwesomeIcon.EYE.name());
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }
}
