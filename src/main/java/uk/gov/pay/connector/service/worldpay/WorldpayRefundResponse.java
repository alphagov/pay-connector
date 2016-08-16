package uk.gov.pay.connector.service.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "paymentService")
public class WorldpayRefundResponse extends WorldpayBaseResponse {

    @XmlPath("reply/ok/refundReceived/@orderCode")
    private String transactionId;

    @Override
    public String getTransactionId() {
        return transactionId;
    }
}
