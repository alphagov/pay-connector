package uk.gov.pay.connector.model;

public class Auth3dsDetailsDTO {
    public final String issuerUrl;
    public final String paRequest;
    public final String htmlOut;

    public Auth3dsDetailsDTO(String issuerUrl, String paRequest, String htmlOut) {
        this.issuerUrl = issuerUrl;
        this.paRequest = paRequest;
        this.htmlOut = htmlOut;
    }
}
