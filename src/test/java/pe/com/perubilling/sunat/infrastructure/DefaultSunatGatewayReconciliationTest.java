package pe.com.perubilling.sunat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.shared.crypto.SecretCryptoService;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.sunat.domain.SunatReconciliationStatus;

class DefaultSunatGatewayReconciliationTest {

    @Test
    void status0011IsConfirmedNotFoundAndUsesCorrelativo() {
        SunatSoapClient soap = mock(SunatSoapClient.class);
        CdrParser cdr = mock(CdrParser.class);
        SecretCryptoService crypto = mock(SecretCryptoService.class);
        when(crypto.decrypt("cipher")).thenReturn("secret");
        when(soap.getStatusCdr(anyString(), anyString(), anyString(), eq("20100066603"),
                eq("01"), eq("F001"), eq(42L)))
                .thenReturn(new SunatSoapClient.SoapCdrStatusResponse("0011", "No existe", null, 200));

        var gateway = new DefaultSunatGateway(
                soap,
                cdr,
                crypto,
                "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService",
                "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService",
                "https://e-factura.sunat.gob.pe/ol-it-wsconscpegem/billConsultService",
                new SunatEndpointPolicy());

        var issuer = new IssuerEntity();
        issuer.setRuc("20100066603");
        issuer.setSunatEnvironment(SunatEnvironment.PRODUCTION);
        issuer.setSolUser("MODDATOS");
        issuer.setSolPasswordEncrypted("cipher");

        var document = new ElectronicDocumentEntity();
        document.setDocumentType(DocumentType.INVOICE);
        document.setSeries("F001");
        document.setCorrelativo(42L);

        var result = gateway.recoverCdr(new DocumentBundle(document, issuer, List.of(), List.of()));

        assertThat(result.status()).isEqualTo(SunatReconciliationStatus.CONFIRMED_NOT_FOUND);
        assertThat(result.hasSubmission()).isFalse();
        verify(soap).getStatusCdr(anyString(), anyString(), anyString(), eq("20100066603"),
                eq("01"), eq("F001"), eq(42L));
    }
}
