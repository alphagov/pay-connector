package uk.gov.pay.connector.service.smartpay;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

import java.time.LocalDateTime;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SmartpayNotification implements Comparable<SmartpayNotification> {

    private final DateTime eventDate;
    private String eventCode;
    private String originalReference;
    private String merchantReference;
    private String success;

    @JsonCreator
    public SmartpayNotification(@JsonProperty("NotificationRequestItem") Map<String, Object> notification){
        this.eventCode = (String) notification.get("eventCode");
        this.originalReference = (String) notification.get("originalReference");
        this.merchantReference = (String) notification.get("merchantReference");
        this.success = (String) notification.get("success");
        this.eventDate = DateTime.parse((String) notification.get("eventDate"));
    }
    public String getEventCode() {
        return eventCode;
    }

    public String getOriginalReference() {
        return originalReference;
    }

    public String getMerchantReference() {
        return merchantReference;
    }

    public Boolean isSuccessFull() {
        return "true".equals(success);
    }

    @Override
    public int compareTo(SmartpayNotification other) {
        return this.eventDate.compareTo(other.eventDate);
    }
}
