package uk.gov.pay.connector.service.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.service.BaseCancelResponse;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "paymentService")
public class WorldpayCancelResponse extends WorldpayBaseResponse implements BaseCancelResponse {

    @XmlPath("reply/ok/cancelReceived/@orderCode")
    private String transactionId;

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

}
