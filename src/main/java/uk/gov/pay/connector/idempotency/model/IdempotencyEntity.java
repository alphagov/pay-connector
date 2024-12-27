package uk.gov.pay.connector.idempotency.model;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.util.JsonToStringObjectMapConverter;
import uk.gov.service.payments.commons.jpa.InstantToUtcTimestampWithoutTimeZoneConverter;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "idempotency")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "idempotency_id_seq",
        sequenceName = "idempotency_id_seq", allocationSize = 1)
public class IdempotencyEntity {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "idempotency_id_seq")
    private Long id;

    @Column(name = "key", nullable = false)
    private String key;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", updatable = false, nullable = false)
    private GatewayAccountEntity gatewayAccount;

    @Column(name = "resource_external_id", nullable = false)
    private String resourceExternalId;

    @Column(name = "request_body", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = JsonToStringObjectMapConverter.class)
    private Map<String, Object> requestBody;

    @Column(name = "created_date", nullable = false)
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant createdDate;

    public IdempotencyEntity() {
        // For JPA
    }

    public IdempotencyEntity(String key, GatewayAccountEntity gatewayAccount, String resourceExternalId,
                              Map<String, Object> requestBody, Instant createdDate) {
        this.key = key;
        this.gatewayAccount = gatewayAccount;
        this.resourceExternalId = resourceExternalId;
        this.requestBody = requestBody;
        this.createdDate = createdDate;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    public void setGatewayAccount(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
    }

    public String getResourceExternalId() {
        return resourceExternalId;
    }

    public void setResourceExternalId(String resourceExternalId) {
        this.resourceExternalId = resourceExternalId;
    }

    public Map<String, Object> getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(Map<String, Object> requestBody) {
        this.requestBody = requestBody;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }
}
