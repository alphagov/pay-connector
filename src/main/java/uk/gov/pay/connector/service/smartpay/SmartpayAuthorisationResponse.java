package uk.gov.pay.connector.service.smartpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
public class SmartpayAuthorisationResponse extends SmartpayBaseResponse implements BaseAuthoriseResponse {

    private static final String AUTHORISED = "Authorised";

    @XmlPath("soap:Body/ns1:authoriseResponse/ns1:paymentResult/ns1:resultCode/text()")
    private String result;

    @XmlPath("soap:Body/ns1:authoriseResponse/ns1:paymentResult/ns1:pspReference/text()")
    private String pspReference;

    public String getPspReference() {
        return pspReference;
    }

    public boolean isAuthorised() {
        return AUTHORISED.equals(result);
    }

    @Override
    public String getTransactionId() {
        return pspReference;
    }
}
