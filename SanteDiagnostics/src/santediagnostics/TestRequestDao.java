package santediagnostics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the `test_requests` table. Read queries join `users` and
 * `tests` so the queue screens can show the customer and test names directly.
 *
 * On create we insert only customer_id, test_id and price_at_order; the
 * database defaults set request_status='submitted' and payment_status='unpaid'.
 */
public class TestRequestDao {

    /** Shared SELECT with the joins the queue/dashboard screens need. */
    private static final String SELECT_BASE =
            "SELECT r.id, r.customer_id, r.test_id, r.price_at_order, "
            + "       r.request_status, r.payment_status, r.paid_by, r.paid_at, "
            + "       r.expected_ready_at, "
            + "       u.first_name, u.last_name, t.name AS test_name "
            + "FROM test_requests r "
            + "JOIN users u ON u.id = r.customer_id "
            + "JOIN tests t ON t.id = r.test_id ";

    /** Places a new order and returns its generated id (-1 on failure). */
    public int create(TestRequest r) throws SQLException {
        String sql = "INSERT INTO test_requests (customer_id, test_id, price_at_order) "
                + "VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, r.getCustomerId());
            stmt.setInt(2, r.getTestId());
            stmt.setBigDecimal(3, r.getPriceAtOrder());

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Marks an unpaid request as paid, recording who confirmed it and when.
     * The "AND payment_status = 'unpaid'" guard makes this safe to call twice:
     * a second call changes nothing and returns false.
     */
    public boolean markPaid(int requestId, int paidByUserId) throws SQLException {
        String sql = "UPDATE test_requests "
                + "SET payment_status = 'paid', paid_by = ?, paid_at = now() "
                + "WHERE id = ? AND payment_status = 'unpaid'";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, paidByUserId);
            stmt.setInt(2, requestId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Called when the sample is collected: stamps the request's
     * expected_ready_at (now + the test's turnaround) and moves it to
     * 'in_progress'. expected_ready_at is what the customer countdown targets.
     */
    public boolean schedule(int requestId, int turnaroundHours) throws SQLException {
        String sql = "UPDATE test_requests "
                + "SET expected_ready_at = now() + make_interval(hours => ?), "
                + "    request_status = 'in_progress' "
                + "WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, turnaroundHours);
            stmt.setInt(2, requestId);
            return stmt.executeUpdate() > 0;
        }
    }

    /** Updates the overall request status (e.g. cancel, complete). */
    public boolean updateStatus(int requestId, String status) throws SQLException {
        String sql = "UPDATE test_requests "
                + "SET request_status = ?::request_status WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setInt(2, requestId);
            return stmt.executeUpdate() > 0;
        }
    }

    public TestRequest findById(int id) throws SQLException {
        String sql = SELECT_BASE + "WHERE r.id = ?";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    /** Whole queue, newest first (Super Admin / Lab Attendant). */
    public List<TestRequest> findAll() throws SQLException {
        return queryList(SELECT_BASE + "ORDER BY r.created_at DESC", null);
    }

    /** Queue filtered by payment status ('paid' or 'unpaid'). */
    public List<TestRequest> findByPaymentStatus(String paymentStatus) throws SQLException {
        return queryList(
                SELECT_BASE + "WHERE r.payment_status = ? ORDER BY r.created_at DESC",
                paymentStatus);
    }

    /** A single customer's own orders (their dashboard list). */
    public List<TestRequest> findByCustomer(int customerId) throws SQLException {
        String sql = SELECT_BASE + "WHERE r.customer_id = ? ORDER BY r.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<TestRequest> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    /** The lab's bank account for the transfer screen, or null if unset. */
    public BankDetails findBankDetails() throws SQLException {
        String sql = "SELECT lab_name, bank_name, bank_account_name, "
                + "bank_account_number FROM lab_settings WHERE id = 1";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return new BankDetails(
                        rs.getString("lab_name"),
                        rs.getString("bank_name"),
                        rs.getString("bank_account_name"),
                        rs.getString("bank_account_number"));
            }
        }
        return null;
    }

    /** Runs a SELECT_BASE query with an optional single String parameter. */
    private List<TestRequest> queryList(String sql, String param) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (param != null) {
                stmt.setString(1, param);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<TestRequest> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    private TestRequest map(ResultSet rs) throws SQLException {
        int paidByRaw = rs.getInt("paid_by");
        Integer paidBy = rs.wasNull() ? null : paidByRaw;

        TestRequest r = new TestRequest();
        r.setId(rs.getInt("id"));
        r.setCustomerId(rs.getInt("customer_id"));
        r.setTestId(rs.getInt("test_id"));
        r.setPriceAtOrder(rs.getBigDecimal("price_at_order"));
        r.setRequestStatus(rs.getString("request_status"));
        r.setPaymentStatus(rs.getString("payment_status"));
        r.setPaidBy(paidBy);
        r.setPaidAt(rs.getTimestamp("paid_at"));
        r.setExpectedReadyAt(rs.getTimestamp("expected_ready_at"));

        String first = rs.getString("first_name");
        String last = rs.getString("last_name");
        r.setCustomerName(((first == null ? "" : first) + " "
                + (last == null ? "" : last)).trim());
        r.setTestName(rs.getString("test_name"));
        return r;
    }
}
