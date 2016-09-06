package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.ImmutableMap;

import javax.persistence.*;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "gateway_accounts")
@SequenceGenerator(name="gateway_accounts_gateway_account_id_seq", sequenceName="gateway_accounts_gateway_account_id_seq", allocationSize=1)
public class GatewayAccountEntity extends AbstractEntity {

    public class Views {
        public class FullView { }
        public class PartialView {}
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
            for (Type typeEnum: Type.values()) {
                if (typeEnum.toString().equalsIgnoreCase(type)) {
                    return typeEnum;
                }
            }
            throw new IllegalArgumentException("gateway account type has to be one of (test, live)");
        }
    }

    public GatewayAccountEntity() {}

    //TODO: Should we rename the columns to be more consistent?
    @Column(name = "payment_provider")
    private String gatewayName;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Type type;

    //TODO: Revisit this to map to a java.util.Map
    @Column(name = "credentials", columnDefinition = "json")
    @Convert( converter = CredentialsConverter.class)
    private Map<String, String> credentials;

    @Column(name = "service_name")
    private String serviceName;

    @JsonBackReference
    @OneToOne(mappedBy="accountEntity", cascade = CascadeType.PERSIST)
    private EmailNotificationEntity emailNotification;

    @OneToOne(mappedBy="accountEntity", cascade = CascadeType.PERSIST)
    private NotificationCredentials notificationCredentials;

    @ManyToMany
    @JoinTable(
            name = "accepted_card_types",
            joinColumns = @JoinColumn(name = "gateway_account_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "card_type_id", referencedColumnName = "id")
    )
    private List<CardTypeEntity> cardTypes;

    public GatewayAccountEntity(String gatewayName, Map<String, String> credentials, Type type) {
        this.gatewayName = gatewayName;
        this.credentials = credentials;
        this.type = type;
    }

    @Override
    @JsonProperty("gateway_account_id")
    @JsonView(Views.FullView.class)
    public Long getId() {
        return super.getId();
    }

    @JsonProperty("payment_provider")
    @JsonView(Views.FullView.class)
    public String getGatewayName() {
        return gatewayName;
    }

    @JsonView(Views.FullView.class)
    public Map<String, String> getCredentials() {
        return credentials;
    }

    @JsonProperty("type")
    @JsonView(Views.FullView.class)
    public String getType() {
        return type.value;
    }

    @JsonProperty("service_name")
    @JsonView(value = {Views.FullView.class, Views.PartialView.class})
    public String getServiceName() {
        return serviceName;
    }

    @JsonView(Views.PartialView.class)
    @JsonProperty("card_types")
    public List<CardTypeEntity> getCardTypes() {
        return cardTypes;
    }

    @JsonView(Views.FullView.class)
    public EmailNotificationEntity getEmailNotification() {
        return emailNotification;
    }

    @JsonView(Views.FullView.class)
    public NotificationCredentials getNotificationCredentials() {
        return notificationCredentials;
    }

    public void setNotificationCredentials(NotificationCredentials notificationCredentials) {
        this.notificationCredentials = notificationCredentials;
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

    public void setEmailNotification(EmailNotificationEntity emailNotification) {
        this.emailNotification = emailNotification;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Map<String, String> withoutCredentials() {
        return ImmutableMap.of(
                "gateway_account_id", String.valueOf(super.getId()),
                "payment_provider", gatewayName,
                "type", getType());
    }

    public boolean hasEmailNotificationsEnabled() {
        return emailNotification != null && emailNotification.isEnabled();
    }
}
