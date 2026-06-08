package santediagnostics.resources.controllers;

import java.math.BigDecimal;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import santediagnostics.Session;
import santediagnostics.Test;
import santediagnostics.TestService;
import santediagnostics.User;

/**
 * Controller for test-builder.fxml (Super Admin only).
 *
 * Lets the admin create, edit, and retire/reactivate test types.
 * Each test has: name, description, price (₦), turnaround hours, result format.
 */
public class TestBuilderController implements Initializable {

    // --- filter / toolbar ---
    @FXML private ToggleButton filterAll;
    @FXML private ToggleButton filterActive;
    @FXML private ToggleButton filterRetired;
    @FXML private TextField    searchField;
    @FXML private Label        countLabel;
    @FXML private Button       refreshBtn;

    // --- table ---
    @FXML private TableView<Test>          testTable;
    @FXML private TableColumn<Test,String> colName;
    @FXML private TableColumn<Test,String> colDescription;
    @FXML private TableColumn<Test,String> colPrice;
    @FXML private TableColumn<Test,String> colTat;
    @FXML private TableColumn<Test,String> colFormat;
    @FXML private TableColumn<Test,String> colStatus;
    @FXML private TableColumn<Test,Void>   colEdit;
    @FXML private TableColumn<Test,Void>   colToggle;

    // --- overlays ---
    @FXML private StackPane overlayPane;
    @FXML private StackPane formPopup;
    @FXML private StackPane confirmPopup;

    // --- form fields ---
    @FXML private Label     formTitle;
    @FXML private TextField nameField;
    @FXML private TextArea  descField;
    @FXML private TextField priceField;
    @FXML private Label tatValueLabel;
    @FXML private Button tatMinusBtn;
    @FXML private Button tatPlusBtn;

    // result format toggle buttons
    @FXML private ToggleButton fmtNumeric;
    @FXML private ToggleButton fmtText;
    @FXML private ToggleButton fmtPdf;
    @FXML private ToggleButton fmtImage;

    @FXML private Label formError;

    // --- confirm popup ---
    @FXML private Label confirmMessage;

    private final TestService testService = new TestService();

    /** null = creating new; non-null = editing existing */
    private Test editingTest = null;

    /** test pending retire/reactivate in confirm popup */
    private Test pendingToggle = null;

    private String currentFilter = "all";
    private ObservableList<Test> masterList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        loadTests();
    }

    // ----------------------------------------------------------------
    //  COLUMN SETUP
    // ----------------------------------------------------------------

    private void setupColumns() {
        colName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getName()));
        colDescription.setCellValueFactory(c -> {
            String d = c.getValue().getDescription();
            return new SimpleStringProperty(d == null || d.isBlank() ? "—" : d);
        });
        colPrice.setCellValueFactory(c -> {
            BigDecimal p = c.getValue().getPrice();
            return new SimpleStringProperty(p == null ? "—"
                    : String.format("\u20A6%,.2f", p.doubleValue()));
        });
        colTat.setCellValueFactory(c -> {
            int h = c.getValue().getTurnaroundHours();
            return new SimpleStringProperty(h < 24 ? h + "h" : (h / 24) + "d " + (h % 24) + "h");
        });
        colFormat.setCellValueFactory(c ->
                new SimpleStringProperty(prettyFormat(c.getValue().getResultFormat())));

        // Status pill
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActive() ? "Active" : "Retired"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("status-pill"); }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                pill.setText(v);
                pill.getStyleClass().removeAll("pill-paid", "pill-unpaid");
                pill.getStyleClass().add("Active".equals(v) ? "pill-paid" : "pill-unpaid");
                setGraphic(pill);
            }
        });

        // Edit button
        colEdit.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Edit");
            { btn.getStyleClass().add("table-action-btn"); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                Test t = getTableView().getItems().get(getIndex());
                if (t == null) { setGraphic(null); return; }
                btn.setOnAction(e -> openEditForm(t));
                setGraphic(btn);
            }
        });

        // Retire / Reactivate button
        colToggle.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            { btn.getStyleClass().add("table-action-btn"); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                Test t = getTableView().getItems().get(getIndex());
                if (t == null) { setGraphic(null); return; }
                btn.setText(t.isActive() ? "Retire" : "Reactivate");
                btn.setOnAction(e -> promptToggle(t));
                setGraphic(btn);
            }
        });
    }

    // ----------------------------------------------------------------
    //  LOAD DATA
    // ----------------------------------------------------------------

    private void loadTests() {
        setBusy(true);
        Task<List<Test>> task = new Task<>() {
            @Override protected List<Test> call() throws Exception {
                return testService.listAll();
            }
        };
        task.setOnSucceeded(e -> {
            masterList = FXCollections.observableArrayList(task.getValue());
            applyFilters();
            setBusy(false);
        });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not load tests: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });
        new Thread(task).start();
    }

    // ----------------------------------------------------------------
    //  FILTER + SEARCH
    // ----------------------------------------------------------------

    @FXML private void handleFilterAll()     { currentFilter = "all";     loadTests(); }
    @FXML private void handleFilterActive()  { currentFilter = "active";  loadTests(); }
    @FXML private void handleFilterRetired() { currentFilter = "retired"; loadTests(); }
    @FXML private void handleRefresh()       { loadTests(); }
    @FXML private void handleSearch()        { applyFilters(); }

    private void applyFilters() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();

        List<Test> filtered = masterList.stream()
                .filter(t -> {
                    switch (currentFilter) {
                        case "active":  return t.isActive();
                        case "retired": return !t.isActive();
                        default:        return true;
                    }
                })
                .filter(t -> query.isEmpty()
                        || t.getName().toLowerCase().contains(query)
                        || (t.getDescription() != null
                                && t.getDescription().toLowerCase().contains(query)))
                .collect(Collectors.toList());

        testTable.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " test" : " tests"));
    }

    // ----------------------------------------------------------------
    //  CREATE / EDIT FORM
    // ----------------------------------------------------------------

    @FXML
    private void handleOpenCreate() {
        editingTest = null;
        formTitle.setText("Add New Test");
        nameField.clear();
        descField.clear();
        priceField.clear();
        tatValueLabel.setText("");  // Empty, validation will catch it
        fmtNumeric.setSelected(true);
        fmtText.setSelected(false);
        fmtPdf.setSelected(false);
        fmtImage.setSelected(false);
        formError.setText("");
        formError.setVisible(false);
        formError.setManaged(false);
        showPopup(formPopup);
    }

    private void openEditForm(Test t) {
        editingTest = t;
        formTitle.setText("Edit Test");
        nameField.setText(t.getName());
        descField.setText(t.getDescription() == null ? "" : t.getDescription());
        priceField.setText(t.getPrice() == null ? "" : t.getPrice().toPlainString());
        tatValueLabel.setText(String.valueOf(t.getTurnaroundHours()));

        fmtNumeric.setSelected(Test.FORMAT_NUMERIC.equals(t.getResultFormat()));
        fmtText.setSelected(Test.FORMAT_TEXT.equals(t.getResultFormat()));
        fmtPdf.setSelected(Test.FORMAT_PDF.equals(t.getResultFormat()));
        fmtImage.setSelected(Test.FORMAT_IMAGE.equals(t.getResultFormat()));

        formError.setText("");
        formError.setVisible(false);
        formError.setManaged(false);
        showPopup(formPopup);
    }

    @FXML
    private void handleCancelForm() {
        hidePopup(formPopup);
        editingTest = null;
    }
      
    @FXML
    private void handleTatMinus() {
        try {
            int current = Integer.parseInt(tatValueLabel.getText());
            if (current > 1) {
                tatValueLabel.setText(String.valueOf(current - 1));
            }
        } catch (NumberFormatException e) {
            tatValueLabel.setText("24");
        }
    }

    @FXML
    private void handleTatPlus() {
        try {
            int current = Integer.parseInt(tatValueLabel.getText());
            tatValueLabel.setText(String.valueOf(current + 1));
        } catch (NumberFormatException e) {
            tatValueLabel.setText("24");
        }
    }
    
    @FXML
    private void handleSubmitForm() {
        String name = nameField.getText().trim();
        String desc = descField.getText().trim();
        String priceStr = priceField.getText().trim();
        String tatStr = tatValueLabel.getText().trim();
        String format   = selectedFormat();

        // client-side validation
        if (name.isEmpty()) { showFormError("Test name is required."); return; }
        if (priceStr.isEmpty()) { showFormError("Price is required."); return; }
        if (tatStr.isEmpty())   { showFormError("Turnaround time is required."); return; }
        if (format == null)     { showFormError("Please select a result format."); return; }

        BigDecimal price;
        int tat;
        try { price = new BigDecimal(priceStr); }
        catch (NumberFormatException ex) { showFormError("Price must be a valid number."); return; }
        try { tat = Integer.parseInt(tatStr); }
        catch (NumberFormatException ex) { showFormError("TAT must be a whole number of hours."); return; }

        User me = Session.getInstance().getCurrentUser();
        setBusy(true);
        hidePopup(formPopup);

        if (editingTest == null) {
            // CREATE
            BigDecimal fPrice = price; int fTat = tat; String fFormat = format;
            Task<Test> task = new Task<>() {
                @Override protected Test call() throws Exception {
                    return testService.createTest(name, desc.isEmpty() ? null : desc,
                            fPrice, fTat, fFormat, me == null ? null : me.getId());
                }
            };
            task.setOnSucceeded(e -> { setBusy(false); loadTests(); });
            task.setOnFailed(e -> {
                setBusy(false);
                Throwable ex = task.getException();
                showFormError(ex == null ? "Unknown error." : ex.getMessage());
                showPopup(formPopup);
            });
            new Thread(task).start();

        } else {
            // UPDATE
            Test updated = editingTest;
            updated.setName(name);
            updated.setDescription(desc.isEmpty() ? null : desc);
            updated.setPrice(price);
            updated.setTurnaroundHours(tat);
            updated.setResultFormat(format);

            Task<Boolean> task = new Task<>() {
                @Override protected Boolean call() throws Exception {
                    return testService.updateTest(updated, me == null ? -1 : me.getId());
                }
            };
            task.setOnSucceeded(e -> { setBusy(false); loadTests(); });
            task.setOnFailed(e -> {
                setBusy(false);
                Throwable ex = task.getException();
                showFormError(ex == null ? "Unknown error." : ex.getMessage());
                showPopup(formPopup);
            });
            new Thread(task).start();
            editingTest = null;
        }
    }

    // ----------------------------------------------------------------
    //  RETIRE / REACTIVATE CONFIRM POPUP
    // ----------------------------------------------------------------

    private void promptToggle(Test t) {
        pendingToggle = t;
        String action = t.isActive() ? "retire" : "reactivate";
        confirmMessage.setText("Are you sure you want to " + action
                + " \"" + t.getName() + "\"?"
                + (t.isActive()
                ? " Customers will no longer be able to order it."
                : " It will become available for ordering again."));
        showPopup(confirmPopup);
    }

    @FXML private void handleCancelConfirm() {
        hidePopup(confirmPopup);
        pendingToggle = null;
    }

    @FXML private void handleAcceptConfirm() {
        hidePopup(confirmPopup);
        Test t = pendingToggle;
        pendingToggle = null;
        if (t == null) return;

        User me = Session.getInstance().getCurrentUser();
        int actorId = me == null ? -1 : me.getId();
        setBusy(true);

        Task<Boolean> task = new Task<>() {
            @Override protected Boolean call() throws Exception {
                return t.isActive()
                        ? testService.retireTest(t.getId(), actorId)
                        : testService.reactivateTest(t.getId(), actorId);
            }
        };
        task.setOnSucceeded(e -> { setBusy(false); loadTests(); });
        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Operation failed: " + (ex == null ? "unknown error" : ex.getMessage()));
        });
        new Thread(task).start();
    }

    // ----------------------------------------------------------------
    //  FORMAT TOGGLE HELPERS
    // ----------------------------------------------------------------

    /** Returns the selected format string, or null if none selected. */
    private String selectedFormat() {
        if (fmtNumeric.isSelected()) return Test.FORMAT_NUMERIC;
        if (fmtText.isSelected())    return Test.FORMAT_TEXT;
        if (fmtPdf.isSelected())     return Test.FORMAT_PDF;
        if (fmtImage.isSelected())   return Test.FORMAT_IMAGE;
        return null;
    }

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
        Platform.runLater(() -> {
            formError.setText(msg);
            formError.setVisible(true);
            formError.setManaged(true);
        });
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            formError.setText(msg);
            formError.setVisible(true);
            formError.setManaged(true);
            showPopup(formPopup);
        });
    }

    private static String prettyFormat(String f) {
        if (f == null) return "—";
        switch (f) {
            case "numeric": return "Numeric";
            case "text":    return "Text";
            case "pdf":     return "PDF";
            case "image":   return "Image";
            default:        return f;
        }
    }
}
