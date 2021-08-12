package uk.gov.pay.connector.gatewayaccount.service;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import javax.ws.rs.core.UriInfo;
import java.net.URI;

import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class GatewayAccountObjectConverter {

    private static final int DEFAULT_INTEGRATION_VERSION_3_DS = 1;

    public static GatewayAccountEntity createEntityFrom(GatewayAccountRequest gatewayAccountRequest) {

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(
                GatewayAccountType.fromString(gatewayAccountRequest.getProviderAccountType()));

        gatewayAccountEntity.setExternalId(randomUuid());

        gatewayAccountEntity.setServiceName(gatewayAccountRequest.getServiceName());
        gatewayAccountEntity.setServiceId(gatewayAccountRequest.getServiceId());
        gatewayAccountEntity.setDescription(gatewayAccountRequest.getDescription());
        gatewayAccountEntity.setAnalyticsId(gatewayAccountRequest.getAnalyticsId());
        gatewayAccountEntity.setRequires3ds(gatewayAccountRequest.getRequires3ds());
        gatewayAccountEntity.setIntegrationVersion3ds(DEFAULT_INTEGRATION_VERSION_3_DS);

        gatewayAccountEntity.addNotification(EmailNotificationType.PAYMENT_CONFIRMED, new EmailNotificationEntity(gatewayAccountEntity));
        gatewayAccountEntity.addNotification(EmailNotificationType.REFUND_ISSUED, new EmailNotificationEntity(gatewayAccountEntity));

        return gatewayAccountEntity;
    }

    public static GatewayAccountResponse createResponseFrom(GatewayAccountEntity entity, UriInfo uriInfo) {
        
        URI uri = uriInfo.
                getBaseUriBuilder().
                path("/v1/api/accounts/{accountId}").build(entity.getId());

        return new GatewayAccountResponse.GatewayAccountResponseBuilder()
                .gatewayAccountId(entity.getId().toString())
                .externalId(entity.getExternalId())
                .serviceName(entity.getServiceName())
                .description(entity.getDescription())
                .analyticsId(entity.getAnalyticsId())
                .providerAccountType(entity.getType())
                .requires3ds(entity.isRequires3ds())
                .location(uri)
                .generateLinks(uri)
                .build();
    }
}
