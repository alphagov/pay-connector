package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Entity
@Table(name = "charges")
@SequenceGenerator(name = "charges_charge_id_seq", sequenceName = "charges_charge_id_seq", allocationSize = 1)
public class ChargeEntity extends AbstractEntity {

    @JsonProperty
    @Column(name = "amount")
    private Long amount;

    @JsonProperty
    @Column(name = "status")
    private String status;

    @JsonProperty("gateway_transaction_id")
    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @JsonProperty("return_url")
    @Column(name = "return_url")
    private String returnUrl;


    //TODO: Need to revisit if GatewayAccountEntity is better suited here as composite object
    @JsonProperty("gateway_account_id")
    @Column(name = "gateway_account_id")
    private Long gatewayAccountId;

    @JsonProperty
    @Column(name = "description")
    private String description;

    @JsonProperty
    @Column(name = "reference")
    private String reference;

    @JsonProperty("created_date")
    @Column(name = "created_date")
    @Convert( converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    protected ChargeEntity() {
        //for jpa
    }

    public ChargeEntity(Long amount, String status, String gatewayTransactionId, String returnUrl, String description, String reference, Long gatewayAccountId) {
        this.amount = amount;
        this.status = status;
        this.gatewayTransactionId = gatewayTransactionId;
        this.returnUrl = returnUrl;
        this.description = description;
        this.reference = reference;
        this.gatewayAccountId = gatewayAccountId;
        this.createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
    }

    public Long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }
}
