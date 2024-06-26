package uk.gov.pay.connector.agreement.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.agreement.model.builder.AgreementResponseBuilder;
import uk.gov.service.payments.commons.api.json.ApiResponseInstantSerializer;

import java.time.Instant;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public record AgreementResponse (
        @JsonProperty("agreement_id")
        @Schema(example = "iaouobo39hiv0m2560q45j3p04")
        String agreementId,

        @JsonProperty("created_date")
        @JsonSerialize(using = ApiResponseInstantSerializer.class)
        @Schema(example = "2022-06-27T13:07:57.580Z")
        Instant createdDate,

        @JsonProperty
        @Schema(example = "Service agreement reference")
        String reference,

        @JsonProperty
        @Schema(example = "Description for the paying user describing the purpose of the agreement")
        String description,

        @JsonProperty("user_identifier")
        @Schema(example = "reference for the paying user")
        String userIdentifier,

        @JsonProperty("service_id")
        @Schema(example = "Service external ID")
        String serviceId,

        @JsonProperty
        boolean live
) {

    public AgreementResponse(AgreementResponseBuilder agreementResponseBuilder) {
        this(agreementResponseBuilder.getAgreementId(),
            agreementResponseBuilder.getCreatedDate(),
            agreementResponseBuilder.getReference(),
            agreementResponseBuilder.getDescription(),
            agreementResponseBuilder.getUserIdentifier(),
            agreementResponseBuilder.getServiceId(),
            agreementResponseBuilder.isLive()
        );
    }
    
    public boolean isLive() {
        return live;
    }

    public static AgreementResponse from(AgreementEntity agreementEntity) {
        return new AgreementResponse(
                agreementEntity.getExternalId(),
                agreementEntity.getCreatedDate(),
                agreementEntity.getReference(),
                agreementEntity.getDescription(),
                agreementEntity.getUserIdentifier(),
                agreementEntity.getServiceId(),
                agreementEntity.isLive()
        );
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
