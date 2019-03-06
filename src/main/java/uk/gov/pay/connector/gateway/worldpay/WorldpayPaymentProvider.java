package uk.gov.pay.connector.gateway.worldpay;

import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.worldpay.applepay.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.GatewayOperation.QUERY;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayInquiryRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayPaymentProvider implements PaymentProvider, WorldpayGatewayResponseGenerator {

    public static final String WORLDPAY_MACHINE_COOKIE_NAME = "machine";

    private final GatewayClient authoriseClient;
    private final GatewayClient cancelClient;
    private final GatewayClient captureClient;
    private final GatewayClient inquiryClient;
    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final WorldpayCaptureHandler worldpayCaptureHandler;
    private final WorldpayRefundHandler worldpayRefundHandler;
    private final WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler;
    private final Map<String, URI> gatewayUrlMap;

    @Inject
    public WorldpayPaymentProvider(ConnectorConfiguration configuration,
                                   GatewayClientFactory gatewayClientFactory,
                                   Environment environment) {
        
        gatewayUrlMap = configuration.getGatewayConfigFor(WORLDPAY).getUrls().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> URI.create(v.getValue())));
        authoriseClient = gatewayClientFactory.createGatewayClient(WORLDPAY, AUTHORISE, environment.metrics());
        cancelClient = gatewayClientFactory.createGatewayClient(WORLDPAY, CANCEL, environment.metrics());
        inquiryClient = gatewayClientFactory.createGatewayClient(WORLDPAY, QUERY, environment.metrics());
        captureClient = gatewayClientFactory.createGatewayClient(WORLDPAY, CAPTURE, environment.metrics());
        externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        worldpayCaptureHandler = new WorldpayCaptureHandler(captureClient, gatewayUrlMap);
        worldpayRefundHandler = new WorldpayRefundHandler(captureClient, gatewayUrlMap);
        worldpayWalletAuthorisationHandler = new WorldpayWalletAuthorisationHandler(authoriseClient, gatewayUrlMap);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return WORLDPAY;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeEntity charge)  throws GatewayErrorException {
        GatewayClient.Response response = inquiryClient.postRequestFor(gatewayUrlMap.get(charge.getGatewayAccount().getType()), charge.getGatewayAccount(), buildQuery(charge));
        GatewayResponse<WorldpayQueryResponse> worldpayGatewayResponse = getWorldpayGatewayResponse(response, WorldpayQueryResponse.class);

        return worldpayGatewayResponse.getBaseResponse()
                .map(ChargeQueryResponse::from)
                .orElseThrow(() ->
                        new WebApplicationException(String.format(
                                "Unable to query charge %s - an error occurred: %s",
                                charge.getExternalId(),
                                worldpayGatewayResponse
                        )));
    }

    private GatewayOrder buildQuery(ChargeEntity charge) {
        return aWorldpayInquiryRequestBuilder()
                .withTransactionId(charge.getGatewayTransactionId())
                .withMerchantCode(charge.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request) throws GatewayErrorException {
        GatewayOrder gatewayOrder = buildAuthoriseOrder(request);
        GatewayClient.Response response = authoriseClient.postRequestFor(gatewayUrlMap.get(request.getGatewayAccount().getType()), 
                request.getGatewayAccount(), gatewayOrder, getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount()));
        return getWorldpayGatewayResponse(response);
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        try {
            List<HttpCookie> cookies = request.getProviderSessionId()
                    .map(providerSessionId -> singletonList(new HttpCookie(WORLDPAY_MACHINE_COOKIE_NAME, providerSessionId)))
                    .orElse(emptyList());
            
            GatewayClient.Response response = authoriseClient.postRequestFor(gatewayUrlMap.get(request.getGatewayAccount().getType()), 
                    request.getGatewayAccount(), build3dsResponseAuthOrder(request), cookies, 
                    getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount()));
            
            GatewayResponse<BaseAuthoriseResponse> gatewayResponse = getWorldpayGatewayResponse(response);
            
            if (!gatewayResponse.getBaseResponse().isPresent()) gatewayResponse.throwGatewayError();
            
            BaseAuthoriseResponse authoriseResponse = gatewayResponse.getBaseResponse().get();
            
            return Gateway3DSAuthorisationResponse.of(gatewayResponse.toString(), authoriseResponse.authoriseStatus(), authoriseResponse.getTransactionId());
        } catch (GatewayErrorException e) {
            return Gateway3DSAuthorisationResponse.of(e.getMessage(), BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION);
        }
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return worldpayCaptureHandler.capture(request);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) throws GatewayErrorException {
        return worldpayWalletAuthorisationHandler.authorise(request);
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return worldpayRefundHandler.refund(request);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) throws GatewayErrorException {
        GatewayClient.Response response = cancelClient.postRequestFor(gatewayUrlMap.get(request.getGatewayAccount().getType()), 
                request.getGatewayAccount(), buildCancelOrder(request), 
                getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount()));
        return getWorldpayGatewayResponse(response);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    private GatewayOrder buildAuthoriseOrder(CardAuthorisationGatewayRequest request) {
        return aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(request.getChargeExternalId())
                .with3dsRequired(request.getGatewayAccount().isRequires3ds())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private GatewayOrder build3dsResponseAuthOrder(Auth3dsResponseGatewayRequest request) {
        return aWorldpay3dsResponseAuthOrderRequestBuilder()
                .withPaResponse3ds(request.getAuth3DsDetails().getPaResponse())
                .withSessionId(request.getChargeExternalId())
                .withTransactionId(request.getTransactionId().orElse(""))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }

    private GatewayOrder buildCancelOrder(CancelGatewayRequest request) {
        return aWorldpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .build();
    }
}
