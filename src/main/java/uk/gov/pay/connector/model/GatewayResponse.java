package uk.gov.pay.connector.model;

public interface GatewayResponse {

    public Boolean isSuccessful();

    public GatewayError getError();
}
