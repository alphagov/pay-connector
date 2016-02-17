package uk.gov.pay.connector.service.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.PaymentProvider;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static uk.gov.pay.connector.model.CancelResponse.aSuccessfulCancelResponse;
import static uk.gov.pay.connector.model.CaptureResponse.aSuccessfulCaptureResponse;
import static uk.gov.pay.connector.model.GatewayErrorType.GenericGatewayError;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.chargeStatusFrom;
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
    public StatusUpdates handleNotification(String inboundNotification, Function<ChargeStatusRequest, Boolean> payloadChecks, Function<String, GatewayAccount> accountFinder, Consumer<StatusUpdates> accountUpdater) {
        try {
            JsonNode node = objectMapper.readValue(inboundNotification, JsonNode.class);

            String transaction_id = node.get("transaction_id").textValue();
            String newStatus = node.get("status").textValue();

            StatusUpdates statusUpdates = StatusUpdates.withUpdate("OK", ImmutableList.of(Pair.of(transaction_id, chargeStatusFrom(newStatus))));
            accountUpdater.accept(statusUpdates);
            return statusUpdates;
        } catch (Exception e) {
            LOGGER.error("Error understanding sandbox notification: " + inboundNotification, e);
            return StatusUpdates.noUpdate("OK");
        }
    }

}
