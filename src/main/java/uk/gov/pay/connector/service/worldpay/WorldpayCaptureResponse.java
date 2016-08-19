package uk.gov.pay.connector.service.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.service.BaseCaptureResponse;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "paymentService")
public class WorldpayCaptureResponse extends WorldpayBaseResponse implements BaseCaptureResponse {

    @XmlPath("reply/ok/captureReceived/@orderCode")
    private String transactionId;

    @Override
    public String getTransactionId() {
        return transactionId;
    }
}
