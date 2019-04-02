package uk.gov.pay.connector.gateway.stripe.handler;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getStripeAuthHeader;

public class StripeCancelHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeCancelHandler.class);

    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;

    public StripeCancelHandler(GatewayClient client, StripeGatewayConfig stripeGatewayConfig) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
    }

    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        String url = stripeGatewayConfig.getUrl() + "/v1/refunds";
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();

        String payload = URLEncodedUtils.format(singletonList(new BasicNameValuePair("charge", request.getTransactionId())), UTF_8);
        
        try {
            client.postRequestFor(URI.create(url), gatewayAccount, new GatewayOrder(OrderRequestType.CANCEL, payload,
                    APPLICATION_FORM_URLENCODED_TYPE), getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive()));
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
                    request.getTransactionId(), e.getResponseFromGateway(), request.getExternalChargeId(), e.getStatus());
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        } catch (GenericGatewayException | GatewayConnectionTimeoutException e) {
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        } 
    }
}
