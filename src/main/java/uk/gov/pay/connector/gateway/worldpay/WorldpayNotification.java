package uk.gov.pay.connector.gateway.worldpay;

import org.eclipse.persistence.oxm.annotations.XmlPath;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.usernotification.model.ChargeStatusRequest;

import javax.xml.bind.annotation.XmlRootElement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

@XmlRootElement(name = "paymentService")
public class WorldpayNotification implements ChargeStatusRequest {

    public WorldpayNotification() {
    }

    public WorldpayNotification(String merchantCode, String status, int dayOfMonth, int month, int year, String transactionId, String reference, String refundAuthorisationReference) {
        this.merchantCode = merchantCode;
        this.status = status;
        this.dayOfMonth = dayOfMonth;
        this.month = month;
        this.year = year;
        this.transactionId = transactionId;
        this.reference = reference;
        this.refundAuthorisationReference = refundAuthorisationReference;
    }

    @XmlPath("@merchantCode")
    private String merchantCode;

    @XmlPath("notify/orderStatusEvent/journal/@journalType")
    private String status;

    @XmlPath("notify/orderStatusEvent/journal/bookingDate/date/@dayOfMonth")
    private int dayOfMonth;

    @XmlPath("notify/orderStatusEvent/journal/bookingDate/date/@month")
    private int month;

    @XmlPath("notify/orderStatusEvent/journal/bookingDate/date/@year")
    private int year;

    @XmlPath("notify/orderStatusEvent/@orderCode")
    private String transactionId;

    @XmlPath("notify/orderStatusEvent/journal/journalReference[@type='refund_authorisation']/@reference")
    private String refundAuthorisationReference;

    @XmlPath("notify/orderStatusEvent/journal/journalReference/@reference")
    private String reference;

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

    public ZonedDateTime getGatewayEventDate() {
        return getBookingDate().atStartOfDay(ZoneOffset.UTC);
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public String getReference() {
        return reference;
    }

    public String getRefundAuthorisationReference() {
        return refundAuthorisationReference;
    }

    public LocalDate getBookingDate() {
        return LocalDate.of(year, month, dayOfMonth);
    }

    @Override
    public String toString() {
        return "WorldpayNotification{" +
                "merchantCode='" + merchantCode + '\'' +
                ", status='" + status + '\'' +
                ", dayOfMonth=" + dayOfMonth +
                ", month=" + month +
                ", year=" + year +
                ", transactionId='" + transactionId + '\'' +
                ", reference='" + reference + '\'' +
                ", captureReference='" + reference + '\'' +
                ", refundAuthorisationReference='" + refundAuthorisationReference + '\'' +
                ", chargeStatus=" + chargeStatus +
                '}';
    }
}
