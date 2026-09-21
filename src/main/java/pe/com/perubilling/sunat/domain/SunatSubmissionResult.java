package pe.com.perubilling.sunat.domain;

import java.util.List;

public record SunatSubmissionResult(
        String responseCode,
        String description,
        List<String> notes,
        byte[] cdrZip,
        int httpStatus
) {
    public boolean accepted() { return "0".equals(responseCode); }
    public boolean observed() { return accepted() && notes != null && !notes.isEmpty(); }
}
