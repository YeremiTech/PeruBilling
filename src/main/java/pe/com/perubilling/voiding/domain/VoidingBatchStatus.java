package pe.com.perubilling.voiding.domain;

public enum VoidingBatchStatus {
    QUEUED,
    PROCESSING,
    WAITING_TICKET,
    SUBMISSION_UNKNOWN,
    RETRY_PENDING,
    ACCEPTED,
    REJECTED,
    SEND_FAILED
}
