package uk.gov.pay.connector.service;

import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.builder.EntityBuilder;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.NotificationCredentials;
import uk.gov.pay.connector.util.HashUtil;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;

public class GatewayAccountNotificationCredentialsService {

    private final GatewayAccountDao gatewayDao;
    private final EntityBuilder entityBuilder;
    private final HashUtil hashUtil;

    @Inject
    public GatewayAccountNotificationCredentialsService(GatewayAccountDao gatewayDao,
                                                        EntityBuilder entityBuilder, HashUtil hashUtil) {
        this.gatewayDao = gatewayDao;
        this.entityBuilder = entityBuilder;
        this.hashUtil = hashUtil;
    }

    public void setCredentialsForAccount(Map<String,String> notificationCredentials, GatewayAccountEntity gatewayAccountEntity) {
        NotificationCredentials existingCredentials = Optional.ofNullable(gatewayAccountEntity.getNotificationCredentials())
                    .orElseGet(() -> entityBuilder.newNotificationCredentials(gatewayAccountEntity));

        existingCredentials.setUserName(notificationCredentials.get("username"));
        existingCredentials.setPassword(hashUtil.hash(notificationCredentials.get("password")));
        gatewayAccountEntity.setNotificationCredentials(existingCredentials);

        gatewayDao.merge(gatewayAccountEntity);
    }
}
