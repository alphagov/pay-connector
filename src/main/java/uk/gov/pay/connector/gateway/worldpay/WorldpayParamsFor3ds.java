package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;

public class WorldpayParamsFor3ds implements GatewayParamsFor3ds {

    private final String issuerUrl;
    private final String paRequest;

    public WorldpayParamsFor3ds(String issuerUrl, String paRequest) {
        this.issuerUrl = issuerUrl;
        this.paRequest = paRequest;
    }

    @Override
    public Auth3dsRequiredEntity toAuth3dsRequiredEntity() {
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setPaRequest(paRequest);
        auth3dsRequiredEntity.setIssuerUrl(issuerUrl);
        return auth3dsRequiredEntity;
    }
}
