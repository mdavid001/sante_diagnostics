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
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import santediagnostics.SceneManager;
import santediagnostics.Session;
import santediagnostics.VerificationService;

/**
 * Drives verify-email.fxml. Reads the pending email from Session, lets the
 * user type the 6-character code from the inbox, calls VerificationService
 * to validate it, and routes back to login on success.
 *
 * The user reaches this screen from two paths:
 *   1. After self-registration -- LoginController.handleSignUp sends the
 *      first code and switches here.
 *   2. After trying to log in with an unverified account -- the login
 *      handler sends a fresh code and switches here instead of routing
 *      to the dashboard.
 *
 * The "Resend code" button re-runs VerificationService.startEmailVerification
 * so the user can request a fresh code if the first one was lost or expired.
 */
public class VerifyEmailController implements Initializable {

    @FXML private Label emailLabel;
    @FXML private TextField codeField;
    @FXML private Button verifyBtn;
    @FXML private Label statusLabel;
    @FXML private StackPane overlayPane;

    private final VerificationService verifyService = new VerificationService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        overlayPane.setVisible(false);
        statusLabel.setVisible(false);

        String pending = Session.getInstance().getPendingVerificationEmail();
        emailLabel.setText(pending != null ? pending : "(no email on file)");

        // Verify button only enabled once the user has typed 6 characters.
        verifyBtn.disableProperty().bind(
                codeField.textProperty().length().lessThan(6)
        );

        // Auto-uppercase as they type so they don't fail on lowercase input.
        codeField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.equals(newV.toUpperCase())) {
                codeField.setText(newV.toUpperCase());
            }
        });
    }

    // ----------------------------------------------------------------
    //  VERIFY
    // ----------------------------------------------------------------

    @FXML
    private void handleVerify(ActionEvent event) {
        final String code = codeField.getText().trim().toUpperCase();
        overlayPane.setVisible(true);
        statusLabel.setVisible(false);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return verifyService.verify(code);
            }
        };

        task.setOnSucceeded(e -> {
            overlayPane.setVisible(false);
            if (Boolean.TRUE.equals(task.getValue())) {
                Session.getInstance().setPendingVerificationEmail(null);
                showInfo("Email verified. Redirecting to login...");
                // Brief pause so the user reads the success message.
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
                showError("Invalid or expired code. Try resending.");
            }
        });

        task.setOnFailed(e -> {
            overlayPane.setVisible(false);
            Logger.getLogger(VerifyEmailController.class.getName())
                  .log(Level.SEVERE, "Verify failed", task.getException());
            showError("Verification failed. Please try again.");
        });

        new Thread(task).start();
    }

    // ----------------------------------------------------------------
    //  RESEND
    // ----------------------------------------------------------------

    @FXML
    private void handleResend() {
        final String email = Session.getInstance().getPendingVerificationEmail();
        if (email == null || email.isBlank()) {
            showError("No email on file. Please go back to login.");
            return;
        }
        overlayPane.setVisible(true);
        statusLabel.setVisible(false);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                verifyService.startEmailVerification(email);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            overlayPane.setVisible(false);
            showInfo("A new code has been sent to " + email + ".");
        });

        task.setOnFailed(e -> {
            overlayPane.setVisible(false);
            Logger.getLogger(VerifyEmailController.class.getName())
                  .log(Level.SEVERE, "Resend failed", task.getException());
            showError("Could not send a new code. Please try again later.");
        });

        new Thread(task).start();
    }

    // ----------------------------------------------------------------
    //  BACK TO LOGIN
    // ----------------------------------------------------------------

    @FXML
    private void handleBackToLogin() {
        Session.getInstance().setPendingVerificationEmail(null);
        try {
            SceneManager.switchTo("resources/views/login.fxml", false);
        } catch (IOException ex) {
            Logger.getLogger(VerifyEmailController.class.getName())
                  .log(Level.SEVERE, null, ex);
            showError("Could not return to login.");
        }
    }

    // ----------------------------------------------------------------
    //  HELPERS
    // ----------------------------------------------------------------

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
