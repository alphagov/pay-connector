package uk.gov.pay.connector.service.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.PaymentProvider;

import java.util.UUID;

import static uk.gov.pay.connector.model.CancelResponse.aSuccessfulCancelResponse;
import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulCaptureResponse;
import static uk.gov.pay.connector.model.GatewayErrorType.GenericGatewayError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.service.sandbox.SandboxCardNumbers.*;

public class SandboxPaymentProvider implements PaymentProvider {

    private final static Logger LOGGER = LoggerFactory.getLogger(SandboxPaymentProvider.class);

    private final ObjectMapper objectMapper;

    public SandboxPaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AuthorisationResponse authorise(AuthorisationRequest request) {

        String cardNumber = request.getCard().getCardNo();
        String transactionId = UUID.randomUUID().toString();

        if (isInvalidCard(cardNumber)) {
            CardError errorInfo = cardErrorFor(cardNumber);
            return new AuthorisationResponse(false, new GatewayError(errorInfo.getErrorMessage(), GenericGatewayError), errorInfo.getNewErrorStatus(), transactionId);
        }

        if (isValidCard(cardNumber)) {
            return new AuthorisationResponse(true, null, AUTHORISATION_SUCCESS, transactionId);
        }

        return new AuthorisationResponse(false, new GatewayError("Unsupported card details.", GenericGatewayError), null, transactionId);
    }

    @Override
    public CaptureResponse capture(CaptureRequest request) {
        return aSuccessfulCaptureResponse();
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        return aSuccessfulCancelResponse();
    }

    @Override
    public StatusUpdates newStatusFromNotification(String notification) {
        try {
            JsonNode node = objectMapper.readValue(notification, JsonNode.class);

            String transaction_id = node.get("transaction_id").textValue();
            String newStatus = node.get("status").textValue();

            return StatusUpdates.withUpdate("OK", ImmutableList.of(Pair.of(transaction_id, ChargeStatus.valueOf(newStatus))));
        } catch (Exception e) {
            LOGGER.error("Error understanding sandbox notification: " + notification, e);
            return StatusUpdates.noUpdate("OK");
        }
    }
}
