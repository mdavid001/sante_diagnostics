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
 * Data access for the `tests` table. Runs the raw SQL; all business rules
 * (validation, who may create a test, etc.) live in TestService.
 *
 * Note the `?::result_format` casts: PostgreSQL will not accept a plain
 * String bind parameter into an enum column, so every write to result_format
 * is cast explicitly.
 */
public class TestDao {

    private static final String COLUMNS =
            "id, name, description, price, turnaround_hours, result_format, is_active, created_by";

    /** Inserts a new test and returns its generated id (-1 on failure). */
    public int create(Test t) throws SQLException {
        String sql = "INSERT INTO tests "
                + "(name, description, price, turnaround_hours, result_format, created_by) "
                + "VALUES (?, ?, ?, ?, ?::result_format, ?)";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, t.getName());
            stmt.setString(2, t.getDescription());
            stmt.setBigDecimal(3, t.getPrice());
            stmt.setInt(4, t.getTurnaroundHours());
            stmt.setString(5, t.getResultFormat());
            if (t.getCreatedBy() == null) {
                stmt.setNull(6, Types.BIGINT);
            } else {
                stmt.setInt(6, t.getCreatedBy());
            }

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    /** Updates the editable fields of an existing test. */
    public boolean update(Test t) throws SQLException {
        String sql = "UPDATE tests SET "
                + "name = ?, description = ?, price = ?, "
                + "turnaround_hours = ?, result_format = ?::result_format "
                + "WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, t.getName());
            stmt.setString(2, t.getDescription());
            stmt.setBigDecimal(3, t.getPrice());
            stmt.setInt(4, t.getTurnaroundHours());
            stmt.setString(5, t.getResultFormat());
            stmt.setInt(6, t.getId());

            return stmt.executeUpdate() > 0;
        }
    }

    /** Activates or retires a test (we soft-retire instead of deleting). */
    public boolean setActive(int id, boolean active) throws SQLException {
        String sql = "UPDATE tests SET is_active = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, active);
            stmt.setInt(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /** Returns one test by id, or null if not found. */
    public Test findById(int id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tests WHERE id = ?";

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

    /** All tests, active and retired (Super Admin view). */
    public List<Test> findAll() throws SQLException {
        return query("SELECT " + COLUMNS + " FROM tests ORDER BY name");
    }

    /** Only active tests (what a Customer is allowed to browse and order). */
    public List<Test> findAllActive() throws SQLException {
        return query("SELECT " + COLUMNS
                + " FROM tests WHERE is_active = TRUE ORDER BY name");
    }

    private List<Test> query(String sql) throws SQLException {
        List<Test> tests = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                tests.add(map(rs));
            }
        }
        return tests;
    }

    private Test map(ResultSet rs) throws SQLException {
        int cb = rs.getInt("created_by");
        Integer createdBy = rs.wasNull() ? null : cb;

        return new Test(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getInt("turnaround_hours"),
                rs.getString("result_format"),
                rs.getBoolean("is_active"),
                createdBy
        );
    }
}
