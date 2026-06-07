package santediagnostics;

import javafx.beans.property.SimpleObjectProperty;

public class Session {

    private static final Session instance = new Session();

    private final SimpleObjectProperty<User> currentUser = new SimpleObjectProperty<>();

    private Session() {
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
