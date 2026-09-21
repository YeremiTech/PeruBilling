package pe.com.perubilling.sunat.domain;

public enum SunatReconciliationStatus {
    FOUND_ACCEPTED,
    FOUND_REJECTED,
    FOUND_VOIDED,
    CONFIRMED_NOT_FOUND,
    UNKNOWN,
    TRANSIENT_ERROR
}
