package santediagnostics;

import javafx.beans.property.SimpleObjectProperty;

public class Session {

    private static final Session instance = new Session();

    private final SimpleObjectProperty<User> currentUser = new SimpleObjectProperty<>();

    /**
     * Email address awaiting verification. Set by the signup flow and the
     * login flow (when an unverified user tries to log in) before routing
     * to the verify-email screen. Cleared on successful verification or
     * when the user navigates back to login.
     */
    private String pendingVerificationEmail;

    private Session() {
    }

    public String getPendingVerificationEmail() {
        return pendingVerificationEmail;
    }

    public void setPendingVerificationEmail(String email) {
        this.pendingVerificationEmail = email;
    }

    public static Session getInstance() {
        return instance;
    }

    public SimpleObjectProperty<User> currentUserProperty() {
        return this.currentUser;
    }

    public User getCurrentUser() {
        return this.currentUser.get();
    }

    public void setCurrentUser(User user) {
        currentUser.set(user);
    }

    public void logout() {
        currentUser.set(null);
    }
}
