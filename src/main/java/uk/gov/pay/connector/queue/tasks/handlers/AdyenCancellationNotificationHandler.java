package uk.gov.pay.connector.queue.tasks.handlers;

import com.adyen.model.notification.NotificationRequestItem;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class AdyenCancellationNotificationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenCancellationNotificationHandler.class);

    private final ChargeNotificationProcessor chargeNotificationProcessor;

    @Inject
    public AdyenCancellationNotificationHandler(ChargeNotificationProcessor chargeNotificationProcessor) {
        this.chargeNotificationProcessor = chargeNotificationProcessor;
    }

    public void process(NotificationRequestItem item, Charge foundCharge) {
        String gatewayTransactionId = item.getOriginalReference();

        if (foundCharge.isHistoric()) {
            LOGGER.atInfo().setMessage("Ignored Adyen cancellation webhook for historic charge")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())
                    .addKeyValue("gateway_Transaction_id", gatewayTransactionId)
                    .log();
            return;
        }

        ChargeStatus currentStatus = ChargeStatus.fromString(foundCharge.getStatus());
        Optional<ChargeStatus> targetStatus = getCancellationTargetStatus(currentStatus, item.isSuccess());

        if (targetStatus.isPresent()) {
            chargeNotificationProcessor.invoke(gatewayTransactionId, foundCharge, targetStatus.get(),
                    ZonedDateTime.ofInstant(item.getEventDate().toInstant(), ZoneId.of("UTC")));
            return;
        }

        if (isIgnoredDuplicateCancellation(currentStatus, item.isSuccess())) {
            LOGGER.atInfo().setMessage("Ignored Adyen cancellation webhook")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())
                    .addKeyValue("gateway_Transaction_id", gatewayTransactionId)
                    .addKeyValue("status", currentStatus.getValue())
                    .addKeyValue("success", item.isSuccess()).log();
            return;
        }

        LOGGER.atWarn().setMessage("Unexpected state transition for Adyen cancellation webhook")
                .addKeyValue(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())
                .addKeyValue("gateway_Transaction_id", gatewayTransactionId)
                .addKeyValue("status", currentStatus.getValue())
                .addKeyValue("success", item.isSuccess()).log();
    }

    private Optional<ChargeStatus> getCancellationTargetStatus(ChargeStatus currentStatus, boolean success) {
        return success
                ? switch (currentStatus) {
            case USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR -> Optional.of(USER_CANCELLED);
            case SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCEL_ERROR -> Optional.of(SYSTEM_CANCELLED);
            default -> Optional.empty();
        }
                : switch (currentStatus) {
            case USER_CANCEL_SUBMITTED -> Optional.of(USER_CANCEL_ERROR);
            case SYSTEM_CANCEL_SUBMITTED -> Optional.of(SYSTEM_CANCEL_ERROR);
            default -> Optional.empty();
        };
    }

    private boolean isIgnoredDuplicateCancellation(ChargeStatus currentStatus, boolean success) {
        return success
                ? switch (currentStatus) {
            case USER_CANCELLED, SYSTEM_CANCELLED -> true;
            default -> false;
        }
                : switch (currentStatus) {
            case USER_CANCEL_ERROR, SYSTEM_CANCEL_ERROR, SYSTEM_CANCELLED -> true;
            default -> false;
        };
    }
}
