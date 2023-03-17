package uk.gov.pay.connector.idempotency.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.util.JsonToStringObjectMapConverter;
import uk.gov.service.payments.commons.jpa.InstantToUtcTimestampWithoutTimeZoneConverter;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
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
@Table(name = "idempotency")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "idempotency_id_seq",
        sequenceName = "idempotency_id_seq", allocationSize = 1)
public class IdempotencyEntity {
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());
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

    private IdempotencyEntity(String key, GatewayAccountEntity gatewayAccount, String resourceExternalId,
                             Map<String, Object> requestBody, Instant createdDate) {
        this.key = key;
        this.gatewayAccount = gatewayAccount;
        this.resourceExternalId = resourceExternalId;
        this.requestBody = requestBody;
        this.createdDate = createdDate;
    }

    public static IdempotencyEntity from(String key, ChargeCreateRequest chargeCreateRequest,
                                         GatewayAccountEntity gatewayAccount, String resourceExternalId) {
        return new IdempotencyEntity(key, gatewayAccount, resourceExternalId,
                mapper.convertValue(chargeCreateRequest, new TypeReference<>() {}), Instant.now());

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
