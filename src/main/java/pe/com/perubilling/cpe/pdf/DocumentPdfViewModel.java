package pe.com.perubilling.cpe.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import pe.com.perubilling.billing.domain.PaymentMethod;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.shared.domain.DocumentType;

public record DocumentPdfViewModel(
        String documentTitle,
        String documentSunatCode,
        String number,
        String operationLabel,
        String issueDate,
        String issueTime,
        String issueYear,
        String currency,
        String currencyLabel,
        String currencySymbol,
        Issuer issuer,
        Customer customer,
        Reference reference,
        List<Item> items,
        List<TotalLine> totals,
        String grandTotal,
        String amountInWords,
        Payment payment,
        String qrDataUri,
        String digestValue,
        String publicAccessUrl
) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static DocumentPdfViewModel from(
            DocumentBundle bundle,
            String amountInWords,
            String qrDataUri,
            String digestValue,
            String publicAccessUrl) {
        var d = bundle.document();
        var issuer = bundle.issuer();

        List<Item> itemRows = bundle.items().stream()
                .map(item -> new Item(
                        item.getLineNumber(),
                        text(item.getSku()),
                        text(item.getSunatProductCode()),
                        text(item.getDescription()),
                        text(item.getUnitCode()),
                        quantity(item.getQuantity()),
                        money(item.getUnitPrice() == null ? item.getUnitValue() : item.getUnitPrice()),
                        money(item.getLineAllowanceAmount()),
                        money(item.getLineTotalAmount())))
                .toList();

        List<TotalLine> totalLines = new ArrayList<>();
        addPositive(totalLines, "Op. gravadas IGV", d.getTaxableAmount(), false);
        addPositive(totalLines, "Op. gravadas IVAP", d.getIvapTaxableAmount(), false);
        addPositive(totalLines, "Op. exoneradas", d.getExoneratedAmount(), false);
        addPositive(totalLines, "Op. inafectas", d.getUnaffectedAmount(), false);
        addPositive(totalLines, "Op. exportación", d.getExportAmount(), false);
        addPositive(totalLines, "Op. gratuitas", d.getFreeAmount(), false);
        addPositive(totalLines, "Descuentos", d.getAllowanceTotalAmount(), true);
        addPositive(totalLines, "Otros cargos", d.getChargeTotalAmount(), false);
        addPositive(totalLines, "IGV", d.getIgvAmount(), false);
        addPositive(totalLines, "IVAP", d.getIvapAmount(), false);
        addPositive(totalLines, "ICBPER", d.getIcbperAmount(), false);

        Payment payment = null;
        if (d.getPaymentMethod() != null) {
            var installments = bundle.installments().stream()
                    .map(i -> new Installment(
                            "Cuota " + String.format(Locale.ROOT, "%03d", i.getInstallmentNumber()),
                            i.getDueDate() == null ? "" : DATE.format(i.getDueDate()),
                            money(i.getAmount())))
                    .toList();
            payment = new Payment(
                    d.getPaymentMethod() == PaymentMethod.CREDITO ? "CRÉDITO" : "CONTADO",
                    d.getPendingAmount() == null ? "" : money(d.getPendingAmount()),
                    installments);
        }

        Reference reference = null;
        if (notBlank(d.getReferenceDocumentNumber())) {
            reference = new Reference(
                    referenceTypeLabel(d.getReferenceDocumentType()),
                    text(d.getReferenceDocumentNumber()),
                    text(d.getReasonCode()),
                    text(d.getReasonText()));
        }

        String issuerLocation = joinNonBlank(" - ", issuer.getDistrict(), issuer.getProvince(), issuer.getDepartment());
        String issuerAddress = text(issuer.getAddress());
        if (notBlank(issuerLocation)) {
            issuerAddress = issuerAddress + (issuerAddress.isBlank() ? "" : " · ") + issuerLocation;
        }

        return new DocumentPdfViewModel(
                documentTitle(d.getDocumentType()),
                d.getDocumentType().getSunatCode(),
                text(d.getFullNumber()),
                operationLabel(d.getOperationType()),
                d.getIssueDate() == null ? "" : DATE.format(d.getIssueDate()),
                d.getIssueTime() == null ? "" : TIME.format(d.getIssueTime()),
                d.getIssueDate() == null ? "" : String.valueOf(d.getIssueDate().getYear()),
                text(d.getCurrency()),
                currencyLabel(d.getCurrency()),
                currencySymbol(d.getCurrency()),
                new Issuer(
                        text(issuer.getBusinessName()),
                        text(issuer.getTradeName()),
                        text(issuer.getRuc()),
                        issuerAddress,
                        text(issuer.getUbigeo()),
                        text(issuer.getEstablishmentCode())),
                new Customer(
                        documentIdentityLabel(d.getCustomerDocumentType()),
                        anonymousCustomer(d.getCustomerDocumentType(), d.getCustomerDocumentNumber()) ? "-" : text(d.getCustomerDocumentNumber()),
                        text(d.getCustomerName()),
                        text(d.getCustomerAddress()),
                        text(d.getCustomerEmail()),
                        text(d.getCustomerCountryCode())),
                reference,
                itemRows,
                List.copyOf(totalLines),
                money(d.getTotalAmount()),
                text(amountInWords),
                payment,
                qrDataUri,
                text(digestValue),
                text(publicAccessUrl));
    }

    private static void addPositive(List<TotalLine> lines, String label, BigDecimal value, boolean negative) {
        if (value != null && value.signum() > 0) {
            lines.add(new TotalLine(label, (negative ? "-" : "") + money(value)));
        }
    }

    private static String documentTitle(DocumentType type) {
        return switch (type) {
            case INVOICE -> "FACTURA ELECTRÓNICA";
            case RECEIPT -> "BOLETA DE VENTA ELECTRÓNICA";
            case CREDIT_NOTE -> "NOTA DE CRÉDITO ELECTRÓNICA";
            case DEBIT_NOTE -> "NOTA DE DÉBITO ELECTRÓNICA";
        };
    }

    private static String operationLabel(String code) {
        if (code == null) return "";
        return switch (code) {
            case "0101" -> "Venta interna";
            case "0102" -> "Exportación";
            case "0107" -> "Venta gravada con IVAP";
            default -> "Operación " + code;
        };
    }

    private static String documentIdentityLabel(String code) {
        if (code == null) return "Documento";
        return switch (code) {
            case "0" -> "Sin documento";
            case "1" -> "DNI";
            case "4" -> "Carné de extranjería";
            case "6" -> "RUC";
            case "7" -> "Pasaporte";
            default -> "Documento " + code;
        };
    }

    private static String referenceTypeLabel(String code) {
        if (code == null) return "Documento de referencia";
        return switch (code) {
            case "01" -> "Factura";
            case "03" -> "Boleta de venta";
            case "07" -> "Nota de crédito";
            case "08" -> "Nota de débito";
            default -> "Documento " + code;
        };
    }

    private static String currencyLabel(String code) {
        if (code == null) return "";
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "PEN" -> "Soles";
            case "USD" -> "Dólares estadounidenses";
            case "EUR" -> "Euros";
            default -> code.toUpperCase(Locale.ROOT);
        };
    }

    private static String currencySymbol(String code) {
        if (code == null) return "";
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "PEN" -> "S/";
            case "USD" -> "US$";
            case "EUR" -> "€";
            default -> code.toUpperCase(Locale.ROOT);
        };
    }

    private static boolean anonymousCustomer(String type, String number) {
        return "0".equals(type) && "-".equals(number);
    }

    private static String quantity(BigDecimal value) {
        if (value == null) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (notBlank(value)) parts.add(value.trim());
        }
        return String.join(separator, parts);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record Issuer(
            String businessName,
            String tradeName,
            String ruc,
            String address,
            String ubigeo,
            String establishmentCode) {}

    public record Customer(
            String documentType,
            String documentNumber,
            String name,
            String address,
            String email,
            String countryCode) {}

    public record Reference(String type, String number, String reasonCode, String reason) {}

    public record Item(
            int line,
            String sku,
            String sunatProductCode,
            String description,
            String unitCode,
            String quantity,
            String unitPrice,
            String discount,
            String total) {}

    public record TotalLine(String label, String amount) {}

    public record Payment(String method, String pendingAmount, List<Installment> installments) {}

    public record Installment(String number, String dueDate, String amount) {}
}
