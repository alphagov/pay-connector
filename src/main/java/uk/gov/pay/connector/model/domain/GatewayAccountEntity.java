package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import javax.persistence.*;
import java.util.Map;

@Entity
@Table(name = "gateway_accounts")
@SequenceGenerator(name="gateway_accounts_gateway_account_id_seq", sequenceName="gateway_accounts_gateway_account_id_seq", allocationSize=1)
public class GatewayAccountEntity extends AbstractEntity {
    public static final String CREDENTIALS_MERCHANT_ID = "merchant_id";
    public static final String CREDENTIALS_USERNAME = "username";
    public static final String CREDENTIALS_PASSWORD = "password";

    protected GatewayAccountEntity() {
    }

    //TODO: Should we rename the columns to be more consistent?
    @Column(name = "payment_provider")
    private String gatewayName;

    //TODO: Revisit this to map to a java.util.Map
    @Column(name = "credentials", columnDefinition = "json")
    @Convert( converter = CredentialsConverter.class)
    private Map<String, String> credentials;

    public GatewayAccountEntity(String gatewayName, Map<String, String> credentials) {
        this.gatewayName = gatewayName;
        this.credentials = credentials;
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

    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
    }

    public Map<String, String> withoutCredentials() {
        return ImmutableMap.of(
                "gateway_account_id", String.valueOf(super.getId()),
                "payment_provider", gatewayName);
    }
}
