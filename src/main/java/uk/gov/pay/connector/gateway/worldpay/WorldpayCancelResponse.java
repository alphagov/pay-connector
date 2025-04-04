package uk.gov.pay.connector.gateway.worldpay;

import jakarta.xml.bind.annotation.XmlRootElement;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;

import java.util.StringJoiner;

@XmlRootElement(name = "paymentService")
public class WorldpayCancelResponse extends WorldpayBaseResponse implements BaseCancelResponse {

    @XmlPath("reply/ok/cancelReceived/@orderCode")
    private String transactionId;

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public CancelStatus cancelStatus() {
        return CancelStatus.CANCELLED;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Worldpay cancel response (", ")");
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

}
