package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.AuthMode;
import uk.gov.pay.connector.gateway.AuthoriseHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeAuthorisationFailedResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.request.StripeCustomerRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentMethodRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripeCustomerResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentIntentResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripePaymentMethodResponse;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.Map;

import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;

public class StripeAuthoriseHandler implements AuthoriseHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeAuthoriseHandler.class);

    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;
    private final String frontendUrl;
    private final PaymentInstrumentService paymentInstrumentService;
    private final ChargeDao chargeDao;
    
    @Inject
    public StripeAuthoriseHandler(GatewayClient client,
                                  StripeGatewayConfig stripeGatewayConfig,
                                  ConnectorConfiguration configuration,
                                  JsonObjectMapper jsonObjectMapper,
                                  PaymentInstrumentService paymentInstrumentService,
                                  ChargeDao chargeDao) {
        this.client = client;
        this.frontendUrl = configuration.getLinks().getFrontendUrl();
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
        this.paymentInstrumentService = paymentInstrumentService;
        this.chargeDao = chargeDao;
    }

    @Override
    public GatewayResponse authorise(CardAuthorisationGatewayRequest request) {
        logger.info("Calling Stripe for authorisation of charge [{}]", request.getChargeExternalId());
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse
                .GatewayResponseBuilder
                .responseBuilder();
        try {
            StripeCustomerResponse stripeCustomerResponse;
            StripePaymentIntentResponse stripePaymentIntentResponse;
            
            StripePaymentMethodResponse stripePaymentMethodResponse;
            
            if (request.getCharge().isSavePaymentInstrumentToAgreement()) {
                stripeCustomerResponse = createCustomer(request);
                stripePaymentMethodResponse = createPaymentMethod(request);
                stripePaymentIntentResponse = createPaymentIntent(request, stripePaymentMethodResponse.getId(), stripeCustomerResponse.getId());
                var instrument = paymentInstrumentService.create(request.getAuthCardDetails(), request.getCharge().getGatewayAccount(), Map.of("customer_id", stripeCustomerResponse.getId(), "payment_method_id", stripePaymentMethodResponse.getId()));
                
                // // @TODO(sfount): move to transactional service helper -- entire operation should be transactional to some extent
                // @TODO(sfount): update PaSTEE to send recurring properties through to ledger
                request.getCharge().setPaymentInstrument(instrument);
                chargeDao.merge(request.getCharge());
            } else {
                
                if (request.getCharge().getAuthMode() == AuthMode.WEB) {
                    stripePaymentMethodResponse = createPaymentMethod(request);
                    stripePaymentIntentResponse = createPaymentIntent(request, stripePaymentMethodResponse.getId());
                } else {
                    var customerId = request.getCharge().getPaymentInstrument().getRecurringAuthToken().get("customer_id");
                    var paymentMethodId = request.getCharge().getPaymentInstrument().getRecurringAuthToken().get("payment_method_id");
                    stripePaymentIntentResponse = createPaymentIntent(request, paymentMethodId, customerId);
                }
            }
            
            return GatewayResponse
                    .GatewayResponseBuilder
                    .responseBuilder()
                    .withResponse(StripeAuthorisationResponse.of(stripePaymentIntentResponse)).build();
        } catch (GatewayException.GatewayErrorException e) {
            String chargeExternalId = request.getChargeExternalId();

            if ((e.getStatus().isPresent() && e.getStatus().get() == SC_UNAUTHORIZED) || e.getFamily() == SERVER_ERROR) {
                logger.error("Authorisation failed for charge {} due to an internal error. Reason: {}. Status code from Stripe: {}.",
                        chargeExternalId, e.getMessage(), e.getStatus().map(String::valueOf).orElse("no status code"));
                GatewayError gatewayError = gatewayConnectionError("There was an internal server error authorising charge_external_id: " + chargeExternalId);
                return responseBuilder.withGatewayError(gatewayError).build();
            }

            if (e.getFamily() == CLIENT_ERROR) {
                StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class);
                logger.info("Authorisation failed for charge {}. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                        chargeExternalId, stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), e.getStatus());
    
                return responseBuilder.withResponse(StripeAuthorisationFailedResponse.of(stripeErrorResponse)).build();
            }

            logger.info("Unrecognised response status when authorising. Charge_id={}, status={}, response={}",
                    chargeExternalId, e.getStatus(), e.getResponseFromGateway());
            throw new RuntimeException("Unrecognised response status when authorising.");

        } catch (GatewayException.GatewayConnectionTimeoutException | GatewayException.GenericGatewayException e) {
            logger.error("GatewayException occurred for charge external id {}, error:\n {}", request.getChargeExternalId(), e);
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
    }
    
    private StripeCustomerResponse createCustomer(CardAuthorisationGatewayRequest request) 
        throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
       String jsonResponse = client.postRequestFor(StripeCustomerRequest.of(request, stripeGatewayConfig)).getEntity();
       return jsonObjectMapper.getObject(jsonResponse, StripeCustomerResponse.class);
    }

    private StripePaymentIntentResponse createPaymentIntent(CardAuthorisationGatewayRequest request, String paymentMethodId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripePaymentIntentRequest.of(request, paymentMethodId, null, stripeGatewayConfig, frontendUrl)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentIntentResponse.class);
    }

    private StripePaymentIntentResponse createPaymentIntent(CardAuthorisationGatewayRequest request, String paymentMethodId, String customerId)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripePaymentIntentRequest.of(request, paymentMethodId, customerId, stripeGatewayConfig, frontendUrl)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentIntentResponse.class);
    }

    private StripePaymentMethodResponse createPaymentMethod(CardAuthorisationGatewayRequest request)
            throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayException.GatewayErrorException {
        String jsonResponse = client.postRequestFor(StripePaymentMethodRequest.of(request, stripeGatewayConfig)).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripePaymentMethodResponse.class);
    }
}
