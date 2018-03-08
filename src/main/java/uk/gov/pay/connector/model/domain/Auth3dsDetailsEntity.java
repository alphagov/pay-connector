package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.model.Auth3dsDetailsDTO;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class Auth3dsDetailsEntity {

    @Column(name = "pa_request_3ds")
    private String paRequest;

    @Column(name = "issuer_url_3ds")
    private String issuerUrl;

    @Column(name = "html_out_3ds")
    private String htmlOut;

    // needed for JPA
    public Auth3dsDetailsEntity(){}

    public Auth3dsDetailsEntity(Auth3dsDetailsDTO auth3dsDetails) {
        this.issuerUrl =auth3dsDetails.issuerUrl;
        this.paRequest = auth3dsDetails.paRequest;
        this.htmlOut = auth3dsDetails.htmlOut;
    }

    public String getPaRequest() {
        return paRequest;
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

    public String getHtmlOut() {
        return htmlOut;
    }

    public void setHtmlOut(String htmlOut) {
        this.htmlOut = htmlOut;
    }
}
