package uk.gov.pay.connector.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Auth3dsDetails implements AuthorisationDetails {

    public enum Auth3dsResultOutcome {
        AUTHORISED, DECLINED, ERROR, CANCELED
    }

    private String paResponse;

    private Auth3dsResultOutcome auth3dsResultOutcome;

    private String md;

    @JsonProperty("pa_response")
    public void setPaResponse(String paResponse) {
            this.paResponse = paResponse;
        }

    public String getPaResponse() {
            return paResponse;
        }

    public Auth3dsResultOutcome getAuth3dsResultOutcome() {
        return auth3dsResultOutcome;
    }

    @JsonProperty("auth_3ds_result")
    public void setAuth3dsResult(String auth3dsResult) {
        this.auth3dsResultOutcome = auth3dsResult == null ? Auth3dsResultOutcome.ERROR : Auth3dsResultOutcome.valueOf(auth3dsResult);
    }

    @JsonProperty("md")
    public void setMd(String md) {
        this.md = md;
    }

    public String getMd() {
        return this.md;
    }

    @Override
    public String toString() {
        return "Auth3dsDetails{" +
                "paResponse='" + paResponse + '\'' +
                ", auth3DsResult=" + auth3dsResultOutcome +
                ", md='" + md + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Auth3dsDetails that = (Auth3dsDetails) o;

        if (paResponse != null ? !paResponse.equals(that.paResponse) : that.paResponse != null) return false;
        if (auth3dsResultOutcome != that.auth3dsResultOutcome) return false;
        return md != null ? md.equals(that.md) : that.md == null;
    }

    @Override
    public int hashCode() {
        int result = paResponse != null ? paResponse.hashCode() : 0;
        result = 31 * result + (auth3dsResultOutcome != null ? auth3dsResultOutcome.hashCode() : 0);
        result = 31 * result + (md != null ? md.hashCode() : 0);
        return result;
    }
}
