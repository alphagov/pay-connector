package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.paymentprocessor.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.gateway.model.Gateway3dsRequiredParams;

public class Worldpay3dsRequiredParams implements Gateway3dsRequiredParams {

    private final String issuerUrl;
    private final String paRequest;

    public Worldpay3dsRequiredParams(String issuerUrl, String paRequest) {
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
