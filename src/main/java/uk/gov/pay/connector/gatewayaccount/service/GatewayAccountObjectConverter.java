package uk.gov.pay.connector.gatewayaccount.service;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResponse;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Map;

public class GatewayAccountObjectConverter {

    public static GatewayAccountEntity createEntityFrom(GatewayAccountRequest gatewayAccountRequest) {

        Map<String, String> credentials = gatewayAccountRequest.getCredentialsAsMap();

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(
                gatewayAccountRequest.getPaymentProvider(), 
                credentials,
                GatewayAccountEntity.Type.fromString(gatewayAccountRequest.getProviderAccountType()));

        gatewayAccountEntity.setServiceName(gatewayAccountRequest.getServiceName());
        gatewayAccountEntity.setDescription(gatewayAccountRequest.getDescription());
        gatewayAccountEntity.setAnalyticsId(gatewayAccountRequest.getAnalyticsId());

        gatewayAccountEntity.addNotification(EmailNotificationType.PAYMENT_CONFIRMED, new EmailNotificationEntity(gatewayAccountEntity));
        gatewayAccountEntity.addNotification(EmailNotificationType.REFUND_ISSUED, new EmailNotificationEntity(gatewayAccountEntity));

        return gatewayAccountEntity;
    }

    public static GatewayAccountResponse createResponseFrom(GatewayAccountEntity entity, UriInfo uriInfo) {
        
        URI uri = uriInfo.
                getBaseUriBuilder().
                path("/v1/api/accounts/{accountId}").build(entity.getId());
        
        GatewayAccountResponse gatewayAccountResponse
                = new GatewayAccountResponse.GatewayAccountResponseBuilder()
                .gatewayAccountId(entity.getId().toString())
                .serviceName(entity.getServiceName())
                .description(entity.getDescription())
                .analyticsId(entity.getAnalyticsId())
                .providerAccountType(entity.getType())
                .location(uri)
                .generateLinks(uri)
                .build();

        return gatewayAccountResponse;
    }
}
