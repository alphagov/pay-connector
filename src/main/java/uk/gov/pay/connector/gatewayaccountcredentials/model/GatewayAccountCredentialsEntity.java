package uk.gov.pay.connector.gatewayaccountcredentials.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import org.eclipse.persistence.annotations.Customizer;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.util.JsonToStringObjectMapConverter;
import uk.gov.pay.connector.common.model.domain.HistoryCustomizer;
import uk.gov.service.payments.commons.api.json.ApiResponseInstantSerializer;
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
import javax.persistence.Transient;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static java.util.Map.entry;

@Entity
@Table(name = "gateway_account_credentials")
@SequenceGenerator(name = "gateway_account_credentials_id_seq",
        sequenceName = "gateway_account_credentials_id_seq", allocationSize = 1)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Customizer(HistoryCustomizer.class)
public class GatewayAccountCredentialsEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gateway_account_credentials_id_seq")
    private Long id;

    @Column(name = "payment_provider")
    @Schema(example = "stripe")
    private String paymentProvider;

    @Column(name = "credentials", columnDefinition = "json")
    @Convert(converter = JsonToStringObjectMapConverter.class)
    @Schema(example = "{" +
            "                \"stripe_account_id\": \"an-id\"" +
            "            }")
    private Map<String, Object> credentials;

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    @Schema(example = "ACTIVE")
    private GatewayAccountCredentialState state;

    @Column(name = "last_updated_by_user_external_id")
    private String lastUpdatedByUserExternalId;

    @Column(name = "created_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    @Schema(example = "2022-05-27T09:17:19.162Z")
    private Instant createdDate;

    @Column(name = "active_start_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    @Schema(example = "2022-05-27T09:17:19.162Z")
    private Instant activeStartDate;

    @Column(name = "active_end_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant activeEndDate;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", nullable = false)
    @JsonIgnore
    private GatewayAccountEntity gatewayAccountEntity;

    @Column(name = "external_id")
    @Schema(example = "731193f990064e698ca1b89775b70bcc")
    private String externalId;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @JsonProperty("gateway_account_credential_id")
    @Schema(example = "11")
    public Long getId() {
        return id;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public Map<String, Object> getCredentials() {
        return credentials;
    }

    public GatewayAccountCredentialState getState() {
        return state;
    }

    public String getLastUpdatedByUserExternalId() {
        return lastUpdatedByUserExternalId;
    }

    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    public Instant getCreatedDate() {
        return createdDate;
    }

    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    public Instant getActiveStartDate() {
        return activeStartDate;
    }

    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    public Instant getActiveEndDate() {
        return activeEndDate;
    }

    public GatewayAccountEntity getGatewayAccountEntity() {
        return gatewayAccountEntity;
    }

    public String getExternalId() {
        return externalId;
    }

    @JsonProperty("gateway_account_id")
    @Schema(example = "1")
    public Long getGatewayAccountId() {
        return gatewayAccountEntity.getId();
    }

    @Transient
    Map<PaymentGatewayName, Class<? extends GatewayCredentials>> gatewayCredentialsMap = Map.ofEntries(
            entry(PaymentGatewayName.WORLDPAY, WorldpayCredentials.class)
    );

    // This would replace `getCredentials()` when we want to reason about all of the code using this
    public GatewayCredentials getCredentialsAsInterface() {
        return objectMapper.convertValue(this.getCredentials(), gatewayCredentialsMap.get(PaymentGatewayName.valueFrom(paymentProvider)));
    }

    public void setCredentialsAsInterface(GatewayCredentials credentials) {
        this.credentials = objectMapper.convertValue(credentials, new TypeReference<Map<String, Object>>() {});
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

    public void setCredentials(Map<String, Object> credentials) {
        this.credentials = credentials;
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
