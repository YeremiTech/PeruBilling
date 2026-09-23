package pe.com.perubilling.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Matriz explícita de funcionalidades que un integrador puede usar en esta versión")
public record ApiCapabilitiesResponse(
        String apiVersion,
        String rulesVersion,
        List<Capability> capabilities
) {
    public record Capability(
            String code,
            String description,
            boolean supported,
            boolean requiresRegulatoryEvidence) {}
}
