package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import uk.gov.pay.connector.gatewayaccount.util.CredentialsConverter;
import uk.gov.pay.connector.gatewayaccount.util.JsonToMapConverter;
import uk.gov.pay.connector.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.model.domain.CardTypeEntity;
import uk.gov.pay.connector.model.domain.EmailCollectionMode;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.model.domain.EmailNotificationType;
import uk.gov.pay.connector.model.domain.NotificationCredentials;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Entity
@Table(name = "gateway_accounts")
@SequenceGenerator(name = "gateway_accounts_gateway_account_id_seq",
        sequenceName = "gateway_accounts_gateway_account_id_seq", allocationSize = 1)
public class GatewayAccountEntity extends AbstractVersionedEntity {

    public class Views {
        public class ApiView {
        }

        public class FrontendView {
        }
    }

    public enum Type {
        TEST("test"), LIVE("live");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        public String toString() {
            return value;
        }

        public static Type fromString(String type) {
            for (Type typeEnum : Type.values()) {
                if (typeEnum.toString().equalsIgnoreCase(type)) {
                    return typeEnum;
                }
            }
            throw new IllegalArgumentException("gateway account type has to be one of (test, live)");
        }
    }

    public GatewayAccountEntity() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_accounts_gateway_account_id_seq")
    @JsonIgnore
    private Long id;

    //TODO: Should we rename the columns to be more consistent?
    @Column(name = "payment_provider")
    private String gatewayName;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Type type;

    @Column(name = "credentials", columnDefinition = "json")
    @Convert(converter = CredentialsConverter.class)
    private Map<String, String> credentials;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "description")
    private String description;

    @Column(name = "analytics_id")
    private String analyticsId;

    @Column(name = "requires_3ds")
    private boolean requires3ds;

    @Column(name = "allow_web_payments")
    private boolean allowWebPayments;

    @Column(name = "corporate_credit_card_surcharge_amount")
    private long corporateCreditCardSurchargeAmount;

    @Column(name = "corporate_debit_card_surcharge_amount")
    private long corporateDebitCardSurchargeAmount;

    @Column(name = "notify_settings", columnDefinition = "json")
    @Convert(converter = JsonToMapConverter.class)
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

    @ManyToMany
    @JoinTable(
            name = "accepted_card_types",
            joinColumns = @JoinColumn(name = "gateway_account_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "card_type_id", referencedColumnName = "id")
    )
    private List<CardTypeEntity> cardTypes = newArrayList();

    public GatewayAccountEntity(String gatewayName, Map<String, String> credentials, Type type) {
        this.gatewayName = gatewayName;
        this.credentials = credentials;
        this.type = type;
    }

    @JsonProperty("gateway_account_id")
    @JsonView({Views.ApiView.class, Views.FrontendView.class})
    public Long getId() {
        return this.id;
    }

    @JsonProperty("payment_provider")
    @JsonView(value = {Views.ApiView.class, Views.FrontendView.class})
    public String getGatewayName() {
        return gatewayName;
    }

    @JsonView(Views.ApiView.class)
    public Map<String, String> getCredentials() {
        return credentials;
    }

    @JsonView(Views.ApiView.class)
    public String getDescription() {
        return description;
    }

    @JsonView(value = {Views.ApiView.class, Views.FrontendView.class})
    @JsonProperty("analytics_id")
    public String getAnalyticsId() {
        return analyticsId;
    }

    @JsonProperty("type")
    @JsonView(value = {Views.ApiView.class, Views.FrontendView.class})
    public String getType() {
        return type.value;
    }

    @JsonProperty("service_name")
    @JsonView(value = {Views.ApiView.class, Views.FrontendView.class})
    public String getServiceName() {
        return serviceName;
    }

    @JsonView(Views.FrontendView.class)
    @JsonProperty("card_types")
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

    public boolean isRequires3ds() {
        return requires3ds;
    }

    public boolean isAllowWebPayments() {
        return allowWebPayments;
    }

    public void setAllowWebPayments(boolean allowWebPayments) {
        this.allowWebPayments = allowWebPayments;
    }

    @JsonView(value = {Views.ApiView.class, Views.FrontendView.class})
    @JsonProperty("corporate_credit_card_surcharge_amount")
    public long getCorporateCreditCardSurchargeAmount() {
        return corporateCreditCardSurchargeAmount;
    }

    @JsonView(value = {Views.ApiView.class, Views.FrontendView.class})
    @JsonProperty("corporate_debit_card_surcharge_amount")
    public long getCorporateDebitCardSurchargeAmount() {
        return corporateDebitCardSurchargeAmount;
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

    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setCardTypes(List<CardTypeEntity> cardTypes) {
        this.cardTypes = cardTypes;
    }

    public void setRequires3ds(boolean requires3ds) {
        this.requires3ds = requires3ds;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setNotifySettings(Map<String, String> notifySettings) {
        this.notifySettings = notifySettings;
    }

    public void setEmailCollectionMode(EmailCollectionMode emailCollectionMode) {
        this.emailCollectionMode = emailCollectionMode;
    }

    public Map<String, String> getNotifySettings() {
        return notifySettings;
    }

    public Map<String, Object> withoutCredentials() {
        Map<String, Object> account = newHashMap();
        account.put("gateway_account_id", String.valueOf(getId()));
        account.put("payment_provider", getGatewayName());
        account.put("email_notifications", getEmailNotifications());
        account.put("email_collection_mode", getEmailCollectionMode().toString());
        account.put("type", getType());
        if (isNotBlank(getDescription())) {
            account.put("description", getDescription());
        }
        if (isNotBlank(getAnalyticsId())) {
            account.put("analytics_id", getAnalyticsId());
        }
        if (isNotBlank(getServiceName())) {
            account.put("service_name", getServiceName());
        }
        account.put("toggle_3ds", String.valueOf(isRequires3ds()));
        account.put("corporate_credit_card_surcharge_amount", getCorporateCreditCardSurchargeAmount());
        account.put("corporate_debit_card_surcharge_amount", getCorporateDebitCardSurchargeAmount());
        account.put("allow_web_payments", String.valueOf(allowWebPayments));
        return account;
    }

    public boolean hasAnyAcceptedCardType3dsRequired() {
        return cardTypes.stream()
                .anyMatch(CardTypeEntity::isRequires3ds);
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isLive() {
        return Type.LIVE.equals(type);
    }

    public void setCorporateCreditCardSurchargeAmount(long corporateCreditCardSurchargeAmount) {
        this.corporateCreditCardSurchargeAmount = corporateCreditCardSurchargeAmount;
    }

    public void setCorporateDebitCardSurchargeAmount(long corporateDebitCardSurchargeAmount) {
        this.corporateDebitCardSurchargeAmount = corporateDebitCardSurchargeAmount;
    }
}
