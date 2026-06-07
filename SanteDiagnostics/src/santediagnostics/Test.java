package santediagnostics;

import java.math.BigDecimal;

/**
 * A test type from the catalogue (the Super Admin's "Custom Test Builder").
 * Mirrors a row in the `tests` table.
 */
public class Test {

    public static final String FORMAT_NUMERIC = "numeric";
    public static final String FORMAT_TEXT    = "text";
    public static final String FORMAT_PDF     = "pdf";
    public static final String FORMAT_IMAGE   = "image";

    private int id;
    private String name;
    private String description;
    private BigDecimal price;
    private int turnaroundHours;   // Standard TAT, in whole hours
    private String resultFormat;   // one of the FORMAT_* constants
    private boolean active;
    private Integer createdBy;     // user id of the admin who created it; may be null

    public Test() {
    }

    public Test(int id, String name, String description, BigDecimal price,
                int turnaroundHours, String resultFormat, boolean active,
                Integer createdBy) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.turnaroundHours = turnaroundHours;
        this.resultFormat = resultFormat;
        this.active = active;
        this.createdBy = createdBy;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getTurnaroundHours() {
        return turnaroundHours;
    }

    public void setTurnaroundHours(int turnaroundHours) {
        this.turnaroundHours = turnaroundHours;
    }

    public String getResultFormat() {
        return resultFormat;
    }

    public void setResultFormat(String resultFormat) {
        this.resultFormat = resultFormat;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return name;
    }
}
