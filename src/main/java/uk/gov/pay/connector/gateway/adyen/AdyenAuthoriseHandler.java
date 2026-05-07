package uk.gov.pay.connector.gateway.adyen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.model.AdyenAuthorisationRequest;
import uk.gov.pay.connector.gateway.adyen.model.AdyenAuthoriseResponse;
import uk.gov.pay.connector.gateway.adyen.model.AdyenPaymentResponse;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getBaseCheckoutUrl;

public class AdyenAuthoriseHandler {

    private static final Logger logger = LoggerFactory.getLogger(AdyenAuthoriseHandler.class);

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;
    private final AdyenRequestFactory adyenRequestFactory;

    public AdyenAuthoriseHandler(GatewayClient gatewayClient,
                                 ConnectorConfiguration configuration,
                                 JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = configuration.getAdyenGatewayConfig();
        this.jsonObjectMapper = jsonObjectMapper;
        this.adyenRequestFactory = new AdyenRequestFactory(configuration);
    }

    public GatewayResponse authorise(CardAuthorisationGatewayRequest request) throws
            GatewayException.GatewayErrorException,
            GatewayException.GenericGatewayException,
            GatewayException.GatewayConnectionTimeoutException {
        logger.info("Calling Adyen for authorisation of charge");
        var authorisationRequest = new AdyenAuthorisationRequest(
                getUrl(request),
                adyenRequestFactory.createPaymentRequest(request),
                jsonObjectMapper);

        var jsonResponse = gatewayClient.postRequestFor(authorisationRequest).getEntity();

        var paymentResponse = jsonObjectMapper.getObject(
                jsonResponse,
                AdyenPaymentResponse.class);

        return GatewayResponse.GatewayResponseBuilder.responseBuilder()
                .withResponse(AdyenAuthoriseResponse.of(paymentResponse))
                .build();
    }

    private URI getUrl(CardAuthorisationGatewayRequest request) {
        return URI.create(getBaseCheckoutUrl(adyenGatewayConfig, request.getGatewayAccount().isLive()) + "/payments");
    }
}
