package santediagnostics;

import java.sql.SQLException;
import java.util.List;

/**
 * Business rules for ordering tests and confirming payment. Controllers call
 * this class the way LoginController calls AuthService.
 *
 * Validation failures throw IllegalArgumentException (message safe to show the
 * user); database problems surface as SQLException.
 */
public class TestRequestService {

    private final TestRequestDao dao = new TestRequestDao();
    private final TestDao testDao = new TestDao();
    private final AuditService audit = new AuditService();

    /**
     * Places an order for a customer. Confirms the test exists and is still
     * available, then snapshots its current price into the request so a later
     * price change never alters this order.
     *
     * @return the created request, with id, statuses and test name populated.
     */
    public TestRequest placeOrder(int customerId, int testId) throws SQLException {
        if (customerId <= 0) {
            throw new IllegalArgumentException("A valid customer is required.");
        }

        Test test = testDao.findById(testId);
        if (test == null) {
            throw new IllegalArgumentException("That test does not exist.");
        }
        if (!test.isActive()) {
            throw new IllegalArgumentException("That test is no longer available.");
        }

        TestRequest r = new TestRequest();
        r.setCustomerId(customerId);
        r.setTestId(testId);
        r.setPriceAtOrder(test.getPrice());   // snapshot at order time

        int id = dao.create(r);
        if (id == -1) {
            throw new SQLException("Order could not be placed.");
        }

        r.setId(id);
        r.setRequestStatus(TestRequest.STATUS_SUBMITTED);
        r.setPaymentStatus(TestRequest.PAYMENT_UNPAID);
        r.setTestName(test.getName());
        audit.log(customerId, AuditService.ORDER_PLACED, AuditService.ENTITY_REQUEST, id);
        return r;
    }

    /**
     * Confirms payment for a request (staff action). Returns false if the
     * request was already paid or does not exist.
     *
     * @param confirmedByUserId the staff member's Session user id.
     */
    public boolean markPaid(int requestId, int confirmedByUserId) throws SQLException {
        if (requestId <= 0 || confirmedByUserId <= 0) {
            throw new IllegalArgumentException("A valid request and staff member are required.");
        }
        boolean ok = dao.markPaid(requestId, confirmedByUserId);
        if (ok) {
            audit.log(confirmedByUserId, AuditService.PAYMENT_CONFIRMED,
                    AuditService.ENTITY_REQUEST, requestId);
        }
        return ok;
    }

    /** Cancels a request. */
    public boolean cancelRequest(int requestId, int cancelledByUserId) throws SQLException {
        boolean ok = dao.updateStatus(requestId, TestRequest.STATUS_CANCELLED);
        if (ok) {
            audit.log(cancelledByUserId, AuditService.REQUEST_CANCELLED,
                    AuditService.ENTITY_REQUEST, requestId);
        }
        return ok;
    }

    public TestRequest getRequest(int requestId) throws SQLException {
        return dao.findById(requestId);
    }

    /** Full queue for staff, newest first. */
    public List<TestRequest> getQueue() throws SQLException {
        return dao.findAll();
    }

    /** Queue filtered to paid or unpaid. */
    public List<TestRequest> getQueueByPayment(String paymentStatus) throws SQLException {
        if (!TestRequest.PAYMENT_PAID.equals(paymentStatus)
                && !TestRequest.PAYMENT_UNPAID.equals(paymentStatus)) {
            throw new IllegalArgumentException("Payment status must be 'paid' or 'unpaid'.");
        }
        return dao.findByPaymentStatus(paymentStatus);
    }

    /** A customer's own orders (for their dashboard). */
    public List<TestRequest> getCustomerRequests(int customerId) throws SQLException {
        return dao.findByCustomer(customerId);
    }

    /** Bank details to display after an order is placed. */
    public BankDetails getBankDetails() throws SQLException {
        return dao.findBankDetails();
    }
}
