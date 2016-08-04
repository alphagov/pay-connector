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
import uk.gov.pay.connector.service.PaymentProvider;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static uk.gov.pay.connector.model.CancelGatewayResponse.successfulCancelResponse;
import static uk.gov.pay.connector.model.CaptureGatewayResponse.successfulCaptureResponse;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.SUCCEDED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.*;

public class SandboxPaymentProvider implements PaymentProvider {

    private final static Logger LOGGER = LoggerFactory.getLogger(SandboxPaymentProvider.class);

    private final ObjectMapper objectMapper;

    public SandboxPaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AuthorisationGatewayResponse authorise(AuthorisationGatewayRequest request) {

        String cardNumber = request.getCard().getCardNo();
        String transactionId = UUID.randomUUID().toString();

        if (isInvalidCard(cardNumber)) {
            CardError errorInfo = cardErrorFor(cardNumber);
            return new AuthorisationGatewayResponse(FAILED, new ErrorResponse(errorInfo.getErrorMessage(), GENERIC_GATEWAY_ERROR), errorInfo.getNewErrorStatus(), transactionId);
        }

        if (isValidCard(cardNumber)) {
            return new AuthorisationGatewayResponse(SUCCEDED, null, AUTHORISATION_SUCCESS, transactionId);
        }

        return new AuthorisationGatewayResponse(FAILED, new ErrorResponse("Unsupported card details.", GENERIC_GATEWAY_ERROR), AUTHORISATION_ERROR, transactionId);
    }

    @Override
    public CaptureGatewayResponse capture(CaptureGatewayRequest request) {
        return successfulCaptureResponse(CAPTURE_SUBMITTED);
    }

    @Override
    public CancelGatewayResponse cancel(CancelGatewayRequest request) {
        return successfulCancelResponse(SYSTEM_CANCELLED);
    }

    @Override
    public RefundGatewayResponse refund(RefundGatewayRequest request) {
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
