package uk.gov.pay.connector.gatewayaccount.model;

import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import java.net.URI;
import java.util.Map;

public record DefaultGatewayAccountResponse (
        long accountId,
        String externalId,
        String paymentProvider,
        GatewayAccountType type,
        boolean isLive,
        String description,
        String serviceName,
        String serviceId,
        String analyticsId,
        long corporateCreditCardSurchargeAmount,
        long corporateDebitCardSurchargeAmount,
        long corporatePrepaidDebitCardSurchargeAmount,
        Map<String, Map<String, URI>> links,
        boolean isAllowApplePay,
        boolean isAllowGooglePay,
        boolean isBlockPrepaidCards,
        Map<EmailNotificationType, EmailNotificationEntity> emailNotifications,
        EmailCollectionMode emailCollectionMode,
        boolean isRequires3ds,
        boolean isAllowZeroAmount,
        int integrationVersion3ds,
        boolean isAllowMoto,
        boolean isAllowTelephonePaymentNotifications,
        boolean isMotoMaskCardNumberInput,
        boolean isMotoMaskCardSecurityCodeInput,
        boolean isProviderSwitchEnabled,
        Worldpay3dsFlexCredentials worldpay3dsFlexCredentials,
        boolean isSendPayerIpAddressToGateway,
        boolean isSendPayerEmailToGateway,
        boolean isSendReferenceToGateway,
        boolean isAllowAuthorisationApi,
        boolean isRecurringEnabled,
        boolean isDisabled,
        String disabledReason
) implements GatewayAccountResponse {
    public DefaultGatewayAccountResponse(GatewayAccountEntity gatewayAccountEntity) {
        this(gatewayAccountEntity, null);
    }
    public DefaultGatewayAccountResponse(GatewayAccountEntity gatewayAccountEntity, Map<String, Map<String, URI>> links) {
        this(
                gatewayAccountEntity.getId(),
                gatewayAccountEntity.getExternalId(),
                gatewayAccountEntity.getGatewayName(),
                GatewayAccountType.fromString(gatewayAccountEntity.getType()),
                gatewayAccountEntity.isLive(),
                gatewayAccountEntity.getDescription(),
                gatewayAccountEntity.getServiceName(),
                gatewayAccountEntity.getServiceId(),
                gatewayAccountEntity.getAnalyticsId(),
                gatewayAccountEntity.getCorporateNonPrepaidCreditCardSurchargeAmount(),
                gatewayAccountEntity.getCorporateNonPrepaidDebitCardSurchargeAmount(),
                gatewayAccountEntity.getCorporatePrepaidDebitCardSurchargeAmount(),
                links,
                gatewayAccountEntity.isAllowApplePay(),
                gatewayAccountEntity.isAllowGooglePay(),
                gatewayAccountEntity.isBlockPrepaidCards(),
                gatewayAccountEntity.getEmailNotifications(),
                gatewayAccountEntity.getEmailCollectionMode(),
                gatewayAccountEntity.isRequires3ds(),
                gatewayAccountEntity.isAllowZeroAmount(),
                gatewayAccountEntity.getIntegrationVersion3ds(),
                gatewayAccountEntity.isAllowMoto(),
                gatewayAccountEntity.isAllowTelephonePaymentNotifications(),
                gatewayAccountEntity.isMotoMaskCardNumberInput(),
                gatewayAccountEntity.isMotoMaskCardSecurityCodeInput(),
                gatewayAccountEntity.isProviderSwitchEnabled(),
                gatewayAccountEntity.getWorldpay3dsFlexCredentials().orElse(null),
                gatewayAccountEntity.isSendPayerIpAddressToGateway(),
                gatewayAccountEntity.isSendPayerEmailToGateway(),
                gatewayAccountEntity.isSendReferenceToGateway(),
                gatewayAccountEntity.isAllowAuthorisationApi(),
                gatewayAccountEntity.isRecurringEnabled(),
                gatewayAccountEntity.isDisabled(),
                gatewayAccountEntity.getDisabledReason()
        );
    }
    
    public String getType() {
        return type.toString();
    }
}
