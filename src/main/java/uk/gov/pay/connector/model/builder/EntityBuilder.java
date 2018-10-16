package uk.gov.pay.connector.model.builder;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;

public class EntityBuilder {

    public NotificationCredentials newNotificationCredentials(GatewayAccountEntity gatewayAccountEntity) {
        return new NotificationCredentials(gatewayAccountEntity);
    }
}
