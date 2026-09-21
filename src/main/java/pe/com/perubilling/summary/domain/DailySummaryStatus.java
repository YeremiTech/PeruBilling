package pe.com.perubilling.summary.domain;

public enum DailySummaryStatus {
    QUEUED,
    PROCESSING,
    WAITING_TICKET,
    SUBMISSION_UNKNOWN,
    RETRY_PENDING,
    ACCEPTED,
    REJECTED,
    SEND_FAILED
}
