package santediagnostics.resources.controllers;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import santediagnostics.AuthService;
import santediagnostics.SceneManager;
import santediagnostics.Session;
import santediagnostics.User;
import santediagnostics.UserDao;
import santediagnostics.VerificationService;

public class LoginController implements Initializable {

    // ----- Login tab -----
    @FXML private TextField emailTextField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private FontAwesomeIconView eyeIcon;
    @FXML private Button continueBtn;

    // ----- Sign-up tab -----
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField signUpEmailField;
    @FXML private PasswordField signUpPasswordField;
    @FXML private TextField signUpVisiblePasswordField;
    @FXML private PasswordField signUpConfirmPasswordField;
    @FXML private TextField signUpVisibleConfirmPasswordField;
    @FXML private FontAwesomeIconView signUpEyeIcon;
    @FXML private FontAwesomeIconView signUpConfirmEyeIcon;
    @FXML private Label passwordErrorLabel;
    @FXML private Button submitBtn;

    // ----- Shared -----
    @FXML private TabPane tabPane;
    @FXML private Tab loginTab;
    @FXML private Tab signUpTab;
    @FXML private StackPane overlayPane;

    // ----- Custom error popup -----
    @FXML private StackPane errorPopup;
    @FXML private Label errorPopupTitle;
    @FXML private Label errorPopupMessage;

    private boolean showPassword;
    private boolean showSignUpPassword;
    private boolean showSignUpConfirmPassword;

    private final AuthService auth = new AuthService();
    private final UserDao userDao = new UserDao();
    private final VerificationService verifyService = new VerificationService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        overlayPane.setVisible(false);
        passwordErrorLabel.setVisible(false);

        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        signUpPasswordField.textProperty().bindBidirectional(signUpVisiblePasswordField.textProperty());
        signUpConfirmPasswordField.textProperty().bindBidirectional(signUpVisibleConfirmPasswordField.textProperty());

        continueBtn.disableProperty().bind(
                emailTextField.textProperty().isEmpty()
                        .or(passwordField.textProperty().isEmpty())
        );
        submitBtn.disableProperty().bind(
                signUpEmailField.textProperty().isEmpty()
                        .or(signUpPasswordField.textProperty().isEmpty())
                        .or(signUpConfirmPasswordField.textProperty().isEmpty())
                        .or(firstNameField.textProperty().isEmpty())
                        .or(lastNameField.textProperty().isEmpty())
        );

        signUpConfirmPasswordField.textProperty().addListener((obs, oldV, newV) -> {
            if (!signUpPasswordField.getText().equals(newV)) {
                passwordErrorLabel.setText("Passwords do not match");
                passwordErrorLabel.setVisible(true);
            } else {
                passwordErrorLabel.setVisible(false);
            }
        });
    }

    // ----------------------------------------------------------------
    //  LOGIN
    // ----------------------------------------------------------------

    @FXML
    private void handleLogin(ActionEvent event) {
        String email    = emailTextField.getText().trim();
        String password = passwordField.getText();

        if (!isValidEmail(email)) {
            showFieldError(emailTextField, "Please enter a valid email");
            return;
        }

        overlayPane.setVisible(true);

        Task<User> loginTask = new Task<>() {
            @Override
            protected User call() throws Exception {
                return auth.login(email, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            overlayPane.setVisible(false);
            User user = loginTask.getValue();

            if (user == null) {
                showErrorPopup("Incorrect Credentials",
                        "The email or password you entered is incorrect. Please try again.");
                return;
            }

            // --- Email verification gate ---
            // Customers self-register unverified. Block login until the
            // 6-character code from the verification email has been entered.
            // Staff-created accounts arrive with email_verified = TRUE, so
            // they sail through this check.
            try {
                if (!userDao.isEmailVerified(user.getId())) {
                    Session.getInstance().setPendingVerificationEmail(user.getEmail());
                    // Send a fresh code in the background so they don't have
                    // to hunt for an old one in their inbox.
                    String pending = user.getEmail();
                    new Thread(() -> {
                        try { verifyService.startEmailVerification(pending); }
                        catch (Exception sendEx) {
                            Logger.getLogger(LoginController.class.getName())
                                  .log(Level.WARNING, "Could not send verification email", sendEx);
                        }
                    }).start();
                    try {
                        SceneManager.switchTo("resources/views/verify-email.fxml", false);
                    } catch (IOException ex) {
                        showErrorPopup("Navigation Error",
                                "Could not open the verification screen.");
                    }
                    return;
                }
            } catch (SQLException ex) {
                Logger.getLogger(LoginController.class.getName())
                      .log(Level.SEVERE, null, ex);
                showErrorPopup("Login Failed",
                        "Could not check verification status. Please try again.");
                return;
            }

            Session.getInstance().setCurrentUser(user);

            try {
                if (user.mustChangePassword()) {
                    SceneManager.switchTo("resources/views/change-password.fxml", false);
                    return;
                }
                routeToDashboard(user);
            } catch (IOException ex) {
                Logger.getLogger(LoginController.class.getName())
                        .log(Level.SEVERE, null, ex);
                showErrorPopup("Navigation Error", "Could not open the dashboard.");
            }
        });

        loginTask.setOnFailed(e -> {
            overlayPane.setVisible(false);
            Throwable ex = loginTask.getException();
            if (ex instanceof IllegalStateException) {
                showErrorPopup("Account Deactivated", ex.getMessage());
            } else {
                showErrorPopup("Login Failed",
                        "A system error occurred. Please try again.");
            }
        });

        new Thread(loginTask).start();
    }

    private void routeToDashboard(User user) throws IOException {
        SceneManager.switchTo("resources/views/main-layout.fxml", true);
    }

    // ----------------------------------------------------------------
    //  SIGN UP
    // ----------------------------------------------------------------

    @FXML
    private void handleSignUp(ActionEvent event) {
        String firstName = firstNameField.getText().trim();
        String lastName  = lastNameField.getText().trim();
        String email     = signUpEmailField.getText().trim();
        String password  = signUpPasswordField.getText();
        String confirm   = signUpConfirmPasswordField.getText();

        if (!isValidEmail(email)) {
            showFieldError(signUpEmailField, "Please enter a valid email");
            return;
        }
        if (!password.equals(confirm)) {
            passwordErrorLabel.setText("Passwords do not match");
            passwordErrorLabel.setVisible(true);
            return;
        }
        if (password.length() < 6) {
            passwordErrorLabel.setText("Password must be at least 6 characters");
            passwordErrorLabel.setVisible(true);
            return;
        }

        overlayPane.setVisible(true);

        Task<AuthService.SignUpResult> signUpTask = new Task<>() {
            @Override
            protected AuthService.SignUpResult call() {
                return auth.registerCustomer(firstName, lastName, email, password);
            }
        };

        signUpTask.setOnSucceeded(e -> {
            switch (signUpTask.getValue()) {
                case SUCCESS -> {
                    // Account is in the DB but unverified. Generate + email
                    // the 6-character code, then route to the verify screen.
                    Session.getInstance().setPendingVerificationEmail(email);
                    Task<Void> sendCodeTask = new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                            verifyService.startEmailVerification(email);
                            return null;
                        }
                    };
                    sendCodeTask.setOnSucceeded(ev -> {
                        overlayPane.setVisible(false);
                        try {
                            SceneManager.switchTo("resources/views/verify-email.fxml", false);
                        } catch (IOException ex) {
                            showErrorPopup("Navigation Error",
                                    "Account created but could not open the verify screen.");
                        }
                    });
                    sendCodeTask.setOnFailed(ev -> {
                        overlayPane.setVisible(false);
                        // Still route to the verify screen -- the user can
                        // request a fresh code from there.
                        try {
                            SceneManager.switchTo("resources/views/verify-email.fxml", false);
                        } catch (IOException ex) {
                            showErrorPopup("Could Not Send Email",
                                    "Account created, but the verification email "
                                    + "could not be sent. Please contact support.");
                        }
                    });
                    new Thread(sendCodeTask).start();
                }
                case EMAIL_TAKEN -> {
                    overlayPane.setVisible(false);
                    showErrorPopup("Email Taken",
                            "An account with that email already exists.");
                }
                default -> {
                    overlayPane.setVisible(false);
                    showErrorPopup("Sign Up Failed",
                            "Something went wrong. Please try again.");
                }
            }
        });

        signUpTask.setOnFailed(e -> {
            overlayPane.setVisible(false);
            showErrorPopup("Sign Up Failed", "Something went wrong. Please try again.");
        });

        new Thread(signUpTask).start();
    }

    // ----------------------------------------------------------------
    //  ERROR POPUP
    // ----------------------------------------------------------------

    private void showErrorPopup(String title, String message) {
        Platform.runLater(() -> {
            errorPopupTitle.setText(title);
            errorPopupMessage.setText(message);
            errorPopup.setVisible(true);
            errorPopup.setManaged(true);
        });
    }

    @FXML
    private void handleDismissError() {
        errorPopup.setVisible(false);
        errorPopup.setManaged(false);
        // reset title colour in case success used it
        errorPopupTitle.setStyle("");
    }

    // ----------------------------------------------------------------
    //  PASSWORD SHOW / HIDE
    // ----------------------------------------------------------------

    @FXML private void handleShowPassword() {
        showPassword = !showPassword;
        togglePassword(showPassword, passwordField, visiblePasswordField, eyeIcon);
    }

    @FXML private void handleSignUpShowPassword() {
        showSignUpPassword = !showSignUpPassword;
        togglePassword(showSignUpPassword, signUpPasswordField,
                signUpVisiblePasswordField, signUpEyeIcon);
    }

    @FXML private void handleSignUpShowConfirmPassword() {
        showSignUpConfirmPassword = !showSignUpConfirmPassword;
        togglePassword(showSignUpConfirmPassword, signUpConfirmPasswordField,
                signUpVisibleConfirmPasswordField, signUpConfirmEyeIcon);
    }

    private void togglePassword(boolean show, PasswordField masked,
                                TextField visible, FontAwesomeIconView icon) {
        masked.setVisible(!show);
        masked.setManaged(!show);
        visible.setVisible(show);
        visible.setManaged(show);
        icon.setGlyphName(show ? FontAwesomeIcon.EYE_SLASH.name()
                : FontAwesomeIcon.EYE.name());
    }

    @FXML private void switchToSignUpTab() { tabPane.getSelectionModel().select(signUpTab); }
    @FXML private void switchToLoginTab()  { tabPane.getSelectionModel().select(loginTab);  }

    // ----------------------------------------------------------------
    //  HELPERS
    // ----------------------------------------------------------------

    private void showFieldError(TextField field, String message) {
        if (!field.getStyleClass().contains("field-error")) {
            field.getStyleClass().add("field-error");
        }
        field.setTooltip(new Tooltip(message));
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            field.getStyleClass().remove("field-error");
            field.setTooltip(null);
        });
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    }
}
