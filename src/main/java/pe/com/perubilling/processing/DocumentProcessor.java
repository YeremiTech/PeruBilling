package pe.com.perubilling.processing;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.infrastructure.AllowanceChargeRepository;
import pe.com.perubilling.billing.infrastructure.DocumentAllowanceChargeRepository;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentItemRepository;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.billing.infrastructure.PaymentInstallmentRepository;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.cpe.pdf.DocumentPdfGenerator;
import pe.com.perubilling.cpe.signature.XmlDocumentSigner;
import pe.com.perubilling.cpe.signature.XmlSignatureVerifier;
import pe.com.perubilling.cpe.ubl.UblGenerator;
import pe.com.perubilling.cpe.validation.BusinessDocumentValidator;
import pe.com.perubilling.cpe.validation.SunatSubmissionRulesValidator;
import pe.com.perubilling.cpe.validation.SunatXsdValidator;
import pe.com.perubilling.delivery.application.DocumentDeliveryService;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.issuer.infrastructure.DigitalCertificateRepository;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.crypto.HashingService;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.shared.storage.ArtifactStorage;
import pe.com.perubilling.sunat.application.SunatGateway;
import pe.com.perubilling.sunat.domain.SunatClientException;
import pe.com.perubilling.sunat.domain.SunatReconciliationStatus;
import pe.com.perubilling.sunat.domain.SunatSubmissionResult;

@Service
public class DocumentProcessor {
    private final ElectronicDocumentRepository documents;
    private final ElectronicDocumentItemRepository items;
    private final AllowanceChargeRepository allowanceCharges;
    private final DocumentAllowanceChargeRepository documentAllowanceCharges;
    private final PaymentInstallmentRepository installments;
    private final IssuerRepository issuers;
    private final DigitalCertificateRepository certificates;
    private final UblGenerator ublGenerator;
    private final BusinessDocumentValidator businessValidator;
    private final SunatSubmissionRulesValidator sunatRules;
    private final SunatXsdValidator xsdValidator;
    private final XmlDocumentSigner signer;
    private final XmlSignatureVerifier signatureVerifier;
    private final SunatGateway sunat;
    private final DocumentPdfGenerator pdfGenerator;
    private final ArtifactStorage storage;
    private final HashingService hashing;
    private final DocumentStateTransitionService transitions;
    private final DocumentDeliveryService delivery;

    public DocumentProcessor(
            ElectronicDocumentRepository documents,
            ElectronicDocumentItemRepository items,
            AllowanceChargeRepository allowanceCharges,
            DocumentAllowanceChargeRepository documentAllowanceCharges,
            PaymentInstallmentRepository installments,
            IssuerRepository issuers,
            DigitalCertificateRepository certificates,
            UblGenerator ublGenerator,
            BusinessDocumentValidator businessValidator,
            SunatSubmissionRulesValidator sunatRules,
            SunatXsdValidator xsdValidator,
            XmlDocumentSigner signer,
            XmlSignatureVerifier signatureVerifier,
            SunatGateway sunat,
            DocumentPdfGenerator pdfGenerator,
            ArtifactStorage storage,
            HashingService hashing,
            DocumentStateTransitionService transitions,
            DocumentDeliveryService delivery) {
        this.documents = documents;
        this.items = items;
        this.allowanceCharges = allowanceCharges;
        this.documentAllowanceCharges = documentAllowanceCharges;
        this.installments = installments;
        this.issuers = issuers;
        this.certificates = certificates;
        this.ublGenerator = ublGenerator;
        this.businessValidator = businessValidator;
        this.sunatRules = sunatRules;
        this.xsdValidator = xsdValidator;
        this.signer = signer;
        this.signatureVerifier = signatureVerifier;
        this.sunat = sunat;
        this.pdfGenerator = pdfGenerator;
        this.storage = storage;
        this.hashing = hashing;
        this.transitions = transitions;
        this.delivery = delivery;
    }

    public void process(UUID documentId) {
        Instant started = Instant.now();
        var document = documents.findById(documentId).orElse(null);
        if (document == null) {
            return;
        }

        var attempt = transitions.startAttempt(documentId, started);
        if (attempt == null) {
            return;
        }

        DocumentBundle bundle = null;
        boolean submissionStarted = false;
        try {
            var issuer = issuers.findById(document.getIssuerId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Emisor inexistente para documento " + documentId));
            var documentItems = items.findAllByTenantIdAndDocumentIdOrderByLineNumber(
                    document.getTenantId(), documentId);
            var paymentInstallments = installments.findAllByTenantIdAndDocumentIdOrderByInstallmentNumber(
                    document.getTenantId(), documentId);
            var adjustments = allowanceCharges.findAllByTenantIdAndDocumentIdOrderByLineNumberAscSequenceNumberAsc(
                    document.getTenantId(), documentId);
            var globalAdjustments = documentAllowanceCharges.findAllByTenantIdAndDocumentIdOrderBySequenceNumberAsc(
                    document.getTenantId(), documentId);
            bundle = new DocumentBundle(
                    document,
                    issuer,
                    documentItems,
                    paymentInstallments,
                    adjustments,
                    globalAdjustments);

            if (document.getStatus() == DocumentStatus.SUBMISSION_UNKNOWN) {
                reconcileOnly(documentId, attempt, bundle);
                return;
            }

            businessValidator.validate(bundle);
            if (issuer.getSunatEnvironment() != SunatEnvironment.LOCAL) {
                sunatRules.validateForDirectSubmission(bundle);
            }

            byte[] xml = ublGenerator.generate(bundle);
            String baseName = issuer.getRuc()
                    + "-"
                    + document.getDocumentType().getSunatCode()
                    + "-"
                    + document.getFullNumber();
            String xmlPath = storage.store(document.getTenantId(), documentId, baseName + ".xml", xml);

            byte[] signedXml;
            if (issuer.getSunatEnvironment() == SunatEnvironment.LOCAL) {
                signedXml = xml;
            } else {
                Instant now = Instant.now();
                var certificate = certificates
                        .findFirstByTenantIdAndIssuerIdAndActiveTrueAndValidFromBeforeAndValidUntilAfterOrderByValidUntilDesc(
                                document.getTenantId(), issuer.getId(), now, now)
                        .orElseThrow(() -> new IllegalStateException(
                                "No existe certificado digital activo para el emisor"));
                signedXml = signer.sign(xml, certificate);
                signatureVerifier.requireValid(signedXml);
            }

            xsdValidator.requireForEnvironment(issuer.getSunatEnvironment());
            xsdValidator.validate(signedXml, document.getDocumentType());
            String signedPath = storage.store(
                    document.getTenantId(), documentId, baseName + "-signed.xml", signedXml);
            String xmlHash = hashing.sha256(signedXml);
            var publicLink = delivery.createProcessingAccessLink(document.getTenantId(), documentId);
            byte[] pdf = pdfGenerator.generate(bundle, signedXml, publicLink.url());
            String pdfPath = storage.store(document.getTenantId(), documentId, baseName + ".pdf", pdf);
            transitions.saveArtifacts(documentId, xmlPath, signedPath, pdfPath, xmlHash);

            if (requiresDailySummary(document)) {
                transitions.markSummaryReady(documentId, attempt);
                return;
            }

            transitions.markSubmitting(documentId);
            submissionStarted = true;
            SunatSubmissionResult result = sunat.submitBill(bundle, signedXml);
            transitions.commitSunatResult(documentId, attempt, result, storeCdr(bundle, result), false);
        } catch (SunatClientException ex) {
            if (ex.isRetryable()
                    && bundle != null
                    && bundle.issuer().getSunatEnvironment() == SunatEnvironment.PRODUCTION) {
                if (reconcileNow(documentId, attempt, bundle)) {
                    return;
                }
                transitions.markSubmissionUnknown(documentId, attempt, ex.getCode(), ex.getMessage());
                return;
            }
            transitions.retryOrFail(documentId, attempt, ex.getCode(), ex.getMessage(), ex.isRetryable());
        } catch (Exception ex) {
            if (submissionStarted
                    && bundle != null
                    && bundle.issuer().getSunatEnvironment() == SunatEnvironment.PRODUCTION) {
                if (reconcileNow(documentId, attempt, bundle)) {
                    return;
                }
                transitions.markSubmissionUnknown(documentId, attempt, "SUBMISSION_INTERRUPTED", safeMessage(ex));
            } else {
                transitions.retryOrFail(documentId, attempt, "PROCESSING_ERROR", safeMessage(ex), false);
            }
        }
    }

    private void reconcileOnly(
            UUID documentId,
            DocumentStateTransitionService.AttemptContext attempt,
            DocumentBundle bundle) {
        try {
            var result = sunat.recoverCdr(bundle);
            switch (result.status()) {
                case FOUND_ACCEPTED, FOUND_REJECTED, FOUND_VOIDED ->
                        transitions.commitSunatResult(
                                documentId,
                                attempt,
                                result.submission(),
                                storeCdr(bundle, result.submission()),
                                true);
                case CONFIRMED_NOT_FOUND ->
                        transitions.confirmedNotFoundForRetry(documentId, attempt, result.message());
                case UNKNOWN, TRANSIENT_ERROR ->
                        transitions.reconciliationNotFound(
                                documentId,
                                attempt,
                                result.message() == null
                                        ? "SUNAT aún no reporta un estado concluyente"
                                        : result.message());
            }
        } catch (Exception ex) {
            transitions.reconciliationNotFound(documentId, attempt, safeMessage(ex));
        }
    }

    private boolean reconcileNow(
            UUID documentId,
            DocumentStateTransitionService.AttemptContext attempt,
            DocumentBundle bundle) {
        try {
            var result = sunat.recoverCdr(bundle);
            if (result.status() == SunatReconciliationStatus.CONFIRMED_NOT_FOUND) {
                transitions.confirmedNotFoundForRetry(documentId, attempt, result.message());
                return true;
            }
            if (!result.hasSubmission()) {
                return false;
            }
            transitions.commitSunatResult(
                    documentId,
                    attempt,
                    result.submission(),
                    storeCdr(bundle, result.submission()),
                    true);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String storeCdr(DocumentBundle bundle, SunatSubmissionResult result) {
        if (result.cdrZip() == null) {
            return null;
        }
        String baseName = bundle.document().getDocumentType().getSunatCode()
                + "-"
                + bundle.document().getFullNumber();
        return storage.store(
                bundle.document().getTenantId(),
                bundle.document().getId(),
                "R-" + bundle.issuer().getRuc() + "-" + baseName + ".zip",
                result.cdrZip());
    }

    private boolean requiresDailySummary(ElectronicDocumentEntity document) {
        if (document.getDocumentType() == DocumentType.RECEIPT) {
            return true;
        }
        return (document.getDocumentType() == DocumentType.CREDIT_NOTE
                        || document.getDocumentType() == DocumentType.DEBIT_NOTE)
                && "03".equals(document.getReferenceDocumentType());
    }

    private String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 1900));
    }
}
