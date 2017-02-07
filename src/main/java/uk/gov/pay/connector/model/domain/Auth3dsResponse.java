package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Auth3dsResponse implements GatewayAuthRequest {

    private String paResponse;

    @JsonProperty("pa_response")
    public void setPaRequest(String paResponse) {
            this.paResponse = paResponse;
        }

    public String getPaResponse() {
            return paResponse;
        }

}
