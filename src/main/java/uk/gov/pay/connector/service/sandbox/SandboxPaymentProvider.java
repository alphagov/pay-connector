package uk.gov.pay.connector.service.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.resources.PaymentGatewayName;
import uk.gov.pay.connector.service.*;

import java.util.Optional;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.*;

public class SandboxPaymentProvider extends BasePaymentProvider<BaseResponse> {

    private final static Logger LOGGER = LoggerFactory.getLogger(SandboxPaymentProvider.class);

    private final ObjectMapper objectMapper;

    public SandboxPaymentProvider(ObjectMapper objectMapper) {
        super(null);
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayResponse authorise(AuthorisationGatewayRequest request) {
        String cardNumber = request.getCard().getCardNo();

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
    public GatewayResponse refund(RefundGatewayRequest request) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public Either<String, Notifications<String>> parseNotification(String payload) {
        try {
            JsonNode node = objectMapper.readValue(payload, JsonNode.class);

            String transactionId = node.get("transaction_id").textValue();
            String status = node.get("status").textValue();

            Notifications.Builder<String> builder = Notifications.builder();
            builder.addNotificationFor(transactionId, "", status);
            return right(builder.build());
        } catch (Exception e) {
            LOGGER.error("Error understanding sandbox notification: " + payload, e);
            return left("Error understanding sandbox notification: " + payload);
        }
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
}
