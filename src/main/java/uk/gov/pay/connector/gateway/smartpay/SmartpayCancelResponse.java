package uk.gov.pay.connector.gateway.smartpay;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

@XmlRootElement(name = "Envelope", namespace="http://schemas.xmlsoap.org/soap/envelope/")
public class SmartpayCancelResponse extends SmartpayBaseResponse implements BaseCancelResponse {

    @XmlPath("soap:Body/ns1:cancelResponse/ns1:cancelResult/ns1:pspReference/text()")
    private String transactionId;

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public CancelStatus cancelStatus() {
        return CancelStatus.CANCELLED;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "SmartPay cancel response (", ")");
        if (StringUtils.isNotBlank(getTransactionId())) {
            joiner.add("pspReference: " + getTransactionId());
        }
        if (StringUtils.isNotBlank(getErrorCode())) {
            joiner.add("faultcode: " + getErrorCode());
        }
        if (StringUtils.isNotBlank(getErrorMessage())) {
            joiner.add("faultstring: " + getErrorMessage());
        }
        return joiner.toString();
    }

}
