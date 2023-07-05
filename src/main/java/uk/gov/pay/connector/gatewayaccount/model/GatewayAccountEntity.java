package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.gatewayaccount.util.JsonToStringStringMapConverter;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.MapKeyColumn;
import javax.persistence.MapKeyEnumerated;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;

@Entity
@Table(name = "gateway_accounts")
@SequenceGenerator(name = "gateway_accounts_gateway_account_id_seq",
        sequenceName = "gateway_accounts_gateway_account_id_seq", allocationSize = 1)
public class GatewayAccountEntity extends AbstractVersionedEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAccountEntity.class);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_accounts_gateway_account_id_seq")
    @JsonIgnore
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private GatewayAccountType type;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "description")
    private String description;

    @Column(name = "analytics_id")
    private String analyticsId;

    @Column(name = "requires_3ds")
    private boolean requires3ds;

    @Column(name = "allow_google_pay")
    private boolean allowGooglePay;

    @Column(name = "allow_apple_pay")
    private boolean allowApplePay;

    @Column(name = "corporate_credit_card_surcharge_amount")
    private long corporateCreditCardSurchargeAmount;

    @Column(name = "corporate_debit_card_surcharge_amount")
    private long corporateDebitCardSurchargeAmount;

    @Column(name = "corporate_prepaid_debit_card_surcharge_amount")
    private long corporatePrepaidDebitCardSurchargeAmount;

    @Column(name = "allow_zero_amount")
    private boolean allowZeroAmount;

    @Column(name = "block_prepaid_cards")
    private boolean blockPrepaidCards;

    @Column(name = "allow_moto")
    private boolean allowMoto;

    @Column(name = "moto_mask_card_number_input")
    private boolean motoMaskCardNumberInput;

    @Column(name = "moto_mask_card_security_code_input")
    private boolean motoMaskCardSecurityCodeInput;

    @Column(name = "integration_version_3ds")
    private int integrationVersion3ds;

    @Column(name = "notify_settings", columnDefinition = "json")
    @Convert(converter = JsonToStringStringMapConverter.class)
    private Map<String, String> notifySettings;

    @OneToMany(mappedBy = "accountEntity", cascade = CascadeType.PERSIST)
    @MapKeyColumn(name = "type")
    @MapKeyEnumerated(value = EnumType.STRING)
    @JsonManagedReference
    private Map<EmailNotificationType, EmailNotificationEntity> emailNotifications = new HashMap<>();

    @Column(name = "email_collection_mode")
    @Enumerated(EnumType.STRING)
    private EmailCollectionMode emailCollectionMode = EmailCollectionMode.MANDATORY;

    @OneToOne(mappedBy = "accountEntity", cascade = CascadeType.PERSIST)
    private NotificationCredentials notificationCredentials;

    @OneToOne(mappedBy = "gatewayAccountEntity", cascade = CascadeType.PERSIST)
    private Worldpay3dsFlexCredentialsEntity worldpay3dsFlexCredentialsEntity;

    @Column(name = "send_payer_ip_address_to_gateway")
    private boolean sendPayerIpAddressToGateway;

    @Column(name = "send_payer_email_to_gateway")
    private boolean sendPayerEmailToGateway;

    @Column(name = "send_reference_to_gateway")
    private boolean sendReferenceToGateway;

    @Column(name = "allow_authorisation_api")
    private boolean allowAuthorisationApi;

    @ManyToMany
    @JoinTable(
            name = "accepted_card_types",
            joinColumns = @JoinColumn(name = "gateway_account_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "card_type_id", referencedColumnName = "id")
    )
    private List<CardTypeEntity> cardTypes = newArrayList();

    @Column(name = "allow_telephone_payment_notifications")
    private boolean allowTelephonePaymentNotifications;

    @OneToMany(mappedBy = "gatewayAccountEntity", cascade = CascadeType.PERSIST)
    private List<GatewayAccountCredentialsEntity> gatewayAccountCredentials = new ArrayList<>();

    @Column(name = "provider_switch_enabled")
    private boolean providerSwitchEnabled;
    @Column(name = "recurring_enabled")
    private boolean recurringEnabled;

    @Column(name = "disabled")
    private boolean disabled;

    @Column(name = "disabled_reason")
    private String disabledReason;

    public GatewayAccountEntity() {
    }

    public GatewayAccountEntity(GatewayAccountType type) {
        this.type = type;
    }

    @JsonProperty("gateway_account_id")
    @JsonView({Views.ApiView.class})
    public Long getId() {
        return this.id;
    }

    @JsonProperty("external_id")
    @JsonView({Views.ApiView.class})
    public String getExternalId() {
        return externalId;
    }

    @JsonProperty("payment_provider")
    @JsonView(value = {Views.ApiView.class})
    public String getGatewayName() {
        return getCurrentOrActiveGatewayAccountCredential()
                .map(GatewayAccountCredentialsEntity::getPaymentProvider)
                .orElseThrow(() -> new WebApplicationException(
                        serviceErrorResponse(format("Active or current credential not found for gateway account [%s]",
                                getId()))));
    }

    @JsonIgnore
    public Optional<GatewayAccountCredentialsEntity> getCurrentOrActiveGatewayAccountCredential() {
        List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities = getGatewayAccountCredentials();
        if (getGatewayAccountCredentials().size() == 1) {
            return Optional.of(gatewayAccountCredentialsEntities.get(0));
        }

        Optional<GatewayAccountCredentialsEntity> mayBeActiveCredential = gatewayAccountCredentialsEntities
                .stream()
                .filter(entity -> entity.getState() == ACTIVE)
                .max(comparing(GatewayAccountCredentialsEntity::getActiveStartDate));

        if (mayBeActiveCredential.isPresent()) {
            return mayBeActiveCredential;
        } else {
            LOGGER.warn("Gateway account [id={}] has multiple credentials but no active credential found", getId(),
                    kv(GATEWAY_ACCOUNT_ID, getId()),
                    kv(GATEWAY_ACCOUNT_TYPE, getType())
            );

            return gatewayAccountCredentialsEntities
                    .stream()
                    .filter(entity -> entity.getState() != RETIRED)
                    .min(comparing(GatewayAccountCredentialsEntity::getCreatedDate))
                    .or(() -> getGatewayAccountCredentials().stream().findFirst());
        }
    }

    public Map<String, Object> getCredentials(String paymentProvider) {
        List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities = getGatewayAccountCredentials();

        return gatewayAccountCredentialsEntities.stream()
                .filter(entity -> entity.getPaymentProvider().equals(paymentProvider))
                .max(comparing(GatewayAccountCredentialsEntity::getActiveStartDate))
                .map(GatewayAccountCredentialsEntity::getCredentials)
                .orElseThrow(() -> new WebApplicationException(serviceErrorResponse(
                        format("Credentials not found for gateway account [%s] and payment_provider [%s] ",
                                getId(), paymentProvider))));
    }

    @JsonIgnore
    public GatewayAccountCredentialsEntity getGatewayAccountCredentialsEntity(String paymentProvider) {
        return gatewayAccountCredentials.stream()
                .filter(entity -> entity.getPaymentProvider().equals(paymentProvider))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(serviceErrorResponse(
                        format("Credentials not found for gateway account [%s] and payment_provider [%s] ",
                                getId(), paymentProvider))));
    }

    @JsonIgnore
    public GatewayAccountCredentialsEntity getRecentNonRetiredGatewayAccountCredentialsEntity(String paymentProvider) {
        return gatewayAccountCredentials.stream()
                .filter(entity -> entity.getPaymentProvider().equals(paymentProvider))
                .filter(entity -> entity.getState() != RETIRED)
                .max(comparing(GatewayAccountCredentialsEntity::getCreatedDate))
                .orElseThrow(() -> new WebApplicationException(serviceErrorResponse(
                        format("Credentials not found for gateway account [%s] and payment_provider [%s] ",
                                getId(), paymentProvider))));
    }

    @JsonIgnore
    public String getGatewayMerchantId() {
        return getCurrentOrActiveGatewayAccountCredential()
                .map(credentialsEntity -> {
                    if (credentialsEntity.getCredentials() != null &&
                            credentialsEntity.getCredentials().containsKey("gateway_merchant_id") &&
                            credentialsEntity.getCredentials().get("gateway_merchant_id") != null) {
                        return credentialsEntity.getCredentials().get("gateway_merchant_id").toString();
                    }
                    return null;
                }).orElse(null);
    }

    @JsonProperty("gateway_account_credentials")
    @JsonView(Views.ApiView.class)
    public List<GatewayAccountCredentialsEntity> getGatewayAccountCredentials() {
        return gatewayAccountCredentials;
    }

    @JsonView(Views.ApiView.class)
    public String getDescription() {
        return description;
    }

    @JsonView(value = {Views.ApiView.class})
    @JsonProperty("analytics_id")
    public String getAnalyticsId() {
        return analyticsId;
    }

    @JsonProperty("type")
    @JsonView(value = {Views.ApiView.class})
    public String getType() {
        return type.toString();
    }

    @JsonProperty("service_name")
    @JsonView(value = {Views.ApiView.class})
    public String getServiceName() {
        return serviceName;
    }

    @JsonProperty("service_id")
    @JsonView(value = {Views.ApiView.class})
    public String getServiceId() {
        return serviceId;
    }

    @JsonIgnore
    public List<CardTypeEntity> getCardTypes() {
        return cardTypes;
    }

    @JsonProperty("email_notifications")
    public Map<EmailNotificationType, EmailNotificationEntity> getEmailNotifications() {
        return emailNotifications;
    }

    @JsonProperty("email_collection_mode")
    public EmailCollectionMode getEmailCollectionMode() {
        return emailCollectionMode;
    }

    @JsonView(Views.ApiView.class)
    public NotificationCredentials getNotificationCredentials() {
        return notificationCredentials;
    }

    @JsonIgnore
    public Optional<Worldpay3dsFlexCredentialsEntity> getWorldpay3dsFlexCredentialsEntity() {
        return Optional.ofNullable(worldpay3dsFlexCredentialsEntity);
    }

    @JsonInclude(NON_NULL)
    @JsonProperty("worldpay_3ds_flex")
    public Optional<Worldpay3dsFlexCredentials> getWorldpay3dsFlexCredentials() {
        return Optional.ofNullable(worldpay3dsFlexCredentialsEntity).map(Worldpay3dsFlexCredentials::fromEntity);
    }

    public boolean isRequires3ds() {
        return requires3ds;
    }

    @JsonProperty("allow_google_pay")
    @JsonView(value = {Views.ApiView.class})
    public boolean isAllowGooglePay() {
        return allowGooglePay && isNotBlank(getGatewayMerchantId());
    }

    @JsonProperty("allow_apple_pay")
    @JsonView(value = {Views.ApiView.class})
    public boolean isAllowApplePay() {
        return allowApplePay;
    }

    @JsonProperty("allow_zero_amount")
    @JsonView(value = {Views.ApiView.class})
    public boolean isAllowZeroAmount() {
        return allowZeroAmount;
    }

    @JsonProperty("block_prepaid_cards")
    @JsonView(value = {Views.ApiView.class})
    public boolean isBlockPrepaidCards() {
        return blockPrepaidCards;
    }

    @JsonProperty("allow_moto")
    @JsonView(value = {Views.ApiView.class})
    public boolean isAllowMoto() {
        return allowMoto;
    }

    @JsonProperty("moto_mask_card_number_input")
    @JsonView(value = {Views.ApiView.class})
    public boolean isMotoMaskCardNumberInput() {
        return motoMaskCardNumberInput;
    }

    @JsonProperty("moto_mask_card_security_code_input")
    @JsonView(value = {Views.ApiView.class})
    public boolean isMotoMaskCardSecurityCodeInput() {
        return motoMaskCardSecurityCodeInput;
    }

    @JsonProperty("corporate_credit_card_surcharge_amount")
    @JsonView(value = {Views.ApiView.class})
    public long getCorporateNonPrepaidCreditCardSurchargeAmount() {
        return corporateCreditCardSurchargeAmount;
    }

    @JsonProperty("corporate_debit_card_surcharge_amount")
    @JsonView(value = {Views.ApiView.class})
    public long getCorporateNonPrepaidDebitCardSurchargeAmount() {
        return corporateDebitCardSurchargeAmount;
    }

    @JsonProperty("corporate_prepaid_debit_card_surcharge_amount")
    @JsonView(value = {Views.ApiView.class})
    public long getCorporatePrepaidDebitCardSurchargeAmount() {
        return corporatePrepaidDebitCardSurchargeAmount;
    }

    @JsonProperty("integration_version_3ds")
    @JsonView(value = {Views.ApiView.class})
    public int getIntegrationVersion3ds() {
        return integrationVersion3ds;
    }

    @JsonProperty("allow_telephone_payment_notifications")
    @JsonView(value = {Views.ApiView.class})
    public boolean isAllowTelephonePaymentNotifications() {
        return allowTelephonePaymentNotifications;
    }

    @JsonProperty("send_payer_ip_address_to_gateway")
    @JsonView(value = {Views.ApiView.class})
    public boolean isSendPayerIpAddressToGateway() {
        return sendPayerIpAddressToGateway;
    }

    @JsonProperty("send_payer_email_to_gateway")
    @JsonView(value = {Views.ApiView.class})
    public boolean isSendPayerEmailToGateway() {
        return sendPayerEmailToGateway;
    }

    @JsonProperty("send_reference_to_gateway")
    @JsonView(value = {Views.ApiView.class})
    public boolean isSendReferenceToGateway() {
        return sendReferenceToGateway;
    }

    @JsonProperty("allow_authorisation_api")
    @JsonView(value = {Views.ApiView.class})
    public boolean isAllowAuthorisationApi() {
        return allowAuthorisationApi;
    }

    @JsonProperty("recurring_enabled")
    @JsonView(value = {Views.ApiView.class})
    public boolean isRecurringEnabled() {
        return recurringEnabled;
    }

    @JsonProperty("disabled")
    @JsonView(value = {Views.ApiView.class})
    public boolean isDisabled() {
        return disabled;
    }

    @JsonProperty("disabled_reason")
    @JsonView(value = {Views.ApiView.class})
    public String getDisabledReason() {
        return disabledReason;
    }

    public void setNotificationCredentials(NotificationCredentials notificationCredentials) {
        this.notificationCredentials = notificationCredentials;
    }

    public void addNotification(EmailNotificationType type, EmailNotificationEntity emailNotificationEntity) {
        emailNotifications.put(type, emailNotificationEntity);
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAnalyticsId(String analyticsId) {
        this.analyticsId = analyticsId;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setCardTypes(List<CardTypeEntity> cardTypes) {
        this.cardTypes = cardTypes;
    }

    public void setRequires3ds(boolean requires3ds) {
        this.requires3ds = requires3ds;
    }

    public void setType(GatewayAccountType type) {
        this.type = type;
    }

    public void setNotifySettings(Map<String, String> notifySettings) {
        this.notifySettings = notifySettings;
    }

    public void setEmailCollectionMode(EmailCollectionMode emailCollectionMode) {
        this.emailCollectionMode = emailCollectionMode;
    }

    public void setEmailNotifications(Map<EmailNotificationType, EmailNotificationEntity> emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    public Map<String, String> getNotifySettings() {
        return notifySettings;
    }

    public boolean hasAnyAcceptedCardType3dsRequired() {
        return cardTypes.stream()
                .anyMatch(CardTypeEntity::isRequires3ds);
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public boolean isLive() {
        return LIVE.equals(type);
    }

    public void setCorporateCreditCardSurchargeAmount(long corporateCreditCardSurchargeAmount) {
        this.corporateCreditCardSurchargeAmount = corporateCreditCardSurchargeAmount;
    }

    public void setCorporateDebitCardSurchargeAmount(long corporateDebitCardSurchargeAmount) {
        this.corporateDebitCardSurchargeAmount = corporateDebitCardSurchargeAmount;
    }

    public void setCorporatePrepaidDebitCardSurchargeAmount(long corporatePrepaidDebitCardSurchargeAmount) {
        this.corporatePrepaidDebitCardSurchargeAmount = corporatePrepaidDebitCardSurchargeAmount;
    }

    public void setAllowGooglePay(boolean allowGooglePay) {
        this.allowGooglePay = allowGooglePay;
    }

    public void setAllowApplePay(boolean allowApplePay) {
        this.allowApplePay = allowApplePay;
    }

    public void setAllowZeroAmount(boolean allowZeroAmount) {
        this.allowZeroAmount = allowZeroAmount;
    }

    public void setBlockPrepaidCards(boolean blockPrepaidCards) {
        this.blockPrepaidCards = blockPrepaidCards;
    }

    public void setAllowMoto(boolean allowMoto) {
        this.allowMoto = allowMoto;
    }

    public void setMotoMaskCardNumberInput(boolean motoMaskCardNumberInput) {
        this.motoMaskCardNumberInput = motoMaskCardNumberInput;
    }

    public void setMotoMaskCardSecurityCodeInput(boolean motoMaskCardSecurityCodeInput) {
        this.motoMaskCardSecurityCodeInput = motoMaskCardSecurityCodeInput;
    }

    public void setIntegrationVersion3ds(int integrationVersion3ds) {
        this.integrationVersion3ds = integrationVersion3ds;
    }

    public void setWorldpay3dsFlexCredentialsEntity(Worldpay3dsFlexCredentialsEntity worldpay3dsFlexCredentialsEntity) {
        this.worldpay3dsFlexCredentialsEntity = worldpay3dsFlexCredentialsEntity;
    }

    public void setSendPayerIpAddressToGateway(boolean sendPayerIpAddressToGateway) {
        this.sendPayerIpAddressToGateway = sendPayerIpAddressToGateway;
    }

    public void setSendPayerEmailToGateway(boolean sendPayerEmailToGateway) {
        this.sendPayerEmailToGateway = sendPayerEmailToGateway;
    }

    public void setAllowTelephonePaymentNotifications(boolean allowTelephonePaymentNotifications) {
        this.allowTelephonePaymentNotifications = allowTelephonePaymentNotifications;
    }

    public void setGatewayAccountCredentials(List<GatewayAccountCredentialsEntity> gatewayAccountCredentials) {
        this.gatewayAccountCredentials = gatewayAccountCredentials;
    }

    public void setAllowAuthorisationApi(boolean allowAuthorisationApi) {
        this.allowAuthorisationApi = allowAuthorisationApi;
    }

    @JsonProperty("provider_switch_enabled")
    @JsonView(value = {Views.ApiView.class})
    public boolean isProviderSwitchEnabled() {
        return providerSwitchEnabled;
    }

    public void setProviderSwitchEnabled(boolean providerSwitchEnabled) {
        this.providerSwitchEnabled = providerSwitchEnabled;
    }

    public void setSendReferenceToGateway(boolean sendReferenceToGateway) {
        this.sendReferenceToGateway = sendReferenceToGateway;
    }

    public void setRecurringEnabled(boolean recurringEnabled) {
        this.recurringEnabled = recurringEnabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public void setDisabledReason(String disabledReason) {
        this.disabledReason = disabledReason;
    }

    public class Views {
        public class ApiView {
        }
    }

}
