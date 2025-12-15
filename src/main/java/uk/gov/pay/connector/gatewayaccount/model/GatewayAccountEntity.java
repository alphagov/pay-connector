package uk.gov.pay.connector.gatewayaccount.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.util.JsonToStringStringMapConverter;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ENTERED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;

@Entity
@Table(name = "gateway_accounts")
@SequenceGenerator(name = "gateway_accounts_gateway_account_id_seq",
        sequenceName = "gateway_accounts_gateway_account_id_seq", allocationSize = 1)
public class GatewayAccountEntity extends AbstractVersionedEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAccountEntity.class);
    
    private static final EnumSet<GatewayAccountCredentialState> pendingCredentialStates = EnumSet.of(CREATED, ENTERED, VERIFIED_WITH_LIVE_PAYMENT);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_accounts_gateway_account_id_seq")
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
    private Map<EmailNotificationType, EmailNotificationEntity> emailNotifications = new HashMap<>();

    @Column(name = "email_collection_mode")
    @Enumerated(EnumType.STRING)
    private EmailCollectionMode emailCollectionMode = EmailCollectionMode.MANDATORY;

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
    
    public Long getId() {
        return this.id;
    }
    
    public String getExternalId() {
        return externalId;
    }
    
    public String getGatewayName() {
        return getCurrentOrActiveGatewayAccountCredential()
                .map(GatewayAccountCredentialsEntity::getPaymentProvider)
                .orElseThrow(() -> new WebApplicationException(
                        serviceErrorResponse(format("Active or current credential not found for gateway account [%s]",
                                getId()))));
    }
    
    public Optional<GatewayAccountCredentialsEntity> getCurrentOrActiveGatewayAccountCredential() {
        List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities = getGatewayAccountCredentials();
        if (getGatewayAccountCredentials().size() == 1) {
            return Optional.of(gatewayAccountCredentialsEntities.getFirst());
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
    
    public GatewayAccountCredentialsEntity getGatewayAccountCredentialsEntity(String paymentProvider) {
        return gatewayAccountCredentials.stream()
                .filter(entity -> entity.getPaymentProvider().equals(paymentProvider))
                .max(comparing(GatewayAccountCredentialsEntity::getActiveStartDate))
                .orElseThrow(() -> new WebApplicationException(serviceErrorResponse(
                        format("Credentials not found for gateway account [%s] and payment_provider [%s] ",
                                getId(), paymentProvider))));
    }
    
    public GatewayAccountCredentialsEntity getRecentNonRetiredGatewayAccountCredentialsEntity(String paymentProvider) {
        return gatewayAccountCredentials.stream()
                .filter(entity -> entity.getPaymentProvider().equals(paymentProvider))
                .filter(entity -> entity.getState() != RETIRED)
                .max(comparing(GatewayAccountCredentialsEntity::getCreatedDate))
                .orElseThrow(() -> new WebApplicationException(serviceErrorResponse(
                        format("Credentials not found for gateway account [%s] and payment_provider [%s] ",
                                getId(), paymentProvider))));
    }
    
    public String getGooglePayMerchantId() {
        return getCurrentOrActiveGatewayAccountCredential()
                .map(GatewayAccountCredentialsEntity::getCredentialsObject)
                .flatMap(GatewayCredentials::getGooglePayMerchantId)
                .orElse(null);
    }

    public List<GatewayAccountCredentialsEntity> getGatewayAccountCredentials() {
        return gatewayAccountCredentials;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getAnalyticsId() {
        return analyticsId;
    }
    
    public String getType() {
        return type.toString();
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public List<CardTypeEntity> getCardTypes() {
        return cardTypes;
    }
    
    public Map<EmailNotificationType, EmailNotificationEntity> getEmailNotifications() {
        return emailNotifications;
    }
    
    public EmailCollectionMode getEmailCollectionMode() {
        return emailCollectionMode;
    }
    
    public Optional<Worldpay3dsFlexCredentialsEntity> getWorldpay3dsFlexCredentialsEntity() {
        return Optional.ofNullable(worldpay3dsFlexCredentialsEntity);
    }
    
    public Optional<Worldpay3dsFlexCredentials> getWorldpay3dsFlexCredentials() {
        return Optional.ofNullable(worldpay3dsFlexCredentialsEntity).map(Worldpay3dsFlexCredentials::fromEntity);
    }

    public boolean isRequires3ds() {
        return requires3ds;
    }
    
    public boolean isAllowGooglePay() {
        Boolean hasCredentialsConfiguredForGooglePay = getCurrentOrActiveGatewayAccountCredential()
                .map(GatewayAccountCredentialsEntity::getCredentialsObject)
                .map(GatewayCredentials::isConfiguredForGooglePayPayments)
                .orElse(false);
        return allowGooglePay && hasCredentialsConfiguredForGooglePay;
    }
    
    public boolean isAllowApplePay() {
        return allowApplePay;
    }
    
    public boolean isAllowZeroAmount() {
        return allowZeroAmount;
    }
    
    public boolean isBlockPrepaidCards() {
        return blockPrepaidCards;
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
    
    public long getCorporateNonPrepaidCreditCardSurchargeAmount() {
        return corporateCreditCardSurchargeAmount;
    }
    
    public long getCorporateNonPrepaidDebitCardSurchargeAmount() {
        return corporateDebitCardSurchargeAmount;
    }
    
    public long getCorporatePrepaidDebitCardSurchargeAmount() {
        return corporatePrepaidDebitCardSurchargeAmount;
    }
    
    public int getIntegrationVersion3ds() {
        return integrationVersion3ds;
    }
    
    public boolean isAllowTelephonePaymentNotifications() {
        return allowTelephonePaymentNotifications;
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
    
    public boolean isStripeGatewayAccount() {
        return PaymentGatewayName.STRIPE.getName().equals(this.getGatewayName());
    }
    
    public boolean isWorldpayGatewayAccount() {
        return PaymentGatewayName.WORLDPAY.getName().equals(this.getGatewayName());
    }

    public boolean isSandboxGatewayAccount() {
        return PaymentGatewayName.SANDBOX.getName().equals(this.getGatewayName());
    }

    public boolean isStripeTestAccount() {
        return GatewayAccountType.TEST.toString().equals(this.getType()) && PaymentGatewayName.STRIPE.getName().equals(this.getGatewayName());
    }

    public boolean hasPendingWorldpayCredential() {
        return providerSwitchEnabled && gatewayAccountCredentials.stream()
                .filter(credential -> credential.getPaymentProvider().equals(WORLDPAY.getName()))
                .anyMatch(credential -> pendingCredentialStates.contains(credential.getState()));
    }
    
    public boolean isLiveOrEnabled() {
        return this.isLive() || !this.isDisabled();
    }
}
