package pe.com.perubilling.voiding.application;

import java.io.ByteArrayOutputStream;
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
import pe.com.perubilling.voiding.domain.VoidingBatchEntity;

@Component
public class VoidingUblGenerator {
    private static final String NS="urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1";
    private static final String CAC="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String CBC="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String EXT="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String SAC="urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
    private static final String DS="http://www.w3.org/2000/09/xmldsig#";

    public byte[] generate(VoidingBatchEntity batch, IssuerEntity issuer, ElectronicDocumentEntity doc){
        try{
            var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            Document d=f.newDocumentBuilder().newDocument();
            Element root=d.createElementNS(NS,"VoidedDocuments");
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns",NS);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns:cac",CAC);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns:cbc",CBC);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns:ext",EXT);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns:sac",SAC);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns:ds",DS);
            d.appendChild(root);

            Element exts=el(d,root,EXT,"ext:UBLExtensions",null);
            Element ext=el(d,exts,EXT,"ext:UBLExtension",null);
            el(d,ext,EXT,"ext:ExtensionContent",null);
            cbc(d,root,"UBLVersionID","2.0");
            cbc(d,root,"CustomizationID","1.0");
            cbc(d,root,"ID",batch.getIdentifier());
            cbc(d,root,"ReferenceDate",batch.getReferenceDate().toString());
            cbc(d,root,"IssueDate",batch.getGenerationDate().toString());

            Element sig=cac(d,root,"Signature");cbc(d,sig,"ID","IDSignSP");
            Element sp=cac(d,sig,"SignatoryParty");Element pi=cac(d,sp,"PartyIdentification");cbc(d,pi,"ID",issuer.getRuc());
            Element pn=cac(d,sp,"PartyName");cbc(d,pn,"Name",issuer.getBusinessName());
            Element da=cac(d,sig,"DigitalSignatureAttachment");Element er=cac(d,da,"ExternalReference");cbc(d,er,"URI","#SignatureSP");

            Element supplier=cac(d,root,"AccountingSupplierParty");
            cbc(d,supplier,"CustomerAssignedAccountID",issuer.getRuc());
            cbc(d,supplier,"AdditionalAccountID","6");
            Element party=cac(d,supplier,"Party");Element legal=cac(d,party,"PartyLegalEntity");cbc(d,legal,"RegistrationName",issuer.getBusinessName());

            Element line=el(d,root,SAC,"sac:VoidedDocumentsLine",null);
            cbc(d,line,"LineID","1");
            cbc(d,line,"DocumentTypeCode",doc.getDocumentType().getSunatCode());
            cbc(d,line,"DocumentSerialID",doc.getSeries());
            cbc(d,line,"DocumentNumberID",Long.toString(doc.getCorrelativo()));
            cbc(d,line,"VoidReasonDescription",batch.getReason());

            var tf=TransformerFactory.newInstance();tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
            var t=tf.newTransformer();t.setOutputProperty(OutputKeys.ENCODING,"UTF-8");t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,"no");
            var out=new ByteArrayOutputStream();t.transform(new DOMSource(d),new StreamResult(out));return out.toByteArray();
        }catch(Exception ex){throw new IllegalStateException("No se pudo generar la Comunicación de Baja UBL",ex);}
    }
    private Element cbc(Document d,Element p,String n,String v){return el(d,p,CBC,"cbc:"+n,v);}
    private Element cac(Document d,Element p,String n){return el(d,p,CAC,"cac:"+n,null);}
    private Element el(Document d,Element p,String ns,String q,String v){Element e=d.createElementNS(ns,q);if(v!=null)e.setTextContent(v);p.appendChild(e);return e;}
}
