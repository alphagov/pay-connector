package uk.gov.pay.connector.service.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.*;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.*;

public class SandboxPaymentProvider implements PaymentProvider<BaseResponse> {

    private final static Logger LOGGER = LoggerFactory.getLogger(SandboxPaymentProvider.class);

    private final ObjectMapper objectMapper;

    public SandboxPaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GatewayResponse<BaseAuthoriseResponse> createGatewayBaseAuthoriseResponse(boolean isAuthorised) {
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

    public GatewayResponse<BaseCancelResponse> createGatewayBaseCancelResponse() {
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

    public GatewayResponse<BaseCaptureResponse> createGatewayBaseCaptureResponse() {
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
    public GatewayResponse inquire(String transactionId, GatewayAccountEntity gatewayAccount) {
        throw new UnsupportedOperationException("Operation not supported");
    }

    @Override
    public StatusUpdates handleNotification(String inboundNotification, Function<ChargeStatusRequest, Boolean> payloadChecks, Function<String, Optional<GatewayAccountEntity>> accountFinder, Consumer<StatusUpdates> accountUpdater) {
        try {
            JsonNode node = objectMapper.readValue(inboundNotification, JsonNode.class);

            String transaction_id = node.get("transaction_id").textValue();
            String newStatus = node.get("status").textValue();

            StatusUpdates statusUpdates = StatusUpdates.withUpdate("OK", ImmutableList.of(Pair.of(transaction_id, ChargeStatus.fromString(newStatus))));
            accountUpdater.accept(statusUpdates);
            return statusUpdates;
        } catch (Exception e) {
            LOGGER.error("Error understanding sandbox notification: " + inboundNotification, e);
            return StatusUpdates.noUpdate("OK");
        }
    }

}
