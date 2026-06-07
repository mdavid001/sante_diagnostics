package santediagnostics.resources.controllers;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import santediagnostics.Session;
import santediagnostics.TestRequest;
import santediagnostics.TestRequestService;
import santediagnostics.User;

/**
 * Controller for request-queue.fxml (Lab Attendant).
 *
 * Lists test requests with a filter (All / Unpaid / Paid) and lets the
 * attendant confirm payment on unpaid rows. Confirming calls
 * TestRequestService.markPaid(requestId, attendantId), which sets
 * payment_status = 'paid', paid_by = attendant, paid_at = now() and writes
 * an audit entry.
 *
 * All DB work runs on background threads via Task<> so the UI never freezes.
 */
public class RequestQueueController implements Initializable {

    @FXML private javafx.scene.control.ToggleButton filterAll;
    @FXML private javafx.scene.control.ToggleButton filterUnpaid;
    @FXML private javafx.scene.control.ToggleButton filterPaid;
    @FXML private Label countLabel;
    @FXML private Button refreshBtn;

    @FXML private TableView<RequestRow> requestTable;
    @FXML private TableColumn<RequestRow, String> colId;
    @FXML private TableColumn<RequestRow, String> colCustomer;
    @FXML private TableColumn<RequestRow, String> colTest;
    @FXML private TableColumn<RequestRow, String> colAmount;
    @FXML private TableColumn<RequestRow, String> colPayment;
    @FXML private TableColumn<RequestRow, String> colStatus;
    @FXML private TableColumn<RequestRow, Void> colAction;

    @FXML private StackPane overlayPane;

    @FXML private StackPane confirmPopup;
    @FXML private Label confirmMessage;

    private final TestRequestService requestService = new TestRequestService();

    /** Which filter is active: "all" | "unpaid" | "paid". */
    private String currentFilter = "all";

    /** The row awaiting confirmation in the custom popup. */
    private RequestRow pendingRow;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        
        confirmMessage.setWrapText(true);
        confirmMessage.setMaxWidth(320);
        confirmMessage.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
    
        setupColumns();
        setupActionColumn();
        loadRequests();
    }

    /* ================================================================
       COLUMN SETUP
       ================================================================ */

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customer"));
        colTest.setCellValueFactory(new PropertyValueFactory<>("test"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colPayment.setCellValueFactory(new PropertyValueFactory<>("payment"));
        colPayment.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("status-pill"); }
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                } else {
                    pill.setText(capitalize(status));
                    pill.getStyleClass().removeAll("pill-paid", "pill-unpaid");
                    pill.getStyleClass().add(
                        "paid".equalsIgnoreCase(status) ? "pill-paid" : "pill-unpaid");
                    setGraphic(pill);
                }
            }
        });
    }

    /** The action column: a "Confirm Payment" button, shown only for unpaid rows. */
    private void setupActionColumn() {
    colAction.setCellFactory(col -> new TableCell<>() {
        private final Button btn = new Button("Confirm Payment");
        { btn.getStyleClass().add("table-action-btn"); }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) { setGraphic(null); return; }

            RequestRow row = getTableView().getItems().get(getIndex());
            if (row != null && TestRequest.PAYMENT_UNPAID.equalsIgnoreCase(row.getPayment())) {
                btn.setOnAction(e -> confirmPayment(row)); // row captured fresh here
                setGraphic(btn);
            } else {
                setGraphic(null);
            }
        }
    });
    }

    /* ================================================================
       DATA LOADING
       ================================================================ */

    private void loadRequests() {
        setBusy(true);

        Task<List<RequestRow>> task = new Task<>() {
            @Override
            protected List<RequestRow> call() throws Exception {
                List<TestRequest> requests;
                switch (currentFilter) {
                    case "unpaid":
                        requests = requestService.getQueueByPayment(TestRequest.PAYMENT_UNPAID);
                        break;
                    case "paid":
                        requests = requestService.getQueueByPayment(TestRequest.PAYMENT_PAID);
                        break;
                    default:
                        requests = requestService.getQueue();
                }

                List<RequestRow> rows = new ArrayList<>();
                for (TestRequest r : requests) {
                    rows.add(new RequestRow(
                            r.getId(),
                            r.getCustomerName(),
                            r.getTestName(),
                            r.getPriceAtOrder(),
                            r.getPaymentStatus(),
                            r.getRequestStatus()
                    ));
                }
                return rows;
            }
        };

        task.setOnSucceeded(e -> {
            List<RequestRow> rows = task.getValue();
            requestTable.setItems(FXCollections.observableArrayList(rows));
            countLabel.setText(rows.size() + (rows.size() == 1 ? " request" : " requests"));
            setBusy(false);
        });

        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not load requests: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    /* ================================================================
       CONFIRM PAYMENT
       ================================================================ */

    private void confirmPayment(RequestRow row) {
        if (row == null) return;

        User user = Session.getInstance().getCurrentUser();
        if (user == null) {
            showError("Your session has expired. Please log in again.");
            return;
        }

        pendingRow = row;
        confirmMessage.setText("Request " + row.getId() + " — " + row.getCustomer() + " "+ row.getAmount());
        showPopup(confirmPopup);
    }

    /** Cancel button on the custom confirm popup. */
    @FXML
    private void handleCancelConfirm() {
        hidePopup(confirmPopup);
        pendingRow = null;
    }

    /** "Yes, confirm" on the custom popup — performs the actual markPaid. */
    @FXML
    private void handleAcceptConfirm() {
        hidePopup(confirmPopup);

        RequestRow row = pendingRow;
        pendingRow = null;
        if (row == null) return;

        User user = Session.getInstance().getCurrentUser();
        if (user == null) {
            showError("Your session has expired. Please log in again.");
            return;
        }

        final int requestId = parseId(row.getId());
        final int attendantId = user.getId();
        setBusy(true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return requestService.markPaid(requestId, attendantId);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false);
            if (Boolean.TRUE.equals(task.getValue())) {
                loadRequests(); // refresh so the row moves to Paid
            } else {
                showError("This request could not be confirmed. "
                        + "It may already be marked as paid.");
                loadRequests();
            }
        });

        task.setOnFailed(e -> {
            setBusy(false);
            Throwable ex = task.getException();
            showError("Could not confirm payment: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    /* ================================================================
       FILTER HANDLERS
       ================================================================ */

    @FXML
    private void handleFilterAll() {
        currentFilter = "all";
        loadRequests();
    }

    @FXML
    private void handleFilterUnpaid() {
        currentFilter = "unpaid";
        loadRequests();
    }

    @FXML
    private void handleFilterPaid() {
        currentFilter = "paid";
        loadRequests();
    }

    @FXML
    private void handleRefresh() {
        loadRequests();
    }

    /* ================================================================
       HELPERS
       ================================================================ */

    private void setBusy(boolean busy) {
        overlayPane.setVisible(busy);
        overlayPane.setManaged(busy);
        refreshBtn.setDisable(busy);
    }

    private void showPopup(StackPane popup) {
        popup.setVisible(true);
        popup.setManaged(true);
    }

    private void hidePopup(StackPane popup) {
        popup.setVisible(false);
        popup.setManaged(false);
    }

    private int parseId(String display) {
        // Rows store the id as "#12"; strip the prefix.
        String digits = display.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? -1 : Integer.parseInt(digits);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void showError(String message) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait());
    }

    /* ================================================================
       ROW MODEL
       ================================================================ */

    public static class RequestRow {
        private final SimpleStringProperty id;
        private final SimpleStringProperty customer;
        private final SimpleStringProperty test;
        private final SimpleStringProperty amount;
        private final SimpleStringProperty payment;
        private final SimpleStringProperty status;

        public RequestRow(int id, String customer, String test,
                          BigDecimal amount, String payment, String status) {
            this.id = new SimpleStringProperty("#" + id);
            this.customer = new SimpleStringProperty(customer == null ? "" : customer);
            this.test = new SimpleStringProperty(test == null ? "" : test);
            this.amount = new SimpleStringProperty(formatAmount(amount));
            this.payment = new SimpleStringProperty(payment);
            this.status = new SimpleStringProperty(prettyStatus(status));
        }

        private static String formatAmount(BigDecimal a) {
            return a == null ? "\u2014" : String.format("\u20A6%,.2f", a.doubleValue());
        }

        private static String prettyStatus(String s) {
            if (s == null) return "";
            switch (s) {
                case "submitted":   return "Submitted";
                case "in_progress": return "In progress";
                case "completed":   return "Completed";
                case "cancelled":   return "Cancelled";
                default:            return s;
            }
        }

        public String getId()       { return id.get(); }
        public String getCustomer() { return customer.get(); }
        public String getTest()     { return test.get(); }
        public String getAmount()   { return amount.get(); }
        public String getPayment()  { return payment.get(); }
        public String getStatus()   { return status.get(); }
    }
}
