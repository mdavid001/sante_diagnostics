package santediagnostics;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the `results` table.
 *
 * The visibility gate lives in the queries, not in app logic: the customer
 * query only ever returns 'verified' rows, so an un-verified result can never
 * leak to a customer even if a screen forgets to check.
 */
public class ResultDao {

    private static final String SELECT_BASE =
            "SELECT res.id, res.test_request_id, res.value_numeric, res.value_text, "
            + "       res.status, res.uploaded_by, res.uploaded_at, "
            + "       res.verified_by, res.verified_at, "
            + "       (u.first_name || ' ' || u.last_name) AS customer_name, "
            + "       t.name AS test_name, t.result_format "
            + "FROM results res "
            + "JOIN test_requests r ON r.id = res.test_request_id "
            + "JOIN users u ON u.id = r.customer_id "
            + "JOIN tests t ON t.id = r.test_id ";

    /** Creates a pending result and returns its generated id. */
    public int create(int testRequestId, BigDecimal numeric, String text,
                      int uploadedByUserId) throws SQLException {
        String sql = "INSERT INTO results "
                + "(test_request_id, value_numeric, value_text, uploaded_by) "
                + "VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, testRequestId);
            if (numeric == null) {
                stmt.setNull(2, Types.NUMERIC);
            } else {
                stmt.setBigDecimal(2, numeric);
            }
            stmt.setString(3, text);
            stmt.setInt(4, uploadedByUserId);

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
     * Updates the values of an existing result and returns it to 'pending'
     * (used when correcting or re-submitting a rejected result).
     */
    public boolean updateValues(int resultId, BigDecimal numeric, String text)
            throws SQLException {
        String sql = "UPDATE results "
                + "SET value_numeric = ?, value_text = ?, status = 'pending', "
                + "    verified_by = NULL, verified_at = NULL "
                + "WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (numeric == null) {
                stmt.setNull(1, Types.NUMERIC);
            } else {
                stmt.setBigDecimal(1, numeric);
            }
            stmt.setString(2, text);
            stmt.setInt(3, resultId);
            return stmt.executeUpdate() > 0;
        }
    }

    /** The manual verification step: pending -> verified. */
    public boolean verify(int resultId, int verifiedByUserId) throws SQLException {
        String sql = "UPDATE results "
                + "SET status = 'verified', verified_by = ?, verified_at = now() "
                + "WHERE id = ? AND status = 'pending'";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, verifiedByUserId);
            stmt.setInt(2, resultId);
            return stmt.executeUpdate() > 0;
        }
    }

    /** Sends a result back: pending -> rejected. */
    public boolean reject(int resultId) throws SQLException {
        String sql = "UPDATE results SET status = 'rejected' "
                + "WHERE id = ? AND status = 'pending'";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, resultId);
            return stmt.executeUpdate() > 0;
        }
    }

    public Result findById(int id) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE res.id = ?", id);
    }

    /** The result for a request (one per request), or null. */
    public Result findByRequest(int testRequestId) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE res.test_request_id = ?", testRequestId);
    }

    /** Results awaiting verification (the Lab Attendant / Admin queue). */
    public List<Result> findPending() throws SQLException {
        return queryList(
                SELECT_BASE + "WHERE res.status = 'pending' ORDER BY res.uploaded_at", -1);
    }

    /**
     * A customer's VERIFIED results only — this is the visibility gate. Nothing
     * pending or rejected is ever returned here.
     */
    public List<Result> findVerifiedByCustomer(int customerId) throws SQLException {
        return queryList(
                SELECT_BASE + "WHERE r.customer_id = ? AND res.status = 'verified' "
                        + "ORDER BY res.verified_at DESC", customerId);
    }

    private Result querySingle(String sql, int param) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    /** Runs a SELECT_BASE query; pass param < 0 for queries with no parameter. */
    private List<Result> queryList(String sql, int param) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (param >= 0) {
                stmt.setInt(1, param);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<Result> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    private Result map(ResultSet rs) throws SQLException {
        Result res = new Result();
        res.setId(rs.getInt("id"));
        res.setTestRequestId(rs.getInt("test_request_id"));
        res.setValueNumeric(rs.getBigDecimal("value_numeric"));
        res.setValueText(rs.getString("value_text"));
        res.setStatus(rs.getString("status"));

        int ub = rs.getInt("uploaded_by");
        res.setUploadedBy(rs.wasNull() ? null : ub);
        res.setUploadedAt(rs.getTimestamp("uploaded_at"));

        int vb = rs.getInt("verified_by");
        res.setVerifiedBy(rs.wasNull() ? null : vb);
        res.setVerifiedAt(rs.getTimestamp("verified_at"));

        res.setCustomerName(rs.getString("customer_name"));
        res.setTestName(rs.getString("test_name"));
        res.setResultFormat(rs.getString("result_format"));
        return res;
    }
}
