package uk.gov.pay.connector.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.hibernate.validator.constraints.Length;
import uk.gov.pay.commons.api.json.ApiResponseDateTimeSerializer;
import uk.gov.pay.commons.api.json.ExternalMetadataDeserialiser;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.SupportedLanguageJsonDeserializer;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.PrefilledCardHolderDetails;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
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

    PaymentCreatedEvent() {}

    public PaymentCreatedEvent(ZonedDateTime time, 
                               String paymentId, Long amount, String description, 
                               String reference, String returnUrl) {
        this.time = time;
        this.paymentId = paymentId;
        this.amount = amount;
        this.description = description;
        this.reference = reference;
        this.returnUrl = returnUrl;
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
