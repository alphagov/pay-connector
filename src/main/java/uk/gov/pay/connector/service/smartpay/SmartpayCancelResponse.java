package uk.gov.pay.connector.service.smartpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;

import javax.xml.bind.annotation.XmlRootElement;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trim;

@XmlRootElement(name = "Envelope", namespace="http://schemas.xmlsoap.org/soap/envelope/")
public class SmartpayCancelResponse {

    @XmlPath("soap:Body/ns1:cancelResponse/ns1:cancelResult/ns1:pspReference/text()")
    private String transactionIdForOk;

    @XmlPath("soap:Body/soap:Fault/ns1:faultstring/text()")
    private String errorMessage;

    public boolean isCancelled() {
        return isNotBlank(transactionIdForOk);
    }

    public String getErrorMessage() {
        return trim(errorMessage);
    }
}
