package uk.gov.pay.connector.gateway.stripe;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

public class StripeGatewayClientResponse {
    private Family statusFamily;
    private String payload;
    private int status;

    StripeGatewayClientResponse(Response response) {
        this.payload = response.readEntity(String.class);
        this.statusFamily = response.getStatusInfo().getFamily();
        this.status = response.getStatus();
    }

    Family getFamily() {
        return this.statusFamily;
    }

    public String getPayload() {
        return this.payload;
    }

    public int getStatus() {
        return this.status;
    }
}
