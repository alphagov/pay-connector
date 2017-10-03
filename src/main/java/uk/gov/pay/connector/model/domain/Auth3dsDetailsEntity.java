package uk.gov.pay.connector.model.domain;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class Auth3dsDetailsEntity {

    @Column(name = "pa_request")
    private String paRequest;

    @Column(name = "issuer_url")
    private String issuerUrl;

    @Column(name = "worldpay_cookie_value")
    private String worldpayCookieValue;

    public void setWorldpayCookieValue(String worldpayCookieValue) {
        this.worldpayCookieValue = worldpayCookieValue;
    }

    public String getPaRequest() {
        return paRequest;
    }

    public String getWorldpayCookieValue() {
        return worldpayCookieValue;
    }

    public void setPaRequest(String paRequest) {
        this.paRequest = paRequest;
    }

    public String getIssuerUrl() {
        return issuerUrl;
    }

    public void setIssuerUrl(String issuerUrl) {
        this.issuerUrl = issuerUrl;
    }

    public Auth3dsDetailsEntity() {
    }

    public Auth3dsDetailsEntity(String paRequest, String issuerUrl,
        String worldpayCookieValue) {
        this.paRequest = paRequest;
        this.issuerUrl = issuerUrl;
        this.worldpayCookieValue = worldpayCookieValue;
    }
}
