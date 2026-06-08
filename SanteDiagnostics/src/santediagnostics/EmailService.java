package santediagnostics;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends email via Gmail SMTP using Jakarta Mail.
 *
 * SMTP credentials are read from a `mail.properties` file in the project's
 * working directory (NOT hard-coded, and NOT committed to git). Keys:
 *   mail.smtp.host, mail.smtp.port, mail.smtp.username,
 *   mail.smtp.password, mail.from.name
 *
 * Setup reminder: the Jakarta Mail library jars must be added to the project's
 * Libraries (like the PostgreSQL driver and jBCrypt). If your team prefers the
 * older single-jar JavaMail, change every "jakarta.mail" import below to
 * "javax.mail" — the rest of the code is identical.
 */
public class EmailService {

    private static final String CONFIG_FILE = "mail.properties";

    private final String host;
    private final String port;
    private final String username;
    private final String password;
    private final String fromName;

    public EmailService() {
        Properties cfg = new Properties();
        try (InputStream in = openConfig()) {
            cfg.load(in);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not read " + CONFIG_FILE + " (place it in the project folder).", ex);
        }
        this.host = cfg.getProperty("mail.smtp.host", "smtp.gmail.com");
        this.port = cfg.getProperty("mail.smtp.port", "587");
        this.username = cfg.getProperty("mail.smtp.username");
        this.password = cfg.getProperty("mail.smtp.password");
        this.fromName = cfg.getProperty("mail.from.name", "Sante Diagnostics");
    }

    private InputStream openConfig() throws IOException {
        // Prefer a file in the working directory; fall back to the classpath.
        try {
            return new FileInputStream(CONFIG_FILE);
        } catch (IOException fileMiss) {
            InputStream cp = getClass().getResourceAsStream("/" + CONFIG_FILE);
            if (cp != null) {
                return cp;
            }
            throw fileMiss;
        }
    }

    /** The email sent to a customer after sign-up, carrying their code. */
    public void sendVerificationEmail(String to, String firstName, String code)
            throws MessagingException {
        String subject = "Verify your Sante Diagnostics account";
        String body = "Hi " + safe(firstName) + ",\n\n"
                + "Welcome to Sante Diagnostics. Your verification code is:\n\n"
                + "    " + code + "\n\n"
                + "Enter this code in the app to activate your account. "
                + "The code expires in 24 hours.\n\n"
                + "If you did not create this account, you can ignore this email.\n\n"
                + "Sante Diagnostics";
        send(to, subject, body);
    }

    /** The email sent to a user who requested a password reset. */
    public void sendPasswordResetEmail(String to, String firstName, String code)
            throws MessagingException {
        String subject = "Reset your Sante Diagnostics password";
        String body = "Hi " + safe(firstName) + ",\n\n"
                + "We received a request to reset the password on your account.\n"
                + "Your password reset code is:\n\n"
                + "    " + code + "\n\n"
                + "Enter this code in the app along with your new password. "
                + "The code expires in 24 hours.\n\n"
                + "If you did not request a reset, you can ignore this email -- "
                + "your password will not change.\n\n"
                + "Sante Diagnostics";
        send(to, subject, body);
    }

    /** The email sent to a customer when their result has been verified. */
    public void sendResultReadyEmail(String to, String firstName, String testName)
            throws MessagingException {
        String subject = "Your test result is ready";
        String body = "Hi " + safe(firstName) + ",\n\n"
                + "Your result for \"" + safe(testName) + "\" has been verified and is "
                + "now available.\n\n"
                + "Log in to your Sante Diagnostics dashboard to view and download it.\n\n"
                + "Sante Diagnostics";
        send(to, subject, body);
    }

    private void send(String to, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
        } catch (UnsupportedEncodingException ex) {
            throw new MessagingException("Bad sender name encoding", ex);
        }
    }

    private String safe(String s) {
        return (s == null || s.isEmpty()) ? "there" : s;
    }
}
