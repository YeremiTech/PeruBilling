package pe.com.perubilling.identity.application;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.identity.domain.UserAccountEntity;
import pe.com.perubilling.identity.domain.UserRole;
import pe.com.perubilling.identity.infrastructure.UserAccountRepository;
import pe.com.perubilling.tenant.domain.TenantEntity;
import pe.com.perubilling.tenant.domain.TenantStatus;
import pe.com.perubilling.tenant.infrastructure.TenantRepository;

@Component
public class BootstrapInitializer implements ApplicationRunner {
    private final boolean enabled;
    private final String tenantName;
    private final String email;
    private final String password;
    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public BootstrapInitializer(@Value("${app.bootstrap.enabled:false}") boolean enabled,
                                @Value("${app.bootstrap.tenant-name:PeruBilling}") String tenantName,
                                @Value("${app.bootstrap.admin-email:}") String email,
                                @Value("${app.bootstrap.admin-password:}") String password,
                                TenantRepository tenants, UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.tenantName = tenantName;
        this.email = email;
        this.password = password;
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || email.isBlank() || password.isBlank() || users.existsByEmailIgnoreCase(email)) return;
        String slug = slugify(tenantName);
        TenantEntity tenant = tenants.findBySlug(slug).orElseGet(() -> {
            TenantEntity entity = new TenantEntity();
            entity.setName(tenantName);
            entity.setSlug(slug);
            entity.setStatus(TenantStatus.ACTIVE);
            return tenants.save(entity);
        });
        UserAccountEntity admin = new UserAccountEntity();
        admin.setTenantId(tenant.getId());
        admin.setEmail(email.trim().toLowerCase(Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        users.save(admin);
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "default" : slug;
    }
}
