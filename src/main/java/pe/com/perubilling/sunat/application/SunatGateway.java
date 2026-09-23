package pe.com.perubilling.sunat.application;

import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.sunat.domain.SunatReconciliationResult;
import pe.com.perubilling.sunat.domain.SunatSubmissionResult;

public interface SunatGateway {
    SunatSubmissionResult submitBill(DocumentBundle bundle, byte[] signedXml);

    SunatReconciliationResult recoverCdr(DocumentBundle bundle);
}
