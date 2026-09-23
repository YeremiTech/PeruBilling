package pe.com.perubilling.billing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.com.perubilling.billing.api.CreateDocumentRequest;
import pe.com.perubilling.billing.api.CustomerRequest;
import pe.com.perubilling.billing.api.DocumentItemRequest;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.issuer.domain.DocumentSeriesEntity;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.issuer.infrastructure.DocumentSeriesRepository;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.shared.security.ApiKeyPrincipal;
import pe.com.perubilling.tenant.domain.TenantEntity;
import pe.com.perubilling.tenant.infrastructure.TenantRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.security.api-key-pepper=0123456789abcdef0123456789abcdef",
        "app.bootstrap.enabled=false",
        "app.sunat.xsd.enabled=false",
        "app.scheduling.enabled=false"
})
class DocumentCreationConcurrencyIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DocumentService service;
    @Autowired ElectronicDocumentRepository documents;
    @Autowired DocumentSeriesRepository series;
    @Autowired IssuerRepository issuers;
    @Autowired TenantRepository tenants;
    @Autowired JdbcClient jdbc;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID issuerId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.sql("TRUNCATE TABLE tenant CASCADE").update();

        tenantId = createTenant("billing-main");
        otherTenantId = createTenant("billing-other");
        userId = UUID.randomUUID();

        IssuerEntity issuer = new IssuerEntity();
        issuer.setTenantId(tenantId);
        issuer.setRuc("20123456786");
        issuer.setBusinessName("TEST BILLING SAC");
        issuer.setAddress("Lima");
        issuer.setUbigeo("150101");
        issuer.setSunatEnvironment(SunatEnvironment.LOCAL);
        issuer.setActive(true);
        issuerId = issuers.saveAndFlush(issuer).getId();

        DocumentSeriesEntity documentSeries = new DocumentSeriesEntity();
        documentSeries.setTenantId(tenantId);
        documentSeries.setIssuerId(issuerId);
        documentSeries.setDocumentType(DocumentType.RECEIPT);
        documentSeries.setSeries("B001");
        documentSeries.setCurrentValue(0);
        documentSeries.setActive(true);
        series.saveAndFlush(documentSeries);

        DocumentSeriesEntity invoiceSeries = new DocumentSeriesEntity();
        invoiceSeries.setTenantId(tenantId);
        invoiceSeries.setIssuerId(issuerId);
        invoiceSeries.setDocumentType(DocumentType.INVOICE);
        invoiceSeries.setSeries("F001");
        invoiceSeries.setCurrentValue(0);
        invoiceSeries.setActive(true);
        series.saveAndFlush(invoiceSeries);
    }

    @Test
    void concurrentRequestsWithSameIdempotencyKeyCreateExactlyOneDocument() throws Exception {
        CreateDocumentRequest request = request("SALE-100");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> authenticatedCall(tenantId, () -> {
                start.await();
                return service.create(DocumentType.RECEIPT, request, "idem-sale-100");
            }));
            var second = executor.submit(() -> authenticatedCall(tenantId, () -> {
                start.await();
                return service.create(DocumentType.RECEIPT, request, "idem-sale-100");
            }));

            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);

            assertEquals(a.id(), b.id());
            assertEquals("B001-1", a.number());
        }

        assertEquals(1, documents.count());
        assertEquals(1, currentSeriesValue());
    }

    @Test
    void concurrentDifferentRequestsReceiveDifferentSequentialCorrelatives() throws Exception {
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> authenticatedCall(tenantId, () -> {
                start.await();
                return service.create(DocumentType.RECEIPT, request("SALE-A"), "idem-a");
            }));
            var second = executor.submit(() -> authenticatedCall(tenantId, () -> {
                start.await();
                return service.create(DocumentType.RECEIPT, request("SALE-B"), "idem-b");
            }));

            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);

            assertNotEquals(a.id(), b.id());
            assertEquals(java.util.Set.of("B001-1", "B001-2"), java.util.Set.of(a.number(), b.number()));
        }

        assertEquals(2, documents.count());
        assertEquals(2, currentSeriesValue());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadIsRejectedWithoutConsumingCorrelative() throws Exception {
        authenticatedCall(tenantId, () -> service.create(
                DocumentType.RECEIPT, request("ORDER-001"), "same-key"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                authenticatedCall(tenantId, () -> service.create(
                        DocumentType.RECEIPT, request("ORDER-002"), "same-key")));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
        assertEquals(1, documents.count());
        assertEquals(1, currentSeriesValue());
    }

    @Test
    void duplicateExternalIdDoesNotConsumeAnotherCorrelative() throws Exception {
        authenticatedCall(tenantId, () -> service.create(
                DocumentType.RECEIPT, request("ERP-001"), "idem-001"));

        BusinessException duplicate = assertThrows(BusinessException.class, () ->
                authenticatedCall(tenantId, () -> service.create(
                        DocumentType.RECEIPT, request("ERP-001"), "idem-002")));

        assertEquals("EXTERNAL_ID_ALREADY_EXISTS", duplicate.getCode());
        assertEquals(1, currentSeriesValue());

        var next = authenticatedCall(tenantId, () -> service.create(
                DocumentType.RECEIPT, request("ERP-002"), "idem-003"));
        assertEquals("B001-2", next.number());
    }

    @Test
    void apiKeyPrincipalCanCreateInvoiceWithIdempotencyRecord() throws Exception {
        var item = new DocumentItemRequest(
                "SKU-M2M",
                "Servicio M2M",
                "NIU",
                BigDecimal.ONE,
                new BigDecimal("100.00"),
                "10",
                new BigDecimal("18.00"),
                BigDecimal.ZERO);
        var customer = new CustomerRequest(
                "6",
                "20100066603",
                "CLIENTE M2M SAC",
                "Av. Cliente 456 - Lima",
                "m2m@example.test",
                "PE");
        var request = new CreateDocumentRequest(
                issuerId,
                "M2M-ORDER-001",
                "F001",
                null,
                null,
                "0101",
                "PEN",
                customer,
                List.of(item),
                null,
                null);

        var created = apiKeyAuthenticatedCall(tenantId, () ->
                service.create(DocumentType.INVOICE, request, "m2m-idem-001"));

        assertEquals("F001-1", created.number());
        assertEquals(1L, jdbc.sql("SELECT COUNT(*) FROM idempotency_record WHERE tenant_id=:tenantId AND idempotency_key='m2m-idem-001'")
                .param("tenantId", tenantId).query(Long.class).single());
    }

    @Test
    void tenantCannotReadAnotherTenantsDocumentByUuid() throws Exception {
        var created = authenticatedCall(tenantId, () -> service.create(
                DocumentType.RECEIPT, request("PRIVATE-001"), "idem-private"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                authenticatedCall(otherTenantId, () -> service.get(created.id())));

        assertEquals("DOCUMENT_NOT_FOUND", exception.getCode());
    }

    private CreateDocumentRequest request(String externalId) {
        var item = new DocumentItemRequest(
                "SKU-1",
                "Servicio de prueba",
                "NIU",
                BigDecimal.ONE,
                new BigDecimal("100.00"),
                "10",
                new BigDecimal("18.00"),
                BigDecimal.ZERO);
        return new CreateDocumentRequest(
                issuerId,
                externalId,
                "B001",
                null,
                null,
                "0101",
                "PEN",
                null,
                List.of(item),
                null,
                null);
    }

    private UUID createTenant(String prefix) {
        TenantEntity tenant = new TenantEntity();
        tenant.setName(prefix);
        tenant.setSlug(prefix + "-" + UUID.randomUUID());
        return tenants.saveAndFlush(tenant).getId();
    }

    private long currentSeriesValue() {
        return series.findAllByTenantIdAndIssuerIdOrderByDocumentTypeAscSeriesAsc(tenantId, issuerId).stream()
                .filter(value -> value.getDocumentType() == DocumentType.RECEIPT && "B001".equals(value.getSeries()))
                .findFirst()
                .orElseThrow()
                .getCurrentValue();
    }

    private <T> T authenticatedCall(UUID currentTenantId, ThrowingSupplier<T> action) throws Exception {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt(currentTenantId)));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private <T> T apiKeyAuthenticatedCall(UUID currentTenantId, ThrowingSupplier<T> action) throws Exception {
        var context = SecurityContextHolder.createEmptyContext();
        var principal = new ApiKeyPrincipal(
                UUID.randomUUID(), currentTenantId, "integration-key", java.util.Set.of("DOCUMENT_WRITE"));
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("SCOPE_DOCUMENT_WRITE"))));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Jwt jwt(UUID currentTenantId) {
        Instant now = Instant.now();
        return new Jwt(
                "test-token",
                now,
                now.plusSeconds(900),
                Map.of("alg", "none"),
                Map.of(
                        "sub", userId.toString(),
                        "tenant_id", currentTenantId.toString(),
                        "email", "integration@example.test",
                        "roles", List.of("ADMIN")));
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
