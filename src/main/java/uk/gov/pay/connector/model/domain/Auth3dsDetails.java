package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Auth3dsDetails implements AuthorisationDetails {

    private String paResponse;

    @JsonProperty("pa_response")
    public void setPaResponse(String paResponse) {
            this.paResponse = paResponse;
        }

    public String getPaResponse() {
            return paResponse;
        }

}
