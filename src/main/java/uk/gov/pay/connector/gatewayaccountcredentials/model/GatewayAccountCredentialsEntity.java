package uk.gov.pay.connector.gatewayaccountcredentials.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.util.CredentialsConverter;
import uk.gov.service.payments.commons.jpa.InstantToUtcTimestampWithoutTimeZoneConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "gateway_account_credentials")
@SequenceGenerator(name = "gateway_account_credentials_id_seq",
        sequenceName = "gateway_account_credentials_id_seq", allocationSize = 1)
public class GatewayAccountCredentialsEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_account_credentials_id_seq")
    @JsonIgnore
    private Long id;

    @Column(name = "payment_provider")
    private String paymentProvider;

    @Column(name = "credentials", columnDefinition = "json")
    @Convert(converter = CredentialsConverter.class)
    private Map<String, String> credentials;

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    private GatewayAccountCredentialState state;

    @Column(name = "last_updated_by_user_external_id")
    private String lastUpdatedByUserExternalId;

    @Column(name = "created_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant createdDate;

    @Column(name = "active_start_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant activeStartDate;

    @Column(name = "active_end_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant activeEndDate;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", nullable = false)
    @JsonIgnore
    private GatewayAccountEntity gatewayAccountEntity;

    public GatewayAccountCredentialsEntity() {
    }

    public GatewayAccountCredentialsEntity(GatewayAccountEntity gatewayAccountEntity, String paymentProvider,
                                           Map<String, String> credentials, GatewayAccountCredentialState state) {
        this.paymentProvider = paymentProvider;
        this.gatewayAccountEntity = gatewayAccountEntity;
        this.credentials = credentials;
        this.state = state;
        this.createdDate = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    public GatewayAccountCredentialState getState() {
        return state;
    }

    public String getLastUpdatedByUserExternalId() {
        return lastUpdatedByUserExternalId;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public Instant getActiveStartDate() {
        return activeStartDate;
    }

    public Instant getActiveEndDate() {
        return activeEndDate;
    }

    public GatewayAccountEntity getGatewayAccountEntity() {
        return gatewayAccountEntity;
    }

    public void setActiveStartDate(Instant activeStartDate) {
        this.activeStartDate = activeStartDate;
    }

    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials;
    }

    public void setState(GatewayAccountCredentialState state) {
        this.state = state;
    }
}
