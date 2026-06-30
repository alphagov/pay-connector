package uk.gov.pay.connector.gateway.adyen.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.request.Adyen3dsAuthorisationRequest;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.Authorise3dsResponseBody;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.get3dsAuthUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getHeaders;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE_3DS;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.ERROR;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ERROR;
import static uk.gov.service.payments.logging.LoggingKeys.HTTP_STATUS;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER_PAYMENT_ID;

public class AdyenAuthorise3dsHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenAuthorise3dsHandler.class);

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenRequestFactory adyenRequestFactory;
    private final JsonObjectMapper jsonObjectMapper;

    public AdyenAuthorise3dsHandler(GatewayClient gatewayClient,
                                    ConnectorConfiguration connectorConfiguration,
                                    JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = connectorConfiguration.getAdyenGatewayConfig();
        this.adyenRequestFactory = new AdyenRequestFactory(connectorConfiguration);
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        var redirectResult = request.getAuth3dsResult() == null ? null : request.getAuth3dsResult().getRedirectResult();
        if (isBlank(redirectResult)) {
            LOGGER.atWarn()
                    .setMessage("Adyen 3DS response authorisation failed because redirect result is blank")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, request.getChargeExternalId())
                    .log();
            return Gateway3DSAuthorisationResponse.of(ERROR);
        }

        var adyen3dsAuthorisationRequest = new Adyen3dsAuthorisationRequest(
                get3dsAuthUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.getGatewayAccount().isLive(), AUTHORISE_3DS, request.getChargeExternalId()),
                request.getGatewayAccount().getType(),
                adyenRequestFactory.createPaymentDetailsRequest(request),
                jsonObjectMapper
        );

        try {
            var jsonResponse = gatewayClient.postRequestFor(adyen3dsAuthorisationRequest).getEntity();
            var responseBody = jsonObjectMapper.getObject(jsonResponse, Authorise3dsResponseBody.class);
            var mappedStatus = mapStatus(responseBody.resultCode());

            return Gateway3DSAuthorisationResponse.of(
                    jsonResponse,
                    mappedStatus,
                    responseBody.pspReference()
            );
        } catch (GatewayException.GatewayErrorException e) {
            return handleGatewayErrorException(request, e);
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            LOGGER.atWarn()
                    .setCause(e)
                    .setMessage("Adyen 3DS authorisation request failed")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, request.getChargeExternalId())
                    .addKeyValue(GATEWAY_ERROR, e.getMessage())
                    .log();
            return Gateway3DSAuthorisationResponse.of(e.getMessage(), AuthoriseStatus.EXCEPTION);
        }
    }

    private Gateway3DSAuthorisationResponse handleGatewayErrorException(
            Auth3dsResponseGatewayRequest request,
            GatewayException.GatewayErrorException exception) {
        try {
            var adyenError = jsonObjectMapper.getObject(exception.getResponseFromGateway(), AdyenError.class);
            LOGGER.atWarn()
                    .setMessage("Adyen 3DS authorisation request failed")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, request.getChargeExternalId())
                    .addKeyValue(PROVIDER_PAYMENT_ID, adyenError.pspReference())
                    .addKeyValue(HTTP_STATUS, exception.getStatus())
                    .addKeyValue(GATEWAY_ERROR, adyenError.message())
                    .log();
        } catch (Exception e) {
            LOGGER.atWarn()
                    .setCause(e)
                    .setMessage("Adyen 3DS authorisation request failed with non-parseable error response")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, request.getChargeExternalId())
                    .addKeyValue(HTTP_STATUS, exception.getStatus())
                    .addKeyValue(GATEWAY_ERROR, exception.getMessage())
                    .log();
        }

        return Gateway3DSAuthorisationResponse.of(exception.getMessage(), AuthoriseStatus.EXCEPTION);
    }

    private AuthoriseStatus mapStatus(String resultCode) {
        if (resultCode == null) {
            return ERROR;
        }
        return switch (resultCode) {
            case "Authorised" -> AuthoriseStatus.AUTHORISED;
            case "Refused" -> AuthoriseStatus.REJECTED;
            case "Cancelled" -> AuthoriseStatus.CANCELLED;
            default -> ERROR;
        };
    }
}
