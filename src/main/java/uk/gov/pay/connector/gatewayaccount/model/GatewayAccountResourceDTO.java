package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class GatewayAccountResourceDTO {

    @JsonProperty("gateway_account_id")
    private long accountId;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("payment_provider")
    private String paymentProvider;

    private GatewayAccountType type;

    private String description;

    @JsonProperty("service_name")
    private String serviceName;

    @JsonProperty("service_id")
    private String serviceId;

    @JsonProperty("analytics_id")
    private String analyticsId;

    @JsonProperty("corporate_credit_card_surcharge_amount")
    private long corporateCreditCardSurchargeAmount;

    @JsonProperty("corporate_debit_card_surcharge_amount")
    private long corporateDebitCardSurchargeAmount;

    @JsonProperty("_links")
    private Map<String, Map<String, URI>> links = new HashMap<>();

    @JsonProperty("allow_apple_pay")
    private boolean allowApplePay;

    @JsonProperty("allow_google_pay")
    private boolean allowGooglePay;

    @JsonProperty("block_prepaid_cards")
    private boolean blockPrepaidCards;

    @JsonProperty("corporate_prepaid_debit_card_surcharge_amount")
    private long corporatePrepaidDebitCardSurchargeAmount;

    @JsonProperty("email_notifications")
    private Map<EmailNotificationType, EmailNotificationEntity> emailNotifications = new HashMap<>();

    @JsonProperty("email_collection_mode")
    private EmailCollectionMode emailCollectionMode = EmailCollectionMode.MANDATORY;

    @JsonProperty("requires3ds")
    private boolean requires3ds;

    @JsonProperty("allow_zero_amount")
    private boolean allowZeroAmount;

    @JsonProperty("integration_version_3ds")
    private int integrationVersion3ds;

    @JsonProperty("allow_moto")
    private boolean allowMoto;

    @JsonProperty("allow_telephone_payment_notifications")
    private boolean allowTelephonePaymentNotifications;

    @JsonProperty("moto_mask_card_number_input")
    private boolean motoMaskCardNumberInput;

    @JsonProperty("moto_mask_card_security_code_input")
    private boolean motoMaskCardSecurityCodeInput;

    @JsonProperty("provider_switch_enabled")
    private boolean providerSwitchEnabled;

    @JsonInclude(NON_NULL)
    @JsonProperty("worldpay_3ds_flex")
    private Worldpay3dsFlexCredentials worldpay3dsFlexCredentials;

    @JsonProperty("send_payer_ip_address_to_gateway")
    private boolean sendPayerIpAddressToGateway;

    @JsonProperty("send_payer_email_to_gateway")
    private boolean sendPayerEmailToGateway;

    @JsonProperty("send_reference_to_gateway")
    private boolean sendReferenceToGateway;

    @JsonProperty("requires_additional_kyc_data")
    private boolean requiresAdditionalKycData;
    
    @JsonProperty("allow_authorisation_api")
    private boolean allowAuthorisationApi;

    @JsonProperty("recurring_enabled")
    private boolean recurringEnabled;

    public GatewayAccountResourceDTO() {
    }

    public GatewayAccountResourceDTO(GatewayAccountEntity gatewayAccountEntity) {
        this.accountId = gatewayAccountEntity.getId();
        this.externalId = gatewayAccountEntity.getExternalId();
        this.paymentProvider = gatewayAccountEntity.getGatewayName();
        this.type = GatewayAccountType.fromString(gatewayAccountEntity.getType());
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
        this.requiresAdditionalKycData = gatewayAccountEntity.isRequiresAdditionalKycData();
        this.allowAuthorisationApi = gatewayAccountEntity.isAllowAuthorisationApi();
        this.recurringEnabled = gatewayAccountEntity.isRecurringEnabled();
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

    public boolean isRequiresAdditionalKycData() {
        return requiresAdditionalKycData;
    }

    public boolean isAllowAuthorisationApi() {
        return allowAuthorisationApi;
    }

    public boolean isRecurringEnabled() {
        return recurringEnabled;
    }

}
