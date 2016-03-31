package uk.gov.pay.connector.model;

public interface GatewayResponse {

    public Boolean isSuccessful();
    public ErrorResponse getError();
    // TODO gotta change this usage of boolean logics, may be with a String/Enum to represent the success or error or progress
    public Boolean isInProgress();
}
