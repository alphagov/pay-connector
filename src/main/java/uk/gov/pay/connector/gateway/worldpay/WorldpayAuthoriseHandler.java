package uk.gov.pay.connector.gateway.worldpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.AuthoriseHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;

import java.net.URI;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayAuthoriseHandler implements AuthoriseHandler, WorldpayGatewayResponseGenerator {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final GatewayClient authoriseClient;
    private final Map<String, URI> gatewayUrlMap;

    public WorldpayAuthoriseHandler(GatewayClient authoriseClient, Map<String, URI> gatewayUrlMap) {
        this.authoriseClient = authoriseClient;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    @Override
    public GatewayResponse<WorldpayOrderStatusResponse> authorise(CardAuthorisationGatewayRequest request) {

        try {
            GatewayClient.Response response = authoriseClient.postRequestFor(
                    gatewayUrlMap.get(request.getGatewayAccount().getType()),
                    request.getGatewayAccount(),
                    buildAuthoriseOrder(request),
                    getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount().getCredentials()));

            if (response.getEntity().contains("request3DSecure")) {
                logger.info(format("Worldpay authorisation response when 3ds required for %s: %s", request.getChargeExternalId(), sanitiseMessage(response.getEntity())));
            }
            return getWorldpayGatewayResponse(response);
        } catch (GatewayException.GatewayErrorException e) {
            
            if (e.getStatus().isPresent() && (e.getFamily() == CLIENT_ERROR || e.getFamily() == SERVER_ERROR)) {
                
                logger.error("Authorisation failed for charge {} due to an internal error. Reason: {}. Status code from Worldpay: {}.",
                        request.getChargeExternalId(), e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));
                
                GatewayError gatewayError = gatewayConnectionError(format("Non-success HTTP status code %s from gateway", e.getStatus().get()));

                return responseBuilder().withGatewayError(gatewayError).build();
            }
            
            logger.info("Unrecognised response status when authorising. Charge_id={}, status={}, response={}",
                    request.getChargeExternalId(), e.getStatus(), e.getResponseFromGateway());
            return responseBuilder().withGatewayError(e.toGatewayError()).build();
            
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            
            logger.error("GatewayException occurred for charge external id {}, error:\n {}", request.getChargeExternalId(), e);

            return responseBuilder().withGatewayError(e.toGatewayError()).build();
        }
    }

    private String sanitiseMessage(String message) {
        return message.replaceAll("<cardHolderName>.*</cardHolderName>", "<cardHolderName>REDACTED</cardHolderName>");
    }

    private GatewayOrder buildAuthoriseOrder(CardAuthorisationGatewayRequest request) {
        logMissingDdcResultFor3dsFlexIntegration(request);

        boolean is3dsRequired = request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isPresent() ||
                request.getGatewayAccount().isRequires3ds();

        boolean exemptionEngineEnabled = request.getGatewayAccount().getWorldpay3dsFlexCredentials()
                .map(Worldpay3dsFlexCredentials::isExemptionEngineEnabled)
                .orElse(false);

        var builder = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of(request.getChargeExternalId()))
                .with3dsRequired(is3dsRequired)
                .withExemptionEngine(exemptionEngineEnabled);

        if (request.getGatewayAccount().isSendPayerIpAddressToGateway()) {
            request.getAuthCardDetails().getIpAddress().ifPresent(builder::withPayerIpAddress);
        }

        return builder
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private void logMissingDdcResultFor3dsFlexIntegration(CardAuthorisationGatewayRequest request) {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        if (gatewayAccount.isRequires3ds() && gatewayAccount.getIntegrationVersion3ds() == 2 &&
                request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isEmpty()) {
            logger.info("[3DS Flex] Missing device data collection result for {}", gatewayAccount.getId());
        }
    }
}
