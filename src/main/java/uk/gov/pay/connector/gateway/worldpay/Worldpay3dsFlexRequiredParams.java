package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.card.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;

import java.util.Objects;

public class Worldpay3dsFlexRequiredParams implements Gateway3dsRequiredParams {
    
    private final String challengeAcsUrl;
    private final String challengeTransactionId;
    private final String challengePayload;
    private final String threeDsVersion;

    public Worldpay3dsFlexRequiredParams(String challengeAcsUrl,
                                         String challengeTransactionId,
                                         String challengePayload,
                                         String threeDsVersion) {
        this.challengeAcsUrl = Objects.requireNonNull(challengeAcsUrl);
        this.challengeTransactionId = Objects.requireNonNull(challengeTransactionId);
        this.challengePayload = Objects.requireNonNull(challengePayload);
        this.threeDsVersion = Objects.requireNonNull(threeDsVersion);
    }

    @Override
    public Auth3dsRequiredEntity toAuth3dsRequiredEntity() {
        var auth3dsDetailsEntity = new Auth3dsRequiredEntity();
        auth3dsDetailsEntity.setWorldpayChallengeAcsUrl(challengeAcsUrl);
        auth3dsDetailsEntity.setWorldpayChallengeTransactionId(challengeTransactionId);
        auth3dsDetailsEntity.setWorldpayChallengePayload(challengePayload);
        auth3dsDetailsEntity.setThreeDsVersion(threeDsVersion);
        return auth3dsDetailsEntity;
    }
}
