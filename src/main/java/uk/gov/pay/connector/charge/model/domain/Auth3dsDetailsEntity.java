package uk.gov.pay.connector.charge.model.domain;

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

    @Column(name = "md_3ds")
    private String md;

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

    public void setMd(String md) {
        this.md = md;
    }

    public String getMd() {
        return md;
    }
}
