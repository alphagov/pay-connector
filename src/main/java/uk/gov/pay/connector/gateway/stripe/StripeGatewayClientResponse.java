package uk.gov.pay.connector.gateway.stripe;

public class StripeGatewayClientResponse {
    private javax.ws.rs.core.Response.Status.Family statusFamily;
    private String payload;
    private int status;

    StripeGatewayClientResponse(javax.ws.rs.core.Response response) {
        this.payload = response.readEntity(String.class);
        this.statusFamily = response.getStatusInfo().getFamily();
        this.status = response.getStatus();
    }

    javax.ws.rs.core.Response.Status.Family getFamily() {
        return this.statusFamily;
    }

    public String getPayload() {
        return this.payload;
    }

    public int getStatus() {
        return this.status;
    }
}
