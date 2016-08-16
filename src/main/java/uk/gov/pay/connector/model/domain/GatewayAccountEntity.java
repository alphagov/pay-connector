package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import javax.persistence.*;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "gateway_accounts")
@SequenceGenerator(name="gateway_accounts_gateway_account_id_seq", sequenceName="gateway_accounts_gateway_account_id_seq", allocationSize=1)
public class GatewayAccountEntity extends AbstractEntity {

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
    public Long getId() {
        return super.getId();
    }

    @JsonProperty("payment_provider")
    public String getGatewayName() {
        return gatewayName;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    @JsonProperty("type")
    public String getType() {
        return type.value;
    }

    @JsonProperty("service_name")
    public String getServiceName() {
        return serviceName;
    }

    public List<CardTypeEntity> getCardTypes() {
        return cardTypes;
    }

    public EmailNotificationEntity getEmailNotification() {
        return emailNotification;
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
