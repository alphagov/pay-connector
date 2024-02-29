package uk.gov.pay.connector.gatewayaccount.model;

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

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static net.logstash.logback.argument.StructuredArguments.kv;
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

    @Column(name = "allow_zero_amount")
    private boolean allowZeroAmount;

    @Column(name = "allow_moto")
    private boolean allowMoto;

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

    @OneToOne(mappedBy = "accountEntity", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private NotificationCredentials notificationCredentials;

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

    @OneToOne(mappedBy = "gatewayAccountEntity", cascade = CascadeType.PERSIST)
    private Worldpay3dsFlexCredentialsEntity worldpay3dsFlexCredentialsEntity;

    @Column(name = "allow_authorisation_api")
    private boolean allowAuthorisationApi;

    @ManyToMany
    @JoinTable(
            name = "accepted_card_types",
            joinColumns = @JoinColumn(name = "gateway_account_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "card_type_id", referencedColumnName = "id")
    )
    private List<CardTypeEntity> cardTypes = newArrayList();
    
    @OneToOne(mappedBy = "gatewayAccountEntity", cascade = CascadeType.PERSIST)
    private GatewayAccountCardConfigurationEntity gatewayAccountCardConfigurationEntity;

    public GatewayAccountEntity() {
        gatewayAccountCardConfigurationEntity = new GatewayAccountCardConfigurationEntity();
    }

    public GatewayAccountEntity(GatewayAccountType type) {
        gatewayAccountCardConfigurationEntity = new GatewayAccountCardConfigurationEntity();
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
    
    public NotificationCredentials getNotificationCredentials() {
        return notificationCredentials;
    }
    
    public Optional<Worldpay3dsFlexCredentialsEntity> getWorldpay3dsFlexCredentialsEntity() {
        return Optional.ofNullable(worldpay3dsFlexCredentialsEntity);
    }
    
    public Optional<Worldpay3dsFlexCredentials> getWorldpay3dsFlexCredentials() {
        return Optional.ofNullable(worldpay3dsFlexCredentialsEntity).map(Worldpay3dsFlexCredentials::fromEntity);
    }
    
    public boolean isAllowZeroAmount() {
        return allowZeroAmount;
    }
    
    public boolean isAllowMoto() {
        return allowMoto;
    }
    
    public boolean isAllowTelephonePaymentNotifications() {
        return allowTelephonePaymentNotifications;
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

    public void setAllowZeroAmount(boolean allowZeroAmount) {
        this.allowZeroAmount = allowZeroAmount;
    }

    public void setAllowMoto(boolean allowMoto) {
        this.allowMoto = allowMoto;
    }

    public void setWorldpay3dsFlexCredentialsEntity(Worldpay3dsFlexCredentialsEntity worldpay3dsFlexCredentialsEntity) {
        this.worldpay3dsFlexCredentialsEntity = worldpay3dsFlexCredentialsEntity;
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

    public void setRecurringEnabled(boolean recurringEnabled) {
        this.recurringEnabled = recurringEnabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public void setDisabledReason(String disabledReason) {
        this.disabledReason = disabledReason;
    }

    public GatewayAccountCardConfigurationEntity getCardConfigurationEntity() {
        return gatewayAccountCardConfigurationEntity;
    }
}
