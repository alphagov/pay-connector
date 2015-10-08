package uk.gov.pay.connector.service.smartpay;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SmartpayNotification {

    private String eventCode;
    private String originalReference;

    @JsonCreator
    public SmartpayNotification(@JsonProperty("NotificationRequestItem") Map<String, Object> notification){
        this.eventCode = (String) notification.get("eventCode");
        this.originalReference = (String) notification.get("originalReference");
    }
    public String getEventCode() {
        return eventCode;
    }

    public String getOriginalReference() {
        return originalReference;
    }
}
