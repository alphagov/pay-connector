package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsSourceResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeParamsFor3ds;

import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.util.Optional;

import static java.lang.String.format;

public class Stripe3dsSourceAuthorisationResponse implements BaseAuthoriseResponse {
    private static final Logger logger = LoggerFactory.getLogger(Stripe3dsSourceAuthorisationResponse.class);

    private Stripe3dsSourceResponse jsonResponse;

    private Stripe3dsSourceAuthorisationResponse(Stripe3dsSourceResponse jsonResponse) {
        this.jsonResponse = jsonResponse;
    }

    @Override
    public String getTransactionId() {
        return jsonResponse.getId();
    }

    @Override
    public AuthoriseStatus authoriseStatus() {
        if ("pending".equals(jsonResponse.getStatus())) {
            return AuthoriseStatus.REQUIRES_3DS;
        }
        return AuthoriseStatus.ERROR;
    }

    @Override
    public Optional<? extends GatewayParamsFor3ds> getGatewayParamsFor3ds() {
        return Optional.of(new StripeParamsFor3ds(jsonResponse.getRedirectUrl()));
    }

    @Override
    public String getErrorCode() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    public static Stripe3dsSourceAuthorisationResponse from(String jsonResponse) {
        try {
            Stripe3dsSourceResponse stripe3dsSourceResponse = new ObjectMapper().readValue(jsonResponse, Stripe3dsSourceResponse.class);
            return new Stripe3dsSourceAuthorisationResponse(stripe3dsSourceResponse);
        } catch (IOException e) {
            logger.error("There was an exception parsing the payload [{}] [{}]", jsonResponse, e.getMessage());
            throw new WebApplicationException(format("There was an exception parsing the payload [%s]", jsonResponse));
        }
    }
}
