package uk.gov.pay.connector.gateway.sandbox;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.epdq.ChargeQueryResponse;
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
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;

import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public class SandboxPaymentProvider implements PaymentProvider, SandboxGatewayResponseGenerator {

    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;

    private SandboxWalletAuthorisationHandler sandboxWalletAuthorisationHandler;

    public SandboxPaymentProvider() {
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        this.sandboxWalletAuthorisationHandler = new SandboxWalletAuthorisationHandler();
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request) {
        String cardNumber = request.getAuthCardDetails().getCardNo();
        return getSandboxGatewayResponse(cardNumber);
    }

    @Override
    public ChargeQueryResponse queryPaymentStatus(ChargeEntity charge) {
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
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
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
