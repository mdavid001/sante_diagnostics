package santediagnostics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A few user-table lookups needed by the verification and email flows.
 * AuthService keeps its own user SQL for login/registration; this class just
 * adds the small reads/writes those features didn't already cover.
 */
public class UserDao {

    /** User id for an email, or -1 if no such user. */
    public int findIdByEmail(String email) throws SQLException {
        String sql = "SELECT id FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    public String findEmailById(int userId) throws SQLException {
        return findStringById("email", userId);
    }

    public String findFirstNameById(int userId) throws SQLException {
        return findStringById("first_name", userId);
    }

    /** Marks a user's email as verified. */
    public boolean setEmailVerified(int userId) throws SQLException {
        String sql = "UPDATE users SET email_verified = TRUE WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean isEmailVerified(int userId) throws SQLException {
        String sql = "SELECT email_verified FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private String findStringById(String column, int userId) throws SQLException {
        String sql = "SELECT " + column + " FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
