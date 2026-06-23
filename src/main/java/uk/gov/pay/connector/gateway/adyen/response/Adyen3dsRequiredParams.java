package uk.gov.pay.connector.gateway.adyen.response;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;

public record Adyen3dsRequiredParams(
        String issuerUrl,
        String httpMethod3ds) implements Gateway3dsRequiredParams {

    @Override
    public Auth3dsRequiredEntity toAuth3dsRequiredEntity() {
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setIssuerUrl(issuerUrl);
        auth3dsRequiredEntity.setHttpMethod3ds(httpMethod3ds);
        return auth3dsRequiredEntity;
    }
}
