package santediagnostics;

/**
 * Throwaway test — run this file directly to confirm email works.
 * Right-click it in NetBeans → Run File.
 * Delete it once you've confirmed sending works.
 */
public class TestEmail {

    public static void main(String[] args) {
        // PUT YOUR OWN EMAIL ADDRESS HERE so you can check the inbox
        String testAddress = "munachikachi@gmail.com";

        System.out.println("Sending test email to: " + testAddress);
        try {
            EmailService email = new EmailService();
            email.sendVerificationEmail(testAddress, "Munachi", "ABC123");
            System.out.println("SUCCESS — check your inbox (and spam folder).");
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
