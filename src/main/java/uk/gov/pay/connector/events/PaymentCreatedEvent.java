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
    public ZonedDateTime time;
    public String paymentId;
    
    public Long amount;
    public String description;
    public String reference;
    public String returnUrl;

    private PaymentCreatedEvent(ZonedDateTime time, 
                               String paymentId, Long amount, String description, 
                               String reference, String returnUrl) {
        this.time = time;
        this.paymentId = paymentId;
        this.amount = amount;
        this.description = description;
        this.reference = reference;
        this.returnUrl = returnUrl;
    }

    public static PaymentCreatedEvent from(ChargeEntity chargeEntity) {
        return new PaymentCreatedEvent(
                chargeEntity.getCreatedDate(),
                chargeEntity.getExternalId(),
                chargeEntity.getAmount(),
                chargeEntity.getDescription(),
                chargeEntity.getReference().toString(),
                chargeEntity.getReturnUrl()
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
}
