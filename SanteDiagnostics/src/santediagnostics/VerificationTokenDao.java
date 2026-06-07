package santediagnostics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Data access for `verification_tokens`. Tokens are single-use and expire.
 */
public class VerificationTokenDao {

    public void create(int userId, String token, String tokenType,
                       Timestamp expiresAt) throws SQLException {
        String sql = "INSERT INTO verification_tokens "
                + "(user_id, token, token_type, expires_at) "
                + "VALUES (?, ?, ?::token_type, ?)";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, token);
            stmt.setString(3, tokenType);
            stmt.setTimestamp(4, expiresAt);
            stmt.executeUpdate();
        }
    }

    /**
     * Returns the user id for a token that is the right type, not yet used and
     * not expired; -1 if no such token.
     */
    public int findValidUserId(String token, String tokenType) throws SQLException {
        String sql = "SELECT user_id FROM verification_tokens "
                + "WHERE token = ? AND token_type = ?::token_type "
                + "AND used_at IS NULL AND expires_at > now()";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            stmt.setString(2, tokenType);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /** Marks a token used so it can't be redeemed twice. */
    public boolean markUsed(String token) throws SQLException {
        String sql = "UPDATE verification_tokens SET used_at = now() "
                + "WHERE token = ? AND used_at IS NULL";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            return stmt.executeUpdate() > 0;
        }
    }
}
