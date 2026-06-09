package santediagnostics.resources.controllers;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import santediagnostics.BankDetails;
import santediagnostics.Session;
import santediagnostics.Test;
import santediagnostics.TestRequestService;
import santediagnostics.TestService;
import santediagnostics.User;

/**
 * Controller for test-catalog.fxml.
 *
 * Flow: Browse cards -> Click card -> Order popup (qty) -> Bank popup -> Success.
 *
 * Now wired to the database via the service layer:
 *  - loadTests()          reads all tests from TestService.listAll()
 *                         (active show as orderable, retired as "Unavailable")
 *  - handlePlaceOrder()   creates real rows via TestRequestService.placeOrder()
 *  - the bank popup        is filled from lab_settings via getBankDetails()
 *
 * Note on quantity: the schema stores one test per request (no quantity
 * column), so ordering a quantity of N places N separate requests for the same
 * test. The spinner still drives the displayed total.
 */
public class TestCatalogController implements Initializable {

    @FXML private TextField searchField;
    @FXML private Label testCountLabel;
    @FXML private VBox testCardsPane;

    /* Popup 1: Order details */
    @FXML private StackPane orderPopup;
    @FXML private Label popupTestName;
    @FXML private Label popupTestPrice;
    @FXML private Button qtyMinusBtn;
    @FXML private Label qtyValueLabel;
    @FXML private Button qtyPlusBtn;
    @FXML private Label popupTotalLabel;

    /** Current quantity in the order popup (1–20). */
    private int quantity = 1;
    private static final int MIN_QTY = 1;
    private static final int MAX_QTY = 20;

    /* Popup 2: Bank details */
    @FXML private StackPane bankPopup;
    @FXML private Label bankAmountLabel;
    @FXML private Label bankNameLabel;
    @FXML private Label accountNameLabel;
    @FXML private Label accountNumberLabel;

    /* Popup 3: Success */
    @FXML private StackPane successPopup;

    /* Services */
    private final TestService testService = new TestService();
    private final TestRequestService requestService = new TestRequestService();

    /** All loaded tests. */
    private List<TestItem> allTests = new ArrayList<>();

    /** Currently selected test for ordering. */
    private TestItem selectedTest;

    /** Bank details cached after first load. */
    private BankDetails bankDetails;

    /* ================================================================
       INITIALIZATION
       ================================================================ */

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterCards(newVal));

        loadTests();
        loadBankDetails();
    }

    /* ================================================================
       DATA LOADING  (real DB queries on background threads)
       ================================================================ */

    /** Loads orderable tests from the database. */
    private void loadTests() {
        testCountLabel.setText("Loading...");

        Task<List<TestItem>> task = new Task<>() {
            @Override
            protected List<TestItem> call() throws Exception {
                List<Test> tests = testService.listAll();
                List<TestItem> items = new ArrayList<>();
                for (Test t : tests) {
                    items.add(new TestItem(
                            t.getId(),
                            t.getName(),
                            t.getPrice() == null ? 0 : t.getPrice().doubleValue(),
                            t.isActive() ? "Available" : "Unavailable"
                    ));
                }
                return items;
            }
        };

        task.setOnSucceeded(e -> {
            allTests = task.getValue();
            buildCards(allTests);
            testCountLabel.setText(allTests.size() + " tests");
        });

        task.setOnFailed(e -> {
            allTests = new ArrayList<>();
            buildCards(allTests);
            testCountLabel.setText("0 tests");
            Throwable ex = task.getException();
            showError("Could not load tests: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    /** Loads the lab's bank account from lab_settings for the payment popup. */
    private void loadBankDetails() {
        Task<BankDetails> task = new Task<>() {
            @Override
            protected BankDetails call() throws Exception {
                return requestService.getBankDetails();
            }
        };

        task.setOnSucceeded(e -> bankDetails = task.getValue());
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("Could not load bank details: "
                    + (ex == null ? "unknown" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    /* ================================================================
       CARD BUILDING
       ================================================================ */

    private void buildCards(List<TestItem> tests) {
        testCardsPane.getChildren().clear();
        for (TestItem test : tests) {
            testCardsPane.getChildren().add(createTestCard(test));
        }
    }

    private VBox createTestCard(TestItem test) {
        boolean available = "Available".equalsIgnoreCase(test.status);

        Label statusPill = new Label(test.status);
        statusPill.getStyleClass().add("status-pill");
        if (available) {
            statusPill.setStyle("-fx-background-color: #E3F5E1; -fx-text-fill: #3D9A38;");
        } else {
            statusPill.setStyle("-fx-background-color: #FFF3E0; -fx-text-fill: #E68A00;");
        }

        Label nameLabel = new Label(test.name);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #11242A;");

        Label priceLabel = new Label(formatPrice(test.price));
        priceLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #16B0A6;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(nameLabel, spacer, priceLabel, statusPill);

        VBox card = new VBox();
        card.setPadding(new Insets(16, 22, 16, 22));
        card.getChildren().add(row);

        if (available) {
            card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-border-color: #D4DEDE; -fx-border-radius: 12; -fx-border-width: 1.2; " +
                "-fx-cursor: hand;"
            );
            card.setOnMouseClicked(e -> openOrderPopup(test));
            card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-border-color: #16B0A6; -fx-border-radius: 12; -fx-border-width: 1.2; " +
                "-fx-cursor: hand;"
            ));
            card.setOnMouseExited(e -> card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-border-color: #D4DEDE; -fx-border-radius: 12; -fx-border-width: 1.2; " +
                "-fx-cursor: hand;"
            ));
        } else {
            card.setStyle(
                "-fx-background-color: #F4F8F8; -fx-background-radius: 12; " +
                "-fx-border-color: #E4EDED; -fx-border-radius: 12; -fx-border-width: 1.2; " +
                "-fx-opacity: 0.7;"
            );
        }

        return card;
    }

    /* ================================================================
       SEARCH / FILTER
       ================================================================ */

    private void filterCards(String query) {
        if (query == null || query.isEmpty()) {
            buildCards(allTests);
            testCountLabel.setText(allTests.size() + " tests");
            return;
        }

        String lower = query.toLowerCase();
        List<TestItem> filtered = new ArrayList<>();
        for (TestItem test : allTests) {
            if (test.name.toLowerCase().contains(lower)) {
                filtered.add(test);
            }
        }
        buildCards(filtered);
        testCountLabel.setText(filtered.size() + " tests");
    }

    /* ================================================================
       POPUP FLOW
       ================================================================ */

    private void openOrderPopup(TestItem test) {
        selectedTest = test;
        popupTestName.setText(test.name);
        popupTestPrice.setText(formatPrice(test.price));
        quantity = 1;
        refreshQuantity();
        showPopup(orderPopup);
    }

    /* ---- Quantity stepper ---- */

    @FXML
    private void handleQtyMinus() {
        if (quantity > MIN_QTY) {
            quantity--;
            refreshQuantity();
        }
    }

    @FXML
    private void handleQtyPlus() {
        if (quantity < MAX_QTY) {
            quantity++;
            refreshQuantity();
        }
    }

    /** Updates the qty label, the total, and disables the buttons at the bounds. */
    private void refreshQuantity() {
        qtyValueLabel.setText(String.valueOf(quantity));
        if (selectedTest != null) {
            popupTotalLabel.setText(formatPrice(selectedTest.price * quantity));
        }
        qtyMinusBtn.setDisable(quantity <= MIN_QTY);
        qtyPlusBtn.setDisable(quantity >= MAX_QTY);
    }

    @FXML
    private void handleCloseOrderPopup() {
        hidePopup(orderPopup);
        selectedTest = null;
    }

    @FXML
    private void handlePlaceOrder() {
        if (selectedTest == null) return;

        User user = Session.getInstance().getCurrentUser();
        if (user == null) {
            showError("Your session has expired. Please log in again.");
            return;
        }
        if (!user.isCustomer()) {
            // Only customers may place orders. Staff hitting this through
            // any unintended path gets a hard refusal rather than a silent
            // staff-as-customer row in test_requests.
            showError("Only customer accounts can place test orders.");
            return;
        }

        final int customerId = user.getId();
        final int testId = selectedTest.id;
        final int qty = quantity;
        final double total = selectedTest.price * qty;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // One row per unit, since the schema stores one test per request.
                for (int i = 0; i < qty; i++) {
                    requestService.placeOrder(customerId, testId);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            hidePopup(orderPopup);
            populateBankPopup(total);
            showPopup(bankPopup);
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            showError("Could not place your order: "
                    + (ex == null ? "unknown error" : ex.getMessage()));
        });

        new Thread(task).start();
    }

    /** Fills the bank popup from the loaded lab_settings details + amount. */
    private void populateBankPopup(double amount) {
        bankAmountLabel.setText(formatPrice(amount));

        if (bankDetails != null) {
            bankNameLabel.setText(nz(bankDetails.getBankName()));
            accountNameLabel.setText(nz(bankDetails.getAccountName()));
            accountNumberLabel.setText(nz(bankDetails.getAccountNumber()));
        } else {
            // Bank details not loaded yet — try once more, then fall back.
            try {
                bankDetails = requestService.getBankDetails();
                if (bankDetails != null) {
                    bankNameLabel.setText(nz(bankDetails.getBankName()));
                    accountNameLabel.setText(nz(bankDetails.getAccountName()));
                    accountNumberLabel.setText(nz(bankDetails.getAccountNumber()));
                }
            } catch (Exception ex) {
                bankNameLabel.setText("\u2014");
                accountNameLabel.setText("Unavailable");
                accountNumberLabel.setText("\u2014");
            }
        }
    }

    @FXML
    private void handlePaymentConfirmed() {
        hidePopup(bankPopup);
        showPopup(successPopup);
    }

    @FXML
    private void handleBackToTests() {
        hidePopup(successPopup);
        selectedTest = null;
        // Refresh so any newly-placed orders are reflected if the catalog grows.
        loadTests();
    }

    /* ================================================================
       HELPERS
       ================================================================ */

    private void showPopup(StackPane popup) {
        popup.setVisible(true);
        popup.setManaged(true);
    }

    private void hidePopup(StackPane popup) {
        popup.setVisible(false);
        popup.setManaged(false);
    }

    private String formatPrice(double amount) {
        return String.format("\u20A6%,.2f", amount);
    }

    private static String nz(String s) {
        return s == null ? "\u2014" : s;
    }

    private void showError(String message) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait());
    }

    /* ================================================================
       MODEL CLASS
       ================================================================ */

    public static class TestItem {
        public final int id;
        public final String name;
        public final double price;
        public final String status;

        public TestItem(int id, String name, double price, String status) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.status = status;
        }
    }
}
