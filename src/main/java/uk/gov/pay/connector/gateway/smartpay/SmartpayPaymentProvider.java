package uk.gov.pay.connector.gateway.smartpay;

import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionTimeoutErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GenericGatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.epdq.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import javax.inject.Inject;
import javax.ws.rs.client.Invocation;
import java.util.Optional;
import java.util.function.BiFunction;

import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class SmartpayPaymentProvider implements PaymentProvider {

    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final GatewayClient client;

    private final SmartpayCaptureHandler smartpayCaptureHandler;
    private final SmartpayRefundHandler smartpayRefundHandler;

    @Inject
    public SmartpayPaymentProvider(ConnectorConfiguration configuration,
                                   GatewayClientFactory gatewayClientFactory,
                                   Environment environment) {
        externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        client = gatewayClientFactory.createGatewayClient(SMARTPAY, configuration.getGatewayConfigFor(SMARTPAY).getUrls(), includeSessionIdentifier(), environment.metrics());
        this.smartpayCaptureHandler = new SmartpayCaptureHandler(client);
        this.smartpayRefundHandler = new SmartpayRefundHandler(client);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return SMARTPAY;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.empty();
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request) throws GatewayErrorException {
        GatewayClient.Response response = client.postRequestFor(null, request.getGatewayAccount(), buildAuthoriseOrderFor(request));
        return getSmartpayGatewayResponse(response, SmartpayAuthorisationResponse.class);
    }

    private static GatewayResponse getSmartpayGatewayResponse(GatewayClient.Response response, Class<? extends BaseResponse> responseClass) throws GatewayConnectionErrorException {
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        responseBuilder.withResponse(unmarshallResponse(response, responseClass));
        return responseBuilder.build();
    }
    
    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        Optional<String> transactionId = Optional.empty();
        String stringifiedResponse = null;
        BaseAuthoriseResponse.AuthoriseStatus authorisationStatus = null;
        
        try {
            GatewayClient.Response response = client.postRequestFor(null, request.getGatewayAccount(), build3dsResponseAuthOrderFor(request));
            GatewayResponse<BaseAuthoriseResponse> gatewayResponse = getSmartpayGatewayResponse(response, SmartpayAuthorisationResponse.class);
            
            if (!gatewayResponse.getBaseResponse().isPresent())
                gatewayResponse.throwGatewayError();
            
            transactionId = Optional.ofNullable(gatewayResponse.getBaseResponse().get().getTransactionId());
            stringifiedResponse = gatewayResponse.toString();
            authorisationStatus = gatewayResponse.getBaseResponse().get().authoriseStatus();
        } catch (GatewayErrorException e) {
            authorisationStatus = BaseAuthoriseResponse.AuthoriseStatus.EXCEPTION;
            stringifiedResponse = e.getMessage();
        }

        if (transactionId.isPresent()) {
            return Gateway3DSAuthorisationResponse.of(stringifiedResponse, authorisationStatus, transactionId.get());
        } else {
            return Gateway3DSAuthorisationResponse.of(stringifiedResponse, authorisationStatus);
        }
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return smartpayCaptureHandler.capture(request);
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return smartpayRefundHandler.refund(request);
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) throws GenericGatewayErrorException, GatewayConnectionErrorException, GatewayConnectionTimeoutErrorException {
        GatewayClient.Response response = client.postRequestFor(null, request.getGatewayAccount(), buildCancelOrderFor(request));
        return getSmartpayGatewayResponse(response, SmartpayCancelResponse.class);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) {
        throw new UnsupportedOperationException("Wallets are not supported for Smartpay");
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeEntity charge) {
        throw new UnsupportedOperationException("Querying payment status not currently supported by Smartpay");
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    private GatewayOrder buildAuthoriseOrderFor(CardAuthorisationGatewayRequest request) {
        SmartpayOrderRequestBuilder smartpayOrderRequestBuilder = request.getGatewayAccount().isRequires3ds() ?
                SmartpayOrderRequestBuilder.aSmartpay3dsRequiredOrderRequestBuilder() : SmartpayOrderRequestBuilder.aSmartpayAuthoriseOrderRequestBuilder();

        return smartpayOrderRequestBuilder
                .withMerchantCode(getMerchantCode(request))
                .withPaymentPlatformReference(request.getChargeExternalId())
                .withDescription(request.getDescription())
                .withAmount(request.getAmount())
                .withAuthorisationDetails(request.getAuthCardDetails())
                .build();
    }

    private GatewayOrder build3dsResponseAuthOrderFor(Auth3dsResponseGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayAuthorise3dsOrderRequestBuilder()
                .withPaResponse(request.getAuth3DsDetails().getPaResponse())
                .withMd(request.getAuth3DsDetails().getMd())
                .withMerchantCode(getMerchantCode(request))
                .build();
    }

    private GatewayOrder buildCancelOrderFor(CancelGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayCancelOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(getMerchantCode(request))
                .build();
    }

    private String getMerchantCode(GatewayRequest request) {
        return request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID);
    }

    public static BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> includeSessionIdentifier() {
        return (order, builder) -> builder;
    }
}
