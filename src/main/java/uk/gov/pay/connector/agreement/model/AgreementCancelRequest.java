package uk.gov.pay.connector.agreement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgreementCancelRequest (
        String userExternalId,
        String userEmail
) {

    public AgreementCancelRequest() {
        this(null, null);
    }
}
