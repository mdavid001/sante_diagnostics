package santediagnostics;

/**
 * Represents an authenticated user of the LIMS.
 *
 * The system has a strict three-tier hierarchy. The {@code role} column in the
 * database stores one of: "super_admin", "lab_attendant", or "customer".
 *
 * {@code mustChangePassword} is set true for accounts created by staff
 * (Super Admin or Lab Attendant). Such accounts are forced through the
 * change-password screen on first login before they can use the system.
 */
public class User {

    public static final String ROLE_SUPER_ADMIN = "super_admin";
    public static final String ROLE_LAB_ATTENDANT = "lab_attendant";
    public static final String ROLE_CUSTOMER = "customer";

    private int id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private boolean mustChangePassword;
    private boolean isActive;

    public User(int id, String firstName, String lastName,
                String email, String role, boolean mustChangePassword,
                boolean isActive) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
        this.isActive = isActive;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return (firstName == null ? "" : firstName)
                + " " + (lastName == null ? "" : lastName);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public boolean isSuperAdmin() {
        return ROLE_SUPER_ADMIN.equalsIgnoreCase(role);
    }

    public boolean isLabAttendant() {
        return ROLE_LAB_ATTENDANT.equalsIgnoreCase(role);
    }

    public boolean isCustomer() {
        return ROLE_CUSTOMER.equalsIgnoreCase(role);
    }
}