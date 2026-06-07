package santediagnostics;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Business rules for the test catalogue. Controllers call this class (the way
 * LoginController calls AuthService); it validates input, then delegates the
 * actual SQL to TestDao.
 *
 * Validation failures throw IllegalArgumentException with a message safe to
 * show the user. Database problems surface as SQLException, like AuthService.
 */
public class TestService {

    private final TestDao dao = new TestDao();
    private final AuditService audit = new AuditService();

    /**
     * Creates a new test (Super Admin only). Returns the saved Test with its
     * generated id populated.
     *
     * @param createdByUserId id of the admin creating it (Session user id);
     *                        may be null.
     */
    public Test createTest(String name, String description, BigDecimal price,
                           int turnaroundHours, String resultFormat,
                           Integer createdByUserId) throws SQLException {

        validate(name, price, turnaroundHours, resultFormat);

        Test t = new Test(
                0,
                name.trim(),
                description == null ? null : description.trim(),
                price,
                turnaroundHours,
                resultFormat,
                true,
                createdByUserId
        );

        int id = dao.create(t);
        if (id == -1) {
            throw new SQLException("Test could not be created.");
        }
        t.setId(id);
        audit.log(createdByUserId, AuditService.TEST_CREATED, AuditService.ENTITY_TEST, id);
        return t;
    }

    /** Updates an existing test after re-validating its fields. */
    public boolean updateTest(Test t, int updatedByUserId) throws SQLException {
        if (t == null || t.getId() <= 0) {
            throw new IllegalArgumentException("A valid test is required.");
        }
        validate(t.getName(), t.getPrice(), t.getTurnaroundHours(), t.getResultFormat());
        boolean ok = dao.update(t);
        if (ok) {
            audit.log(updatedByUserId, AuditService.TEST_UPDATED, AuditService.ENTITY_TEST, t.getId());
        }
        return ok;
    }

    /** Retires a test so customers can no longer order it (kept for history). */
    public boolean retireTest(int id, int actorUserId) throws SQLException {
        boolean ok = dao.setActive(id, false);
        if (ok) {
            audit.log(actorUserId, AuditService.TEST_RETIRED, AuditService.ENTITY_TEST, id);
        }
        return ok;
    }

    /** Brings a retired test back into the catalogue. */
    public boolean reactivateTest(int id, int actorUserId) throws SQLException {
        boolean ok = dao.setActive(id, true);
        if (ok) {
            audit.log(actorUserId, AuditService.TEST_REACTIVATED, AuditService.ENTITY_TEST, id);
        }
        return ok;
    }

    public Test getTest(int id) throws SQLException {
        return dao.findById(id);
    }

    /** Full catalogue for the Super Admin (active and retired). */
    public List<Test> listAll() throws SQLException {
        return dao.findAll();
    }

    /** Orderable tests for the Customer browse screen. */
    public List<Test> listAvailable() throws SQLException {
        return dao.findAllActive();
    }

    private void validate(String name, BigDecimal price,
                          int turnaroundHours, String resultFormat) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Test name is required.");
        }
        if (name.trim().length() > 150) {
            throw new IllegalArgumentException("Test name is too long (max 150).");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be zero or more.");
        }
        if (turnaroundHours <= 0) {
            throw new IllegalArgumentException("Turnaround time must be at least 1 hour.");
        }
        if (!isValidFormat(resultFormat)) {
            throw new IllegalArgumentException(
                    "Result format must be numeric, text, pdf, or image.");
        }
    }

    private boolean isValidFormat(String f) {
        return Test.FORMAT_NUMERIC.equals(f)
                || Test.FORMAT_TEXT.equals(f)
                || Test.FORMAT_PDF.equals(f)
                || Test.FORMAT_IMAGE.equals(f);
    }
}
