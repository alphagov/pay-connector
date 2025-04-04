package uk.gov.pay.connector.gateway.sandbox;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.sandbox.wallets.SandboxWalletAuthorisationHandler;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.service.RefundEntityFactory;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;

import java.util.List;
import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.sandbox.SandboxCardNumbers.RECURRING_FIRST_AUTHORISE_SUCCESS_SUBSEQUENT_DECLINE;

public class SandboxPaymentProvider implements PaymentProvider {

    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;
    private final SandboxWalletAuthorisationHandler sandboxWalletAuthorisationHandler;
    private final RefundEntityFactory refundEntityFactory;
    private final SandboxGatewayResponseGenerator fullCardNumberSandboxResponseGenerator = new SandboxGatewayResponseGenerator(new SandboxFullCardNumbers());
    private final SandboxGatewayResponseGenerator walletSandboxResponseGenerator = new SandboxGatewayResponseGenerator(new SandboxLast4DigitsCardNumbers());
    private final SandboxGatewayResponseGenerator recurringSandboxResponseGenerator = new SandboxGatewayResponseGenerator(new SandboxFirst6AndLast4CardNumbers());

    @Inject
    public SandboxPaymentProvider(@Named("DefaultRefundEntityFactory") RefundEntityFactory refundEntityFactory) {
        this.refundEntityFactory = refundEntityFactory;
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        this.sandboxWalletAuthorisationHandler = new SandboxWalletAuthorisationHandler(walletSandboxResponseGenerator);
    }

    @Override
    public boolean canQueryPaymentStatus() {
        return false;
    }
    
    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(CardAuthorisationGatewayRequest request, ChargeEntity charge) {
        return authorise(request.getAuthCardDetails().getCardNo());
    }

    @Override
    public GatewayResponse authoriseMotoApi(CardAuthorisationGatewayRequest request) {
        return authorise(request.getAuthCardDetails().getCardNo());
    }

    @Override
    public GatewayResponse authoriseUserNotPresent(RecurringPaymentAuthorisationGatewayRequest request) {
        var paymentInstrumentEntity = request.getPaymentInstrument()
                .orElseThrow(() -> new IllegalArgumentException("Expected charge to have payment instrument but it does not"));
        var firstDigitsCardNumber = paymentInstrumentEntity.getCardDetails().getFirstDigitsCardNumber().toString();
        var lastDigitsCardNumber = paymentInstrumentEntity.getCardDetails().getLastDigitsCardNumber().toString();

        if (RECURRING_FIRST_AUTHORISE_SUCCESS_SUBSEQUENT_DECLINE.startsWith(firstDigitsCardNumber) && RECURRING_FIRST_AUTHORISE_SUCCESS_SUBSEQUENT_DECLINE.endsWith(lastDigitsCardNumber)) {
            return recurringSandboxResponseGenerator.getSandboxGatewayResponse(false);
        }
        return recurringSandboxResponseGenerator.getSandboxGatewayResponse(firstDigitsCardNumber.concat(lastDigitsCardNumber));
    }

    /**
     * IMPORTANT: this method should not attempt to update the Charge in the database. This is because it is executed
     * on a worker thread and the initiating thread can attempt to update the Charge status while it is still being
     * executed.
     */
    private GatewayResponse authorise(String cardNumber) {
        var gatewayResponse = fullCardNumberSandboxResponseGenerator.getSandboxGatewayResponse(cardNumber);
        return gatewayResponse;
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
    public GatewayResponse<BaseAuthoriseResponse> authoriseApplePay(ApplePayAuthorisationGatewayRequest authorisationGatewayRequest) throws GatewayException {
        return sandboxWalletAuthorisationHandler.authoriseApplePay(authorisationGatewayRequest);
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseGooglePay(GooglePayAuthorisationGatewayRequest authorisationGatewayRequest) throws GatewayException {
        return sandboxWalletAuthorisationHandler.authoriseGooglePay(authorisationGatewayRequest);
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
    public SandboxAuthorisationRequestSummary generateAuthorisationRequestSummary(ChargeEntity chargeEntity, AuthCardDetails authCardDetails, boolean isSetupAgreement) {
        return new SandboxAuthorisationRequestSummary(authCardDetails);
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

    @Override
    public void deleteStoredPaymentDetails(DeleteStoredPaymentDetailsGatewayRequest request) {
        //No action needs to be taken in sandbox in response to a deleteStoredPaymentDetails task
    }

    @Override
    public RefundEntityFactory getRefundEntityFactory() {
        return refundEntityFactory;
    }
}
