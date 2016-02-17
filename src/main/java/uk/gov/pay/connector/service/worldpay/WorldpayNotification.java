package uk.gov.pay.connector.service.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.model.ChargeStatusRequest;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Optional;

@XmlRootElement(name = "paymentService")
public class WorldpayNotification implements ChargeStatusRequest {

    @XmlPath("@merchantCode")
    private String merchantCode;

    @XmlPath("notify/orderStatusEvent/journal/@journalType")
    private String status;

    @XmlPath("notify/orderStatusEvent/@orderCode")
    private String transactionId;

    private Optional<ChargeStatus> chargeStatus = Optional.empty();

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public Optional<ChargeStatus> getChargeStatus() {
        return chargeStatus;
    }

    public void setChargeStatus(Optional<ChargeStatus> chargeStatus) {
        this.chargeStatus = chargeStatus;
    }

    public String getStatus() {
        return status;
    }

    public String getMerchantCode() {
        return merchantCode;
    }
}
