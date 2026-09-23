package pe.com.perubilling.summary.application;

import pe.com.perubilling.issuer.domain.IssuerEntity;

public interface SunatSummaryGateway {
    SummaryTicket submit(IssuerEntity issuer, String fileBase, byte[] signedXml);
    TicketResult getStatus(IssuerEntity issuer, String ticket);

    record SummaryTicket(String ticket, int httpStatus) {}
    record TicketResult(String statusCode, String description, byte[] cdrZip, int httpStatus) {
        public boolean processing(){return "98".equals(statusCode);}
        public boolean accepted(){return "0".equals(statusCode);}
        public boolean rejected(){return !processing() && !accepted();}
    }
}
