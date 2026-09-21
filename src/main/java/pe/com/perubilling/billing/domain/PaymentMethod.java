package pe.com.perubilling.billing.domain;

public enum PaymentMethod {
    CONTADO("Contado"),
    CREDITO("Credito");

    private final String sunatValue;
    PaymentMethod(String sunatValue) { this.sunatValue = sunatValue; }
    public String sunatValue() { return sunatValue; }
}
