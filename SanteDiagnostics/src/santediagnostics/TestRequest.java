package santediagnostics;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * A customer's order for one test. Mirrors a row in `test_requests`, plus two
 * convenience fields (customerName, testName) that the queue queries fill in
 * via a join so the UI doesn't have to look them up separately.
 */
public class TestRequest {

    public static final String STATUS_SUBMITTED   = "submitted";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED   = "completed";
    public static final String STATUS_CANCELLED   = "cancelled";

    public static final String PAYMENT_UNPAID = "unpaid";
    public static final String PAYMENT_PAID   = "paid";

    private int id;
    private int customerId;
    private int testId;
    private BigDecimal priceAtOrder;
    private String requestStatus;
    private String paymentStatus;
    private Integer paidBy;            // staff user id; null until paid
    private Timestamp paidAt;          // null until paid
    private Timestamp expectedReadyAt; // set when processing begins; drives countdown

    // Display helpers (populated by the join queries; may be null otherwise)
    private String customerName;
    private String testName;

    public TestRequest() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getTestId() {
        return testId;
    }

    public void setTestId(int testId) {
        this.testId = testId;
    }

    public BigDecimal getPriceAtOrder() {
        return priceAtOrder;
    }

    public void setPriceAtOrder(BigDecimal priceAtOrder) {
        this.priceAtOrder = priceAtOrder;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(String requestStatus) {
        this.requestStatus = requestStatus;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public boolean isPaid() {
        return PAYMENT_PAID.equals(paymentStatus);
    }

    public Integer getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(Integer paidBy) {
        this.paidBy = paidBy;
    }

    public Timestamp getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Timestamp paidAt) {
        this.paidAt = paidAt;
    }

    public Timestamp getExpectedReadyAt() {
        return expectedReadyAt;
    }

    public void setExpectedReadyAt(Timestamp expectedReadyAt) {
        this.expectedReadyAt = expectedReadyAt;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }
}
