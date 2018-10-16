package uk.gov.pay.connector.gateway.smartpay;

import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;

public class SmartpayParamsFor3ds implements GatewayParamsFor3ds {

    private final String issuerUrl;
    private final String paRequest;
    private final String md;

    public SmartpayParamsFor3ds(String issuerUrl, String paRequest, String md) {
        this.issuerUrl = issuerUrl;
        this.paRequest = paRequest;
        this.md = md;
    }

    @Override
    public Auth3dsDetailsEntity toAuth3dsDetailsEntity() {
        Auth3dsDetailsEntity auth3dsDetailsEntity = new Auth3dsDetailsEntity();
        auth3dsDetailsEntity.setPaRequest(paRequest);
        auth3dsDetailsEntity.setIssuerUrl(issuerUrl);
        auth3dsDetailsEntity.setMd(md);
        return auth3dsDetailsEntity;
    }
}
