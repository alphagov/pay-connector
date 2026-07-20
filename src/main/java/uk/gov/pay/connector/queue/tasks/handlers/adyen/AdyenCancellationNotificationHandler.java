package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import com.adyen.model.notification.NotificationRequestItem;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;

import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenWebhookHandlerSupport.GATEWAY_TRANSACTION_ID;
import static uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenWebhookHandlerSupport.eventDateInUtc;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class AdyenCancellationNotificationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenCancellationNotificationHandler.class);

    private final ChargeNotificationProcessor chargeNotificationProcessor;

    private final List<ChargeStatus> systemCancelledStatuses = List.of(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCELLED, SYSTEM_CANCEL_ERROR);
    private final List<ChargeStatus> userCancelledStatuses = List.of(USER_CANCEL_SUBMITTED, USER_CANCELLED, USER_CANCEL_ERROR);

    @Inject
    public AdyenCancellationNotificationHandler(ChargeNotificationProcessor chargeNotificationProcessor) {
        this.chargeNotificationProcessor = chargeNotificationProcessor;
    }

    public void process(NotificationRequestItem item, Charge foundCharge) {
        String gatewayTransactionId = item.getOriginalReference();

        if (foundCharge.isHistoric()) {
            LOGGER.atInfo().setMessage("Ignored Adyen cancellation webhook for historic charge")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())
                    .addKeyValue(GATEWAY_TRANSACTION_ID, gatewayTransactionId)
                    .log();
            return;
        }

        ChargeStatus currentStatus = ChargeStatus.fromString(foundCharge.getStatus());
        Optional<ChargeStatus> targetStatus = getCancellationTargetStatus(currentStatus, item.isSuccess());

        if (targetStatus.isPresent()) {
            chargeNotificationProcessor.invoke(gatewayTransactionId, foundCharge, targetStatus.get(),
                    eventDateInUtc(item));
            return;
        }

        LOGGER.atWarn().setMessage("Charge is not in expected state for cancellation: {}")
                .addArgument(currentStatus.getValue())
                .addKeyValue(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())
                .addKeyValue(GATEWAY_TRANSACTION_ID, gatewayTransactionId)
                .addKeyValue("status", currentStatus.getValue())
                .addKeyValue("success", item.isSuccess()).log();
    }

    private Optional<ChargeStatus> getCancellationTargetStatus(ChargeStatus currentStatus, boolean success) {
        if (systemCancelledStatuses.contains(currentStatus)) {
            return success ? Optional.of(SYSTEM_CANCELLED) : Optional.of(SYSTEM_CANCEL_ERROR);
        } else if (userCancelledStatuses.contains(currentStatus)) {
            return success ? Optional.of(USER_CANCELLED) : Optional.of(USER_CANCEL_ERROR);
        }

        return Optional.empty();
    }
}
