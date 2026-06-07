package santediagnostics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

/**
 * All authentication on boarding database work lives here so the controllers
 * stay thin. Teammates building other pages can reuse these methods.
 *
 * Expected "users" table columns:
 *   id (serial PK), first_name, last_name, email (unique),
 *   password (bcrypt hash), role, must_change_password (boolean),
 *   email_verified (boolean)
 */
public class AuthService {

    /** Result wrapper so callers can distinguish failure reasons. */
    public enum SignUpResult { SUCCESS, EMAIL_TAKEN, ERROR }

    /**
     * Validates credentials against the bcrypt-hashed record.
     * @return the User on success, or null if email not found / password wrong.
     */
    public User login(String email, String plainPassword) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnect()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM users WHERE email = ?"
            );
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");
                if (storedHash != null && BCrypt.checkpw(plainPassword, storedHash)) {
                    boolean isActive = rs.getBoolean("is_active");
                    if (!isActive) {
                        throw new IllegalStateException(
                            "This account has been deactivated. Please contact the lab.");
                    }
                    return new User(
                            rs.getInt("id"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("email"),
                            rs.getString("role"),
                            rs.getBoolean("must_change_password"),
                            isActive
                    );
                }
            }
        }
    
        return null;
    }

    /**
     * Self-registration for customers. New customers are unverified and are
     * never forced to change their password (they chose it themselves).
     */
    public SignUpResult registerCustomer(String firstName, String lastName,
                                         String email, String plainPassword) {
        try (Connection conn = DatabaseConnection.getConnect()) {
            PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM users WHERE email = ?"
            );
            check.setString(1, email);
            if (check.executeQuery().next()) {
                return SignUpResult.EMAIL_TAKEN;
            }

            PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO users "
                    + "(first_name, last_name, email, password, role, "
                    + " must_change_password, email_verified) "
                    + "VALUES (?, ?, ?, ?, ?::user_role, ?, ?)"
            );
            insert.setString(1, firstName);
            insert.setString(2, lastName);
            insert.setString(3, email);
            insert.setString(4, hash(plainPassword));
            insert.setString(5, User.ROLE_CUSTOMER);
            insert.setBoolean(6, false);   // self-chosen password
            insert.setBoolean(7, false);   // pending email verification

            return insert.executeUpdate() > 0
                    ? SignUpResult.SUCCESS : SignUpResult.ERROR;

        } catch (SQLException e) {
            e.printStackTrace();
            return SignUpResult.ERROR;
        }
    }

    /**
     * Updates a user's password and clears the force-change flag. Used by the
     * change-password screen after a staff-created account's first login.
     */
    public boolean changePassword(int userId, String newPlainPassword)
            throws SQLException {
        try (Connection conn = DatabaseConnection.getConnect()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET password = ?, must_change_password = FALSE "
                    + "WHERE id = ?"
            );
            stmt.setString(1, hash(newPlainPassword));
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        }
    }

    public String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt());
    }
}