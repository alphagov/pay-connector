package uk.gov.pay.connector.gateway.smartpay;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;

public class Smartpay3dsRequiredParams implements Gateway3dsRequiredParams {

    private final String issuerUrl;
    private final String paRequest;
    private final String md;

    public Smartpay3dsRequiredParams(String issuerUrl, String paRequest, String md) {
        this.issuerUrl = issuerUrl;
        this.paRequest = paRequest;
        this.md = md;
    }

    @Override
    public Auth3dsRequiredEntity toAuth3dsRequiredEntity() {
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setPaRequest(paRequest);
        auth3dsRequiredEntity.setIssuerUrl(issuerUrl);
        auth3dsRequiredEntity.setMd(md);
        return auth3dsRequiredEntity;
    }
}
