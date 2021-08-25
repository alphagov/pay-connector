package uk.gov.pay.connector.gateway.stripe.response;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;

public class Stripe3dsRequiredParams implements Gateway3dsRequiredParams {

    private final String issuerUrl;
    private final String threeDsVersion;

    public Stripe3dsRequiredParams(String issuerUrl, String threeDsVersion) {
        this.issuerUrl = issuerUrl;
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
