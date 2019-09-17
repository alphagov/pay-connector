package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;

import java.util.Objects;

public class WorldpayParamsFor3dsFlex implements GatewayParamsFor3ds {
    
    private final String challengeAcsUrl;
    private final String challengeTransactionId;
    private final String challengePayload;
    private final String threeDsVersion;

    public WorldpayParamsFor3dsFlex(String challengeAcsUrl,
                                    String challengeTransactionId,
                                    String challengePayload,
                                    String threeDsVersion) {
        this.challengeAcsUrl = Objects.requireNonNull(challengeAcsUrl);
        this.challengeTransactionId = Objects.requireNonNull(challengeTransactionId);
        this.challengePayload = Objects.requireNonNull(challengePayload);
        this.threeDsVersion = Objects.requireNonNull(threeDsVersion);
    }

    @Override
    public Auth3dsDetailsEntity toAuth3dsDetailsEntity() {
        var auth3dsDetailsEntity = new Auth3dsDetailsEntity();
        auth3dsDetailsEntity.setWorldpayChallengeAcsUrl(challengeAcsUrl);
        auth3dsDetailsEntity.setWorldpayChallengeTransactionId(challengeTransactionId);
        auth3dsDetailsEntity.setWorldpayChallengePayload(challengePayload);
        auth3dsDetailsEntity.setThreeDsVersion(threeDsVersion);
        return auth3dsDetailsEntity;
    }
}
