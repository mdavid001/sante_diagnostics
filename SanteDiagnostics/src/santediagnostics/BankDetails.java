package santediagnostics;

/**
 * The laboratory's bank account, shown to a customer after they place an order
 * so they can pay by transfer. Read from the single `lab_settings` row.
 */
public class BankDetails {

    private final String labName;
    private final String bankName;
    private final String accountName;
    private final String accountNumber;

    public BankDetails(String labName, String bankName,
                       String accountName, String accountNumber) {
        this.labName = labName;
        this.bankName = bankName;
        this.accountName = accountName;
        this.accountNumber = accountNumber;
    }

    public String getLabName() {
        return labName;
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
}
