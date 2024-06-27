package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import java.net.URI;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public interface GatewayAccountResponse {

    @JsonProperty("gateway_account_id")
    @Schema(example = "1", description = "The account ID")
    long accountId();

    @JsonProperty("external_id")
    @Schema(example = "fbf905a3f7ea416c8c252410eb45ddbd", description = "External ID for the gateway account")
    String externalId();

    @JsonProperty("payment_provider")
    @Schema(example = "sandbox", description = "The payment provider for which this account is created")
    String paymentProvider();
    
    @JsonProperty("type")
    @Schema(example = "test", description = "Account type for the payment provider (test/live)")
    GatewayAccountType type();

    @JsonProperty("live")
    @Schema(example = "true", description = "Whether the account is live")
    boolean isLive();

    @JsonProperty("description")
    @Schema(example = "Account for service xxx", description = "An internal description to identify the gateway account. The default value is null.", defaultValue = "null")
    String description();

    @JsonProperty("service_name")
    @Schema(example = "service name", description = "The service name for the account")
    String serviceName();

    @JsonProperty("service_id")
    @Schema(example = "cd1b871207a94a7fa157dee678146acd", description = "Service external ID")
    String serviceId();

    @JsonProperty("analytics_id")
    @Schema(description = "An identifier used to identify the service in Google Analytics. The default value is null")
    String analyticsId();

    @JsonProperty("corporate_credit_card_surcharge_amount")
    @Schema(example = "250", description = "A corporate credit card surcharge amount in pence", defaultValue = "0")
    long corporateCreditCardSurchargeAmount();

    @JsonProperty("corporate_debit_card_surcharge_amount")
    @Schema(example = "250", description = "A corporate debit card surcharge amount in pence", defaultValue = "0")
    long corporateDebitCardSurchargeAmount();

    @JsonProperty("corporate_prepaid_debit_card_surcharge_amount")
    @Schema(example = "0", description = "A corporate prepaid debit card surcharge amount in pence")
    long corporatePrepaidDebitCardSurchargeAmount();

    @JsonProperty("_links")
    @Schema(example = "{" +
            "        {" +
            "            \"href\": \"https://connector.url/v1/api/accounts/1\"," +
            "            \"rel\": \"self\"," +
            "            \"method\": \"GET\"" +
            "        }" +
            "    }")
    Map<String, Map<String, URI>> links();

    @JsonProperty("allow_apple_pay")
    @Schema(example = "true", description = "Set to true to enable Apple Pay", defaultValue = "false")
    boolean isAllowApplePay();

    @JsonProperty("allow_google_pay")
    @Schema(example = "true", description = "Set to true to enable Google Pay", defaultValue = "false")
    boolean isAllowGooglePay();

    @JsonProperty("block_prepaid_cards")
    @Schema(example = "true", description = "Whether pre-paid cards are allowed as a payment method for this gateway account", defaultValue = "false")
    boolean isBlockPrepaidCards();

    @JsonProperty("email_notifications")
    @Schema(description = "The settings for the different emails (payments/refunds) that are sent out", example = "{" +
            "        \"REFUND_ISSUED\": {" +
            "            \"version\": 1," +
            "            \"enabled\": true," +
            "            \"template_body\": null" +
            "        }," +
            "        \"PAYMENT_CONFIRMED\": {" +
            "            \"version\": 1," +
            "            \"enabled\": true," +
            "            \"template_body\": null" +
            "        }" +
            "    }")
    Map<EmailNotificationType, EmailNotificationEntity> emailNotifications();

    @JsonProperty("email_collection_mode")
    @Schema(description = "Whether email address is required from paying users. Can be MANDATORY, OPTIONAL or OFF")
    EmailCollectionMode emailCollectionMode();

    @JsonProperty("requires3ds")
    @Schema(example = "true", description = "Flag to indicate whether 3DS is enabled")
    boolean isRequires3ds();

    @JsonProperty("allow_zero_amount")
    @Schema(example = "true", description = "Set to true to support charges with a zero amount", defaultValue = "false")
    boolean isAllowZeroAmount();

    @JsonProperty("integration_version_3ds")
    @Schema(example = "2", description = "3DS version used for payments for the gateway account")
    int integrationVersion3ds();

    @JsonProperty("allow_moto")
    @Schema(description = "Indicates whether the Mail Order and Telephone Order (MOTO) payments are allowed", defaultValue = "false")
    boolean isAllowMoto();

    @JsonProperty("allow_telephone_payment_notifications")
    @Schema(description = "Indicates if the account is used for telephone payments reporting", defaultValue = "false")
    boolean isAllowTelephonePaymentNotifications();

    @JsonProperty("moto_mask_card_number_input")
    @Schema(description = "Indicates whether the card number is masked when being input for MOTO payments. The default value is false.", defaultValue = "false")
    boolean isMotoMaskCardNumberInput();

    @JsonProperty("moto_mask_card_security_code_input")
    @Schema(description = "Indicates whether the card security code is masked when being input for MOTO payments.", defaultValue = "false")
    boolean isMotoMaskCardSecurityCodeInput();

    @JsonProperty("provider_switch_enabled")
    @Schema(example = "false", description = "Flag to enable payment provider switching", defaultValue = "false")
    boolean isProviderSwitchEnabled();

    @JsonInclude(NON_NULL)
    @JsonProperty("worldpay_3ds_flex")
    Worldpay3dsFlexCredentials worldpay3dsFlexCredentials();

    @JsonProperty("send_payer_ip_address_to_gateway")
    @Schema(example = "true", description = "If enabled, user IP address is sent to to gateway", defaultValue = "false")
    boolean isSendPayerIpAddressToGateway();

    @JsonProperty("send_payer_email_to_gateway")
    @Schema(example = "true", description = "If enabled, user email address is included in the authorisation request to gateway", defaultValue = "false")
    boolean isSendPayerEmailToGateway();

    @JsonProperty("send_reference_to_gateway")
    @Schema(example = "true", description = "If enabled, service payment reference is sent to gateway as description. " +
            "Otherwise payment description is sent to the gateway. Only applicable for Worldpay accounts. Default value is 'false'", defaultValue = "false")
    boolean isSendReferenceToGateway();

    @JsonProperty("allow_authorisation_api")
    @Schema(example = "true", description = "Flag to indicate whether the account is allowed to initiate MOTO payments that are authorised via " +
            "an API request rather than the web interface", defaultValue = "false")
    boolean isAllowAuthorisationApi();

    @JsonProperty("recurring_enabled")
    @Schema(example = "true", description = "Flag to indicate whether the account is allowed to take recurring card payments", defaultValue = "false")
    boolean isRecurringEnabled();

    @JsonProperty("disabled")
    @Schema(example = "false", description = "Flag to indicate whether the account is allowed to take payments and make refunds", defaultValue = "false")
    boolean isDisabled();

    @JsonProperty("disabled_reason")
    @Schema(example = "No longer required", description = "The reason the account is disabled, if applicable")
    String disabledReason();
    
    String getType();
    static DefaultGatewayAccountResponse of(GatewayAccountEntity gatewayAccountEntity) {
        return new DefaultGatewayAccountResponse(gatewayAccountEntity);
    }
    static DefaultGatewayAccountResponse of(GatewayAccountEntity gatewayAccountEntity, String key, URI uri) {
        return new DefaultGatewayAccountResponse(gatewayAccountEntity, Map.of(
                key, Map.of("href", uri)
        ));
    }
}
