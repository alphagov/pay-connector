package uk.gov.pay.connector.service.smartpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.service.BaseCaptureResponse;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
public class SmartpayCaptureResponse extends SmartpayBaseResponse implements BaseCaptureResponse {
    @XmlPath("soap:Body/ns1:captureResponse/ns1:captureResult/ns1:pspReference/text()")
    private String captureTransactionId;

    @XmlPath("soap:Body/ns1:captureResponse/ns1:captureResult/ns1:response/text()")
    private String captureResponse;

    @Override
    public String getTransactionId() {
        return captureTransactionId;
    }
}
