package uk.gov.pay.connector.gatewayaccountcredentials.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.persistence.annotations.Customizer;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.common.model.domain.HistoryCustomizer;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.EpdqCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.SandboxCredentials;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.util.JsonToStringObjectMapConverter;
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
import java.util.Optional;

@Entity
@Table(name = "gateway_account_credentials")
@SequenceGenerator(name = "gateway_account_credentials_id_seq",
        sequenceName = "gateway_account_credentials_id_seq", allocationSize = 1)
@Customizer(HistoryCustomizer.class)
public class GatewayAccountCredentialsEntity extends AbstractVersionedEntity {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_account_credentials_id_seq")
    private Long id;

    @Column(name = "payment_provider")
    private String paymentProvider;

    @Column(name = "credentials", columnDefinition = "json")
    @Convert(converter = JsonToStringObjectMapConverter.class)
    private Map<String, Object> credentials;

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
    private GatewayAccountEntity gatewayAccountEntity;

    @Column(name = "external_id")
    private String externalId;
    
    public GatewayAccountCredentialsEntity() {
    }

    public GatewayAccountCredentialsEntity(GatewayAccountEntity gatewayAccountEntity, String paymentProvider,
                                           Map<String, Object> credentials, GatewayAccountCredentialState state) {
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

    /**
     * @deprecated Use {@link GatewayAccountCredentialsEntity#getCredentialsObject()} instead
     */
    public Map<String, Object> getCredentials() {
        return credentials;
    }
    
    public GatewayCredentials getCredentialsObject() {
        Map<String, Object> credentialsMap = Optional.ofNullable(credentials).orElse(Map.of());
        switch (PaymentGatewayName.valueFrom(paymentProvider)) {
            case WORLDPAY:
                return objectMapper.convertValue(credentialsMap, WorldpayCredentials.class);
            case STRIPE:
                return objectMapper.convertValue(credentialsMap, StripeCredentials.class);
            case EPDQ:
                return objectMapper.convertValue(credentialsMap, EpdqCredentials.class);
            case SANDBOX:
                return objectMapper.convertValue(credentialsMap, SandboxCredentials.class);
            default:
                throw new IllegalArgumentException("Unsupported payment provider: " + paymentProvider);
        }
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

    public String getExternalId() {
        return externalId;
    }
    
    public Long getGatewayAccountId() {
        return gatewayAccountEntity.getId();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setActiveStartDate(Instant activeStartDate) {
        this.activeStartDate = activeStartDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public void setActiveEndDate(Instant activeEndDate) {
        this.activeEndDate = activeEndDate;
    }

    /**
     * @deprecated Use {@link GatewayAccountCredentialsEntity#setCredentials(GatewayCredentials)} ()} instead
     */
    public void setCredentials(Map<String, Object> credentials) {
        this.credentials = credentials;
    }

    public void setCredentials(GatewayCredentials credentials) {
        this.credentials = objectMapper.convertValue(credentials, new TypeReference<Map<String, Object>>() {});
    }

    public void setState(GatewayAccountCredentialState state) {
        this.state = state;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setLastUpdatedByUserExternalId(String lastUpdatedByUserExternalId) {
        this.lastUpdatedByUserExternalId = lastUpdatedByUserExternalId;
    }
}
