package uk.gov.pay.connector.charge.model.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Auth3dsRequiredEntity {

    @Column(name = "pa_request_3ds")
    private String paRequest;

    @Column(name = "issuer_url_3ds")
    private String issuerUrl;

    @Column(name = "html_out_3ds")
    private String htmlOut;

    @Column(name = "md_3ds")
    private String md;
    
    @Column(name = "worldpay_challenge_acs_url_3ds")
    private String worldpayChallengeAcsUrl;
    
    @Column(name = "worldpay_challenge_transaction_id_3ds")
    private String worldpayChallengeTransactionId;
    
    @Column(name = "worldpay_challenge_payload_3ds")
    private String worldpayChallengePayload;
    
    @Column(name = "version_3ds")
    private String threeDsVersion;

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

    public String getWorldpayChallengeAcsUrl() {
        return worldpayChallengeAcsUrl;
    }

    public void setWorldpayChallengeAcsUrl(String worldpayChallengeAcsUrl) {
        this.worldpayChallengeAcsUrl = worldpayChallengeAcsUrl;
    }

    public String getWorldpayChallengeTransactionId() {
        return worldpayChallengeTransactionId;
    }

    public void setWorldpayChallengeTransactionId(String worldpayChallengeTransactionId) {
        this.worldpayChallengeTransactionId = worldpayChallengeTransactionId;
    }

    public String getWorldpayChallengePayload() {
        return worldpayChallengePayload;
    }

    public void setWorldpayChallengePayload(String worldpayChallengePayload) {
        this.worldpayChallengePayload = worldpayChallengePayload;
    }

    public String getThreeDsVersion() {
        return threeDsVersion;
    }

    public void setThreeDsVersion(String threeDsVersion) {
        this.threeDsVersion = threeDsVersion;
    }
    
    
}
