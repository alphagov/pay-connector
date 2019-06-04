package uk.gov.pay.connector.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

import java.time.ZonedDateTime;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentCreatedEvent extends Event {

    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    private final ZonedDateTime time;
    private final String paymentId;

    private final Long amount;
    private final String description;
    private final String reference;
    private final String returnUrl;
    private final Long gatewayAccountId;
    private final Boolean isLive;
    private final  String paymentProvider;

    private PaymentCreatedEvent(ZonedDateTime time, 
                               String paymentId, Long amount, String description, 
                               String reference, String returnUrl, Long gatewayAccountId,
                                Boolean isLive, String paymentProvider) {
        this.time = time;
        this.paymentId = paymentId;
        this.amount = amount;
        this.description = description;
        this.reference = reference;
        this.returnUrl = returnUrl;
        this.gatewayAccountId = gatewayAccountId;
        this.isLive = isLive;
        this.paymentProvider = paymentProvider;
    }

    public static PaymentCreatedEvent from(ChargeEntity chargeEntity) {
        return new PaymentCreatedEvent(
                chargeEntity.getCreatedDate(),
                chargeEntity.getExternalId(),
                chargeEntity.getAmount(),
                chargeEntity.getDescription(),
                chargeEntity.getReference().toString(),
                chargeEntity.getReturnUrl(),
                chargeEntity.getGatewayAccount().getId(),
                chargeEntity.getGatewayAccount().isLive(),
                chargeEntity.getGatewayAccount().getGatewayName()
        );
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.PAYMENT;
    }

    @Override
    public String getEventType() {
        return "PaymentCreated";
    }

    public ZonedDateTime getTime() {
        return time;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public Long getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public Boolean isLive() {
        return isLive;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }
}
