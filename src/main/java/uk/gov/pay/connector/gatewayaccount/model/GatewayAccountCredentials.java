package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.service.payments.commons.api.json.IsoInstantMillisecondSerializer;

import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GatewayAccountCredentials {

    @Schema(example = "787460d16d4a4d14b4c94787b8f427db")
    private String externalId;

    @Schema(example = "stripe")
    private String paymentProvider;

    @Schema(oneOf = {WorldpayCredentials.class, StripeCredentials.class, EpdqCredentials.class})
    @JsonSerialize(using = GatewayCredentialsApiSerializer.class)
    private GatewayCredentials credentials;

    @Schema(example = "ACTIVE")
    private GatewayAccountCredentialState state;

    @Schema(example = "vdwke0d16d4a4d14b4c94787b8f427d", description = "User external ID")
    private String lastUpdatedByUserExternalId;

    @JsonSerialize(using = IsoInstantMillisecondSerializer.class)
    @Schema(example = "2022-06-30T15:44:19.323Z")
    private Instant createdDate;

    @JsonSerialize(using = IsoInstantMillisecondSerializer.class)
    @Schema(example = "2022-06-28T16:40:56.869Z")
    private Instant activeStartDate;

    @JsonSerialize(using = IsoInstantMillisecondSerializer.class)
    @Schema(example = " ")
    private Instant activeEndDate;

    @Schema(example = "1")
    private Long gatewayAccountId;

    public GatewayAccountCredentials(GatewayAccountCredentialsEntity entity) {
        this.externalId = entity.getExternalId();
        this.paymentProvider = entity.getPaymentProvider();
        this.state = entity.getState();
        this.lastUpdatedByUserExternalId = entity.getLastUpdatedByUserExternalId();
        this.createdDate = entity.getCreatedDate();
        this.activeStartDate = entity.getActiveStartDate();
        this.activeEndDate = entity.getActiveEndDate();
        this.gatewayAccountId = entity.getGatewayAccountEntity().getId();
        this.credentials = entity.getCredentialsObject();
    }

    public String getExternalId() {
        return externalId;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public GatewayCredentials getCredentials() {
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

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }
}
