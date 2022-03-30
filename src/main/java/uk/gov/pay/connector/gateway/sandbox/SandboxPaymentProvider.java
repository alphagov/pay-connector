package uk.gov.pay.connector.gateway.sandbox;

import com.google.inject.Inject;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.sandbox.applepay.SandboxWalletAuthorisationHandler;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public class SandboxPaymentProvider implements PaymentProvider, SandboxGatewayResponseGenerator {

    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;

    private SandboxWalletAuthorisationHandler sandboxWalletAuthorisationHandler;
    
    private final ChargeDao chargeDao;
    
    private final PaymentInstrumentService paymentInstrumentService;
    
    @Inject
    public SandboxPaymentProvider(PaymentInstrumentService paymentInstrumentService, ChargeDao chargeDao) {
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        this.sandboxWalletAuthorisationHandler = new SandboxWalletAuthorisationHandler();
        this.paymentInstrumentService = paymentInstrumentService;
        this.chargeDao = chargeDao;
    }

    @Override
    public boolean canQueryPaymentStatus() {
        return false;
    }
    
    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request) {
        String cardNumber = request.getAuthCardDetails().getCardNo();

        if (request.getCharge().isSavePaymentInstrumentToAgreement()) {
            // TODO(sfount): is this the point the provider comes back with a failed response(?) should these all go to gateway error for now or do they map to decline etc.
            var token = setupTokenWithProviderStub();
            var instrument = paymentInstrumentService.create(request.getAuthCardDetails(), request.getCharge().getGatewayAccount(), Map.of("token", token));
            request.getCharge().setPaymentInstrument(instrument);
            chargeDao.merge(request.getCharge());
//            request.getCharge().setPaymentInstrument(instrument);
        }
        return getSandboxGatewayResponse(cardNumber);
    }
    
    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseUserNotPresent(CardAuthorisationGatewayRequest request) {
        return getSandboxGatewayResponse(request.getCharge().getPaymentInstrument());
    } 
    
    private String setupTokenWithProviderStub() {
        return randomUUID().toString();
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeQueryGatewayRequest chargeQueryGatewayRequest) {
        throw new UnsupportedOperationException("Querying payment status not currently supported by Sandbox");
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return Gateway3DSAuthorisationResponse.of(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseWallet(WalletAuthorisationGatewayRequest request) {
        return sandboxWalletAuthorisationHandler.authorise(request);
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        return fromBaseCaptureResponse(BaseCaptureResponse.fromTransactionId(randomUUID().toString(), SANDBOX), COMPLETE);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return SANDBOX;
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }

    @Override
    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        return createGatewayBaseCancelResponse();
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        return GatewayRefundResponse.fromBaseRefundResponse(BaseRefundResponse.fromReference(randomUUID().toString(), SANDBOX),
                GatewayRefundResponse.RefundState.COMPLETE);
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(Charge charge, List<Refund> refundList) {
        return externalRefundAvailabilityCalculator.calculate(charge, refundList);
    }

    @Override
    public SandboxAuthorisationRequestSummary generateAuthorisationRequestSummary(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        return new SandboxAuthorisationRequestSummary();
    }

    private GatewayResponse<BaseCancelResponse> createGatewayBaseCancelResponse() {
        GatewayResponseBuilder<BaseCancelResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseCancelResponse() {

            private final String transactionId = randomUUID().toString();

            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }

            @Override
            public String getTransactionId() {
                return transactionId;
            }

            @Override
            public CancelStatus cancelStatus() {
                return CancelStatus.CANCELLED;
            }

            @Override
            public String toString() {
                return "Sandbox cancel response (transactionId: " + getTransactionId() + ')';
            }
        }).build();
    }
}
