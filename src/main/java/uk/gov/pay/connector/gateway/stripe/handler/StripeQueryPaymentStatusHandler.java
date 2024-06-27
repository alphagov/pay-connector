package uk.gov.pay.connector.gateway.stripe.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;
import uk.gov.pay.connector.gateway.stripe.json.StripeSearchPaymentIntentsResponse;
import uk.gov.pay.connector.gateway.stripe.model.StripeChargeStatus;
import uk.gov.pay.connector.gateway.stripe.request.StripeQueryPaymentStatusRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripeQueryResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.List;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

public class StripeQueryPaymentStatusHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StripeQueryPaymentStatusHandler.class);
    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private JsonObjectMapper jsonObjectMapper;

    public StripeQueryPaymentStatusHandler(GatewayClient client, StripeGatewayConfig stripeGatewayConfig, JsonObjectMapper jsonObjectMapper) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public ChargeQueryResponse queryPaymentStatus(ChargeQueryGatewayRequest chargeQueryGatewayRequest) throws GatewayException {
        LOGGER.info(format("Querying Stripe payment status for [%s]", chargeQueryGatewayRequest.chargeExternalId()));
        StripeQueryPaymentStatusRequest request = StripeQueryPaymentStatusRequest.of(chargeQueryGatewayRequest.getGatewayAccount(),
                stripeGatewayConfig, chargeQueryGatewayRequest.chargeExternalId());
        try {
            String rawResponse = client.getRequestFor(request).getEntity();
            StripeSearchPaymentIntentsResponse queryResponse = jsonObjectMapper.getObject(rawResponse, StripeSearchPaymentIntentsResponse.class);
            List<StripePaymentIntent> paymentIntentList = queryResponse.getPaymentIntents();
            if (paymentIntentList == null || paymentIntentList.isEmpty()) {
                LOGGER.info("There are no payment intents for charge: [{}]", chargeQueryGatewayRequest.chargeExternalId());
                return new ChargeQueryResponse(GatewayError.genericGatewayError("There are no payment intents for charge"));
            }
            if (paymentIntentList.size() > 1) {
                LOGGER.error("There are more than one payment intents for charge: [{}]", chargeQueryGatewayRequest.chargeExternalId());
                throw new ChargeNotFoundRuntimeException(request.getChargeExternalId());
            }
            return new ChargeQueryResponse(StripeChargeStatus.mapToChargeStatus(StripeChargeStatus.fromString(paymentIntentList.get(0).getStatus())),
                    new StripeQueryResponse(paymentIntentList.get(0).getId()));
        } catch (GatewayException.GatewayErrorException ex) {
            if ((ex.getStatus().isPresent() && ex.getStatus().get() == SC_UNAUTHORIZED) || ex.getFamily() == SERVER_ERROR) {
                LOGGER.info("Querying payment status failed due to an internal error. Reason: {}. Status code from Stripe: {}.",
                        ex.getMessage(), ex.getStatus().map(String::valueOf).orElse("no status code"));
                throw new GatewayException.GatewayErrorException("There was an internal server error querying payment status charge");
            }

            if (ex.getFamily() == CLIENT_ERROR) {
                StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(ex.getResponseFromGateway(), StripeErrorResponse.class);
                LOGGER.info("Querying payment status. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                        stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), ex.getStatus());

                throw new GatewayException.GatewayErrorException(
                        format("Querying payment status. Failure code from Stripe: %s, failure message from Stripe: %s. Response code from Stripe: %s",
                                stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), ex.getStatus()));
            }

            LOGGER.info("Unrecognised response status when querying payment status - status={}, response={}",
                    ex.getStatus(), ex.getResponseFromGateway());
            throw new GatewayException.GatewayErrorException("Unrecognised response status when querying payment status.");

        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException ex) {
            LOGGER.info("GatewayException occurred, error:\n {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
