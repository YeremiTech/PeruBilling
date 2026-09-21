package pe.com.perubilling.summary.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.summary.infrastructure.DailySummaryRepository;
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
class DailySummaryConcurrencyIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DailySummaryService service;
    @Autowired DailySummaryRepository summaries;
    @Autowired ElectronicDocumentRepository documents;
    @Autowired IssuerRepository issuers;
    @Autowired TenantRepository tenants;

    UUID tenantId; UUID issuerId; LocalDate date;

    @BeforeEach
    void setup() {
        documents.deleteAll(); summaries.deleteAll(); issuers.deleteAll(); tenants.deleteAll();
        var tenant = new TenantEntity(); tenant.setName("Concurrency"); tenant.setSlug("summary-"+UUID.randomUUID());
        tenant = tenants.saveAndFlush(tenant); tenantId=tenant.getId();
        var issuer = new IssuerEntity(); issuer.setTenantId(tenantId); issuer.setRuc("20123456786");
        issuer.setBusinessName("TEST SAC"); issuer.setAddress("Lima"); issuer.setUbigeo("150101");
        issuer.setSunatEnvironment(SunatEnvironment.LOCAL); issuer.setActive(true);
        issuer=issuers.saveAndFlush(issuer); issuerId=issuer.getId(); date=LocalDate.now();
        documents.saveAndFlush(receipt(1)); documents.saveAndFlush(receipt(2));
    }

    @Test
    void twoNodesCannotCreateDuplicateSummaryFromSamePendingSet() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return service.createAutomatically(tenantId,issuerId,date); });
            var b = executor.submit(() -> { start.await(); return service.createAutomatically(tenantId,issuerId,date); });
            start.countDown();
            int created=(a.get(20,TimeUnit.SECONDS).isPresent()?1:0)+(b.get(20,TimeUnit.SECONDS).isPresent()?1:0);
            assertEquals(1,created);
        }
        assertEquals(1,summaries.count());
        assertEquals(2,documents.findAll().stream().filter(d->d.getDailySummaryId()!=null).count());
    }

    private ElectronicDocumentEntity receipt(long number) {
        var d=new ElectronicDocumentEntity(); d.setTenantId(tenantId); d.setIssuerId(issuerId);
        d.setDocumentType(DocumentType.RECEIPT); d.setSeries("B001"); d.setCorrelativo(number);
        d.setFullNumber("B001-"+number); d.setIssueDate(date); d.setIssueTime(LocalTime.of(10,0));
        d.setOperationType("0101"); d.setCurrency("PEN"); d.setCustomerDocumentType("0");
        d.setCustomerDocumentNumber("-"); d.setCustomerName("CONSUMIDOR FINAL");
        d.setTaxableAmount(new BigDecimal("100.00")); d.setIgvAmount(new BigDecimal("18.00"));
        d.setTotalAmount(new BigDecimal("118.00")); d.setStatus(DocumentStatus.PENDING_SUMMARY);
        d.setSummaryConditionCode("1"); return d;
    }
}
