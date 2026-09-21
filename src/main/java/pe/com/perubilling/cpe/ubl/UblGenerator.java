package pe.com.perubilling.cpe.ubl;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
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
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.billing.domain.AllowanceChargeEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.DocumentAllowanceChargeEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.billing.domain.TaxAffectation;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;

@Component
public class UblGenerator {
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String INVOICE_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CREDIT_NS = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    private static final String DEBIT_NS = "urn:oasis:names:specification:ubl:schema:xsd:DebitNote-2";
    private static final DateTimeFormatter SUNAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final AmountInWords amountInWords;

    public UblGenerator(AmountInWords amountInWords) { this.amountInWords = amountInWords; }

    public byte[] generate(DocumentBundle bundle) {
        try {
            DocumentType type = bundle.document().getDocumentType();
            String namespace = switch (type) {
                case INVOICE, RECEIPT -> INVOICE_NS;
                case CREDIT_NOTE -> CREDIT_NS;
                case DEBIT_NOTE -> DEBIT_NS;
            };
            String rootName = switch (type) {
                case INVOICE, RECEIPT -> "Invoice";
                case CREDIT_NOTE -> "CreditNote";
                case DEBIT_NOTE -> "DebitNote";
            };
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Document xml = factory.newDocumentBuilder().newDocument();
            Element root = xml.createElementNS(namespace, rootName);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", namespace);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:cac", CAC);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:cbc", CBC);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ext", EXT);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ds", DS);
            xml.appendChild(root);

            addExtensions(xml, root);
            addText(xml, root, CBC, "cbc:UBLVersionID", "2.1");
            addText(xml, root, CBC, "cbc:CustomizationID", "2.0");
            if (type == DocumentType.INVOICE || type == DocumentType.RECEIPT) {
                Element profile = addText(xml, root, CBC, "cbc:ProfileID", bundle.document().getOperationType());
                profile.setAttribute("schemeName", "SUNAT:Identificador de Tipo de Operación");
                profile.setAttribute("schemeAgencyName", "PE:SUNAT");
                profile.setAttribute("schemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo17");
            }
            addText(xml, root, CBC, "cbc:ID", bundle.document().getFullNumber());
            addText(xml, root, CBC, "cbc:IssueDate", bundle.document().getIssueDate().toString());
            if (bundle.document().getIssueTime() != null) {
                addText(xml, root, CBC, "cbc:IssueTime", SUNAT_TIME.format(bundle.document().getIssueTime()));
            }

            if (type == DocumentType.INVOICE || type == DocumentType.RECEIPT) {
                Element invoiceType = addText(xml, root, CBC, "cbc:InvoiceTypeCode", type.getSunatCode());
                invoiceType.setAttribute("listAgencyName", "PE:SUNAT");
                invoiceType.setAttribute("listName", "Tipo de Documento");
                invoiceType.setAttribute("listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");
            }
            Element note = addText(xml, root, CBC, "cbc:Note", amountInWords.convert(bundle.document().getTotalAmount(), bundle.document().getCurrency()));
            note.setAttribute("languageLocaleID", "1000");
            Element currency = addText(xml, root, CBC, "cbc:DocumentCurrencyCode", bundle.document().getCurrency());
            currency.setAttribute("listID", "ISO 4217 Alpha");
            currency.setAttribute("listName", "Currency");
            currency.setAttribute("listAgencyName", "United Nations Economic Commission for Europe");

            if (type == DocumentType.CREDIT_NOTE || type == DocumentType.DEBIT_NOTE) addReference(xml, root, bundle.document());
            addSignatureReference(xml, root, bundle.issuer());
            addSupplier(xml, root, bundle.issuer());
            addCustomer(xml, root, bundle.document());
            if (type == DocumentType.INVOICE) addPaymentTerms(xml, root, bundle);
            addDocumentAllowanceCharges(xml, root, bundle.document(), bundle.documentAllowanceCharges());
            addDocumentTaxes(xml, root, bundle.document());
            addLegalTotals(xml, root, bundle.document(), type);
            for (ElectronicDocumentItemEntity item : bundle.items()) {
                List<AllowanceChargeEntity> adjustments = bundle.allowanceCharges().stream()
                        .filter(adjustment -> adjustment.getDocumentItemId().equals(item.getId()))
                        .toList();
                addLine(xml, root, bundle.document(), item, type, adjustments);
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(xml), new StreamResult(out));
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el XML UBL", ex);
        }
    }

    private void addExtensions(Document d, Element root) {
        Element extensions = element(d, EXT, "ext:UBLExtensions");
        Element extension = element(d, EXT, "ext:UBLExtension");
        extension.appendChild(element(d, EXT, "ext:ExtensionContent"));
        extensions.appendChild(extension);
        root.appendChild(extensions);
    }

    private void addReference(Document d, Element root, ElectronicDocumentEntity doc) {
        Element discrepancy = element(d, CAC, "cac:DiscrepancyResponse");
        addText(d, discrepancy, CBC, "cbc:ReferenceID", doc.getReferenceDocumentNumber());
        addText(d, discrepancy, CBC, "cbc:ResponseCode", doc.getReasonCode());
        addText(d, discrepancy, CBC, "cbc:Description", doc.getReasonText());
        root.appendChild(discrepancy);
        Element billing = element(d, CAC, "cac:BillingReference");
        Element ref = element(d, CAC, "cac:InvoiceDocumentReference");
        addText(d, ref, CBC, "cbc:ID", doc.getReferenceDocumentNumber());
        addText(d, ref, CBC, "cbc:DocumentTypeCode", doc.getReferenceDocumentType());
        billing.appendChild(ref);
        root.appendChild(billing);
    }

    private void addSignatureReference(Document d, Element root, IssuerEntity issuer) {
        Element signature = element(d, CAC, "cac:Signature");
        addText(d, signature, CBC, "cbc:ID", "SignatureSP");
        Element signatory = element(d, CAC, "cac:SignatoryParty");
        Element id = element(d, CAC, "cac:PartyIdentification");
        addText(d, id, CBC, "cbc:ID", issuer.getRuc());
        signatory.appendChild(id);
        Element name = element(d, CAC, "cac:PartyName");
        addText(d, name, CBC, "cbc:Name", issuer.getBusinessName());
        signatory.appendChild(name);
        signature.appendChild(signatory);
        Element attach = element(d, CAC, "cac:DigitalSignatureAttachment");
        Element external = element(d, CAC, "cac:ExternalReference");
        addText(d, external, CBC, "cbc:URI", "#SignatureSP");
        attach.appendChild(external);
        signature.appendChild(attach);
        root.appendChild(signature);
    }

    private void addSupplier(Document d, Element root, IssuerEntity issuer) {
        Element supplier = element(d, CAC, "cac:AccountingSupplierParty");
        Element party = element(d, CAC, "cac:Party");

        if (issuer.getTradeName() != null && !issuer.getTradeName().isBlank()) {
            Element partyName = element(d, CAC, "cac:PartyName");
            addText(d, partyName, CBC, "cbc:Name", issuer.getTradeName());
            party.appendChild(partyName);
        }

        Element partyTaxScheme = element(d, CAC, "cac:PartyTaxScheme");
        addText(d, partyTaxScheme, CBC, "cbc:RegistrationName", issuer.getBusinessName());
        Element companyId = addText(d, partyTaxScheme, CBC, "cbc:CompanyID", issuer.getRuc());
        addSunatIdentityAttributes(companyId, "6");

        Element address = element(d, CAC, "cac:RegistrationAddress");
        Element ubigeo = addText(d, address, CBC, "cbc:ID", issuer.getUbigeo());
        ubigeo.setAttribute("schemeName", "Ubigeos");
        ubigeo.setAttribute("schemeAgencyName", "PE:INEI");
        addText(d, address, CBC, "cbc:AddressTypeCode", issuer.getEstablishmentCode());
        addText(d, address, CBC, "cbc:CityName", value(issuer.getProvince()));
        addText(d, address, CBC, "cbc:CountrySubentity", value(issuer.getDepartment()));
        addText(d, address, CBC, "cbc:District", value(issuer.getDistrict()));
        Element line = element(d, CAC, "cac:AddressLine");
        addText(d, line, CBC, "cbc:Line", issuer.getAddress());
        address.appendChild(line);
        Element country = element(d, CAC, "cac:Country");
        Element countryCode = addText(d, country, CBC, "cbc:IdentificationCode", "PE");
        countryCode.setAttribute("listID", "ISO 3166-1");
        countryCode.setAttribute("listAgencyName", "United Nations Economic Commission for Europe");
        address.appendChild(country);
        partyTaxScheme.appendChild(address);

        Element taxScheme = element(d, CAC, "cac:TaxScheme");
        addText(d, taxScheme, CBC, "cbc:ID", "-");
        partyTaxScheme.appendChild(taxScheme);
        party.appendChild(partyTaxScheme);
        supplier.appendChild(party);
        root.appendChild(supplier);
    }

    private void addCustomer(Document d, Element root, ElectronicDocumentEntity doc) {
        Element customer = element(d, CAC, "cac:AccountingCustomerParty");
        Element party = element(d, CAC, "cac:Party");
        Element partyTaxScheme = element(d, CAC, "cac:PartyTaxScheme");
        addText(d, partyTaxScheme, CBC, "cbc:RegistrationName", doc.getCustomerName());

        // Para boleta de consumidor final sin identificación no se debe convertir el
        // marcador interno 0/- en un documento tributario ficticio dentro del UBL.
        if (!isAnonymousCustomer(doc)) {
            Element companyId = addText(d, partyTaxScheme, CBC, "cbc:CompanyID", doc.getCustomerDocumentNumber());
            addSunatIdentityAttributes(companyId, doc.getCustomerDocumentType());
        }

        boolean hasAddress = doc.getCustomerAddress() != null && !doc.getCustomerAddress().isBlank();
        boolean hasCountry = doc.getCustomerCountryCode() != null && !doc.getCustomerCountryCode().isBlank();
        if (hasAddress || hasCountry) {
            Element addr = element(d, CAC, "cac:RegistrationAddress");
            if (hasAddress) {
                Element line = element(d, CAC, "cac:AddressLine");
                addText(d, line, CBC, "cbc:Line", doc.getCustomerAddress());
                addr.appendChild(line);
            }
            if (hasCountry) {
                Element country = element(d, CAC, "cac:Country");
                Element countryCode = addText(d, country, CBC, "cbc:IdentificationCode",
                        doc.getCustomerCountryCode());
                countryCode.setAttribute("listID", "ISO 3166-1");
                countryCode.setAttribute("listAgencyName", "United Nations Economic Commission for Europe");
                addr.appendChild(country);
            }
            partyTaxScheme.appendChild(addr);
        }

        Element taxScheme = element(d, CAC, "cac:TaxScheme");
        addText(d, taxScheme, CBC, "cbc:ID", "-");
        partyTaxScheme.appendChild(taxScheme);
        party.appendChild(partyTaxScheme);
        customer.appendChild(party);
        root.appendChild(customer);
    }

    private boolean isAnonymousCustomer(ElectronicDocumentEntity doc) {
        return doc.getDocumentType() == DocumentType.RECEIPT
                && "0".equals(doc.getCustomerDocumentType())
                && "-".equals(doc.getCustomerDocumentNumber());
    }

    private void addSunatIdentityAttributes(Element companyId, String schemeId) {
        companyId.setAttribute("schemeID", schemeId);
        companyId.setAttribute("schemeName", "SUNAT:Identificador de Documento de Identidad");
        companyId.setAttribute("schemeAgencyName", "PE:SUNAT");
        companyId.setAttribute("schemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06");
    }

    private void addPaymentTerms(Document d, Element root, DocumentBundle bundle) {
        var doc = bundle.document();
        if (doc.getPaymentMethod() == null) return;

        Element terms = element(d, CAC, "cac:PaymentTerms");
        addText(d, terms, CBC, "cbc:ID", "FormaPago");
        addText(d, terms, CBC, "cbc:PaymentMeansID", doc.getPaymentMethod().sunatValue());
        if (doc.getPaymentMethod() == pe.com.perubilling.billing.domain.PaymentMethod.CREDITO) {
            Element amount = addText(d, terms, CBC, "cbc:Amount", money(doc.getPendingAmount()));
            amount.setAttribute("currencyID", doc.getCurrency());
        }
        root.appendChild(terms);

        if (doc.getPaymentMethod() == pe.com.perubilling.billing.domain.PaymentMethod.CREDITO) {
            for (var installment : bundle.installments()) {
                Element cuota = element(d, CAC, "cac:PaymentTerms");
                addText(d, cuota, CBC, "cbc:ID", "FormaPago");
                addText(d, cuota, CBC, "cbc:PaymentMeansID",
                        "Cuota" + String.format("%03d", installment.getInstallmentNumber()));
                Element amount = addText(d, cuota, CBC, "cbc:Amount", money(installment.getAmount()));
                amount.setAttribute("currencyID", doc.getCurrency());
                addText(d, cuota, CBC, "cbc:PaymentDueDate", installment.getDueDate().toString());
                root.appendChild(cuota);
            }
        }
    }

    private void addDocumentTaxes(Document d, Element root, ElectronicDocumentEntity doc) {
        Element total = element(d, CAC, "cac:TaxTotal");
        // El impuesto teórico de las operaciones gratuitas se informa en el subtotal 9996,
        // pero no forma parte de la sumatoria de impuestos pagables del comprobante.
        BigDecimal allTax = doc.getIgvAmount()
                .add(doc.getIvapAmount())
                .add(doc.getIcbperAmount());
        Element taxAmount = addText(d, total, CBC, "cbc:TaxAmount", money(allTax));
        taxAmount.setAttribute("currencyID", doc.getCurrency());

        if (doc.getTaxableAmount().signum() > 0)
            addTaxSubtotal(d, total, doc.getTaxableAmount(), doc.getIgvAmount(), "1000", "IGV", "VAT", doc.getCurrency());
        if (doc.getIvapTaxableAmount().signum() > 0)
            addTaxSubtotal(d, total, doc.getIvapTaxableAmount(), doc.getIvapAmount(), "1016", "IVAP", "VAT", doc.getCurrency());
        if (doc.getExoneratedAmount().signum() > 0)
            addTaxSubtotal(d, total, doc.getExoneratedAmount(), BigDecimal.ZERO, "9997", "EXO", "VAT", doc.getCurrency());
        if (doc.getUnaffectedAmount().signum() > 0)
            addTaxSubtotal(d, total, doc.getUnaffectedAmount(), BigDecimal.ZERO, "9998", "INA", "FRE", doc.getCurrency());
        if (doc.getExportAmount().signum() > 0)
            addTaxSubtotal(d, total, doc.getExportAmount(), BigDecimal.ZERO, "9995", "EXP", "FRE", doc.getCurrency());
        if (doc.getFreeAmount().signum() > 0)
            addTaxSubtotal(d, total, doc.getFreeAmount(), doc.getFreeTaxAmount(), "9996", "GRA", "FRE", doc.getCurrency());
        if (doc.getIcbperAmount().signum() > 0)
            addTaxSubtotal(d, total, BigDecimal.ZERO, doc.getIcbperAmount(), "7152", "ICBPER", "OTH", doc.getCurrency());
        root.appendChild(total);
    }

    private void addTaxSubtotal(Document d, Element taxTotal, BigDecimal base, BigDecimal tax, String idValue, String name, String type, String currency) {
        Element subtotal = element(d, CAC, "cac:TaxSubtotal");
        if (base.signum() > 0) {
            Element taxable = addText(d, subtotal, CBC, "cbc:TaxableAmount", money(base));
            taxable.setAttribute("currencyID", currency);
        }
        Element amount = addText(d, subtotal, CBC, "cbc:TaxAmount", money(tax));
        amount.setAttribute("currencyID", currency);
        Element category = element(d, CAC, "cac:TaxCategory");
        addTaxCategoryId(d, category, categoryForTaxScheme(idValue));
        Element scheme = element(d, CAC, "cac:TaxScheme");
        addTaxScheme(d, scheme, idValue, name, type);
        category.appendChild(scheme);
        subtotal.appendChild(category);
        taxTotal.appendChild(subtotal);
    }

    private String categoryForTaxScheme(String taxSchemeId) {
        return switch (taxSchemeId) {
            case "1000", "1016", "7152" -> "S";
            case "9997" -> "E";
            case "9998" -> "O";
            case "9995" -> "G";
            case "9996" -> "Z";
            default -> "S";
        };
    }

    private void addTaxCategoryId(Document d, Element category, String value) {
        Element id = addText(d, category, CBC, "cbc:ID", value);
        id.setAttribute("schemeID", "UN/ECE 5305");
        id.setAttribute("schemeName", "Tax Category Identifier");
        id.setAttribute("schemeAgencyName", "United Nations Economic Commission for Europe");
    }

    private void addTaxScheme(Document d, Element scheme, String idValue, String name, String type) {
        Element id = addText(d, scheme, CBC, "cbc:ID", idValue);
        id.setAttribute("schemeID", "UN/ECE 5153");
        id.setAttribute("schemeName", "Tax Scheme Identifier");
        id.setAttribute("schemeAgencyName", "United Nations Economic Commission for Europe");
        addText(d, scheme, CBC, "cbc:Name", name);
        addText(d, scheme, CBC, "cbc:TaxTypeCode", type);
    }

    private void addDocumentAllowanceCharges(
            Document d,
            Element root,
            ElectronicDocumentEntity doc,
            List<DocumentAllowanceChargeEntity> adjustments) {
        for (DocumentAllowanceChargeEntity adjustment : adjustments) {
            Element allowanceCharge = element(d, CAC, "cac:AllowanceCharge");
            addText(d, allowanceCharge, CBC, "cbc:ChargeIndicator", Boolean.toString(adjustment.isCharge()));
            Element reason = addText(d, allowanceCharge, CBC, "cbc:AllowanceChargeReasonCode", adjustment.getReasonCode());
            reason.setAttribute("listAgencyName", "PE:SUNAT");
            reason.setAttribute("listName", "Cargo/descuento");
            reason.setAttribute("listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53");
            addText(d, allowanceCharge, CBC, "cbc:MultiplierFactorNumeric",
                    adjustment.getFactor().stripTrailingZeros().toPlainString());
            Element amount = addText(d, allowanceCharge, CBC, "cbc:Amount", money(adjustment.getAmount()));
            amount.setAttribute("currencyID", doc.getCurrency());
            Element base = addText(d, allowanceCharge, CBC, "cbc:BaseAmount", money(adjustment.getBaseAmount()));
            base.setAttribute("currencyID", doc.getCurrency());
            root.appendChild(allowanceCharge);
        }
    }

    private void addLegalTotals(Document d, Element root, ElectronicDocumentEntity doc, DocumentType type) {
        String node = type == DocumentType.CREDIT_NOTE ? "cac:LegalMonetaryTotal"
                : type == DocumentType.DEBIT_NOTE ? "cac:RequestedMonetaryTotal" : "cac:LegalMonetaryTotal";
        Element total = element(d, CAC, node);
        BigDecimal lineBase = doc.getTaxableAmount()
                .add(doc.getIvapTaxableAmount())
                .add(doc.getExoneratedAmount())
                .add(doc.getUnaffectedAmount())
                .add(doc.getExportAmount());
        Element line = addText(d, total, CBC, "cbc:LineExtensionAmount", money(lineBase));
        line.setAttribute("currencyID", doc.getCurrency());
        BigDecimal chargeTotal = doc.getChargeTotalAmount() == null ? BigDecimal.ZERO : doc.getChargeTotalAmount();
        BigDecimal taxInclusiveAmount = lineBase
                .add(doc.getIgvAmount())
                .add(doc.getIvapAmount())
                .add(doc.getIcbperAmount());
        Element inclusive = addText(d, total, CBC, "cbc:TaxInclusiveAmount", money(taxInclusiveAmount));
        inclusive.setAttribute("currencyID", doc.getCurrency());
        BigDecimal allowanceTotal = doc.getAllowanceTotalAmount() == null ? BigDecimal.ZERO : doc.getAllowanceTotalAmount();
        if (allowanceTotal.signum() > 0) {
            Element allowance = addText(d, total, CBC, "cbc:AllowanceTotalAmount", money(allowanceTotal));
            allowance.setAttribute("currencyID", doc.getCurrency());
        }
        if (chargeTotal.signum() > 0) {
            Element charge = addText(d, total, CBC, "cbc:ChargeTotalAmount", money(chargeTotal));
            charge.setAttribute("currencyID", doc.getCurrency());
        }
        Element payable = addText(d, total, CBC, "cbc:PayableAmount", money(doc.getTotalAmount()));
        payable.setAttribute("currencyID", doc.getCurrency());
        root.appendChild(total);
    }

    private void addLine(Document d, Element root, ElectronicDocumentEntity doc, ElectronicDocumentItemEntity item, DocumentType type, List<AllowanceChargeEntity> adjustments) {
        String lineNode = switch (type) {
            case INVOICE, RECEIPT -> "cac:InvoiceLine";
            case CREDIT_NOTE -> "cac:CreditNoteLine";
            case DEBIT_NOTE -> "cac:DebitNoteLine";
        };
        String qtyNode = switch (type) {
            case INVOICE, RECEIPT -> "cbc:InvoicedQuantity";
            case CREDIT_NOTE -> "cbc:CreditedQuantity";
            case DEBIT_NOTE -> "cbc:DebitedQuantity";
        };

        Element line = element(d, CAC, lineNode);
        addText(d, line, CBC, "cbc:ID", Integer.toString(item.getLineNumber()));
        Element qty = addText(d, line, CBC, qtyNode, item.getQuantity().stripTrailingZeros().toPlainString());
        qty.setAttribute("unitCode", item.getUnitCode());
        qty.setAttribute("unitCodeListID", "UN/ECE rec 20");
        qty.setAttribute("unitCodeListAgencyName", "United Nations Economic Commission for Europe");

        Element extension = addText(d, line, CBC, "cbc:LineExtensionAmount",
                item.isFreeOperation() ? "0.00" : money(item.getLineBaseAmount()));
        extension.setAttribute("currencyID", doc.getCurrency());

        Element pricing = element(d, CAC, "cac:PricingReference");
        Element alt = element(d, CAC, "cac:AlternativeConditionPrice");
        Element altPrice = addText(d, alt, CBC, "cbc:PriceAmount",
                item.isFreeOperation()
                        ? item.getUnitValue().setScale(6, RoundingMode.HALF_UP).toPlainString()
                        : item.getUnitPrice().setScale(6, RoundingMode.HALF_UP).toPlainString());
        altPrice.setAttribute("currencyID", doc.getCurrency());
        Element priceType = addText(d, alt, CBC, "cbc:PriceTypeCode", item.isFreeOperation() ? "02" : "01");
        priceType.setAttribute("listName", "SUNAT:Indicador de Tipo de Precio");
        priceType.setAttribute("listAgencyName", "PE:SUNAT");
        priceType.setAttribute("listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16");
        pricing.appendChild(alt);
        line.appendChild(pricing);

        for (AllowanceChargeEntity adjustment : adjustments) {
            Element allowanceCharge = element(d, CAC, "cac:AllowanceCharge");
            addText(d, allowanceCharge, CBC, "cbc:ChargeIndicator", Boolean.toString(adjustment.isCharge()));
            Element reason = addText(d, allowanceCharge, CBC, "cbc:AllowanceChargeReasonCode", adjustment.getReasonCode());
            reason.setAttribute("listAgencyName", "PE:SUNAT");
            reason.setAttribute("listName", "SUNAT:Codigo de cargos/descuentos");
            reason.setAttribute("listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53");
            addText(d, allowanceCharge, CBC, "cbc:MultiplierFactorNumeric",
                    adjustment.getFactor().stripTrailingZeros().toPlainString());
            Element amount = addText(d, allowanceCharge, CBC, "cbc:Amount", money(adjustment.getAmount()));
            amount.setAttribute("currencyID", doc.getCurrency());
            Element base = addText(d, allowanceCharge, CBC, "cbc:BaseAmount", money(adjustment.getBaseAmount()));
            base.setAttribute("currencyID", doc.getCurrency());
            line.appendChild(allowanceCharge);
        }

        Element taxTotal = element(d, CAC, "cac:TaxTotal");
        Element taxAmount = addText(d, taxTotal, CBC, "cbc:TaxAmount",
                money(item.getLineIgvAmount().add(item.getLineIcbperAmount())));
        taxAmount.setAttribute("currencyID", doc.getCurrency());

        Element subtotal = element(d, CAC, "cac:TaxSubtotal");
        Element taxable = addText(d, subtotal, CBC, "cbc:TaxableAmount", money(item.getLineBaseAmount()));
        taxable.setAttribute("currencyID", doc.getCurrency());
        Element lineTax = addText(d, subtotal, CBC, "cbc:TaxAmount", money(item.getLineIgvAmount()));
        lineTax.setAttribute("currencyID", doc.getCurrency());

        Element category = element(d, CAC, "cac:TaxCategory");
        TaxAffectation affectation = TaxAffectation.fromCode(item.getTaxAffectationCode());
        addTaxCategoryId(d, category, affectation.taxCategoryId());
        addText(d, category, CBC, "cbc:Percent", item.getIgvRate().stripTrailingZeros().toPlainString());
        Element exemptionReason = addText(
                d, category, CBC, "cbc:TaxExemptionReasonCode", item.getTaxAffectationCode());
        exemptionReason.setAttribute("listAgencyName", "PE:SUNAT");
        exemptionReason.setAttribute("listName", "SUNAT:Codigo de Tipo de Afectación del IGV");
        exemptionReason.setAttribute(
                "listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07");
        Element scheme = element(d, CAC, "cac:TaxScheme");
        addTaxScheme(d, scheme, affectation.taxSchemeId(), affectation.taxSchemeName(), affectation.taxTypeCode());
        category.appendChild(scheme);
        subtotal.appendChild(category);
        taxTotal.appendChild(subtotal);

        if (item.getLineIcbperAmount().signum() > 0) {
            Element icbSubtotal = element(d, CAC, "cac:TaxSubtotal");
            Element icbAmount = addText(d, icbSubtotal, CBC, "cbc:TaxAmount", money(item.getLineIcbperAmount()));
            icbAmount.setAttribute("currencyID", doc.getCurrency());
            Element icbCategory = element(d, CAC, "cac:TaxCategory");
            addTaxCategoryId(d, icbCategory, "S");
            Element perUnit = addText(d, icbCategory, CBC, "cbc:PerUnitAmount", item.getIcbperPerUnit().toPlainString());
            perUnit.setAttribute("currencyID", doc.getCurrency());
            Element icbScheme = element(d, CAC, "cac:TaxScheme");
            addTaxScheme(d, icbScheme, "7152", "ICBPER", "OTH");
            icbCategory.appendChild(icbScheme);
            icbSubtotal.appendChild(icbCategory);
            taxTotal.appendChild(icbSubtotal);
        }
        line.appendChild(taxTotal);

        Element itemNode = element(d, CAC, "cac:Item");
        addText(d, itemNode, CBC, "cbc:Description", item.getDescription());
        if (item.getSku() != null) {
            Element seller = element(d, CAC, "cac:SellersItemIdentification");
            addText(d, seller, CBC, "cbc:ID", item.getSku());
            itemNode.appendChild(seller);
        }
        if (item.getSunatProductCode() != null) {
            Element classification = element(d, CAC, "cac:CommodityClassification");
            Element code = addText(d, classification, CBC, "cbc:ItemClassificationCode", item.getSunatProductCode());
            code.setAttribute("listID", "UNSPSC");
            code.setAttribute("listAgencyName", "GS1 US");
            code.setAttribute("listName", "Item Classification");
            itemNode.appendChild(classification);
        }
        if (item.getGtin() != null) {
            Element standard = element(d, CAC, "cac:StandardItemIdentification");
            Element id = addText(d, standard, CBC, "cbc:ID", item.getGtin());
            id.setAttribute("schemeID", item.getGtinSchemeId());
            itemNode.appendChild(standard);
        }
        line.appendChild(itemNode);

        Element price = element(d, CAC, "cac:Price");
        Element priceAmount = addText(d, price, CBC, "cbc:PriceAmount",
                item.isFreeOperation()
                        ? "0.000000"
                        : item.getUnitValue().setScale(6, RoundingMode.HALF_UP).toPlainString());
        priceAmount.setAttribute("currencyID", doc.getCurrency());
        line.appendChild(price);
        root.appendChild(line);
    }

    private Element element(Document d, String ns, String qualifiedName) { return d.createElementNS(ns, qualifiedName); }
    private Element addText(Document d, Element parent, String ns, String qualifiedName, String text) {
        Element e = element(d, ns, qualifiedName); e.setTextContent(text == null ? "" : text); parent.appendChild(e); return e;
    }
    private String money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private String value(String value) { return value == null ? "" : value; }
}
