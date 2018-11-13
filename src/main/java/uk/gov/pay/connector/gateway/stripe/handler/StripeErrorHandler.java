package uk.gov.pay.connector.gateway.stripe.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.stripe.response.StripeErrorResponse;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.UUID;

public class StripeErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeErrorHandler.class);

    private StripeErrorHandler() {
    }

    public static void checkResponseForClientError(String url, Response response) {
        if (hasClientError(response)) {

            StripeErrorResponse stripeErrorResponse = toErrorResponse(response);
            String errorId = UUID.randomUUID().toString();

            logger.error("Gateway returned unexpected status code: {}, for gateway url={} with error [code={},message={}. ErrorId : {}]",
                    response.getStatus(), url,
                    stripeErrorResponse.getError().getCode(),
                    stripeErrorResponse.getError().getMessage(),
                    errorId);

            throw new WebApplicationException(String.format("Unexpected HTTP status code %s from gateway. ErrorId : %s", response.getStatus(), errorId));
        }
    }

    public static StripeErrorResponse toErrorResponse(Response response) {
        return response.readEntity(StripeErrorResponse.class);
    }

    public static boolean hasClientError(Response response) {
        return response.getStatusInfo().getFamily() == Response.Status.Family.CLIENT_ERROR;
    }
}
