package pe.com.perubilling.issuer.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.com.perubilling.issuer.api.CertificateResponse;
import pe.com.perubilling.issuer.api.CreateIssuerRequest;
import pe.com.perubilling.issuer.api.CreateSeriesRequest;
import pe.com.perubilling.issuer.api.IssuerResponse;
import pe.com.perubilling.issuer.api.SeriesResponse;
import pe.com.perubilling.issuer.api.SunatCredentialsRequest;
import pe.com.perubilling.issuer.domain.DigitalCertificateEntity;
import pe.com.perubilling.issuer.domain.DocumentSeriesEntity;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.issuer.infrastructure.DigitalCertificateRepository;
import pe.com.perubilling.issuer.infrastructure.DocumentSeriesRepository;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.crypto.SecretCryptoService;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;

@Service
public class IssuerService {
    private final IssuerRepository issuers;
    private final DigitalCertificateRepository certificates;
    private final DocumentSeriesRepository seriesRepository;
    private final TenantContext tenantContext;
    private final RucValidator rucValidator;
    private final SecretCryptoService crypto;

    public IssuerService(IssuerRepository issuers, DigitalCertificateRepository certificates,
                         DocumentSeriesRepository seriesRepository, TenantContext tenantContext,
                         RucValidator rucValidator, SecretCryptoService crypto) {
        this.issuers = issuers;
        this.certificates = certificates;
        this.seriesRepository = seriesRepository;
        this.tenantContext = tenantContext;
        this.rucValidator = rucValidator;
        this.crypto = crypto;
    }

    @Transactional
    public IssuerResponse create(CreateIssuerRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        if (!rucValidator.isValid(request.ruc())) throw BusinessException.badRequest("INVALID_RUC", "El RUC no supera la validación de dígito verificador");
        if (issuers.existsByTenantIdAndRuc(tenantId, request.ruc())) throw BusinessException.conflict("RUC_EXISTS", "El RUC ya está registrado en este tenant");
        IssuerEntity entity = new IssuerEntity();
        entity.setTenantId(tenantId);
        entity.setRuc(request.ruc());
        entity.setBusinessName(request.businessName().trim());
        entity.setTradeName(trimToNull(request.tradeName()));
        entity.setAddress(request.address().trim());
        entity.setUbigeo(request.ubigeo());
        entity.setEstablishmentCode(request.establishmentCode() == null ? "0000" : request.establishmentCode());
        entity.setDepartment(trimToNull(request.department()));
        entity.setProvince(trimToNull(request.province()));
        entity.setDistrict(trimToNull(request.district()));
        if (request.environment() != null) entity.setSunatEnvironment(request.environment());
        return toResponse(issuers.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public List<IssuerResponse> list() {
        return issuers.findAllByTenantIdOrderByBusinessName(tenantContext.requireTenantId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public IssuerResponse get(UUID issuerId) {
        return toResponse(requireEntity(issuerId));
    }

    @Transactional
    public IssuerResponse setActive(UUID issuerId, boolean active) {
        IssuerEntity issuer = requireEntity(issuerId);
        issuer.setActive(active);
        return toResponse(issuers.saveAndFlush(issuer));
    }

    @Transactional(readOnly = true)
    public IssuerEntity requireEntity(UUID issuerId) {
        return issuers.findByIdAndTenantId(issuerId, tenantContext.requireTenantId())
                .orElseThrow(() -> BusinessException.notFound("ISSUER_NOT_FOUND", "Emisor no encontrado"));
    }

    @Transactional
    public IssuerResponse configureSunat(UUID issuerId, SunatCredentialsRequest request) {
        IssuerEntity issuer = requireEntity(issuerId);
        issuer.setSolUser(request.user().trim().toUpperCase(Locale.ROOT));
        issuer.setSolPasswordEncrypted(crypto.encrypt(request.password()));
        issuer.setSunatEnvironment(request.environment());
        return toResponse(issuers.saveAndFlush(issuer));
    }

    @Transactional
    public SeriesResponse createSeries(UUID issuerId, CreateSeriesRequest request) {
        IssuerEntity issuer = requireEntity(issuerId);
        String series = request.series().toUpperCase(Locale.ROOT);
        validateSeriesType(request.documentType(), series);
        DocumentSeriesEntity entity = new DocumentSeriesEntity();
        entity.setTenantId(issuer.getTenantId());
        entity.setIssuerId(issuerId);
        entity.setDocumentType(request.documentType());
        entity.setSeries(series);
        entity.setCurrentValue(request.startAt() - 1);
        return toSeries(seriesRepository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public List<SeriesResponse> listSeries(UUID issuerId) {
        requireEntity(issuerId);
        return seriesRepository.findAllByTenantIdAndIssuerIdOrderByDocumentTypeAscSeriesAsc(tenantContext.requireTenantId(), issuerId)
                .stream().map(this::toSeries).toList();
    }

    @Transactional
    public SeriesResponse setSeriesActive(UUID issuerId, UUID seriesId, boolean active) {
        IssuerEntity issuer = requireEntity(issuerId);
        DocumentSeriesEntity series = seriesRepository.findByIdAndTenantIdAndIssuerId(
                        seriesId, issuer.getTenantId(), issuerId)
                .orElseThrow(() -> BusinessException.notFound("SERIES_NOT_FOUND", "Serie no encontrada"));
        series.setActive(active);
        return toSeries(seriesRepository.saveAndFlush(series));
    }

    @Transactional
    public CertificateResponse uploadCertificate(UUID issuerId, MultipartFile file, String password, String requestedAlias) {
        IssuerEntity issuer = requireEntity(issuerId);
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_CERTIFICATE", "Debe adjuntar un certificado PKCS#12");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw BusinessException.badRequest("CERTIFICATE_TOO_LARGE", "El certificado supera 5 MB");
        }
        if (password == null || password.isBlank()) {
            throw BusinessException.badRequest("CERTIFICATE_PASSWORD_REQUIRED", "La contraseña del PKCS#12 es obligatoria");
        }

        byte[] pfx = readCertificateBytes(file);
        KeyStore keyStore = loadPkcs12(pfx, password);
        String alias = selectAlias(keyStore, requestedAlias);
        X509Certificate x509 = certificateFrom(keyStore, alias);
        validateCertificateValidity(x509);
        validateCertificateIdentity(issuer, x509);
        String fingerprint = certificateFingerprint(x509);

        if (certificates.existsByTenantIdAndIssuerIdAndFingerprint(
                issuer.getTenantId(), issuerId, fingerprint)) {
            throw BusinessException.conflict(
                    "CERTIFICATE_ALREADY_EXISTS",
                    "El certificado ya está registrado para este emisor");
        }

        var previous = certificates.findAllByTenantIdAndIssuerIdOrderByCreatedAtDesc(
                issuer.getTenantId(), issuerId);
        previous.forEach(certificate -> certificate.setActive(false));
        certificates.saveAllAndFlush(previous);

        DigitalCertificateEntity entity = new DigitalCertificateEntity();
        entity.setTenantId(issuer.getTenantId());
        entity.setIssuerId(issuerId);
        entity.setCertificateAlias(alias);
        entity.setEncryptedPfx(crypto.encryptBytes(pfx));
        entity.setPasswordEncrypted(crypto.encrypt(password));
        entity.setFingerprint(fingerprint);
        entity.setSubjectDn(x509.getSubjectX500Principal().getName());
        entity.setSerialNumber(x509.getSerialNumber().toString(16));
        entity.setValidFrom(x509.getNotBefore().toInstant());
        entity.setValidUntil(x509.getNotAfter().toInstant());
        entity.setActive(true);
        return toCertificate(certificates.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public List<CertificateResponse> listCertificates(UUID issuerId) {
        IssuerEntity issuer = requireEntity(issuerId);
        return certificates.findAllByTenantIdAndIssuerIdOrderByCreatedAtDesc(issuer.getTenantId(), issuerId).stream().map(this::toCertificate).toList();
    }

    @Transactional
    public CertificateResponse activateCertificate(UUID issuerId, UUID certificateId) {
        IssuerEntity issuer = requireEntity(issuerId);
        DigitalCertificateEntity selected = certificates.findByIdAndTenantIdAndIssuerId(certificateId, issuer.getTenantId(), issuerId)
                .orElseThrow(() -> BusinessException.notFound("CERTIFICATE_NOT_FOUND", "Certificado no encontrado"));
        Instant now = Instant.now();
        if (selected.getValidFrom().isAfter(now) || !selected.getValidUntil().isAfter(now)) {
            throw BusinessException.badRequest("CERTIFICATE_NOT_VALID", "El certificado no está vigente");
        }
        var all = certificates.findAllByTenantIdAndIssuerIdOrderByCreatedAtDesc(issuer.getTenantId(), issuerId);
        all.forEach(value -> value.setActive(value.getId().equals(certificateId)));
        certificates.saveAllAndFlush(all);
        return toCertificate(selected);
    }

    @Transactional
    public CertificateResponse revokeCertificate(UUID issuerId, UUID certificateId) {
        IssuerEntity issuer = requireEntity(issuerId);
        DigitalCertificateEntity selected = certificates.findByIdAndTenantIdAndIssuerId(certificateId, issuer.getTenantId(), issuerId)
                .orElseThrow(() -> BusinessException.notFound("CERTIFICATE_NOT_FOUND", "Certificado no encontrado"));
        selected.setActive(false);
        return toCertificate(certificates.saveAndFlush(selected));
    }

    public DigitalCertificateEntity requireActiveCertificate(UUID tenantId, UUID issuerId) {
        Instant now = Instant.now();
        return certificates.findFirstByTenantIdAndIssuerIdAndActiveTrueAndValidFromBeforeAndValidUntilAfterOrderByValidUntilDesc(tenantId, issuerId, now, now)
                .orElseThrow(() -> BusinessException.badRequest("CERTIFICATE_REQUIRED", "El emisor no tiene un certificado digital activo y vigente"));
    }

    private byte[] readCertificateBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw BusinessException.badRequest(
                    "CERTIFICATE_READ_ERROR",
                    "No se pudo leer el archivo PKCS#12 recibido");
        }
    }

    private KeyStore loadPkcs12(byte[] pfx, String password) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(pfx), password.toCharArray());
            return keyStore;
        } catch (IOException ex) {
            if (isPasswordFailure(ex)) {
                throw BusinessException.badRequest(
                        "CERTIFICATE_PASSWORD_INVALID",
                        "La contraseña del PKCS#12 es incorrecta");
            }
            throw BusinessException.badRequest(
                    "INVALID_CERTIFICATE",
                    "El archivo no contiene un PKCS#12 válido");
        } catch (GeneralSecurityException ex) {
            throw BusinessException.badRequest(
                    "INVALID_CERTIFICATE",
                    "El archivo no contiene un PKCS#12 válido");
        }
    }

    private boolean isPasswordFailure(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 8) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("password") || normalized.contains("contraseña")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private X509Certificate certificateFrom(KeyStore keyStore, String alias) {
        try {
            if (!keyStore.isKeyEntry(alias)) {
                throw BusinessException.badRequest(
                        "PRIVATE_KEY_NOT_FOUND",
                        "El alias seleccionado no contiene una clave privada");
            }
            var certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509)) {
                throw BusinessException.badRequest(
                        "INVALID_CERTIFICATE",
                        "El alias seleccionado no contiene un certificado X.509");
            }
            return x509;
        } catch (java.security.KeyStoreException ex) {
            throw BusinessException.badRequest(
                    "INVALID_CERTIFICATE",
                    "No se pudo leer el certificado X.509 del PKCS#12");
        }
    }

    private void validateCertificateValidity(X509Certificate certificate) {
        try {
            certificate.checkValidity();
        } catch (CertificateExpiredException ex) {
            throw BusinessException.badRequest(
                    "CERTIFICATE_EXPIRED",
                    "El certificado digital está vencido");
        } catch (CertificateNotYetValidException ex) {
            throw BusinessException.badRequest(
                    "CERTIFICATE_NOT_YET_VALID",
                    "El certificado digital todavía no está vigente");
        }
    }

    private String certificateFingerprint(X509Certificate certificate) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo calcular el fingerprint del certificado", ex);
        }
    }


    private void validateCertificateIdentity(IssuerEntity issuer, X509Certificate certificate) {
        if (issuer.getSunatEnvironment() != SunatEnvironment.PRODUCTION) {
            return;
        }

        StringBuilder identity = new StringBuilder(certificate.getSubjectX500Principal().getName());
        try {
            Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
            if (altNames != null) {
                for (List<?> alt : altNames) {
                    if (alt.size() > 1 && alt.get(1) != null) {
                        identity.append(' ').append(alt.get(1));
                    }
                }
            }
        } catch (CertificateParsingException ex) {
            throw BusinessException.badRequest(
                    "INVALID_CERTIFICATE",
                    "No se pudo interpretar la identidad X.509 del certificado");
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?<!\\d)\\d{11}(?!\\d)")
                .matcher(identity.toString());
        boolean foundAnyRuc = false;
        boolean foundIssuerRuc = false;
        while (matcher.find()) {
            foundAnyRuc = true;
            if (issuer.getRuc().equals(matcher.group())) {
                foundIssuerRuc = true;
            }
        }
        if (!foundIssuerRuc) {
            throw BusinessException.badRequest(
                    "CERTIFICATE_RUC_MISMATCH",
                    foundAnyRuc
                            ? "El certificado digital no corresponde al RUC del emisor"
                            : "En PRODUCCIÓN el certificado debe exponer el RUC del emisor en su identidad X.509");
        }
    }

    private String selectAlias(KeyStore keyStore, String requestedAlias) {
        try {
            if (requestedAlias != null && !requestedAlias.isBlank()) {
                if (!keyStore.containsAlias(requestedAlias)) {
                    throw BusinessException.badRequest(
                            "CERTIFICATE_ALIAS_NOT_FOUND",
                            "El alias solicitado no existe en el PKCS#12");
                }
                return requestedAlias;
            }
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    return alias;
                }
            }
            throw BusinessException.badRequest(
                    "PRIVATE_KEY_NOT_FOUND",
                    "El PKCS#12 no contiene una clave privada");
        } catch (java.security.KeyStoreException ex) {
            throw BusinessException.badRequest(
                    "INVALID_CERTIFICATE",
                    "No se pudo inspeccionar el contenido del PKCS#12");
        }
    }

    private void validateSeriesType(pe.com.perubilling.shared.domain.DocumentType type, String series) {
        if (type == pe.com.perubilling.shared.domain.DocumentType.INVOICE && !series.startsWith("F"))
            throw BusinessException.badRequest("INVALID_SERIES", "Las facturas deben usar una serie iniciada en F");
        if (type == pe.com.perubilling.shared.domain.DocumentType.RECEIPT && !series.startsWith("B"))
            throw BusinessException.badRequest("INVALID_SERIES", "Las boletas deben usar una serie iniciada en B");
    }

    private IssuerResponse toResponse(IssuerEntity entity) {
        return new IssuerResponse(entity.getId(), entity.getRuc(), entity.getBusinessName(), entity.getTradeName(), entity.getAddress(), entity.getUbigeo(),
                entity.getEstablishmentCode(), entity.getDepartment(), entity.getProvince(), entity.getDistrict(), entity.getSunatEnvironment(), entity.isActive(),
                entity.getSolUser() != null && entity.getSolPasswordEncrypted() != null);
    }

    private SeriesResponse toSeries(DocumentSeriesEntity entity) {
        return new SeriesResponse(entity.getId(), entity.getDocumentType(), entity.getSeries(), entity.getCurrentValue(), entity.isActive());
    }

    private CertificateResponse toCertificate(DigitalCertificateEntity entity) {
        return new CertificateResponse(entity.getId(), entity.getCertificateAlias(), entity.getFingerprint(), entity.getSubjectDn(), entity.getSerialNumber(),
                entity.getValidFrom(), entity.getValidUntil(), entity.isActive());
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
