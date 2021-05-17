package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.*;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.gatewayaccount.util.CredentialsConverter;

import javax.persistence.*;
import java.util.Map;

@Entity
@Table(name = "gateway_accounts_credentials")
@SequenceGenerator(name = "gateway_accounts_credentials_id_seq",
        sequenceName = "gateway_accounts_credentials_id_seq", allocationSize = 1)
public class GatewayAccountCredentialsEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_accounts_credentials_id_seq")
    @JsonIgnore
    private Long id;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false)
    @JsonIgnore
    private GatewayAccountEntity accountEntity;

    @Column(name = "payment_provider")
    private String gatewayName;

    @Column(name = "credentials", columnDefinition = "json")
    @Convert(converter = CredentialsConverter.class)
    private Map<String, String> credentials;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private GatewayAccountCredentialsRole role;

    public GatewayAccountCredentialsEntity() {
    }

    public GatewayAccountCredentialsEntity(String gatewayName, Map<String, String> credentials, GatewayAccountCredentialsRole role) {
        this.gatewayName = gatewayName;
        this.credentials = credentials;
        this.role = role;
    }

    @JsonProperty("gateway_account_id")
//    @JsonView({Views.ApiView.class, Views.FrontendView.class})
    public Long getId() {
        return this.id;
    }

    @JsonProperty("payment_provider")
//    @JsonView(value = {Views.ApiView.class, Views.FrontendView.class})
    public String getGatewayName() {
        return gatewayName;
    }
    
//    @JsonView(Views.ApiView.class)
    public Map<String, String> getCredentials() {
        return credentials;
    }

    @JsonProperty("role")
//    @JsonView(value = {Views.ApiView.class, Views.FrontendView.class})
    public GatewayAccountCredentialsRole getRole() {
        return role;
    }
    
    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
    }
    
    public void setRole(GatewayAccountCredentialsRole role) {
        this.role = role;
    }

    public void setId(Long id) {
        this.id = id;
    }

//    public class Views {
//        public class ApiView {
//        }
//
//        public class FrontendView {
//        }
//    }
}
