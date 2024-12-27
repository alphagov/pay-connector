package uk.gov.pay.connector.gateway.worldpay;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.StringJoiner;

@XmlRootElement(name = "paymentService")
public class WorldpayCaptureResponse extends WorldpayBaseResponse implements BaseCaptureResponse {

    @XmlPath("reply/ok/captureReceived/@orderCode")
    private String transactionId;

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public String stringify() {
        StringJoiner joiner = new StringJoiner(", ", "Worldpay capture response (", ")");
        if (StringUtils.isNotBlank(getTransactionId())) {
            joiner.add("orderCode: " + getTransactionId());
        }
        if (StringUtils.isNotBlank(getErrorCode())) {
            joiner.add("error code: " + getErrorCode());
        }
        if (StringUtils.isNotBlank(getErrorMessage())) {
            joiner.add("error: " + getErrorMessage());
        }
        return joiner.toString();
    }

    @Override
    public String toString() {
        return stringify();
    }
}
