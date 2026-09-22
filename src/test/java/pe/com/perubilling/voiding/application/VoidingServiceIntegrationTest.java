package pe.com.perubilling.voiding.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.com.perubilling.billing.domain.DeliveryStatus;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.tenant.domain.TenantEntity;
import pe.com.perubilling.tenant.infrastructure.TenantRepository;
import pe.com.perubilling.voiding.api.VoidDocumentRequest;
import pe.com.perubilling.voiding.infrastructure.VoidingBatchRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.api-key-pepper=0123456789abcdef0123456789abcdef",
        "app.bootstrap.enabled=false",
        "app.sunat.xsd.enabled=false",
        "app.scheduling.enabled=false"
})
class VoidingServiceIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired VoidingService service;
    @Autowired ElectronicDocumentRepository documents;
    @Autowired IssuerRepository issuers;
    @Autowired TenantRepository tenants;
    @Autowired VoidingBatchRepository batches;
    @Autowired JdbcClient jdbc;

    private UUID tenantId;
    private UUID documentId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.sql("TRUNCATE TABLE tenant CASCADE").update();

        TenantEntity tenant = new TenantEntity();
        tenant.setName("voiding-test");
        tenant.setSlug("voiding-test-" + UUID.randomUUID());
        tenantId = tenants.saveAndFlush(tenant).getId();
        userId = UUID.randomUUID();

        IssuerEntity issuer = new IssuerEntity();
        issuer.setTenantId(tenantId);
        issuer.setRuc("20123456786");
        issuer.setBusinessName("VOIDING TEST SAC");
        issuer.setAddress("Lima");
        issuer.setUbigeo("150101");
        issuer.setSunatEnvironment(SunatEnvironment.LOCAL);
        issuer.setActive(true);
        UUID issuerId = issuers.saveAndFlush(issuer).getId();

        ElectronicDocumentEntity document = new ElectronicDocumentEntity();
        document.setTenantId(tenantId);
        document.setIssuerId(issuerId);
        document.setDocumentType(DocumentType.INVOICE);
        document.setSeries("F001");
        document.setCorrelativo(1);
        document.setFullNumber("F001-1");
        document.setIssueDate(LocalDate.now());
        document.setIssueTime(LocalTime.of(10, 30));
        document.setOperationType("0101");
        document.setCurrency("PEN");
        document.setCustomerDocumentType("6");
        document.setCustomerDocumentNumber("20100066603");
        document.setCustomerName("CLIENTE TEST SAC");
        document.setTaxableAmount(new BigDecimal("100.00"));
        document.setIgvAmount(new BigDecimal("18.00"));
        document.setTotalAmount(new BigDecimal("118.00"));
        document.setStatus(DocumentStatus.ACCEPTED);
        document.setDeliveryStatus(DeliveryStatus.NOT_DELIVERED);
        document.setAcceptedAt(Instant.now());
        documentId = documents.saveAndFlush(document).getId();
    }

    @Test
    void repeatedVoidingRequestIsIdempotentAndCreatesOnlyOneBatch() throws Exception {
        var first = authenticated(() -> service.requestVoid(
                documentId, new VoidDocumentRequest("Error en la operación")));
        var second = authenticated(() -> service.requestVoid(
                documentId, new VoidDocumentRequest("Reintento seguro")));

        assertThat(first.status()).isEqualTo(DocumentStatus.VOID_REQUESTED);
        assertThat(first.batchIdentifier()).isNotBlank();
        assertThat(second.status()).isEqualTo(DocumentStatus.VOID_REQUESTED);
        assertThat(second.batchIdentifier()).isEqualTo(first.batchIdentifier());
        assertThat(batches.countByTenantIdAndIssuerIdAndGenerationDate(
                tenantId,
                documents.findById(documentId).orElseThrow().getIssuerId(),
                LocalDate.now())).isEqualTo(1);
    }

    private <T> T authenticated(ThrowingSupplier<T> supplier) throws Exception {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt()));
        SecurityContextHolder.setContext(context);
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return new Jwt(
                "voiding-test-token",
                now,
                now.plusSeconds(900),
                Map.of("alg", "none"),
                Map.of(
                        "sub", userId.toString(),
                        "tenant_id", tenantId.toString(),
                        "email", "voiding@example.test",
                        "roles", List.of("ADMIN")));
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
