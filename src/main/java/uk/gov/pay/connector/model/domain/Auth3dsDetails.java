package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Auth3dsDetails implements AuthorisationDetails {

    public enum Auth3dsResult {
        AUTHORISED, DECLINED, ERROR
    }

    private String paResponse;

    private Auth3dsResult auth3DsResult;

    @JsonProperty("pa_response")
    public void setPaResponse(String paResponse) {
            this.paResponse = paResponse;
        }

    public String getPaResponse() {
            return paResponse;
        }

    public Auth3dsResult getAuth3DsResult() {
        return auth3DsResult;
    }

    @JsonProperty("auth_3ds_result")
    public void setAuth3dsResult(String auth3dsResult) {
        this.auth3DsResult = auth3dsResult == null ? Auth3dsResult.ERROR : Auth3dsResult.valueOf(auth3dsResult);
    }
}
