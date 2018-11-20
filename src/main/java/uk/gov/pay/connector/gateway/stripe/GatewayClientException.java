package uk.gov.pay.connector.gateway.stripe;

import javax.ws.rs.core.Response;

/**
 * This class represents 4xx exceptions
 */
public class GatewayClientException extends Exception{
    private final transient Response response;
    
    GatewayClientException(String message, Response response) {
        super(message);
        this.response = response;
    }

    public Response getResponse() {
        return response;
    }
}
