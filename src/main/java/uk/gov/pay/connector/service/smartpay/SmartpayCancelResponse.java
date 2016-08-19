package uk.gov.pay.connector.service.smartpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.service.BaseCancelResponse;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Envelope", namespace="http://schemas.xmlsoap.org/soap/envelope/")
public class SmartpayCancelResponse extends SmartpayBaseResponse implements BaseCancelResponse {

    @XmlPath("soap:Body/ns1:cancelResponse/ns1:cancelResult/ns1:pspReference/text()")
    private String transactionId;

    @Override
    public String getTransactionId() {
        return transactionId;
    }

}
