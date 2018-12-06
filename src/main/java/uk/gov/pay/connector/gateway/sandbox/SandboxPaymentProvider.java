package uk.gov.pay.connector.gateway.sandbox;

import uk.gov.pay.connector.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.sandbox.applepay.SandboxApplePayAuthorisationHandler;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.GatewayResponseGenerator;
import uk.gov.pay.connector.paymentprocessor.service.PaymentProviderAuthorisationResponse;

import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public class SandboxPaymentProvider implements PaymentProvider {

    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;

    private SandboxCaptureHandler sandboxCaptureHandler;
    private SandboxApplePayAuthorisationHandler sandboxApplePayAuthorisationHandler;

    public SandboxPaymentProvider() {
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        this.sandboxCaptureHandler = new SandboxCaptureHandler();
        this.sandboxApplePayAuthorisationHandler = new SandboxApplePayAuthorisationHandler();
    }

    @Override
    public PaymentProviderAuthorisationResponse authorise(CardAuthorisationGatewayRequest request) {
        String cardNumber = request.getAuthCardDetails().getCardNo();
        GatewayResponseBuilder<BaseResponse> gatewayResponseBuilder = responseBuilder();

        if (SandboxCardNumbers.isErrorCard(cardNumber)) {
            CardError errorInfo = SandboxCardNumbers.cardErrorFor(cardNumber);
            final GatewayResponse gatewayResponse = gatewayResponseBuilder
                    .withGatewayError(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR))
                    .build();
            return PaymentProviderAuthorisationResponse.from(request.getChargeExternalId(), gatewayResponse);
        } else if (SandboxCardNumbers.isRejectedCard(cardNumber)) {
            final GatewayResponse gatewayResponse = GatewayResponseGenerator.getSandboxGatewayResponse(false);
            return PaymentProviderAuthorisationResponse.from(request.getChargeExternalId(), gatewayResponse);
        } else if (SandboxCardNumbers.isValidCard(cardNumber)) {
            final GatewayResponse gatewayResponse = GatewayResponseGenerator.getSandboxGatewayResponse(true);
            return PaymentProviderAuthorisationResponse.from(request.getChargeExternalId(), gatewayResponse);
        }

        final GatewayResponse gatewayResponse = gatewayResponseBuilder
                .withGatewayError(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR))
                .build();
        return PaymentProviderAuthorisationResponse.from(request.getChargeExternalId(), gatewayResponse);
    }

    @Override
    public Gateway3DSAuthorisationResponse authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        return Gateway3DSAuthorisationResponse.ofException();
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authoriseApplePay(ApplePayAuthorisationGatewayRequest request) {
        return sandboxApplePayAuthorisationHandler.authorise(request);
    }

    @Override
    public GatewayResponse<BaseCaptureResponse> capture(CaptureGatewayRequest request) {
        return sandboxCaptureHandler.capture(request);
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.SANDBOX;
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
    public GatewayResponse<BaseRefundResponse> refund(RefundGatewayRequest request) {
        return createGatewayBaseRefundResponse(request);
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

    private GatewayResponse<BaseRefundResponse> createGatewayBaseRefundResponse(RefundGatewayRequest request) {
        GatewayResponseBuilder<BaseRefundResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseRefundResponse() {
            @Override
            public Optional<String> getReference() {
                return Optional.of(request.getReference());
            }

            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }

            @Override
            public String toString() {
                return getReference()
                        .map(reference -> "Sandbox refund response (reference: " + reference + ')')
                        .orElse("Sandbox refund response");
            }
        }).build();
    }
}
