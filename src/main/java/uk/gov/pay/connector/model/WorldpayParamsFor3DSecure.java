package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;

public class WorldpayParamsFor3DSecure implements GatewayParamsFor3DSecure {

    public final String issuerUrl;
    public final String paRequest;

    public WorldpayParamsFor3DSecure(String issuerUrl, String paRequest) {
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
