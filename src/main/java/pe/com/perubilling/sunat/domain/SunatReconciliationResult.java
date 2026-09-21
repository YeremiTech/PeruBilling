package pe.com.perubilling.sunat.domain;

public record SunatReconciliationResult(
        SunatReconciliationStatus status,
        SunatSubmissionResult submission,
        String message) {

    public static SunatReconciliationResult found(SunatReconciliationStatus status, SunatSubmissionResult submission) {
        return new SunatReconciliationResult(
                status, submission, submission == null ? null : submission.description());
    }

    public static SunatReconciliationResult of(SunatReconciliationStatus status, String message) {
        return new SunatReconciliationResult(status, null, message);
    }

    public boolean hasSubmission() {
        return submission != null;
    }
}
