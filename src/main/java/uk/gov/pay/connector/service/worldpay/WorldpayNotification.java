package uk.gov.pay.connector.service.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.model.ChargeStatusRequest;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "paymentService")
public class WorldpayNotification implements ChargeStatusRequest {

    @XmlPath("@merchantCode")
    private String merchantCode;

    @XmlPath("notify/orderStatusEvent/journal/@journalType")
    private String status;

    @XmlPath("notify/orderStatusEvent/@orderCode")
    private String transactionId;

    public String getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getMerchantCode() {
        return merchantCode;
    }
}
