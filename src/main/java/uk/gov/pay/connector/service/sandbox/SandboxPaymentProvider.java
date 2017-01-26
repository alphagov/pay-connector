package uk.gov.pay.connector.service.sandbox;

import fj.data.Either;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.resources.PaymentGatewayName;
import uk.gov.pay.connector.service.*;

import java.util.Optional;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.*;

public class SandboxPaymentProvider extends BasePaymentProvider<BaseResponse> {

    public SandboxPaymentProvider() {
        super(null);
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        String cardNumber = request.getAuthorisationDetails().getCardNo();

        if (isErrorCard(cardNumber)) {
            CardError errorInfo = cardErrorFor(cardNumber);
            return GatewayResponse.with(new GatewayError(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR));
        } else if (isRejectedCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(false);
        } else if (isValidCard(cardNumber)) {
            return createGatewayBaseAuthoriseResponse(true);
        }

        return GatewayResponse.with(new GatewayError("Unsupported card details.", GENERIC_GATEWAY_ERROR));
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
        return GatewayResponse.with(new BaseAuthoriseResponse() {
            @Override
            public boolean isAuthorised() {
                return isAuthorised;
            }

            @Override
            public AuthoriseStatus authoriseStatus() {
                return null;
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
        });
    }

    private GatewayResponse<BaseCancelResponse> createGatewayBaseCancelResponse() {
        return GatewayResponse.with(new BaseCancelResponse() {
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
        });
    }

    private GatewayResponse<BaseCaptureResponse> createGatewayBaseCaptureResponse() {
        return GatewayResponse.with(new BaseCaptureResponse() {
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
        });
    }

    private GatewayResponse<BaseRefundResponse> createGatewayBaseRefundResponse(RefundGatewayRequest request) {
        return GatewayResponse.with(new BaseRefundResponse() {
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
        });
    }
}
