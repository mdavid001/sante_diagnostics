package santediagnostics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Business logic for user management (create, list, toggle active).
 *
 * Rules enforced here:
 *  - super_admin can create lab_attendant OR customer accounts.
 *  - lab_attendant can only create customer accounts.
 *  - Passwords are BCrypt-hashed before storage (same as AuthService).
 *  - Every account created by staff gets must_change_password = TRUE so the
 *    user is forced through the change-password screen on first login.
 *  - A temporary password is generated here and returned to the creator so
 *    they can hand it to the new user out-of-band.
 */
public class UserService {

    private final AuditService audit = new AuditService();
    private final EmailService emailService = new EmailService();

    // ----------------------------------------------------------------
    //  CREATE
    // ----------------------------------------------------------------

    /**
     * Creates a new user account on behalf of a staff member.
     *
     * @param createdBy  the Session user performing the action
     * @param firstName  new user's first name
     * @param lastName   new user's last name
     * @param email      new user's email (must be unique)
     * @param role       "lab_attendant" or "customer"
     * @return           a {@link CreateResult} carrying the new user and
     *                   the plain-text temporary password to hand to them
     * @throws IllegalArgumentException if the creator lacks permission,
     *                                  inputs are invalid, or email is taken
     * @throws SQLException             on any DB error
     */
    public CreateResult createUser(User createdBy,
                                   String firstName, String lastName,
                                   String email, String role)
            throws SQLException {

        // --- permission check ---
        if (createdBy.isSuperAdmin()) {
            if (!User.ROLE_LAB_ATTENDANT.equals(role)
                    && !User.ROLE_CUSTOMER.equals(role)) {
                throw new IllegalArgumentException(
                        "Super admin can only create lab attendant or customer accounts.");
            }
        } else if (createdBy.isLabAttendant()) {
            if (!User.ROLE_CUSTOMER.equals(role)) {
                throw new IllegalArgumentException(
                        "Lab attendants can only create customer accounts.");
            }
        } else {
            throw new IllegalArgumentException(
                    "You do not have permission to create user accounts.");
        }

        // --- input validation ---
        if (firstName == null || firstName.isBlank())
            throw new IllegalArgumentException("First name is required.");
        if (lastName == null || lastName.isBlank())
            throw new IllegalArgumentException("Last name is required.");
        if (email == null || !email.contains("@"))
            throw new IllegalArgumentException("A valid email address is required.");

        String emailTrimmed = email.trim().toLowerCase();

        // --- duplicate check ---
        if (emailExists(emailTrimmed)) {
            throw new IllegalArgumentException(
                    "An account with that email address already exists.");
        }

        // --- generate + hash temporary password ---
        String tempPassword = generateTempPassword();
        String hashed       = BCrypt.hashpw(tempPassword, BCrypt.gensalt());

        // --- insert ---
        String sql = "INSERT INTO users "
                + "(first_name, last_name, email, password, role, "
                + " email_verified, must_change_password, created_by) "
                + "VALUES (?, ?, ?, ?, ?::user_role, TRUE, TRUE, ?)";

        int newId;
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, firstName.trim());
            stmt.setString(2, lastName.trim());
            stmt.setString(3, emailTrimmed);
            stmt.setString(4, hashed);
            stmt.setString(5, role);
            stmt.setInt(6, createdBy.getId());
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No generated key returned.");
                newId = keys.getInt(1);
            }
        }

        User newUser = new User(newId, firstName.trim(), lastName.trim(),
                emailTrimmed, role, true, true);

        // Audit: who created whom, in what role.
        audit.log(createdBy.getId(), AuditService.USER_CREATED,
                AuditService.ENTITY_USER, newId,
                "{\"role\":\"" + role + "\"}");
        
        new Thread(() -> {
        try {
            emailService.sendWelcomeEmail(
                emailTrimmed,
                firstName.trim(),
                role,
                tempPassword
            );
        } catch (Exception ex) {
            
        }
        }, "welcome-email-thread").start();

   
        return new CreateResult(newUser, tempPassword);
    }

    // ----------------------------------------------------------------
    //  LIST
    // ----------------------------------------------------------------

    /** All users except the caller, newest first. */
    public List<UserRow> listAll(int excludeUserId) throws SQLException {
        String sql = "SELECT u.id, u.first_name, u.last_name, u.email, "
                + "       u.role, u.is_active, u.must_change_password, "
                + "       u.created_at, "
                + "       c.first_name AS cb_first, c.last_name AS cb_last "
                + "FROM users u "
                + "LEFT JOIN users c ON c.id = u.created_by "
                + "WHERE u.id <> ? "
                + "ORDER BY u.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, excludeUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<UserRow> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    /** Users filtered by role, excluding the caller. */
    public List<UserRow> listByRole(String role, int excludeUserId) throws SQLException {
        String sql = "SELECT u.id, u.first_name, u.last_name, u.email, "
                + "       u.role, u.is_active, u.must_change_password, "
                + "       u.created_at, "
                + "       c.first_name AS cb_first, c.last_name AS cb_last "
                + "FROM users u "
                + "LEFT JOIN users c ON c.id = u.created_by "
                + "WHERE u.role = ?::user_role AND u.id <> ? "
                + "ORDER BY u.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, role);
            stmt.setInt(2, excludeUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<UserRow> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        }
    }

    // ----------------------------------------------------------------
    //  TOGGLE ACTIVE
    // ----------------------------------------------------------------

    /**
     * Activates or deactivates a user. Returns the new is_active value.
     * @param userId   user being toggled
     * @param actorUserId  staff member performing the action (for audit)
     */
    public boolean toggleActive(int userId, int actorUserId) throws SQLException {
        String sql = "UPDATE users SET is_active = NOT is_active WHERE id = ? "
                + "RETURNING is_active";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean nowActive = rs.getBoolean(1);
                    audit.log(actorUserId,
                            nowActive ? AuditService.USER_ACTIVATED
                                      : AuditService.USER_DEACTIVATED,
                            AuditService.ENTITY_USER, userId);
                    return nowActive;
                }
                throw new SQLException("User not found: " + userId);
            }
        }
    }

    // ----------------------------------------------------------------
    //  HELPERS
    // ----------------------------------------------------------------

    private boolean emailExists(String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Generates a readable 10-char temporary password. */
    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(10);
        java.util.Random rng = new java.security.SecureRandom();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private UserRow mapRow(ResultSet rs) throws SQLException {
        String cbFirst = rs.getString("cb_first");
        String cbLast  = rs.getString("cb_last");
        String createdBy = (cbFirst == null && cbLast == null)
                ? "System"
                : ((cbFirst == null ? "" : cbFirst)
                        + " " + (cbLast == null ? "" : cbLast)).trim();

        return new UserRow(
                rs.getInt("id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getBoolean("is_active"),
                rs.getBoolean("must_change_password"),
                createdBy,
                rs.getTimestamp("created_at")
        );
    }

    // ----------------------------------------------------------------
    //  VALUE OBJECTS
    // ----------------------------------------------------------------

    /** Returned after a successful createUser call. */
    public static class CreateResult {
        public final User user;
        public final String tempPassword;

        public CreateResult(User user, String tempPassword) {
            this.user = user;
            this.tempPassword = tempPassword;
        }
    }

    /** Flat display row for the table (avoids exposing password hash). */
    public static class UserRow {
        public final int     id;
        public final String  firstName;
        public final String  lastName;
        public final String  email;
        public final String  role;
        public final boolean active;
        public final boolean mustChangePassword;
        public final String  createdBy;
        public final java.sql.Timestamp createdAt;

        public UserRow(int id, String firstName, String lastName,
                       String email, String role, boolean active,
                       boolean mustChangePassword, String createdBy,
                       java.sql.Timestamp createdAt) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.role = role;
            this.active = active;
            this.mustChangePassword = mustChangePassword;
            this.createdBy = createdBy;
            this.createdAt = createdAt;
        }

        public String getFullName() {
            return (firstName == null ? "" : firstName)
                    + " " + (lastName == null ? "" : lastName);
        }

        public String getPrettyRole() {
            if (role == null) return "";
            switch (role) {
                case "super_admin":    return "Super Admin";
                case "lab_attendant":  return "Lab Attendant";
                case "customer":       return "Customer";
                default:               return role;
            }
        }
    }
}
