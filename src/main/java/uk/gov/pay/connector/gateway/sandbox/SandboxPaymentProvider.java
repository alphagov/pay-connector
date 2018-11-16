package uk.gov.pay.connector.gateway.sandbox;

import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.applepay.ApplePayAuthoriser;
import uk.gov.pay.connector.applepay.AuthorisationApplePayGatewayRequest;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.StatusMapper;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.GatewayParamsFor3ds;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationCardGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;

import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public class SandboxPaymentProvider implements PaymentProvider<BaseResponse, String>, ApplePayAuthoriser {
    private final static Logger LOGGER = LoggerFactory.getLogger(SandboxPaymentProvider.class);

    private final ExternalRefundAvailabilityCalculator externalRefundAvailabilityCalculator;

    private SandboxCaptureHandler sandboxCaptureHandler;

    public SandboxPaymentProvider() {
        this.externalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();
        this.sandboxCaptureHandler = new SandboxCaptureHandler();
    }

    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(AuthorisationCardGatewayRequest request) {
        LOGGER.info("sandbox auth");
        String cardNumber = request.getAuthCardDetails().getCardNo();
        LOGGER.info("sandbox auth for " + cardNumber);
        GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();

        if (SandboxCardNumbers.isErrorCard(cardNumber)) {
            CardError errorInfo = SandboxCardNumbers.cardErrorFor(cardNumber);
            return gatewayResponseBuilder
                    .withGatewayError(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR))
                    .build();
        } else if (SandboxCardNumbers.isRejectedCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(false);
        } else if (SandboxCardNumbers.isValidCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(true);
        }

        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR))
                .build();
    }
    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise(AuthorisationApplePayGatewayRequest request) {
        LOGGER.info("sandbox apple pay auth");
        String cardNumber = request.getAppleDecryptedPaymentData().getApplicationPrimaryAccountNumber();
        LOGGER.info("sandbox auth for " + cardNumber);
        GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();

        if (SandboxCardNumbers.isErrorCard(cardNumber)) {
            CardError errorInfo = SandboxCardNumbers.cardErrorFor(cardNumber);
            return gatewayResponseBuilder
                    .withGatewayError(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR))
                    .build();
        } else if (SandboxCardNumbers.isRejectedCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(false);
        } else if (SandboxCardNumbers.isValidCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(true);
        }

        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR))
                .build();
    }
    @Override
    public GatewayResponse<BaseAuthoriseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("3D Secure not implemented for Sandbox", GENERIC_GATEWAY_ERROR))
                .build();
    }

    @Override
    public CaptureHandler getCaptureHandler() {
        return sandboxCaptureHandler;
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
    public GatewayResponse cancel(CancelGatewayRequest request) {
        return createGatewayBaseCancelResponse();
    }

    @Override
    public Boolean isNotificationEndpointSecured() {
        return false;
    }

    @Override
    public String getNotificationDomain() {
        return null;
    }

    @Override
    public boolean verifyNotification(Notification notification, GatewayAccountEntity gatewayAccountEntity) {
        return true;
    }

    @Override
    public GatewayResponse refund(RefundGatewayRequest request) {
        return createGatewayBaseRefundResponse(request);
    }

    @Override
    public Either<String, Notifications<String>> parseNotification(String payload) {
        throw new UnsupportedOperationException("Sandbox account does not support notifications");
    }

    @Override
    public StatusMapper<String> getStatusMapper() {
        return SandboxStatusMapper.get();
    }

    @Override
    public ExternalChargeRefundAvailability getExternalChargeRefundAvailability(ChargeEntity chargeEntity) {
        return externalRefundAvailabilityCalculator.calculate(chargeEntity);
    }

    private GatewayResponse<BaseAuthoriseResponse> createGatewayBaseAuthoriseResponse(boolean isAuthorised) {
        GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseAuthoriseResponse() {

            private final String transactionId = randomUUID().toString();

            @Override
            public AuthoriseStatus authoriseStatus() {
                return isAuthorised ? AuthoriseStatus.AUTHORISED : AuthoriseStatus.REJECTED;
            }

            @Override
            public String getTransactionId() {
                return transactionId;
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
            public Optional<GatewayParamsFor3ds> getGatewayParamsFor3ds() {
                return Optional.empty();
            }

            @Override
            public String toString() {
                return "Sandbox authorisation response (transactionId: " + getTransactionId()
                        + ", isAuthorised: " + isAuthorised + ')';
            }
        }).build();
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
