package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.paymentprocessor.model.Auth3dsRequiredEntity;

public final class Auth3dsRequiredEntityFixture {
    private String paRequest;
    private String issuerUrl;
    private String htmlOut;
    private String md;
    private String worldpayChallengeAcsUrl;
    private String worldpayChallengeTransactionId;
    private String worldpayChallengePayload;
    private String threeDsVersion;

    private Auth3dsRequiredEntityFixture() {
    }

    public static Auth3dsRequiredEntityFixture anAuth3dsRequiredEntity() {
        return new Auth3dsRequiredEntityFixture();
    }

    public Auth3dsRequiredEntityFixture withPaRequest(String paRequest) {
        this.paRequest = paRequest;
        return this;
    }

    public Auth3dsRequiredEntityFixture withIssuerUrl(String issuerUrl) {
        this.issuerUrl = issuerUrl;
        return this;
    }

    public Auth3dsRequiredEntityFixture withHtmlOut(String htmlOut) {
        this.htmlOut = htmlOut;
        return this;
    }

    public Auth3dsRequiredEntityFixture withMd(String md) {
        this.md = md;
        return this;
    }

    public Auth3dsRequiredEntityFixture withWorldpayChallengeAcsUrl(String worldpayChallengeAcsUrl) {
        this.worldpayChallengeAcsUrl = worldpayChallengeAcsUrl;
        return this;
    }

    public Auth3dsRequiredEntityFixture withWorldpayChallengeTransactionId(String worldpayChallengeTransactionId) {
        this.worldpayChallengeTransactionId = worldpayChallengeTransactionId;
        return this;
    }

    public Auth3dsRequiredEntityFixture withWorldpayChallengePayload(String worldpayChallengePayload) {
        this.worldpayChallengePayload = worldpayChallengePayload;
        return this;
    }

    public Auth3dsRequiredEntityFixture withThreeDsVersion(String threeDsVersion) {
        this.threeDsVersion = threeDsVersion;
        return this;
    }

    public Auth3dsRequiredEntity build() {
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setPaRequest(paRequest);
        auth3dsRequiredEntity.setIssuerUrl(issuerUrl);
        auth3dsRequiredEntity.setHtmlOut(htmlOut);
        auth3dsRequiredEntity.setMd(md);
        auth3dsRequiredEntity.setWorldpayChallengeAcsUrl(worldpayChallengeAcsUrl);
        auth3dsRequiredEntity.setWorldpayChallengeTransactionId(worldpayChallengeTransactionId);
        auth3dsRequiredEntity.setWorldpayChallengePayload(worldpayChallengePayload);
        auth3dsRequiredEntity.setThreeDsVersion(threeDsVersion);
        return auth3dsRequiredEntity;
    }
}
