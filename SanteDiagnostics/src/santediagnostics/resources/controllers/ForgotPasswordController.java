package santediagnostics.resources.controllers;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import santediagnostics.SceneManager;
import santediagnostics.VerificationService;

/**
 * Drives forgot-password.fxml.
 *
 * Two phases driven by visibility flags on the FXML:
 *   1) email entry  -> "Send code" -> VerificationService.startPasswordReset
 *   2) code + new password -> "Reset password" -> VerificationService.resetPassword
 *
 * To prevent user-enumeration the UI always shows a generic
 * "If that email is on file, a code has been sent" message regardless
 * of whether the email exists in the database.
 */
public class ForgotPasswordController implements Initializable {

    @FXML private TextField emailField;
    @FXML private Button sendCodeBtn;

    @FXML private VBox resetBox;          // hidden until a code is sent
    @FXML private TextField codeField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button resetBtn;

    @FXML private Label statusLabel;
    @FXML private StackPane overlayPane;

    private final VerificationService verifyService = new VerificationService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        overlayPane.setVisible(false);
        statusLabel.setVisible(false);

        resetBox.setVisible(false);
        resetBox.setManaged(false);

        sendCodeBtn.disableProperty().bind(emailField.textProperty().isEmpty());
        resetBtn.disableProperty().bind(
                codeField.textProperty().length().lessThan(6)
                        .or(newPasswordField.textProperty().length().lessThan(6))
                        .or(confirmPasswordField.textProperty().isEmpty())
        );

        codeField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.equals(newV.toUpperCase())) {
                codeField.setText(newV.toUpperCase());
            }
        });
    }

    // ----------------------------------------------------------------
    //  PHASE 1: send code
    // ----------------------------------------------------------------

    @FXML
    private void handleSendCode(ActionEvent event) {
        final String email = emailField.getText().trim();
        if (!isValidEmail(email)) {
            showError("Please enter a valid email address.");
            return;
        }

        overlayPane.setVisible(true);
        statusLabel.setVisible(false);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                verifyService.startPasswordReset(email);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            overlayPane.setVisible(false);
            // Generic message regardless of whether the email existed.
            showInfo("If that email is on file, a reset code has been sent.");
            revealResetPhase();
        });

        task.setOnFailed(e -> {
            overlayPane.setVisible(false);
            Logger.getLogger(ForgotPasswordController.class.getName())
                  .log(Level.SEVERE, "Send reset code failed", task.getException());
            showError("Could not send the code. Please try again later.");
        });

        new Thread(task).start();
    }

    private void revealResetPhase() {
        resetBox.setVisible(true);
        resetBox.setManaged(true);
        emailField.setDisable(true);
        sendCodeBtn.setDisable(true);
    }

    // ----------------------------------------------------------------
    //  PHASE 2: reset password
    // ----------------------------------------------------------------

    @FXML
    private void handleReset(ActionEvent event) {
        final String code     = codeField.getText().trim().toUpperCase();
        final String pwd      = newPasswordField.getText();
        final String confirm  = confirmPasswordField.getText();

        if (!pwd.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }
        if (pwd.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }

        overlayPane.setVisible(true);
        statusLabel.setVisible(false);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return verifyService.resetPassword(code, pwd);
            }
        };

        task.setOnSucceeded(e -> {
            overlayPane.setVisible(false);
            if (Boolean.TRUE.equals(task.getValue())) {
                showInfo("Password reset. Redirecting to login...");
                new Thread(() -> {
                    try { Thread.sleep(900); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> {
                        try {
                            SceneManager.switchTo("resources/views/login.fxml", false);
                        } catch (IOException ex) {
                            showError("Could not return to login.");
                        }
                    });
                }).start();
            } else {
                showError("Invalid or expired code. Try sending a new one.");
            }
        });

        task.setOnFailed(e -> {
            overlayPane.setVisible(false);
            Logger.getLogger(ForgotPasswordController.class.getName())
                  .log(Level.SEVERE, "Reset password failed", task.getException());
            showError("Could not reset password. Please try again.");
        });

        new Thread(task).start();
    }

    // ----------------------------------------------------------------
    //  BACK TO LOGIN
    // ----------------------------------------------------------------

    @FXML
    private void handleBackToLogin() {
        try {
            SceneManager.switchTo("resources/views/login.fxml", false);
        } catch (IOException ex) {
            Logger.getLogger(ForgotPasswordController.class.getName())
                  .log(Level.SEVERE, null, ex);
            showError("Could not return to login.");
        }
    }

    // ----------------------------------------------------------------
    //  HELPERS
    // ----------------------------------------------------------------

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            statusLabel.setStyle("-fx-text-fill: #C0392B;");
            statusLabel.setText(message);
            statusLabel.setVisible(true);
        });
    }

    private void showInfo(String message) {
        Platform.runLater(() -> {
            statusLabel.setStyle("-fx-text-fill: #3D9A38;");
            statusLabel.setText(message);
            statusLabel.setVisible(true);
        });
    }
}
