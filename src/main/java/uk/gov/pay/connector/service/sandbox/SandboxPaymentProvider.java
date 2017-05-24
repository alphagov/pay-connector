package uk.gov.pay.connector.service.sandbox;

import fj.data.Either;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.gateway.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.service.*;

import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.gateway.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.*;

public class SandboxPaymentProvider extends BasePaymentProvider<BaseResponse> {

    public SandboxPaymentProvider() {
        super(null);
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        String cardNumber = request.getAuthCardDetails().getCardNo();
        GatewayResponseBuilder<BaseResponse> gatewayResponseBuilder = responseBuilder();

        if (isErrorCard(cardNumber)) {
            CardError errorInfo = cardErrorFor(cardNumber);
            return gatewayResponseBuilder
                    .withGatewayError(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR))
                    .build();
        } else if (isRejectedCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(false);
        } else if (isValidCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(true);
        }

        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR))
                .build();
    }

    @Override
    public GatewayResponse<BaseResponse> authorise3dsResponse(Auth3dsResponseGatewayRequest request) {
        GatewayResponseBuilder<BaseResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder
                .withGatewayError(new GatewayError("3D Secure not implemented for Sandbox", GENERIC_GATEWAY_ERROR))
                .build();
    }

    @Override
    public String getPaymentGatewayName() {
        return PaymentGatewayName.SANDBOX.getName();
    }

    @Override
    public Optional<String> generateTransactionId() {
        return Optional.of(randomUUID().toString());
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {
        return createGatewayBaseCaptureResponse();
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
    public boolean verifyNotification(Notification notification, String passphrase) {
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
    public StatusMapper getStatusMapper() {
        return SandboxStatusMapper.get();
    }

    private GatewayResponse<BaseAuthoriseResponse> createGatewayBaseAuthoriseResponse(boolean isAuthorised) {
        GatewayResponseBuilder<BaseAuthoriseResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseAuthoriseResponse() {

            @Override
            public AuthoriseStatus authoriseStatus() {
                return isAuthorised ? AuthoriseStatus.AUTHORISED : AuthoriseStatus.REJECTED;
            }

            @Override
            public String getTransactionId() {
                return randomUUID().toString();
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
            public String get3dsPaRequest() {
                return null;
            }

            @Override
            public String get3dsIssuerUrl() {
                return null;
            }
        }).build();
    }

    private GatewayResponse<BaseCancelResponse> createGatewayBaseCancelResponse() {
        GatewayResponseBuilder<BaseCancelResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseCancelResponse() {
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
                return randomUUID().toString();
            }

            @Override
            public CancelStatus cancelStatus() {
                return CancelStatus.CANCELLED;
            }
        }).build();
    }

    private GatewayResponse<BaseCaptureResponse> createGatewayBaseCaptureResponse() {
        GatewayResponseBuilder<BaseCaptureResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseCaptureResponse() {
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
                return randomUUID().toString();
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
        }).build();
    }
}
