package uk.gov.pay.connector.usernotification.service;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;

public class EntityBuilder {

    public NotificationCredentials newNotificationCredentials(GatewayAccountEntity gatewayAccountEntity) {
        return new NotificationCredentials(gatewayAccountEntity);
    }
}
