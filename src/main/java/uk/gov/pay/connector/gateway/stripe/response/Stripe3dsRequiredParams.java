package uk.gov.pay.connector.gateway.stripe.response;

import uk.gov.pay.connector.card.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;

import java.util.Optional;

public class Stripe3dsRequiredParams implements Gateway3dsRequiredParams {

    private final String issuerUrl;
    private final String threeDsVersion;

    public Stripe3dsRequiredParams(String issuerUrl) {
        this.issuerUrl = issuerUrl;
        this.threeDsVersion = null;
    }
    
    public Stripe3dsRequiredParams(Auth3dsRequiredEntity existingAuth3dsRequiredEntity, String threeDsVersion) {
        this.issuerUrl = Optional.ofNullable(existingAuth3dsRequiredEntity).map(Auth3dsRequiredEntity::getIssuerUrl).orElse(null);
        this.threeDsVersion = threeDsVersion;
    }

    @Override
    public Auth3dsRequiredEntity toAuth3dsRequiredEntity() {
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setIssuerUrl(issuerUrl);
        auth3dsRequiredEntity.setThreeDsVersion(threeDsVersion);
        return auth3dsRequiredEntity;
    }
}
