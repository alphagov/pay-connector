package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Auth3dsDetails implements AuthorisationDetails {

    public enum Auth3DResult {
        AUTHORISED, DECLINED, ERROR
    }

    private String paResponse;

    private Auth3DResult auth3DResult;

    @JsonProperty("pa_response")
    public void setPaResponse(String paResponse) {
            this.paResponse = paResponse;
        }

    public String getPaResponse() {
            return paResponse;
        }

    public Auth3DResult getAuth3DResult() {
        return auth3DResult;
    }

    @JsonProperty("auth_3d_result")
    public void setAuth3DResult(String auth3DResult) {
        this.auth3DResult = auth3DResult == null ? null : Auth3DResult.valueOf(auth3DResult);
    }
}
