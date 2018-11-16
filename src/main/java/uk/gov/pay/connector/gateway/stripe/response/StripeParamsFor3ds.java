package uk.gov.pay.connector.gateway.stripe.response;

import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;

public class StripeParamsFor3ds implements GatewayParamsFor3ds {

    private final String issuerUrl;

    public StripeParamsFor3ds(String issuerUrl) {
        this.issuerUrl = issuerUrl;
    }

    @Override
    public Auth3dsDetailsEntity toAuth3dsDetailsEntity() {
        Auth3dsDetailsEntity auth3dsDetailsEntity = new Auth3dsDetailsEntity();
        auth3dsDetailsEntity.setIssuerUrl(issuerUrl);
        return auth3dsDetailsEntity;
    }
}
