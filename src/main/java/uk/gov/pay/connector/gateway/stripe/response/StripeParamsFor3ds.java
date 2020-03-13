package uk.gov.pay.connector.gateway.stripe.response;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;

public class StripeParamsFor3ds implements GatewayParamsFor3ds {

    private final String issuerUrl;

    public StripeParamsFor3ds(String issuerUrl) {
        this.issuerUrl = issuerUrl;
    }

    @Override
    public Auth3dsRequiredEntity toAuth3dsRequiredEntity() {
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setIssuerUrl(issuerUrl);
        return auth3dsRequiredEntity;
    }
}
