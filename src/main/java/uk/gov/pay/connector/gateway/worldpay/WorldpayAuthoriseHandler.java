package uk.gov.pay.connector.gateway.worldpay;

import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.inject.Inject;
import java.net.URI;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;

public class WorldpayAuthoriseHandler implements WorldpayGatewayResponseGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldpayAuthoriseHandler.class);
    
    private final GatewayClient authoriseClient;
    private final Map<String, URI> gatewayUrlMap;

    @Inject
    public WorldpayAuthoriseHandler(@Named("WorldpayAuthoriseGatewayClient") GatewayClient authoriseClient,
                                    @Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap) {
        this.authoriseClient = authoriseClient;
        this.gatewayUrlMap = gatewayUrlMap;
    }
    
    public GatewayResponse<WorldpayOrderStatusResponse> authoriseWithExemption(CardAuthorisationGatewayRequest request) {
        return authorise(request, true);
    }

    public GatewayResponse<WorldpayOrderStatusResponse> authoriseWithoutExemption(CardAuthorisationGatewayRequest request) {
        return authorise(request, false);
    }
    
    private GatewayResponse<WorldpayOrderStatusResponse> authorise(CardAuthorisationGatewayRequest request, 
                                                                  boolean withExemptionEngine) {

        logMissingDdcResultFor3dsFlexIntegration(request);
        
        try {
            GatewayClient.Response response = authoriseClient.postRequestFor(
                    gatewayUrlMap.get(request.getGatewayAccount().getType()),
                    WORLDPAY,
                    request.getGatewayAccount().getType(),
                    WorldpayOrderBuilder.buildAuthoriseOrderWithExemptionEngine(request, withExemptionEngine),
                    getGatewayAccountCredentialsAsAuthHeader(request.getGatewayCredentials()));

            if (response.getEntity().contains("request3DSecure")) {
                LOGGER.info(format("Worldpay authorisation response when 3ds required for %s: %s", request.getChargeExternalId(), sanitiseMessage(response.getEntity())));
            }
            
            return getWorldpayGatewayResponse(response);
        } catch (GatewayException.GatewayErrorException e) {
            
            if (e.getStatus().isPresent() && (e.getFamily() == CLIENT_ERROR || e.getFamily() == SERVER_ERROR)) {
                
                LOGGER.error("Authorisation failed for charge {} due to an internal error. Reason: {}. Status code from Worldpay: {}.",
                        request.getChargeExternalId(), e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));
                
                GatewayError gatewayError = gatewayConnectionError(format("Non-success HTTP status code %s from gateway", e.getStatus().get()));

                return responseBuilder().withGatewayError(gatewayError).build();
            }
            
            LOGGER.info("Unrecognised response status when authorising. Charge_id={}, status={}, response={}",
                    request.getChargeExternalId(), e.getStatus(), e.getResponseFromGateway());
            return responseBuilder().withGatewayError(e.toGatewayError()).build();
            
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            
            LOGGER.error("GatewayException occurred for charge external id {}, error:\n {}", request.getChargeExternalId(), e);

            return responseBuilder().withGatewayError(e.toGatewayError()).build();
        }
    }

    private String sanitiseMessage(String message) {
        return message.replaceAll("<cardHolderName>.*</cardHolderName>", "<cardHolderName>REDACTED</cardHolderName>");
    }
    
    private void logMissingDdcResultFor3dsFlexIntegration(CardAuthorisationGatewayRequest request) {
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        if (gatewayAccount.isRequires3ds() && gatewayAccount.getIntegrationVersion3ds() == 2 &&
                request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().isEmpty()) {
            LOGGER.info("[3DS Flex] Missing device data collection result for {}", gatewayAccount.getId());
        }
    }
}
