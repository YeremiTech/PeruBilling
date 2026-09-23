package pe.com.perubilling.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.com.perubilling.identity.domain.UserAccountEntity;
import pe.com.perubilling.identity.domain.UserRole;
import pe.com.perubilling.identity.infrastructure.UserAccountRepository;
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
class AuthenticationLockoutIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AuthenticationStateService authentication;
    @Autowired UserAccountRepository users;
    @Autowired TenantRepository tenants;
    @Autowired PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        tenants.deleteAll();

        var tenant = new TenantEntity();
        tenant.setName("Auth integration");
        tenant.setSlug("auth-integration");
        tenant = tenants.saveAndFlush(tenant);

        email = "admin@example.test";
        var user = new UserAccountEntity();
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("CorrectPassword!"));
        user.setRole(UserRole.ADMIN);
        user.setEnabled(true);
        users.saveAndFlush(user);
    }

    @Test
    void fifthInvalidPasswordPersistsAccountLock() {
        for (int i = 0; i < 4; i++) {
            var result = authentication.authenticate(email, "wrong-password", Instant.now());
            assertEquals(AuthenticationStateService.Outcome.INVALID, result.outcome());
        }

        var fifth = authentication.authenticate(email, "wrong-password", Instant.now());
        assertEquals(AuthenticationStateService.Outcome.LOCKED, fifth.outcome());

        var persisted = users.findByEmailIgnoreCase(email).orElseThrow();
        assertNotNull(persisted.getLockedUntil());
        assertEquals(0, persisted.getFailedLoginCount());
    }
}
