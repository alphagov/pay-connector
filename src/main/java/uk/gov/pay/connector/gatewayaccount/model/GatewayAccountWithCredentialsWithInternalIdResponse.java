package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record GatewayAccountWithCredentialsWithInternalIdResponse (
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
        String disabledReason,

        @JsonProperty("notifySettings")
        @Schema(description = "An object containing the Notify credentials and configuration for sending custom branded emails")
        Map<String, String> notifySettings,

        @JsonProperty("notificationCredentials")
        @Schema(description = "The gateway credentials for receiving notifications. Only present for Smartpay accounts")
        NotificationCredentials notificationCredentials,

        @JsonProperty("gateway_account_credentials")
        @Schema(description = "Array of the credentials configured for this account")
        List<GatewayAccountCredentialsWithInternalId> gatewayAccountCredentials
) implements GatewayAccountResponse {
    public GatewayAccountWithCredentialsWithInternalIdResponse(GatewayAccountEntity gatewayAccountEntity) {
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
                null,
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
                gatewayAccountEntity.getDisabledReason(),
                gatewayAccountEntity.getNotifySettings(),
                gatewayAccountEntity.getNotificationCredentials(),
                gatewayAccountEntity.getGatewayAccountCredentials()
                        .stream()
                        .map(GatewayAccountCredentialsWithInternalId::new)
                        .toList()
        );
    }

    public String getType() {
        return type.toString();
    }
}
