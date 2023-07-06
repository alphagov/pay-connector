package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class GatewayAccountResponse {

    @JsonProperty("gateway_account_id")
    @Schema(example = "1", description = "The account ID")
    private long accountId;

    @JsonProperty("external_id")
    @Schema(example = "fbf905a3f7ea416c8c252410eb45ddbd", description = "External ID for the gateway account")
    private String externalId;

    @JsonProperty("payment_provider")
    @Schema(example = "sandbox", description = "The payment provider for which this account is created")
    private String paymentProvider;

    @Schema(example = "test", description = "Account type for the payment provider (test/live)")
    private GatewayAccountType type;
    
    @Schema(example = "true", description = "Whether the account is live")
    private boolean live;

    @Schema(example = "Account for service xxx", description = "An internal description to identify the gateway account. The default value is null.", defaultValue = "null")
    private String description;

    @JsonProperty("service_name")
    @Schema(example = "service name", description = "The service name for the account")
    private String serviceName;

    @JsonProperty("service_id")
    @Schema(example = "cd1b871207a94a7fa157dee678146acd", description = "Service external ID")
    private String serviceId;

    @JsonProperty("analytics_id")
    @Schema(description = "An identifier used to identify the service in Google Analytics. The default value is null")
    private String analyticsId;

    @JsonProperty("corporate_credit_card_surcharge_amount")
    @Schema(example = "250", description = "A corporate credit card surcharge amount in pence", defaultValue = "0")
    private long corporateCreditCardSurchargeAmount;

    @JsonProperty("corporate_debit_card_surcharge_amount")
    @Schema(example = "250", description = "A corporate debit card surcharge amount in pence", defaultValue = "0")
    private long corporateDebitCardSurchargeAmount;

    @JsonProperty("corporate_prepaid_debit_card_surcharge_amount")
    @Schema(example = "0", description = "A corporate prepaid debit card surcharge amount in pence")
    private long corporatePrepaidDebitCardSurchargeAmount;

    @JsonProperty("_links")
    @Schema(example = "{" +
            "        {" +
            "            \"href\": \"https://connector.url/v1/api/accounts/1\"," +
            "            \"rel\": \"self\"," +
            "            \"method\": \"GET\"" +
            "        }" +
            "    }")
    private Map<String, Map<String, URI>> links = new HashMap<>();

    @JsonProperty("allow_apple_pay")
    @Schema(example = "true", description = "Set to true to enable Apple Pay", defaultValue = "false")
    private boolean allowApplePay;

    @JsonProperty("allow_google_pay")
    @Schema(example = "true", description = "Set to true to enable Google Pay", defaultValue = "false")
    private boolean allowGooglePay;

    @JsonProperty("block_prepaid_cards")
    @Schema(example = "true", description = "Whether pre-paid cards are allowed as a payment method for this gateway account", defaultValue = "false")
    private boolean blockPrepaidCards;

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
    private Map<EmailNotificationType, EmailNotificationEntity> emailNotifications = new HashMap<>();

    @JsonProperty("email_collection_mode")
    @Schema(description = "Whether email address is required from paying users. Can be MANDATORY, OPTIONAL or OFF")
    private EmailCollectionMode emailCollectionMode = EmailCollectionMode.MANDATORY;

    @JsonProperty("requires3ds")
    @Schema(example = "true", description = "Flag to indicate whether 3DS is enabled")
    private boolean requires3ds;

    @JsonProperty("allow_zero_amount")
    @Schema(example = "true", description = "Set to true to support charges with a zero amount", defaultValue = "false")
    private boolean allowZeroAmount;

    @JsonProperty("integration_version_3ds")
    @Schema(example = "2", description = "3DS version used for payments for the gateway account")
    private int integrationVersion3ds;

    @JsonProperty("allow_moto")
    @Schema(description = "Indicates whether the Mail Order and Telephone Order (MOTO) payments are allowed", defaultValue = "false")
    private boolean allowMoto;

    @JsonProperty("allow_telephone_payment_notifications")
    @Schema(description = "Indicates if the account is used for telephone payments reporting", defaultValue = "false")
    private boolean allowTelephonePaymentNotifications;

    @JsonProperty("moto_mask_card_number_input")
    @Schema(description = "Indicates whether the card number is masked when being input for MOTO payments. The default value is false.", defaultValue = "false")
    private boolean motoMaskCardNumberInput;

    @JsonProperty("moto_mask_card_security_code_input")
    @Schema(description = "Indicates whether the card security code is masked when being input for MOTO payments.", defaultValue = "false")
    private boolean motoMaskCardSecurityCodeInput;

    @JsonProperty("provider_switch_enabled")
    @Schema(example = "false", description = "Flag to enable payment provider switching", defaultValue = "false")
    private boolean providerSwitchEnabled;

    @JsonInclude(NON_NULL)
    @JsonProperty("worldpay_3ds_flex")
    private Worldpay3dsFlexCredentials worldpay3dsFlexCredentials;

    @JsonProperty("send_payer_ip_address_to_gateway")
    @Schema(example = "true", description = "If enabled, user IP address is sent to to gateway", defaultValue = "false")
    private boolean sendPayerIpAddressToGateway;

    @JsonProperty("send_payer_email_to_gateway")
    @Schema(example = "true", description = "If enabled, user email address is included in the authorisation request to gateway", defaultValue = "false")
    private boolean sendPayerEmailToGateway;

    @JsonProperty("send_reference_to_gateway")
    @Schema(example = "true", description = "If enabled, service payment reference is sent to gateway as description. " +
            "Otherwise payment description is sent to the gateway. Only applicable for Worldpay accounts. Default value is 'false'", defaultValue = "false")
    private boolean sendReferenceToGateway;

    @JsonProperty("allow_authorisation_api")
    @Schema(example = "true", description = "Flag to indicate whether the account is allowed to initiate MOTO payments that are authorised via " +
            "an API request rather than the web interface", defaultValue = "false")
    private boolean allowAuthorisationApi;

    @JsonProperty("recurring_enabled")
    @Schema(example = "true", description = "Flag to indicate whether the account is allowed to take recurring card payments", defaultValue = "false")
    private boolean recurringEnabled;

    @JsonProperty("disabled")
    @Schema(example = "false", description = "Flag to indicate whether the account is allowed to take payments and make refunds", defaultValue = "false")
    private boolean disabled;

    @JsonProperty("disabled_reason")
    @Schema(example = "No longer required", description = "The reason the account is disabled, if applicable")
    private String disabledReason;

    public GatewayAccountResponse() {
    }

    public GatewayAccountResponse(GatewayAccountEntity gatewayAccountEntity) {
        this.accountId = gatewayAccountEntity.getId();
        this.externalId = gatewayAccountEntity.getExternalId();
        this.paymentProvider = gatewayAccountEntity.getGatewayName();
        this.type = GatewayAccountType.fromString(gatewayAccountEntity.getType());
        this.live = gatewayAccountEntity.isLive();
        this.description = gatewayAccountEntity.getDescription();
        this.serviceName = gatewayAccountEntity.getServiceName();
        this.analyticsId = gatewayAccountEntity.getAnalyticsId();
        this.corporateCreditCardSurchargeAmount = gatewayAccountEntity.getCorporateNonPrepaidCreditCardSurchargeAmount();
        this.corporateDebitCardSurchargeAmount = gatewayAccountEntity.getCorporateNonPrepaidDebitCardSurchargeAmount();
        this.allowApplePay = gatewayAccountEntity.isAllowApplePay();
        this.allowGooglePay = gatewayAccountEntity.isAllowGooglePay();
        this.blockPrepaidCards = gatewayAccountEntity.isBlockPrepaidCards();
        this.corporatePrepaidDebitCardSurchargeAmount = gatewayAccountEntity.getCorporatePrepaidDebitCardSurchargeAmount();
        this.emailNotifications = gatewayAccountEntity.getEmailNotifications();
        this.emailCollectionMode = gatewayAccountEntity.getEmailCollectionMode();
        this.requires3ds = gatewayAccountEntity.isRequires3ds();
        this.allowZeroAmount = gatewayAccountEntity.isAllowZeroAmount();
        this.integrationVersion3ds = gatewayAccountEntity.getIntegrationVersion3ds();
        this.allowMoto = gatewayAccountEntity.isAllowMoto();
        this.motoMaskCardNumberInput = gatewayAccountEntity.isMotoMaskCardNumberInput();
        this.motoMaskCardSecurityCodeInput = gatewayAccountEntity.isMotoMaskCardSecurityCodeInput();
        this.allowTelephonePaymentNotifications = gatewayAccountEntity.isAllowTelephonePaymentNotifications();
        this.worldpay3dsFlexCredentials = gatewayAccountEntity.getWorldpay3dsFlexCredentials().orElse(null);
        this.providerSwitchEnabled = gatewayAccountEntity.isProviderSwitchEnabled();
        this.sendPayerEmailToGateway = gatewayAccountEntity.isSendPayerEmailToGateway();
        this.sendReferenceToGateway = gatewayAccountEntity.isSendReferenceToGateway();
        this.sendPayerIpAddressToGateway = gatewayAccountEntity.isSendPayerIpAddressToGateway();
        this.serviceId = gatewayAccountEntity.getServiceId();
        this.allowAuthorisationApi = gatewayAccountEntity.isAllowAuthorisationApi();
        this.recurringEnabled = gatewayAccountEntity.isRecurringEnabled();
        this.disabled = gatewayAccountEntity.isDisabled();
        this.disabledReason = gatewayAccountEntity.getDisabledReason();
    }

    public long getAccountId() {
        return accountId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getType() {
        return type.toString();
    }

    public boolean isLive() {
        return live;
    }

    public String getDescription() {
        return description;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getAnalyticsId() {
        return analyticsId;
    }

    public long getCorporateCreditCardSurchargeAmount() {
        return corporateCreditCardSurchargeAmount;
    }

    public long getCorporateDebitCardSurchargeAmount() {
        return corporateDebitCardSurchargeAmount;
    }

    public Map<String, Map<String, URI>> getLinks() {
        return links;
    }

    public void addLink(String key, URI uri) {
        links.put(key, ImmutableMap.of("href", uri));
    }

    public long getCorporatePrepaidDebitCardSurchargeAmount() {
        return corporatePrepaidDebitCardSurchargeAmount;
    }

    public boolean isAllowApplePay() {
        return allowApplePay;
    }

    public boolean isAllowGooglePay() {
        return allowGooglePay;
    }

    public boolean isBlockPrepaidCards() {
        return blockPrepaidCards;
    }

    public Map<EmailNotificationType, EmailNotificationEntity> getEmailNotifications() {
        return emailNotifications;
    }

    public EmailCollectionMode getEmailCollectionMode() {
        return emailCollectionMode;
    }

    public boolean isRequires3ds() {
        return requires3ds;
    }

    public boolean isAllowZeroAmount() {
        return allowZeroAmount;
    }

    public int getIntegrationVersion3ds() {
        return integrationVersion3ds;
    }

    public boolean isAllowMoto() {
        return allowMoto;
    }

    public boolean isMotoMaskCardNumberInput() {
        return motoMaskCardNumberInput;
    }

    public boolean isMotoMaskCardSecurityCodeInput() {
        return motoMaskCardSecurityCodeInput;
    }

    public boolean isAllowTelephonePaymentNotifications() {
        return allowTelephonePaymentNotifications;
    }

    public boolean isProviderSwitchEnabled() {
        return providerSwitchEnabled;
    }

    public boolean isSendPayerIpAddressToGateway() {
        return sendPayerIpAddressToGateway;
    }

    public boolean isSendPayerEmailToGateway() {
        return sendPayerEmailToGateway;
    }

    public boolean isSendReferenceToGateway() {
        return sendReferenceToGateway;
    }

    public Optional<Worldpay3dsFlexCredentials> getWorldpay3dsFlexCredentials() {
        return Optional.ofNullable(worldpay3dsFlexCredentials);
    }

    public boolean isAllowAuthorisationApi() {
        return allowAuthorisationApi;
    }

    public boolean isRecurringEnabled() {
        return recurringEnabled;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public String getDisabledReason() {
        return disabledReason;
    }

}
