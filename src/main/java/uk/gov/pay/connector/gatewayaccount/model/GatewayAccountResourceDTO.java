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
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;

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

    @JsonProperty("corporate_prepaid_credit_card_surcharge_amount")
    private long corporatePrepaidCreditCardSurchargeAmount;

    @JsonProperty("corporate_prepaid_debit_card_surcharge_amount")
    private long corporatePrepaidDebitCardSurchargeAmount;

    @JsonProperty("email_notifications")
    private Map<EmailNotificationType, EmailNotificationEntity> emailNotifications;

    @JsonProperty("email_collection_mode")
    private EmailCollectionMode emailCollectionMode;

    @JsonProperty("requires3ds")
    private boolean requires3ds;

    @JsonProperty("allow_zero_amount")
    private boolean allowZeroAmount;

    @JsonProperty("integration_version_3ds")
    private int integrationVersion3ds;

    @JsonProperty("allow_moto")
    private boolean allowMoto;

    @JsonProperty("moto_mask_card_number_input")
    private boolean motoMaskCardNumberInput;

    @JsonProperty("moto_mask_card_security_code_input")
    private boolean motoMaskCardSecurityCodeInput;

    @JsonProperty("send_payer_ip_address_to_gateway")
    private boolean sendPayerIpAddressToGateway;

    @JsonProperty("worldpay_3ds_flex")
    private Worldpay3dsFlexCredentials worldpay3dsFlexCredentials;

    @JsonProperty("notificationCredentials")
    private final NotificationCredentialsDTO notificationCredentials;

    @JsonProperty("notify_settings")
    private final Map<String, String> notifySettings;

    @JsonProperty("credentials")
    private final Map<String, String> credentials;

    public GatewayAccountResourceDTO(GatewayAccountResourceDTOBuilder builder) {
        this.accountId = builder.accountId;
        this.externalId = builder.externalId;
        this.paymentProvider = builder.paymentProvider;
        this.type = builder.type;
        this.description = builder.description;
        this.serviceName = builder.serviceName;
        this.analyticsId = builder.analyticsId;
        this.corporateCreditCardSurchargeAmount = builder.corporateCreditCardSurchargeAmount;
        this.corporateDebitCardSurchargeAmount = builder.corporateDebitCardSurchargeAmount;
        this.allowApplePay = builder.allowApplePay;
        this.allowGooglePay = builder.allowGooglePay;
        this.blockPrepaidCards = builder.blockPrepaidCards;
        this.corporatePrepaidCreditCardSurchargeAmount = builder.corporatePrepaidCreditCardSurchargeAmount;
        this.corporatePrepaidDebitCardSurchargeAmount = builder.corporatePrepaidDebitCardSurchargeAmount;
        this.emailNotifications = builder.emailNotifications;
        this.emailCollectionMode = builder.emailCollectionMode;
        this.requires3ds = builder.requires3ds;
        this.allowZeroAmount = builder.allowZeroAmount;
        this.integrationVersion3ds = builder.integrationVersion3ds;
        this.allowMoto = builder.allowMoto;
        this.motoMaskCardNumberInput = builder.motoMaskCardNumberInput;
        this.motoMaskCardSecurityCodeInput = builder.motoMaskCardSecurityCodeInput;
        this.sendPayerIpAddressToGateway = builder.sendPayerIpAddressToGateway;
        this.worldpay3dsFlexCredentials = builder.worldpay3dsFlexCredentials;
        this.notificationCredentials = builder.notificationCredentials;
        this.notifySettings = builder.notifySettings;
        this.credentials = builder.credentials;

        this.links = builder.links;
    }

    public static GatewayAccountResourceDTO fromEntity(GatewayAccountEntity gatewayAccountEntity) {
        return buildGatewayAccountResourceDTO(gatewayAccountEntity).build();
    }

    public static GatewayAccountResourceDTO fromEntityWithCredentialsExcludingPassword(
            GatewayAccountEntity gatewayAccountEntity) {
        GatewayAccountResourceDTOBuilder builder = buildGatewayAccountResourceDTO(gatewayAccountEntity);

        builder.withSendPayerIpAddressToGateway(gatewayAccountEntity.isSendPayerIpAddressToGateway())
                .withNotifySettings(gatewayAccountEntity.getNotifySettings());

        Map<String, String> credentials = gatewayAccountEntity.getCredentials();
        credentials.remove("password");
        builder.withCredentials(credentials);

        gatewayAccountEntity.getWorldpay3dsFlexCredentials()
                .ifPresent(builder::withWorldpay3dsFlexCredentials);
        Optional.ofNullable(gatewayAccountEntity.getNotificationCredentials())
                .ifPresent(entity ->
                        builder.withNotificationCredentials(new NotificationCredentialsDTO(entity.getUserName()))
                );

        //TODO: Add DTO test for new fields and check if any other fields are missing ?
        // (added fields - credentials, worldpay_3ds_flex, live, send_payer_ip_address_to_gateway, notificationCredentials, notify_settings) -- done

        return builder.build();
    }

    private static GatewayAccountResourceDTOBuilder buildGatewayAccountResourceDTO(GatewayAccountEntity gatewayAccountEntity) {
        return GatewayAccountResourceDTOBuilder.aGatewayAccountResourceDTO()
                .withAccountId(gatewayAccountEntity.getId())
                .withExternalId(gatewayAccountEntity.getExternalId())
                .withPaymentProvider(gatewayAccountEntity.getGatewayName())
                .withType(GatewayAccountType.fromString(gatewayAccountEntity.getType()))
                .withDescription(gatewayAccountEntity.getDescription())
                .withServiceName(gatewayAccountEntity.getServiceName())
                .withAnalyticsId(gatewayAccountEntity.getAnalyticsId())
                .withCorporateCreditCardSurchargeAmount(gatewayAccountEntity.getCorporateNonPrepaidCreditCardSurchargeAmount())
                .withCorporateDebitCardSurchargeAmount(gatewayAccountEntity.getCorporateNonPrepaidDebitCardSurchargeAmount())
                .withAllowApplePay(gatewayAccountEntity.isAllowApplePay())
                .withAllowGooglePay(gatewayAccountEntity.isAllowGooglePay())
                .withBlockPrepaidCards(gatewayAccountEntity.isBlockPrepaidCards())
                .withCorporatePrepaidCreditCardSurchargeAmount(gatewayAccountEntity.getCorporatePrepaidCreditCardSurchargeAmount())
                .withCorporatePrepaidDebitCardSurchargeAmount(gatewayAccountEntity.getCorporatePrepaidDebitCardSurchargeAmount())
                .withEmailNotifications(gatewayAccountEntity.getEmailNotifications())
                .withEmailCollectionMode(gatewayAccountEntity.getEmailCollectionMode())
                .withRequires3ds(gatewayAccountEntity.isRequires3ds())
                .withAllowZeroAmount(gatewayAccountEntity.isAllowZeroAmount())
                .withIntegrationVersion3ds(gatewayAccountEntity.getIntegrationVersion3ds())
                .withAllowMoto(gatewayAccountEntity.isAllowMoto())
                .withMotoMaskCardNumberInput(gatewayAccountEntity.isMotoMaskCardNumberInput())
                .withMotoMaskCardSecurityCodeInput(gatewayAccountEntity.isMotoMaskCardSecurityCodeInput());
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

    public long getCorporatePrepaidCreditCardSurchargeAmount() {
        return corporatePrepaidCreditCardSurchargeAmount;
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

    public boolean isSendPayerIpAddressToGateway() {
        return sendPayerIpAddressToGateway;
    }

    public Worldpay3dsFlexCredentials getWorldpay3dsFlexCredentials() {
        return worldpay3dsFlexCredentials;
    }

    public NotificationCredentialsDTO getNotificationCredentials() {
        return notificationCredentials;
    }

    public Map<String, String> getNotifySettings() {
        return notifySettings;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    @JsonProperty("live")
    public boolean isLive() {
        return LIVE.equals(type);
    }

    public static final class GatewayAccountResourceDTOBuilder {
        private long accountId;
        private String externalId;
        private String paymentProvider;
        private GatewayAccountType type;
        private String description;
        private String serviceName;
        private String analyticsId;
        private long corporateCreditCardSurchargeAmount;
        private long corporateDebitCardSurchargeAmount;
        private Map<String, Map<String, URI>> links = new HashMap<>();
        private boolean allowApplePay;
        private boolean allowGooglePay;
        private boolean blockPrepaidCards;
        private long corporatePrepaidCreditCardSurchargeAmount;
        private long corporatePrepaidDebitCardSurchargeAmount;
        private Map<EmailNotificationType, EmailNotificationEntity> emailNotifications = new HashMap<>();
        private EmailCollectionMode emailCollectionMode = EmailCollectionMode.MANDATORY;
        private boolean requires3ds;
        private boolean allowZeroAmount;
        private int integrationVersion3ds;
        private boolean allowMoto;
        private boolean motoMaskCardNumberInput;
        private boolean motoMaskCardSecurityCodeInput;
        private boolean sendPayerIpAddressToGateway;
        private Worldpay3dsFlexCredentials worldpay3dsFlexCredentials;
        private NotificationCredentialsDTO notificationCredentials;
        private Map<String, String> notifySettings;
        private Map<String, String> credentials;

        private GatewayAccountResourceDTOBuilder() {
        }

        public static GatewayAccountResourceDTOBuilder aGatewayAccountResourceDTO() {
            return new GatewayAccountResourceDTOBuilder();
        }

        public GatewayAccountResourceDTOBuilder withAccountId(long accountId) {
            this.accountId = accountId;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withExternalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withType(GatewayAccountType type) {
            this.type = type;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withAnalyticsId(String analyticsId) {
            this.analyticsId = analyticsId;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withCorporateCreditCardSurchargeAmount(long corporateCreditCardSurchargeAmount) {
            this.corporateCreditCardSurchargeAmount = corporateCreditCardSurchargeAmount;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withCorporateDebitCardSurchargeAmount(long corporateDebitCardSurchargeAmount) {
            this.corporateDebitCardSurchargeAmount = corporateDebitCardSurchargeAmount;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withLinks(Map<String, Map<String, URI>> links) {
            this.links = links;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withAllowApplePay(boolean allowApplePay) {
            this.allowApplePay = allowApplePay;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withAllowGooglePay(boolean allowGooglePay) {
            this.allowGooglePay = allowGooglePay;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withBlockPrepaidCards(boolean blockPrepaidCards) {
            this.blockPrepaidCards = blockPrepaidCards;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withCorporatePrepaidCreditCardSurchargeAmount(long corporatePrepaidCreditCardSurchargeAmount) {
            this.corporatePrepaidCreditCardSurchargeAmount = corporatePrepaidCreditCardSurchargeAmount;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withCorporatePrepaidDebitCardSurchargeAmount(long corporatePrepaidDebitCardSurchargeAmount) {
            this.corporatePrepaidDebitCardSurchargeAmount = corporatePrepaidDebitCardSurchargeAmount;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withEmailNotifications(Map<EmailNotificationType, EmailNotificationEntity> emailNotifications) {
            this.emailNotifications = emailNotifications;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withEmailCollectionMode(EmailCollectionMode emailCollectionMode) {
            this.emailCollectionMode = emailCollectionMode;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withRequires3ds(boolean requires3ds) {
            this.requires3ds = requires3ds;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withAllowZeroAmount(boolean allowZeroAmount) {
            this.allowZeroAmount = allowZeroAmount;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withIntegrationVersion3ds(int integrationVersion3ds) {
            this.integrationVersion3ds = integrationVersion3ds;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withAllowMoto(boolean allowMoto) {
            this.allowMoto = allowMoto;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withMotoMaskCardNumberInput(boolean motoMaskCardNumberInput) {
            this.motoMaskCardNumberInput = motoMaskCardNumberInput;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withMotoMaskCardSecurityCodeInput(boolean motoMaskCardSecurityCodeInput) {
            this.motoMaskCardSecurityCodeInput = motoMaskCardSecurityCodeInput;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withSendPayerIpAddressToGateway(boolean sendPayerIpAddressToGateway) {
            this.sendPayerIpAddressToGateway = sendPayerIpAddressToGateway;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withWorldpay3dsFlexCredentials(Worldpay3dsFlexCredentials worldpay3dsFlexCredentials) {
            this.worldpay3dsFlexCredentials = worldpay3dsFlexCredentials;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withNotificationCredentials(NotificationCredentialsDTO notificationCredentials) {
            this.notificationCredentials = notificationCredentials;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withNotifySettings(Map<String, String> notifySettings) {
            this.notifySettings = notifySettings;
            return this;
        }

        public GatewayAccountResourceDTOBuilder withCredentials(Map<String, String> credentials) {
            this.credentials = credentials;
            return this;
        }

        public GatewayAccountResourceDTO build() {
            return new GatewayAccountResourceDTO(this);
        }


    }
}
