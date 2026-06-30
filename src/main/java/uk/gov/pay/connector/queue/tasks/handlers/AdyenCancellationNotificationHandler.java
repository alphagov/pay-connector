package uk.gov.pay.connector.queue.tasks.handlers;

import com.adyen.model.notification.NotificationRequestItem;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class AdyenCancellationNotificationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenCancellationNotificationHandler.class);

    private final ChargeService chargeService;
    private final ChargeNotificationProcessor chargeNotificationProcessor;

    @Inject
    public AdyenCancellationNotificationHandler(ChargeService chargeService,
                                                ChargeNotificationProcessor chargeNotificationProcessor) {
        this.chargeService = chargeService;
        this.chargeNotificationProcessor = chargeNotificationProcessor;
    }

    public void process(NotificationRequestItem item) {
        String gatewayTransactionId = item.getOriginalReference();

        Optional<Charge> charge = chargeService.findByProviderAndTransactionIdFromDbOrLedger(
                "adyen", gatewayTransactionId);

        if (charge.isEmpty()) {
            LOGGER.atInfo().setMessage("Charge not found in Connector for Adyen cancellation webhook")
                    .addKeyValue("gatewayTransactionId", gatewayTransactionId).log();
            return;
        }

        Charge foundCharge = charge.get();

        if (foundCharge.isHistoric()) {
            LOGGER.atInfo().setMessage("Ignored Adyen cancellation webhook for historic charge")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())
                    .addKeyValue("gatewayTransactionId", gatewayTransactionId)
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
                    .addKeyValue("gatewayTransactionId", gatewayTransactionId)
                    .addKeyValue("status", currentStatus.getValue())
                    .addKeyValue("success", item.isSuccess()).log();
            return;
        }

        LOGGER.atWarn().setMessage("Unexpected state transition for Adyen cancellation webhook")
                .addKeyValue(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())
                .addKeyValue("gatewayTransactionId", gatewayTransactionId)
                .addKeyValue("status", currentStatus.getValue())
                .addKeyValue("success", item.isSuccess()).log();
    }

    private Optional<ChargeStatus> getCancellationTargetStatus(ChargeStatus currentStatus, boolean success) {
        if (success) {
            if (currentStatus == USER_CANCEL_SUBMITTED || currentStatus == USER_CANCEL_ERROR) {
                return Optional.of(USER_CANCELLED);
            }
            if (currentStatus == SYSTEM_CANCEL_SUBMITTED || currentStatus == SYSTEM_CANCEL_ERROR) {
                return Optional.of(SYSTEM_CANCELLED);
            }
            return Optional.empty();
        }

        if (currentStatus == USER_CANCEL_SUBMITTED) {
            return Optional.of(USER_CANCEL_ERROR);
        }
        if (currentStatus == SYSTEM_CANCEL_SUBMITTED) {
            return Optional.of(SYSTEM_CANCEL_ERROR);
        }
        return Optional.empty();
    }

    private boolean isIgnoredDuplicateCancellation(ChargeStatus currentStatus, boolean success) {
        if (success) {
            return currentStatus == USER_CANCELLED || currentStatus == SYSTEM_CANCELLED;
        }
        return currentStatus == USER_CANCEL_ERROR || currentStatus == SYSTEM_CANCEL_ERROR;
    }
}
