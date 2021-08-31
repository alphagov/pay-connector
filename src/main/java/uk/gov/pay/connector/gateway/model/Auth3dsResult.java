package uk.gov.pay.connector.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class Auth3dsResult implements AuthorisationDetails {

    public enum Auth3dsResultOutcome {
        AUTHORISED, DECLINED, ERROR, CANCELED
    }

    private String paResponse;

    private Auth3dsResultOutcome auth3dsResultOutcome;

    private String md;

    private String threeDsVersion;

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

    public String getThreeDsVersion() {
        return threeDsVersion;
    }

    @JsonProperty("three_ds_version")
    public void setThreeDsVersion(String threeDsVersion) {
        this.threeDsVersion = threeDsVersion;
    }

    @Override
    public String toString() {
        return "Auth3dsDetails{" +
                "paResponse='" + paResponse + '\'' +
                ", auth3DsResult=" + auth3dsResultOutcome +
                ", md='" + md + '\'' +
                ", threeDsVersion='" + threeDsVersion + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Auth3dsResult that = (Auth3dsResult) o;

        if (!Objects.equals(paResponse, that.paResponse)) return false;
        if (auth3dsResultOutcome != that.auth3dsResultOutcome) return false;
        if (!Objects.equals(threeDsVersion, that.threeDsVersion)) return false;
        return Objects.equals(md, that.md);
    }

    @Override
    public int hashCode() {
        int result = paResponse != null ? paResponse.hashCode() : 0;
        result = 31 * result + (auth3dsResultOutcome != null ? auth3dsResultOutcome.hashCode() : 0);
        result = 31 * result + (md != null ? md.hashCode() : 0);
        return result;
    }
}
