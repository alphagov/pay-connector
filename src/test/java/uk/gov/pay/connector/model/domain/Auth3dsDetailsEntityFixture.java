package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;

public final class Auth3dsDetailsEntityFixture {
    private String paRequest;
    private String issuerUrl;
    private String htmlOut;
    private String md;
    private String worldpayChallengeAcsUrl;
    private String worldpayChallengeTransactionId;
    private String worldpayChallengePayload;
    private String threeDsVersion;

    private Auth3dsDetailsEntityFixture() {
    }

    public static Auth3dsDetailsEntityFixture anAuth3dsDetailsEntity() {
        return new Auth3dsDetailsEntityFixture();
    }

    public Auth3dsDetailsEntityFixture withPaRequest(String paRequest) {
        this.paRequest = paRequest;
        return this;
    }

    public Auth3dsDetailsEntityFixture withIssuerUrl(String issuerUrl) {
        this.issuerUrl = issuerUrl;
        return this;
    }

    public Auth3dsDetailsEntityFixture withHtmlOut(String htmlOut) {
        this.htmlOut = htmlOut;
        return this;
    }

    public Auth3dsDetailsEntityFixture withMd(String md) {
        this.md = md;
        return this;
    }

    public Auth3dsDetailsEntityFixture withWorldpayChallengeAcsUrl(String worldpayChallengeAcsUrl) {
        this.worldpayChallengeAcsUrl = worldpayChallengeAcsUrl;
        return this;
    }

    public Auth3dsDetailsEntityFixture withWorldpayChallengeTransactionId(String worldpayChallengeTransactionId) {
        this.worldpayChallengeTransactionId = worldpayChallengeTransactionId;
        return this;
    }

    public Auth3dsDetailsEntityFixture withWorldpayChallengePayload(String worldpayChallengePayload) {
        this.worldpayChallengePayload = worldpayChallengePayload;
        return this;
    }

    public Auth3dsDetailsEntityFixture withThreeDsVersion(String threeDsVersion) {
        this.threeDsVersion = threeDsVersion;
        return this;
    }

    public Auth3dsDetailsEntity build() {
        Auth3dsDetailsEntity auth3dsDetailsEntity = new Auth3dsDetailsEntity();
        auth3dsDetailsEntity.setPaRequest(paRequest);
        auth3dsDetailsEntity.setIssuerUrl(issuerUrl);
        auth3dsDetailsEntity.setHtmlOut(htmlOut);
        auth3dsDetailsEntity.setMd(md);
        auth3dsDetailsEntity.setWorldpayChallengeAcsUrl(worldpayChallengeAcsUrl);
        auth3dsDetailsEntity.setWorldpayChallengeTransactionId(worldpayChallengeTransactionId);
        auth3dsDetailsEntity.setWorldpayChallengePayload(worldpayChallengePayload);
        auth3dsDetailsEntity.setThreeDsVersion(threeDsVersion);
        return auth3dsDetailsEntity;
    }
}
