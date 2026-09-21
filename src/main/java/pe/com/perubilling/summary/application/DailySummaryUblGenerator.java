package pe.com.perubilling.summary.application;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.summary.domain.DailySummaryEntity;

@Component
public class DailySummaryUblGenerator {
    private static final String ROOT = "urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String SAC = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";

    public byte[] generate(DailySummaryEntity summary,
                           IssuerEntity issuer,
                           List<ElectronicDocumentEntity> documents,
                           LocalDate issueDate) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            Element root = document.createElementNS(ROOT, "SummaryDocuments");
            root.setAttribute("xmlns:cac", CAC);
            root.setAttribute("xmlns:cbc", CBC);
            root.setAttribute("xmlns:ext", EXT);
            root.setAttribute("xmlns:sac", SAC);
            document.appendChild(root);

            Element extensions = el(document, root, EXT, "ext:UBLExtensions", null);
            Element extension = el(document, extensions, EXT, "ext:UBLExtension", null);
            el(document, extension, EXT, "ext:ExtensionContent", null);

            cbc(document, root, "UBLVersionID", "2.0");
            cbc(document, root, "CustomizationID", "1.1");
            cbc(document, root, "ID", summary.getIdentifier());
            cbc(document, root, "ReferenceDate", summary.getReferenceDate().toString());
            cbc(document, root, "IssueDate", issueDate.toString());
            cbc(document, root, "Note", "CONSOLIDADO DE BOLETAS DE VENTA");

            signature(document, root, issuer);

            Element supplier = cac(document, root, "AccountingSupplierParty");
            cbc(document, supplier, "CustomerAssignedAccountID", issuer.getRuc());
            cbc(document, supplier, "AdditionalAccountID", "6");
            Element party = cac(document, supplier, "Party");
            Element legal = cac(document, party, "PartyLegalEntity");
            cbc(document, legal, "RegistrationName", issuer.getBusinessName());

            int lineNumber = 1;
            for (ElectronicDocumentEntity electronicDocument : documents) {
                line(document, root, lineNumber++, electronicDocument);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(out));
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el Resumen Diario UBL", ex);
        }
    }

    private void signature(Document document, Element root, IssuerEntity issuer) {
        Element signature = cac(document, root, "Signature");
        cbc(document, signature, "ID", "IDSignSP");
        Element party = cac(document, signature, "SignatoryParty");
        Element identification = cac(document, party, "PartyIdentification");
        cbc(document, identification, "ID", issuer.getRuc());
        Element partyName = cac(document, party, "PartyName");
        cbc(document, partyName, "Name", issuer.getBusinessName());
        Element attachment = cac(document, signature, "DigitalSignatureAttachment");
        Element reference = cac(document, attachment, "ExternalReference");
        cbc(document, reference, "URI", "#SignatureSP");
    }

    private void line(Document document,
                      Element root,
                      int number,
                      ElectronicDocumentEntity electronicDocument) {
        Element line = el(document, root, SAC, "sac:SummaryDocumentsLine", null);
        cbc(document, line, "LineID", String.valueOf(number));
        cbc(document, line, "DocumentTypeCode", electronicDocument.getDocumentType().getSunatCode());
        cbc(document, line, "ID", electronicDocument.getFullNumber());

        Element customer = cac(document, line, "AccountingCustomerParty");
        boolean anonymous = isAnonymousCustomer(electronicDocument);
        cbc(document, customer, "CustomerAssignedAccountID",
                anonymous ? "-" : electronicDocument.getCustomerDocumentNumber());
        cbc(document, customer, "AdditionalAccountID",
                anonymous ? "-" : electronicDocument.getCustomerDocumentType());

        if (electronicDocument.getReferenceDocumentNumber() != null) {
            Element billingReference = cac(document, line, "BillingReference");
            Element invoiceReference = cac(document, billingReference, "InvoiceDocumentReference");
            cbc(document, invoiceReference, "ID", electronicDocument.getReferenceDocumentNumber());
            cbc(document, invoiceReference, "DocumentTypeCode", electronicDocument.getReferenceDocumentType());
        }

        Element status = el(document, line, SAC, "sac:Status", null);
        cbc(document, status, "ConditionCode", electronicDocument.getSummaryConditionCode());

        amount(document, line, "TotalAmount",
                electronicDocument.getTotalAmount(), electronicDocument.getCurrency());

        // SUNAT Resumen Diario UBL 2.0 exige 01/02/03 incluso si el importe es 0.00.
        payMandatory(document, line,
                electronicDocument.getTaxableAmount().add(electronicDocument.getIvapTaxableAmount()),
                "01", electronicDocument.getCurrency());
        payMandatory(document, line, electronicDocument.getExoneratedAmount(),
                "02", electronicDocument.getCurrency());
        payMandatory(document, line, electronicDocument.getUnaffectedAmount(),
                "03", electronicDocument.getCurrency());
        payConditional(document, line, electronicDocument.getExportAmount(),
                "04", electronicDocument.getCurrency());
        payConditional(document, line, electronicDocument.getFreeAmount(),
                "05", electronicDocument.getCurrency());

        // El IGV referencial de operaciones gratuitas no es impuesto pagable del Resumen.
        BigDecimal payableTax = safe(electronicDocument.getIgvAmount())
                .add(safe(electronicDocument.getIvapAmount()))
                .add(safe(electronicDocument.getIcbperAmount()));
        Element taxTotal = cac(document, line, "TaxTotal");
        amount(document, taxTotal, "TaxAmount", payableTax, electronicDocument.getCurrency());

        // Si hay gratuidad gravada, SUNAT representa IGV 1000 con importe 0.00.
        if (safe(electronicDocument.getIgvAmount()).signum() != 0
                || safe(electronicDocument.getFreeTaxAmount()).signum() != 0) {
            taxSubtotal(document, taxTotal, safe(electronicDocument.getIgvAmount()),
                    "1000", "IGV", "VAT", electronicDocument.getCurrency());
        }
        if (safe(electronicDocument.getIvapAmount()).signum() != 0) {
            taxSubtotal(document, taxTotal, electronicDocument.getIvapAmount(),
                    "1016", "IVAP", "VAT", electronicDocument.getCurrency());
        }
        if (safe(electronicDocument.getIcbperAmount()).signum() != 0) {
            taxSubtotal(document, taxTotal, electronicDocument.getIcbperAmount(),
                    "7152", "ICBPER", "OTH", electronicDocument.getCurrency());
        }
    }

    private boolean isAnonymousCustomer(ElectronicDocumentEntity document) {
        return "-".equals(document.getCustomerDocumentNumber())
                && ("0".equals(document.getCustomerDocumentType())
                || "-".equals(document.getCustomerDocumentType()));
    }

    private void payMandatory(Document document,
                              Element parent,
                              BigDecimal value,
                              String code,
                              String currency) {
        billingPayment(document, parent, safe(value), code, currency);
    }

    private void payConditional(Document document,
                                Element parent,
                                BigDecimal value,
                                String code,
                                String currency) {
        if (value == null || value.signum() == 0) {
            return;
        }
        billingPayment(document, parent, value, code, currency);
    }

    private void billingPayment(Document document,
                                Element parent,
                                BigDecimal value,
                                String code,
                                String currency) {
        Element billingPayment = cac(document, parent, "BillingPayment");
        amount(document, billingPayment, "PaidAmount", value, currency);
        cbc(document, billingPayment, "InstructionID", code);
    }

    private void taxSubtotal(Document document,
                             Element taxTotal,
                             BigDecimal value,
                             String id,
                             String name,
                             String type,
                             String currency) {
        Element subtotal = cac(document, taxTotal, "TaxSubtotal");
        amount(document, subtotal, "TaxAmount", value, currency);
        Element category = cac(document, subtotal, "TaxCategory");
        Element scheme = cac(document, category, "TaxScheme");
        cbc(document, scheme, "ID", id);
        cbc(document, scheme, "Name", name);
        cbc(document, scheme, "TaxTypeCode", type);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void amount(Document document,
                        Element parent,
                        String name,
                        BigDecimal value,
                        String currency) {
        Element element = el(document, parent, CBC, "cbc:" + name,
                safe(value).setScale(2, RoundingMode.HALF_UP).toPlainString());
        element.setAttribute("currencyID", currency);
    }

    private Element cbc(Document document, Element parent, String name, String value) {
        return el(document, parent, CBC, "cbc:" + name, value);
    }

    private Element cac(Document document, Element parent, String name) {
        return el(document, parent, CAC, "cac:" + name, null);
    }

    private Element el(Document document,
                       Element parent,
                       String namespace,
                       String qualifiedName,
                       String value) {
        Element element = document.createElementNS(namespace, qualifiedName);
        if (value != null) {
            element.setTextContent(value);
        }
        parent.appendChild(element);
        return element;
    }
}
