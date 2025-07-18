package uk.gov.pay.connector.gateway.worldpay;

import com.google.inject.name.Named;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;

import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static jakarta.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static jakarta.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;

public class WorldpayAuthoriseHandler implements WorldpayGatewayResponseGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldpayAuthoriseHandler.class);

    private final GatewayClient authoriseClient;
    private final Map<String, URI> gatewayUrlMap;
    private final AcceptLanguageHeaderParser acceptLanguageHeaderParser;

    @Inject
    public WorldpayAuthoriseHandler(@Named("WorldpayAuthoriseGatewayClient") GatewayClient authoriseClient,
                                    @Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap,
                                    AcceptLanguageHeaderParser acceptLanguageHeaderParser
    ) {
        this.acceptLanguageHeaderParser = acceptLanguageHeaderParser;
        this.authoriseClient = authoriseClient;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    public GatewayResponse<WorldpayOrderStatusResponse> authoriseUserNotPresent(RecurringPaymentAuthorisationGatewayRequest request) {
        LOGGER.info("Authorising user not present request: {}", request.getGatewayTransactionId().orElse("gatewayTransactionId is not present"));

        try {
            GatewayClient.Response response = authoriseClient.postRequestFor(
                    gatewayUrlMap.get(request.getGatewayAccount().getType()),
                    WORLDPAY,
                    request.getGatewayAccount().getType(),
                    WorldpayOrderBuilder.buildAuthoriseRecurringOrder(request),
                    getWorldpayAuthHeader(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()));
            return getWorldpayGatewayResponse(response);
        } catch (GatewayException.GatewayErrorException e) {
            LOGGER.error("Authorisation user not present failed due to an internal error. Reason: {}. Status code from Worldpay: {}.",
                    e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));

            GatewayError gatewayError = gatewayConnectionError(format("Non-success HTTP status code %s from gateway", e.getStatus().get()));

            return responseBuilder().withGatewayError(gatewayError).build();
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {

            LOGGER.error("GatewayException occurred, error:\n {}", e);

            return responseBuilder().withGatewayError(e.toGatewayError()).build();
        }
    }

    public GatewayResponse<WorldpayOrderStatusResponse> authorise(CardAuthorisationGatewayRequest request,
                                                                  SendWorldpayExemptionRequest sendExemptionRequest) {

        logMissingDdcResultFor3dsFlexIntegration(request);

        try {
            var worldpayOrderBuilder  = WorldpayOrderBuilder.buildAuthoriseOrder(request, sendExemptionRequest, acceptLanguageHeaderParser);
            var structuredArguments = new AuthorisationRequestSummaryStructuredLogging().createArgs(worldpayOrderBuilder, request.getAuthCardDetails());
            var logMessage = String.format(Locale.UK, "Authorisation request%s for %s (%s %s)",
                    stringifyLogMessage(worldpayOrderBuilder, request.getAuthCardDetails()),
                    request.getGovUkPayPaymentId(),
                    WORLDPAY.getName(),
                    request.getGatewayAccount().getId()
                    );
            
            LOGGER.info(logMessage, structuredArguments);
            
            GatewayClient.Response response = authoriseClient.postRequestFor(
                    gatewayUrlMap.get(request.getGatewayAccount().getType()),
                    WORLDPAY,
                    request.getGatewayAccount().getType(),
                    worldpayOrderBuilder.build(),
                    getWorldpayAuthHeader(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()));
            
            if (response.getEntity().contains("request3DSecure")) {
                LOGGER.info(format("Worldpay authorisation response when 3ds required: %s", sanitiseMessage(response.getEntity())));
            }
            return getWorldpayGatewayResponse(response);
        } catch (GatewayException.GatewayErrorException e) {

            if (e.getStatus().isPresent() && (e.getFamily() == CLIENT_ERROR || e.getFamily() == SERVER_ERROR)) {

                LOGGER.error("Authorisation failed due to an internal error. Reason: {}. Status code from Worldpay: {}.",
                        e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));

                GatewayError gatewayError = gatewayConnectionError(format("Non-success HTTP status code %s from gateway", e.getStatus().get()));

                return responseBuilder().withGatewayError(gatewayError).build();
            }

            LOGGER.info("Unrecognised response status when authorising - status={}, response={}",
                    e.getStatus(), e.getResponseFromGateway());
            return responseBuilder().withGatewayError(e.toGatewayError()).build();

        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {

            LOGGER.error("GatewayException occurred, error:\n {}", e);

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
    
    private String stringifyLogMessage(WorldpayOrderRequestBuilder orderRequestBuilder, AuthCardDetails authCardDetails) {
        var stringJoiner = new StringJoiner(" and ", " ", "");
        
        authCardDetails.getAddress()
                .map(address -> stringJoiner.add("with billing address"))
                .orElse(stringJoiner.add("without billing address"));

        Optional.ofNullable(orderRequestBuilder.getWorldpayTemplateData().getPayerEmail())
                .map(email -> stringJoiner.add("with email address"))
                .orElse(stringJoiner.add("without email address"));

        Optional.ofNullable(orderRequestBuilder.getWorldpayTemplateData().getPayerIpAddress())
                .map(ipAddress -> stringJoiner.add("with ip address"))
                .orElse(stringJoiner.add("without ip address"));
        
        Optional.ofNullable(orderRequestBuilder.getWorldpayTemplateData().isRequires3ds())
                .map(requires3ds -> stringJoiner.add("with requires 3ds"))
                .orElse(stringJoiner.add("without requires 3ds"));
        
        return stringJoiner.toString();
    }
}
