package uk.gov.pay.connector.agreement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgreementCancelRequest {
    private String userExternalId;
    private String userEmail;

    public AgreementCancelRequest() {
    }

    public AgreementCancelRequest(String userExternalId, String userEmail) {
        this.userExternalId = userExternalId;
        this.userEmail = userEmail;
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    public String getUserEmail() {
        return userEmail;
    }
}
