package santediagnostics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the `samples` table. The status-change methods guard their
 * transitions in the WHERE clause (e.g. you can only start processing a sample
 * that is currently 'collected'), so an out-of-order click changes nothing.
 *
 * Read queries join through test_requests to users and tests so the Lab
 * Attendant's worklist can show the customer and test names.
 */
public class SampleDao {

    private static final String SELECT_BASE =
            "SELECT s.id, s.test_request_id, s.status, s.collected_by, "
            + "       s.collected_at, s.processing_started_at, s.processed_at, s.notes, "
            + "       (u.first_name || ' ' || u.last_name) AS customer_name, "
            + "       t.name AS test_name "
            + "FROM samples s "
            + "JOIN test_requests r ON r.id = s.test_request_id "
            + "JOIN users u ON u.id = r.customer_id "
            + "JOIN tests t ON t.id = r.test_id ";

    /** Records a newly collected sample and returns its generated id. */
    public int create(int testRequestId, int collectedByUserId, String notes)
            throws SQLException {
        String sql = "INSERT INTO samples "
                + "(test_request_id, status, collected_by, collected_at, notes) "
                + "VALUES (?, 'collected', ?, now(), ?)";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, testRequestId);
            stmt.setInt(2, collectedByUserId);
            stmt.setString(3, notes);

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    /** collected -> processing (stamps processing_started_at). */
    public boolean startProcessing(int sampleId) throws SQLException {
        String sql = "UPDATE samples "
                + "SET status = 'processing', processing_started_at = now() "
                + "WHERE id = ? AND status = 'collected'";
        return runUpdate(sql, sampleId);
    }

    /** processing -> processed (stamps processed_at). */
    public boolean markProcessed(int sampleId) throws SQLException {
        String sql = "UPDATE samples "
                + "SET status = 'processed', processed_at = now() "
                + "WHERE id = ? AND status = 'processing'";
        return runUpdate(sql, sampleId);
    }

    /** Reverts a processed sample back to processing. Clears processed_at. */
    public boolean revertToProcessing(int sampleId) throws SQLException {
        String sql = "UPDATE samples "
                + "SET status = 'processing', processed_at = NULL "
                + "WHERE id = ? AND status = 'processed'";
        return runUpdate(sql, sampleId);
    }

    /** Reverts a processing sample back to collected. Clears processing_started_at. */
    public boolean revertToCollected(int sampleId) throws SQLException {
        String sql = "UPDATE samples "
                + "SET status = 'collected', processing_started_at = NULL "
                + "WHERE id = ? AND status = 'processing'";
        return runUpdate(sql, sampleId);
    }

    public Sample findById(int id) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE s.id = ?", id);
    }

    /** The sample for a given request (one per request), or null. */
    public Sample findByRequest(int testRequestId) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE s.test_request_id = ?", testRequestId);
    }

    /** All samples, newest collection first (Lab Attendant worklist). */
    public List<Sample> findAll() throws SQLException {
        return queryList(SELECT_BASE + "ORDER BY s.collected_at DESC NULLS LAST", null);
    }

    /** Samples at a given lifecycle status. */
    public List<Sample> findByStatus(String status) throws SQLException {
        return queryList(
                SELECT_BASE + "WHERE s.status = ?::sample_status "
                        + "ORDER BY s.collected_at DESC NULLS LAST",
                status);
    }

    private boolean runUpdate(String sql, int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    private Sample querySingle(String sql, int param) throws SQLException {
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

    private List<Sample> queryList(String sql, String param) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (param != null) {
                stmt.setString(1, param);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<Sample> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        }
    }

    private Sample map(ResultSet rs) throws SQLException {
        int cbRaw = rs.getInt("collected_by");
        Integer collectedBy = rs.wasNull() ? null : cbRaw;

        Sample s = new Sample();
        s.setId(rs.getInt("id"));
        s.setTestRequestId(rs.getInt("test_request_id"));
        s.setStatus(rs.getString("status"));
        s.setCollectedBy(collectedBy);
        s.setCollectedAt(rs.getTimestamp("collected_at"));
        s.setProcessingStartedAt(rs.getTimestamp("processing_started_at"));
        s.setProcessedAt(rs.getTimestamp("processed_at"));
        s.setNotes(rs.getString("notes"));
        s.setCustomerName(rs.getString("customer_name"));
        s.setTestName(rs.getString("test_name"));
        return s;
    }
}
