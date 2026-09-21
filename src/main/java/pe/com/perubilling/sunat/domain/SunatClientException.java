package pe.com.perubilling.sunat.domain;

public class SunatClientException extends RuntimeException {
    private final boolean retryable;
    private final String code;

    public SunatClientException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public SunatClientException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public boolean isRetryable() { return retryable; }
    public String getCode() { return code; }
}
