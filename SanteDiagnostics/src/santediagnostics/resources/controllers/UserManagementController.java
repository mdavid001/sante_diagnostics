package santediagnostics.resources.controllers;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import santediagnostics.Session;
import santediagnostics.User;
import santediagnostics.UserService;
import santediagnostics.UserService.UserRow;

public class UserManagementController implements Initializable {

    // --- filter bar ---
    @FXML private ToggleButton filterAll;
    @FXML private ToggleButton filterStaff;
    @FXML private ToggleButton filterCustomers;
    @FXML private Label countLabel;
    @FXML private Button refreshBtn;

    // --- search ---
    @FXML private TextField searchField;

    // --- table ---
    @FXML private TableView<UserRow> userTable;
    @FXML private TableColumn<UserRow, String> colName;
    @FXML private TableColumn<UserRow, String> colEmail;
    @FXML private TableColumn<UserRow, String> colRole;
    @FXML private TableColumn<UserRow, String> colStatus;
    @FXML private TableColumn<UserRow, String> colCreatedBy;
    @FXML private TableColumn<UserRow, Void>   colAction;

    // --- overlays ---
    @FXML private StackPane overlayPane;
    @FXML private StackPane createPopup;
    @FXML private StackPane successPopup;

    // --- create-user form ---
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private HBox      roleToggleRow;
    @FXML private ToggleButton roleAttendant;
    @FXML private ToggleButton roleCustomer;
    @FXML private Label     formError;

    // --- success card ---
    @FXML private Label successName;
    @FXML private Label successEmail;
    @FXML private Label successPassword;
    @FXML private Label successRole;

    private final UserService userService = new UserService();
    private String currentFilter = "all";

    /** Master list — never filtered. Search filters against this. */
    private ObservableList<UserRow> masterList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        User me = Session.getInstance().getCurrentUser();

        if (me != null && me.isLabAttendant()) {
            roleToggleRow.setVisible(false);
            roleToggleRow.setManaged(false);
            filterStaff.setVisible(false);
            filterStaff.setManaged(false);
        }

        setupColumns();
        loadUsers();
    }

    // ----------------------------------------------------------------
    //  COLUMNS
    // ----------------------------------------------------------------

    private void setupColumns() {
        colName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getFullName()));
        colEmail.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().email));
        colRole.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPrettyRole()));
        colCreatedBy.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().createdBy));

        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().active ? "Active" : "Inactive"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("status-pill"); }
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setGraphic(null); return; }
                pill.setText(val);
                pill.getStyleClass().removeAll("pill-paid", "pill-unpaid");
                pill.getStyleClass().add("Active".equals(val) ? "pill-paid" : "pill-unpaid");
                setGraphic(pill);
            }
        });

        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            { btn.getStyleClass().add("table-action-btn"); }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                UserRow row = getTableView().getItems().get(getIndex());
                if (row == null) { setGraphic(null); return; }
                btn.setText(row.active ? "Deactivate" : "Activate");
                btn.setOnAction(e -> handleToggleActive(row));
                setGraphic(btn);
            }
        });
    }

    // ----------------------------------------------------------------
    //  LOAD DATA
    // ----------------------------------------------------------------

    private void loadUsers() {
        setBusy(true);
        User me = Session.getInstance().getCurrentUser();
        if (me == null) { setBusy(false); return; }

        Task<List<UserRow>> task = new Task<>() {
            @Override
            protected List<UserRow> call() throws Exception {
                switch (currentFilter) {
                    case "staff":
                        return userService.listByRole(User.ROLE_LAB_ATTENDANT, me.getId());
                    case "customers":
                        return userService.listByRole(User.ROLE_CUSTOMER, me.getId());
                    default:
                        return userService.listAll(me.getId());
                }
            }
        };

        task.setOnSucceeded(e -> {
            masterList = FXCollections.observableArrayList(task.getValue());
            applySearch(); // applies current search text (or shows all if empty)
            setBusy(false);
        });

        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not load users: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    // ----------------------------------------------------------------
    //  SEARCH
    // ----------------------------------------------------------------

    @FXML
    private void handleSearch() {
        applySearch();
    }

    private void applySearch() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();

        List<UserRow> filtered;
        if (query.isEmpty()) {
            filtered = masterList;
        } else {
            filtered = masterList.stream()
                    .filter(r -> r.getFullName().toLowerCase().contains(query)
                              || r.email.toLowerCase().contains(query))
                    .collect(Collectors.toList());
        }

        userTable.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " user" : " users"));
    }

    // ----------------------------------------------------------------
    //  CREATE USER
    // ----------------------------------------------------------------

    @FXML
    private void handleOpenCreate() {
        firstNameField.clear();
        lastNameField.clear();
        emailField.clear();
        formError.setText("");
        formError.setVisible(false);
        formError.setManaged(false);
        roleCustomer.setSelected(true);
        showPopup(createPopup);
    }

    @FXML
    private void handleCancelCreate() {
        hidePopup(createPopup);
    }

    @FXML
    private void handleSubmitCreate() {
        String firstName = firstNameField.getText().trim();
        String lastName  = lastNameField.getText().trim();
        String email     = emailField.getText().trim();

        User me = Session.getInstance().getCurrentUser();
        String role;
        if (me != null && me.isLabAttendant()) {
            role = User.ROLE_CUSTOMER;
        } else {
            role = (roleAttendant != null && roleAttendant.isSelected())
                    ? User.ROLE_LAB_ATTENDANT : User.ROLE_CUSTOMER;
        }

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            showFormError("All fields are required.");
            return;
        }

        setBusy(true);
        hidePopup(createPopup);

        Task<UserService.CreateResult> task = new Task<>() {
            @Override
            protected UserService.CreateResult call() throws Exception {
                return userService.createUser(me, firstName, lastName, email, role);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false);
            showSuccessCard(task.getValue());
            loadUsers();
        });

        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showFormError(ex == null ? "Unknown error." : ex.getMessage());
            showPopup(createPopup);
        });

        new Thread(task).start();
    }

    // ----------------------------------------------------------------
    //  SUCCESS CARD
    // ----------------------------------------------------------------

    private void showSuccessCard(UserService.CreateResult result) {
        Platform.runLater(() -> {
            successName.setText(result.user.getFullName());
            successEmail.setText(result.user.getEmail());
            successPassword.setText(result.tempPassword);
            successRole.setText(prettyRole(result.user.getRole()));
            showPopup(successPopup);
        });
    }

    @FXML
    private void handleDismissSuccess() {
        hidePopup(successPopup);
    }

    // ----------------------------------------------------------------
    //  TOGGLE ACTIVE
    // ----------------------------------------------------------------

    private void handleToggleActive(UserRow row) {
        setBusy(true);
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return userService.toggleActive(row.id);
            }
        };
        task.setOnSucceeded(e -> { setBusy(false); loadUsers(); });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not update user: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });
        new Thread(task).start();
    }

    // ----------------------------------------------------------------
    //  FILTER HANDLERS
    // ----------------------------------------------------------------

    @FXML private void handleFilterAll()       { currentFilter = "all";       loadUsers(); }
    @FXML private void handleFilterStaff()     { currentFilter = "staff";     loadUsers(); }
    @FXML private void handleFilterCustomers() { currentFilter = "customers"; loadUsers(); }
    @FXML private void handleRefresh()         { loadUsers(); }

    // ----------------------------------------------------------------
    //  HELPERS
    // ----------------------------------------------------------------

    private void setBusy(boolean busy) {
        overlayPane.setVisible(busy);
        overlayPane.setManaged(busy);
        refreshBtn.setDisable(busy);
    }

    private void showPopup(StackPane p) { p.setVisible(true);  p.setManaged(true);  }
    private void hidePopup(StackPane p) { p.setVisible(false); p.setManaged(false); }

    private void showFormError(String msg) {
        formError.setText(msg);
        formError.setVisible(true);
        formError.setManaged(true);
    }

    private void showError(String msg) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait());
    }

    private static String prettyRole(String role) {
        if (role == null) return "";
        switch (role) {
            case "super_admin":   return "Super Admin";
            case "lab_attendant": return "Lab Attendant";
            case "customer":      return "Customer";
            default:              return role;
        }
    }
}
