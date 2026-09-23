package pe.com.perubilling.shared.domain;

public enum DocumentType {
    INVOICE("01"),
    RECEIPT("03"),
    CREDIT_NOTE("07"),
    DEBIT_NOTE("08");

    private final String sunatCode;
    DocumentType(String sunatCode) { this.sunatCode = sunatCode; }
    public String getSunatCode() { return sunatCode; }
}
