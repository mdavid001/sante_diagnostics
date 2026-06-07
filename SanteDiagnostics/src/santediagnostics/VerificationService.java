package santediagnostics;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.mail.MessagingException;

/**
 * Email-verification flow for self-registered customers.
 *
 * The frontend calls startEmailVerification(email) right after a successful
 * AuthService.registerCustomer(...). The customer then types the emailed code
 * into a "verify your email" screen, which calls verify(code).
 *
 * A code is a short, easy-to-type string (no ambiguous characters) since this
 * is a desktop app with no clickable web link.
 */
public class VerificationService {

    private static final String TYPE_EMAIL = "email_verification";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int EXPIRY_HOURS = 24;

    private final VerificationTokenDao tokenDao = new VerificationTokenDao();
    private final UserDao userDao = new UserDao();
    private final EmailService emailService = new EmailService();
    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a fresh code, stores it, and emails it to the user.
     * Call this after registration and for "resend code".
     */
    public void startEmailVerification(String email) throws SQLException, MessagingException {
        int userId = userDao.findIdByEmail(email);
        if (userId == -1) {
            throw new IllegalArgumentException("No account found for that email.");
        }

        String code = generateCode();
        Timestamp expiresAt = Timestamp.from(
                Instant.now().plus(EXPIRY_HOURS, ChronoUnit.HOURS));
        tokenDao.create(userId, code, TYPE_EMAIL, expiresAt);

        String firstName = userDao.findFirstNameById(userId);
        emailService.sendVerificationEmail(email, firstName, code);
    }

    /**
     * Checks a code; if valid, marks the user verified and burns the code.
     *
     * @return true if the code was valid and the account is now verified.
     */
    public boolean verify(String code) throws SQLException {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        String trimmed = code.trim().toUpperCase();

        int userId = tokenDao.findValidUserId(trimmed, TYPE_EMAIL);
        if (userId == -1) {
            return false;   // wrong, expired, or already-used code
        }

        userDao.setEmailVerified(userId);
        tokenDao.markUsed(trimmed);
        return true;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
