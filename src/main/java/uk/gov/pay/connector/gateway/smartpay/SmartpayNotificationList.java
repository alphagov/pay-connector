package uk.gov.pay.connector.gateway.smartpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SmartpayNotificationList {

    private List<SmartpayNotification> notifications;

    public List<SmartpayNotification> getNotifications() {
        return notifications;
    }

    @JsonProperty("notificationItems")
    public void setNotifications(List<SmartpayNotification> notifications) {
        this.notifications = notifications;
    }
}
