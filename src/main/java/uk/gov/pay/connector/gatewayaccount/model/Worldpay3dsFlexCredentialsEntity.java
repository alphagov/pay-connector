package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@SequenceGenerator(name="worldpay_3ds_flex_credentials_id_seq",
        sequenceName = "worldpay_3ds_flex_credentials_id_seq", allocationSize = 1)
@Table(name = "worldpay_3ds_flex_credentials")
public class Worldpay3dsFlexCredentialsEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(generator = "worldpay_3ds_flex_credentials_id_seq", strategy = GenerationType.SEQUENCE)
    @Column(name = "id")
    @JsonIgnore
    private Long id;

    @JoinColumn(name = "gateway_account_id", updatable = false, insertable = false)
    @JsonIgnore
    private GatewayAccountEntity gatewayAccountEntity;

    @Column(name = "gateway_account_id")
    @JsonIgnore
    private Long gatewayAccountId;

    @Column
    private String issuer;

    @Column(name = "organisational_unit_id")
    private String organisationalUnitId;

    @Column(name = "jwt_mac_key")
    @JsonIgnore
    private String jwtMacKey;

    @Column(name = "exemption_engine")
    private boolean exemptionEngineEnabled;

    @Column(name = "corporate_exemptions")
    private boolean corporateExemptionEnabled;

    public Worldpay3dsFlexCredentialsEntity() {
        super();
    }
    
    public boolean isExemptionEngineEnabled() {
        return exemptionEngineEnabled;
    }

    public Long getId() { return id; }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getOrganisationalUnitId() {
        return organisationalUnitId;
    }

    public String getJwtMacKey() {
        return jwtMacKey;
    }

    public void setExemptionEngineEnabled(boolean isExemptionEngineEnabled) {
        this.exemptionEngineEnabled = isExemptionEngineEnabled;
    }

    public boolean isCorporateExemptionEnabled() {
        return corporateExemptionEnabled;
    }

    public void setCorporateExemptionEnabled(boolean corporateExemptionEnabled) {
        this.corporateExemptionEnabled = corporateExemptionEnabled;
    }

    public void setGatewayAccountId(Long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public void setOrganisationalUnitId(String organisationalUnitId) {
        this.organisationalUnitId = organisationalUnitId;
    }

    public void setJwtMacKey(String jwtMacKey) {
        this.jwtMacKey = jwtMacKey;
    }

    public GatewayAccountEntity getGatewayAccountEntity() {
        return gatewayAccountEntity;
    }

    public static final class Worldpay3dsFlexCredentialsEntityBuilder {
        private Long gatewayAccountId;
        private String issuer;
        private String organisationalUnitId;
        private String jwtMacKey;
        private boolean exemptionEngine;
        private boolean corporateExemptions;

        private Worldpay3dsFlexCredentialsEntityBuilder() {
        }

        public static Worldpay3dsFlexCredentialsEntityBuilder aWorldpay3dsFlexCredentialsEntity() {
            return new Worldpay3dsFlexCredentialsEntityBuilder();
        }

        public Worldpay3dsFlexCredentialsEntityBuilder withGatewayAccountId(Long gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public Worldpay3dsFlexCredentialsEntityBuilder withIssuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Worldpay3dsFlexCredentialsEntityBuilder withOrganisationalUnitId(String organisationalUnitId) {
            this.organisationalUnitId = organisationalUnitId;
            return this;
        }

        public Worldpay3dsFlexCredentialsEntityBuilder withJwtMacKey(String jwtMacKey) {
            this.jwtMacKey = jwtMacKey;
            return this;
        }

        public Worldpay3dsFlexCredentialsEntityBuilder withExemptionEngine(boolean exemptionEngine) {
            this.exemptionEngine = exemptionEngine;
            return this;
        }

        public Worldpay3dsFlexCredentialsEntityBuilder withCorporateExemptions(boolean corporateExemptions) {
            this.corporateExemptions = corporateExemptions;
            return this;
        }

        public Worldpay3dsFlexCredentialsEntity build() {
            Worldpay3dsFlexCredentialsEntity worldpay3dsFlexCredentialsEntity = new Worldpay3dsFlexCredentialsEntity();
            worldpay3dsFlexCredentialsEntity.jwtMacKey = this.jwtMacKey;
            worldpay3dsFlexCredentialsEntity.gatewayAccountId = this.gatewayAccountId;
            worldpay3dsFlexCredentialsEntity.organisationalUnitId = this.organisationalUnitId;
            worldpay3dsFlexCredentialsEntity.issuer = this.issuer;
            worldpay3dsFlexCredentialsEntity.exemptionEngineEnabled = this.exemptionEngine;
            worldpay3dsFlexCredentialsEntity.corporateExemptionEnabled = this.corporateExemptions;
            return worldpay3dsFlexCredentialsEntity;
        }
    }
}
