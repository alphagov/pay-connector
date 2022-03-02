package uk.gov.pay.connector.agreement.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.agreement.model.builder.AgreementResponseBuilder;
import uk.gov.service.payments.commons.api.json.ApiResponseInstantSerializer;

import java.time.Instant;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class AgreementResponse {

    @JsonProperty("agreement_id")
    private String agreementId;

    @JsonProperty("created_date")
    @JsonSerialize(using = ApiResponseInstantSerializer.class)
    private Instant createdDate;

    @JsonProperty
    private String reference;

    @JsonProperty("service_id")
    private String serviceId;

    @JsonProperty
    private boolean live;

    public AgreementResponse(AgreementResponseBuilder agreementResponseBuilder) {
        this.agreementId = agreementResponseBuilder.getAgreementId();
        this.createdDate = agreementResponseBuilder.getCreatedDate();
        this.reference = agreementResponseBuilder.getReference();
        this.serviceId = agreementResponseBuilder.getServiceId();
        this.live = agreementResponseBuilder.isLive();
    }
    
    public String getAgreementId() {
        return agreementId;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public String getReference() {
        return reference;
    }

    public String getServiceId() {
        return serviceId;
    }

    public boolean isLive() {
        return live;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgreementResponse that = (AgreementResponse) o;
        return live == that.live && agreementId.equals(that.agreementId) && createdDate.equals(that.createdDate) && reference.equals(that.reference) && serviceId.equals(that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agreementId, createdDate, reference, serviceId, live);
    }

    @Override
    public String toString() {
        // The reference may include personally-identifiable information
        return "AgreementResponse{" +
                "agreementId='" + agreementId + '\'' +
                ", createdDate=" + createdDate +
                ", serviceId='" + serviceId + '\'' +
                ", live=" + live +
                '}';
    }

    
    
}
