package pe.com.perubilling.issuer.api;

import java.util.UUID;
import pe.com.perubilling.shared.domain.DocumentType;

public record SeriesResponse(UUID id, DocumentType documentType, String series, long currentValue, boolean active) {}
