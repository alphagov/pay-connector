package uk.gov.pay.connector.service.smartpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;

import javax.xml.bind.annotation.XmlRootElement;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trim;

@XmlRootElement(name = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
public class SmartpayCaptureResponse {
    @XmlPath("soap:Body/ns1:captureResponse/ns1:captureResult/ns1:pspReference/text()")
    private String captureTransactionId;

    @XmlPath("soap:Body/ns1:captureResponse/ns1:captureResult/ns1:response/text()")
    private String captureResponse;

    @XmlPath("soap:Body/soap:Fault/faultstring/text()")
    private String errorMessage;

    public boolean isCaptured() {
        return isNotBlank(captureTransactionId) && "[capture-received]".equalsIgnoreCase(captureResponse);
    }

    public String getPspRefrence() {
        return captureTransactionId;
    }

    public String getErrorMessage() {
        return trim(errorMessage);
    }
}
