package pe.com.perubilling.delivery.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.delivery.api.DocumentAccessLinkResponse;
import pe.com.perubilling.delivery.api.PublicDocumentResponse;
import pe.com.perubilling.billing.domain.DeliveryChannel;
import pe.com.perubilling.billing.domain.DeliveryStatus;
import pe.com.perubilling.delivery.domain.DocumentAccessTokenEntity;
import pe.com.perubilling.delivery.infrastructure.DocumentAccessTokenRepository;
import pe.com.perubilling.shared.domain.DocumentPdfLayout;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.crypto.HashingService;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;
import pe.com.perubilling.shared.storage.ArtifactStorage;

@Service
public class DocumentDeliveryService {
    private final ElectronicDocumentRepository documents;
    private final DocumentAccessTokenRepository tokens;
    private final IssuerRepository issuers;
    private final TenantContext tenant;
    private final HashingService hashing;
    private final ArtifactStorage storage;
    private final SecureRandom random = new SecureRandom();
    private final String publicBaseUrl;

    public DocumentDeliveryService(ElectronicDocumentRepository documents, DocumentAccessTokenRepository tokens,
                                   IssuerRepository issuers, TenantContext tenant, HashingService hashing,
                                   ArtifactStorage storage,
                                   @Value("${app.public.base-url:http://localhost:8080}") String publicBaseUrl) {
        this.documents=documents; this.tokens=tokens; this.issuers=issuers; this.tenant=tenant;
        this.hashing=hashing; this.storage=storage;
        this.publicBaseUrl=publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public DocumentAccessLinkResponse createAccessLink(UUID documentId, Integer validDays) {
        UUID tenantId=tenant.requireTenantId();
        var document=documents.findByIdAndTenantId(documentId,tenantId)
                .orElseThrow(()->BusinessException.notFound("DOCUMENT_NOT_FOUND","Documento no encontrado"));
        if(document.getPdfPath()==null) {
            throw BusinessException.conflict("DOCUMENT_NOT_READY","El documento todavía no tiene representación PDF");
        }
        int days=validDays==null?370:Math.max(365,Math.min(validDays,730));
        return generateAccessLink(tenantId, documentId, days);
    }


    /**
     * Crea el enlace que se imprimirá en el PDF durante el procesamiento.
     * No exige que el PDF exista todavía y no depende de TenantContext. El token
     * en claro se devuelve una sola vez; en PostgreSQL solo queda su SHA-256.
     */
    @Transactional
    public DocumentAccessLinkResponse createProcessingAccessLink(UUID tenantId, UUID documentId) {
        var document = documents.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(()->BusinessException.notFound("DOCUMENT_NOT_FOUND","Documento no encontrado"));
        // Durante reintentos internos aún no publicables no conviene dejar tokens huérfanos
        // con un año de vigencia. Los enlaces ya otorgados nunca se revocan automáticamente.
        if (!isPubliclyAvailable(document.getStatus())) {
            Instant revokedAt = Instant.now();
            for (var active : tokens.findAllByTenantIdAndDocumentIdAndRevokedAtIsNull(tenantId, documentId)) {
                active.setRevokedAt(revokedAt);
            }
        }
        return generateAccessLink(tenantId, documentId, 370);
    }

    private DocumentAccessLinkResponse generateAccessLink(UUID tenantId, UUID documentId, int days) {
        byte[] raw=new byte[32]; random.nextBytes(raw);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        var entity=new DocumentAccessTokenEntity();
        entity.setTenantId(tenantId);
        entity.setDocumentId(documentId);
        entity.setTokenHash(hashing.sha256(token));
        entity.setExpiresAt(Instant.now().plus(days, ChronoUnit.DAYS));
        tokens.save(entity);
        return new DocumentAccessLinkResponse(token, publicBaseUrl+"/public/v1/documents/"+token, entity.getExpiresAt());
    }

    @Transactional
    public void markGranted(UUID documentId, DeliveryChannel channel) {
        UUID tenantId=tenant.requireTenantId();
        var document=documents.findByIdAndTenantId(documentId,tenantId)
                .orElseThrow(()->BusinessException.notFound("DOCUMENT_NOT_FOUND","Documento no encontrado"));
        grant(document, DeliveryStatus.DELIVERED, channel);
    }

    @Transactional
    public PublicDocumentResponse view(String token) {
        var resolved=resolve(token);
        grant(resolved.document(), DeliveryStatus.VIEWED, DeliveryChannel.PUBLIC_PORTAL);
        resolved.token().setLastAccessAt(Instant.now());
        var issuer=issuers.findById(resolved.document().getIssuerId())
                .orElseThrow(()->BusinessException.notFound("ISSUER_NOT_FOUND","Emisor no encontrado"));
        return new PublicDocumentResponse(
                resolved.document().getDocumentType().getSunatCode(), resolved.document().getFullNumber(),
                resolved.document().getIssueDate(), issuer.getRuc(), issuer.getBusinessName(),
                resolved.document().getCurrency(), resolved.document().getTotalAmount(), resolved.document().getStatus().name());
    }

    @Transactional
    public PublicArtifact download(String token, String type) {
        return download(token, type, DocumentPdfLayout.A4);
    }

    @Transactional
    public PublicArtifact download(String token, String type, DocumentPdfLayout layout) {
        var resolved=resolve(token);
        var document=resolved.document();
        String path; String mediaType; String filename;
        if("pdf".equals(type)) {
            DocumentPdfLayout safeLayout = layout == null ? DocumentPdfLayout.A4 : layout;
            if (safeLayout == DocumentPdfLayout.THERMAL_80) {
                path=document.getThermalPdfPath();
                filename=document.getFullNumber()+"-thermal-80.pdf";
            } else {
                path=document.getPdfPath();
                filename=document.getFullNumber()+".pdf";
            }
            mediaType="application/pdf";
        } else if("xml".equals(type)) {
            path=document.getSignedXmlPath()!=null?document.getSignedXmlPath():document.getXmlPath();
            mediaType="application/xml"; filename=document.getFullNumber()+".xml";
        } else {
            throw BusinessException.badRequest("INVALID_ARTIFACT","Artefacto público no soportado");
        }
        if(path==null) throw BusinessException.notFound("ARTIFACT_NOT_READY","El artefacto todavía no está disponible");
        grant(document, DeliveryStatus.DOWNLOADED, DeliveryChannel.PUBLIC_PORTAL);
        resolved.token().setLastAccessAt(Instant.now());
        return new PublicArtifact(storage.read(path), mediaType, filename);
    }

    private ResolvedAccess resolve(String rawToken) {
        if(rawToken==null || rawToken.length()<32 || rawToken.length()>200) {
            throw BusinessException.notFound("ACCESS_LINK_NOT_FOUND","Enlace no válido o vencido");
        }
        var token=tokens.findByTokenHash(hashing.sha256(rawToken))
                .orElseThrow(()->BusinessException.notFound("ACCESS_LINK_NOT_FOUND","Enlace no válido o vencido"));
        Instant now=Instant.now();
        if(token.getRevokedAt()!=null || !token.getExpiresAt().isAfter(now)) {
            throw BusinessException.notFound("ACCESS_LINK_NOT_FOUND","Enlace no válido o vencido");
        }
        var document=documents.findByIdAndTenantId(token.getDocumentId(), token.getTenantId())
                .orElseThrow(()->BusinessException.notFound("DOCUMENT_NOT_FOUND","Documento no encontrado"));
        if(!isPubliclyAvailable(document.getStatus())) {
            throw BusinessException.notFound("DOCUMENT_NOT_AVAILABLE","Documento no disponible");
        }
        return new ResolvedAccess(token,document);
    }

    private boolean isPubliclyAvailable(DocumentStatus status) {
        return switch (status) {
            case PENDING_SUMMARY, SUMMARY_PROCESSING, ACCEPTED, OBSERVED, VOID_REQUESTED, VOIDED -> true;
            default -> false;
        };
    }

    private void grant(pe.com.perubilling.billing.domain.ElectronicDocumentEntity document,
                       DeliveryStatus target, DeliveryChannel channel) {
        if(document.getGrantedAt()==null) document.setGrantedAt(Instant.now());
        document.setGrantedChannel(channel);
        if(target.ordinal()>document.getDeliveryStatus().ordinal()) document.setDeliveryStatus(target);
    }

    private record ResolvedAccess(DocumentAccessTokenEntity token,
                                  pe.com.perubilling.billing.domain.ElectronicDocumentEntity document) {}
    public record PublicArtifact(byte[] content,String mediaType,String filename) {}
}
