package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;

public class WorldpayParamsFor3ds implements GatewayParamsFor3ds {

    private final String issuerUrl;
    private final String paRequest;

    public WorldpayParamsFor3ds(String issuerUrl, String paRequest) {
        this.issuerUrl = issuerUrl;
        this.paRequest = paRequest;
    }

    @Override
    public Auth3dsDetailsEntity toAuth3dsDetailsEntity() {
        Auth3dsDetailsEntity auth3dsDetailsEntity = new Auth3dsDetailsEntity();
        auth3dsDetailsEntity.setPaRequest(paRequest);
        auth3dsDetailsEntity.setIssuerUrl(issuerUrl);
        return auth3dsDetailsEntity;
    }
}
