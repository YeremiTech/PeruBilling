package pe.com.perubilling.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.com.perubilling.shared.api.ApiError;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI perubillingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PeruBilling API")
                        .version("v1")
                        .description("API de facturación electrónica peruana para integración con POS, ERP y sistemas de ventas")
                        .contact(new Contact().name("PeruBilling")))
                .components(components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("apiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")));
    }

    private Components components() {
        Components components = new Components();
        ModelConverters.getInstance().read(ApiError.class).forEach(components::addSchemas);
        var errorContent = new Content().addMediaType("application/json", new MediaType()
                .schema(new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/ApiError")));
        components.addResponses("BadRequest", new ApiResponse().description("Solicitud inválida").content(errorContent));
        components.addResponses("Unauthorized", new ApiResponse().description("Autenticación requerida o inválida").content(errorContent));
        components.addResponses("Forbidden", new ApiResponse().description("Permisos insuficientes").content(errorContent));
        components.addResponses("NotFound", new ApiResponse().description("Recurso no encontrado").content(errorContent));
        components.addResponses("Conflict", new ApiResponse().description("Conflicto de idempotencia, unicidad o estado").content(errorContent));
        components.addResponses("UnprocessableEntity", new ApiResponse().description("Documento no cumple reglas tributarias").content(errorContent));
        components.addResponses("TooManyRequests", new ApiResponse().description("Límite de solicitudes excedido").content(errorContent));
        components.addResponses("InternalServerError", new ApiResponse().description("Error interno correlacionado mediante requestId").content(errorContent));
        return components;
    }
}
