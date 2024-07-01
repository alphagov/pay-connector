package uk.gov.pay.connector.gateway.stripe.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentCancelRequest;

import static java.util.UUID.randomUUID;

public class StripeCancelHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeCancelHandler.class);

    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;

    public StripeCancelHandler(GatewayClient client, StripeGatewayConfig stripeGatewayConfig) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
    }

    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();

        try {
            client.postRequestFor(StripePaymentIntentCancelRequest.of(request, stripeGatewayConfig));

            return responseBuilder.withResponse(new BaseCancelResponse() {

                private final String transactionId = randomUUID().toString();

                @Override
                public String getErrorCode() {
                    return null;
                }

                @Override
                public String getErrorMessage() {
                    return null;
                }

                @Override
                public String getTransactionId() {
                    return transactionId;
                }

                @Override
                public CancelStatus cancelStatus() {
                    return CancelStatus.CANCELLED;
                }
            }).build();
        } catch (GatewayErrorException e) {
            logger.error("Cancel failed for gateway transaction id {}. Failure message from Stripe: {}. Charge External Id: {}. Response code from Stripe: {}",
                    request.transactionId(), e.getResponseFromGateway(), request.externalChargeId(), e.getStatus());
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        } catch (GenericGatewayException | GatewayConnectionTimeoutException e) {
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }
}
