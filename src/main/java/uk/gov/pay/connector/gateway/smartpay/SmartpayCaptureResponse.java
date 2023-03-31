package uk.gov.pay.connector.gateway.smartpay;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

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

    @Override
    public String stringify() {
        StringJoiner joiner = new StringJoiner(", ", "SmartPay capture response (", ")");
        if (StringUtils.isNotBlank(getTransactionId())) {
            joiner.add("pspReference: " + getTransactionId());
        }
        if (StringUtils.isNotBlank(captureResponse)) {
            joiner.add("response: " + captureResponse);
        }
        if (StringUtils.isNotBlank(getErrorCode())) {
            joiner.add("faultcode: " + getErrorCode());
        }
        if (StringUtils.isNotBlank(getErrorMessage())) {
            joiner.add("faultstring: " + getErrorMessage());
        }
        return joiner.toString();
    }

    @Override
    public String toString() {
        return stringify();
    }
}
