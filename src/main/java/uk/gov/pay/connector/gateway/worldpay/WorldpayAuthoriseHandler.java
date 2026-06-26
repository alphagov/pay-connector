package uk.gov.pay.connector.gateway.worldpay;

import com.google.inject.name.Named;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.templates.TemplateBuilder;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static jakarta.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static jakarta.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getAuthHeader;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;

public class WorldpayAuthoriseHandler implements WorldpayGatewayResponseGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldpayAuthoriseHandler.class);

    private final GatewayClient authoriseClient;
    private final Map<String, URI> gatewayUrlMap;
    private final AcceptLanguageHeaderParser acceptLanguageHeaderParser;
    private final TemplateBuilder templateBuilder;

    @Inject
    public WorldpayAuthoriseHandler(@Named("WorldpayAuthoriseGatewayClient") GatewayClient authoriseClient,
                                    @Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap,
                                    AcceptLanguageHeaderParser acceptLanguageHeaderParser,
                                    TemplateBuilder templateBuilder
    ) {
        this.acceptLanguageHeaderParser = acceptLanguageHeaderParser;
        this.authoriseClient = authoriseClient;
        this.gatewayUrlMap = gatewayUrlMap;
        this.templateBuilder = templateBuilder;
    }

    public GatewayResponse<WorldpayOrderStatusResponse> authoriseUserNotPresent(RecurringPaymentAuthorisationGatewayRequest request) {
        LOGGER.info("Authorising user not present request: {}", request.getGatewayTransactionId().orElse("gatewayTransactionId is not present"));

        GatewayResponseBuilder<WorldpayOrderStatusResponse> responseBuilder = GatewayResponseBuilder.responseBuilder();
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

            return responseBuilder.withGatewayError(gatewayError).build();
        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {

            LOGGER.error("GatewayException occurred, error:\n {}", e);

            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }

    public GatewayResponse authorise(CardAuthorisationGatewayRequest request,
                                     SendWorldpayExemptionRequest sendExemptionRequest) {

        logMissingDdcResultFor3dsFlexIntegration(request);

        var worldpayOrderBuilder  = WorldpayOrderBuilder.buildAuthoriseOrder(request, sendExemptionRequest, acceptLanguageHeaderParser);
        logAuthorisationRequestToBePosted(request, worldpayOrderBuilder);

        GatewayOrder gatewayOrder = worldpayOrderBuilder.build();

        Map<String, String> headers = getWorldpayAuthHeader(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment());

        return getGatewayResponse(request.getGatewayAccount().getType(), gatewayOrder, headers);
    }

    public GatewayResponse authorise(WorldpayAuthoriseRequest worldpayAuthoriseRequest,
                                     String gatewayAccountType) {
        Map<String, String> headers = getAuthHeader(worldpayAuthoriseRequest.username(), worldpayAuthoriseRequest.password());
        String body = templateBuilder.buildWith(worldpayAuthoriseRequest);
        GatewayOrder gatewayOrder = new GatewayOrder(OrderRequestType.AUTHORISE, body, MediaType.APPLICATION_XML_TYPE);
        return getGatewayResponse(gatewayAccountType, gatewayOrder, headers);
    }
    
    private GatewayResponse getGatewayResponse(String gatewayAccountType, GatewayOrder gatewayOrder, Map<String, String> headers) {
        GatewayResponseBuilder<WorldpayOrderStatusResponse> responseBuilder = GatewayResponseBuilder.responseBuilder();
        try {            
            GatewayClient.Response response = authoriseClient.postRequestFor(
                    gatewayUrlMap.get(gatewayAccountType),
                    WORLDPAY,
                    gatewayAccountType,
                    gatewayOrder,
                    headers);
            
            if (response.getEntity().contains("request3DSecure")) {
                LOGGER.info(format("Worldpay authorisation response when 3ds required: %s", sanitiseMessage(response.getEntity())));
            }
            return getWorldpayGatewayResponse(response);
        } catch (GatewayException.GatewayErrorException e) {

            if (e.getStatus().isPresent() && (e.getFamily() == CLIENT_ERROR || e.getFamily() == SERVER_ERROR)) {

                LOGGER.error("Authorisation failed due to an internal error. Reason: {}. Status code from Worldpay: {}.",
                        e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));

                GatewayError gatewayError = gatewayConnectionError(format("Non-success HTTP status code %s from gateway", e.getStatus().get()));

                return responseBuilder.withGatewayError(gatewayError).build();
            }

            LOGGER.info("Unrecognised response status when authorising - status={}, response={}",
                    e.getStatus(), e.getResponseFromGateway());
            return responseBuilder.withGatewayError(e.toGatewayError()).build();

        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {

            LOGGER.error("GatewayException occurred, error:\n {}", e);

            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }

    private void logAuthorisationRequestToBePosted(CardAuthorisationGatewayRequest request, WorldpayOrderRequestBuilder worldpayOrderBuilder) {
        var structuredLoggingArguments = new AuthorisationRequestSummaryStructuredLogging()
                .createArgsForPreAuthorisationLogging(worldpayOrderBuilder, request.getAuthCardDetails(), request.isMoto());
        var logMessage = String.format(Locale.UK, "Authorisation request will be posted%s for %s (%s %s)",
                stringifyLogMessage(worldpayOrderBuilder, request.getAuthCardDetails(), request.isMoto()),
                request.getGovUkPayPaymentId(),
                WORLDPAY.getName(),
                request.getGatewayAccount().getId()
        );

        LOGGER.info(logMessage, structuredLoggingArguments);
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
    
    private String stringifyLogMessage(WorldpayOrderRequestBuilder orderRequestBuilder, AuthCardDetails authCardDetails, boolean isMoto) {
        var stringJoiner = new StringJoiner(" and ", " ", "");
        
        stringJoiner.add(isMoto ? "MOTO" : "not MOTO");

        authCardDetails.getAddress()
                .map(address -> stringJoiner.add("with billing address"))
                .orElseGet(() -> stringJoiner.add("without billing address"));

        Optional.ofNullable(orderRequestBuilder.getWorldpayTemplateData().getPayerEmail())
                .map(email -> stringJoiner.add("with email address"))
                .orElseGet(() ->stringJoiner.add("without email address"));

        Optional.ofNullable(orderRequestBuilder.getWorldpayTemplateData().getPayerIpAddress())
                .map(ipAddress -> stringJoiner.add("with IP address"))
                .orElseGet(() ->stringJoiner.add("without IP address"));

        stringJoiner.add(orderRequestBuilder.getWorldpayTemplateData().isRequires3ds() ? "with 3DS data" : "without 3DS data");
        
        return stringJoiner.toString();
    }
}
