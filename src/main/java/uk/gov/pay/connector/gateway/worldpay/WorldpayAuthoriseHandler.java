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
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.Worldpay3dsFlexEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayCardNumberAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayGooglePayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffApplePayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayRecurringMerchantInitiatedAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.templates.PayloadBuilder;
import uk.gov.pay.connector.gateway.templates.TemplateBuilder;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static jakarta.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static jakarta.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static jakarta.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getAuthHeader;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;

public class WorldpayAuthoriseHandler implements WorldpayGatewayResponseGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldpayAuthoriseHandler.class);

    private static final TemplateBuilder AUTHORISE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayAuthoriseOrderTemplate.xml");
    private static final TemplateBuilder AUTHORISE_RECURRING_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayAuthoriseRecurringOrderTemplate.xml");
    private static final TemplateBuilder AUTHORISE_APPLE_PAY_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayAuthoriseApplePayOrderTemplate.xml");
    private static final TemplateBuilder AUTHORISE_GOOGLE_PAY_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/worldpay/WorldpayAuthoriseGooglePayOrderTemplate.xml");
    
    private final GatewayClient authoriseClient;
    private final Map<String, URI> gatewayUrlMap;

    @Inject
    public WorldpayAuthoriseHandler(@Named("WorldpayAuthoriseGatewayClient") GatewayClient authoriseClient,
                                    @Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap
    ) {
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

    public GatewayResponse<WorldpayOrderStatusResponse> authorise(WorldpayAuthoriseRequest request, String gatewayAccountType) {

        logMissingDdcResultFor3dsFlexIntegration(request);

        GatewayOrder gatewayOrder = switch (request) {
            case WorldpayRecurringMerchantInitiatedAuthoriseRequest req -> new GatewayOrder(OrderRequestType.AUTHORISE,
                    AUTHORISE_RECURRING_ORDER_TEMPLATE_BUILDER.buildWith(req), APPLICATION_XML_TYPE);

            case WorldpayOneOffApplePayAuthoriseRequest req -> new GatewayOrder(OrderRequestType.AUTHORISE_APPLE_PAY,
                    AUTHORISE_APPLE_PAY_ORDER_TEMPLATE_BUILDER.buildWith(req), APPLICATION_XML_TYPE);

            case WorldpayGooglePayAuthoriseRequest req -> new GatewayOrder(OrderRequestType.AUTHORISE_GOOGLE_PAY,
                    AUTHORISE_GOOGLE_PAY_ORDER_TEMPLATE_BUILDER.buildWith(req), APPLICATION_XML_TYPE);

            case WorldpayAuthoriseRequest req -> new GatewayOrder(OrderRequestType.AUTHORISE,
                    AUTHORISE_ORDER_TEMPLATE_BUILDER.buildWith(req), APPLICATION_XML_TYPE);
        };

        try {            
            GatewayClient.Response response = authoriseClient.postRequestFor(
                    gatewayUrlMap.get(gatewayAccountType),
                    WORLDPAY,
                    gatewayAccountType,
                    gatewayOrder,
                    getAuthHeader(request.username(), request.password()));

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

    private void logMissingDdcResultFor3dsFlexIntegration(WorldpayAuthoriseRequest request) {
        if (request instanceof Worldpay3dsFlexEligibleAuthoriseRequest req && req.dfReferenceId() == null) {
            LOGGER.info("[3DS Flex] Missing device data collection result for {}", req.orderCode());
        }
    }
    
}
