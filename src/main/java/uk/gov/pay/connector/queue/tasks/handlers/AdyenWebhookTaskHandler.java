package uk.gov.pay.connector.queue.tasks.handlers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static com.adyen.model.notification.NotificationRequestItem.EVENT_CODE_CAPTURE;
import static com.adyen.model.notification.NotificationRequestItem.EVENT_CODE_REFUND;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class AdyenWebhookTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenWebhookTaskHandler.class);
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    private final ChargeService chargeService;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final RefundNotificationProcessor refundNotificationProcessor;
    private final GatewayAccountService gatewayAccountService;
    private final AdyenNotificationService adyenNotificationService;

    @Inject
    public AdyenWebhookTaskHandler(ChargeService chargeService,
                                   ChargeNotificationProcessor chargeNotificationProcessor,
                                   RefundNotificationProcessor refundNotificationProcessor,
                                   GatewayAccountService gatewayAccountService,
                                   AdyenNotificationService adyenNotificationService) {
        this.chargeService = chargeService;
        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.refundNotificationProcessor = refundNotificationProcessor;
        this.gatewayAccountService = gatewayAccountService;
        this.adyenNotificationService = adyenNotificationService;
    }

    @Transactional
    public void processAdyenWebhookNotification(String payload) {
        NotificationRequest notificationRequest =
                adyenNotificationService.deserialisePayloadToNotificationRequest(payload);

        List<NotificationRequestItem> items = adyenNotificationService.extractNotificationItems(notificationRequest);

        for (NotificationRequestItem item : items) {
            processNotificationItem(item);
        }
    }

    private void processNotificationItem(NotificationRequestItem item) {
        String eventCode = item.getEventCode();
        if (eventCode == null) {
            LOGGER.atWarn()
                    .setMessage("Ignoring Adyen webhook item with null event code")
                    .addKeyValue("originalReference", item.getOriginalReference())
                    .log();
            return;
        }

        switch (eventCode) {
            case EVENT_CODE_CAPTURE -> processCapturedNotification(item);
            case EVENT_CODE_REFUND -> processRefundNotification(item);
            default -> LOGGER.atDebug()
                    .setMessage("Ignoring unsupported Adyen webhook event")
                    .addKeyValue("eventCode", eventCode)
                    .addKeyValue("originalReference", item.getOriginalReference())
                    .log();
        }
    }

    private void processRefundNotification(NotificationRequestItem item) {
        String gatewayTransactionId = item.getOriginalReference();
        Optional<Charge> optionalCharge = chargeService.findByProviderAndTransactionIdFromDbOrLedger(
                ADYEN.getName(),
                gatewayTransactionId);

        if (optionalCharge.isEmpty()) {
            LOGGER.atWarn()
                    .setMessage("Charge not found in Connector or Ledger for Adyen refund webhook")
                    .addKeyValue("gatewayTransactionId", gatewayTransactionId)
                    .log();
            return;
        }

        Charge charge = optionalCharge.get();
        Optional<GatewayAccountEntity> gatewayAccount = gatewayAccountService.getGatewayAccount(
                charge.getGatewayAccountId());

        if (gatewayAccount.isEmpty()) {
            LOGGER.atError()
                    .setMessage("Gateway account not found for charge")
                    .addKeyValue(PAYMENT_EXTERNAL_ID, charge.getExternalId())
                    .log();
            return;
        }

        refundNotificationProcessor.invoke(
                PaymentGatewayName.ADYEN,
                item.isSuccess() ? RefundStatus.REFUNDED : RefundStatus.REFUND_ERROR,
                gatewayAccount.get(),
                item.getPspReference(),
                gatewayTransactionId,
                charge);
    }

    private void processCapturedNotification(NotificationRequestItem item) {
        String gatewayTransactionId = item.getOriginalReference();

        Optional<Charge> charge = chargeService.findByProviderAndTransactionIdFromDbOrLedger(
                ADYEN.getName(), gatewayTransactionId);

        if (charge.isEmpty()) {
            LOGGER.atWarn()
                    .setMessage("Charge not found in Connector or Ledger for Adyen capture webhook")
                    .addKeyValue("gatewayTransactionId", gatewayTransactionId)
                    .log();
            return;
        }

        Charge foundCharge = charge.get();
        ChargeStatus targetStatus = item.isSuccess() ? CAPTURED : CAPTURE_ERROR;

        if (foundCharge.isHistoric()) {
            processCaptureForHistoricCharge(foundCharge, gatewayTransactionId, targetStatus);
        } else {
            chargeNotificationProcessor.invoke(gatewayTransactionId, foundCharge, targetStatus, ZonedDateTime.ofInstant(
                    item.getEventDate().toInstant(), UTC_ZONE));
        }

        if (!item.isSuccess()) {
            LOGGER.atError()
                    .setMessage("Capture failed")
                    .addKeyValue("gateway_transaction_id", gatewayTransactionId)
                    .addKeyValue("eventCode", item.getEventCode())
                    .log();
        }
    }

    private void processCaptureForHistoricCharge(Charge foundCharge, String gatewayTransactionId, ChargeStatus targetStatus) {
        Optional<GatewayAccountEntity> gatewayAccount = gatewayAccountService.getGatewayAccount(
                foundCharge.getGatewayAccountId());

        gatewayAccount.ifPresentOrElse(
                gatewayAccountEntity -> chargeNotificationProcessor.processCaptureNotificationForExpungedCharge(
                        gatewayAccountEntity, gatewayTransactionId, foundCharge, targetStatus),
                () -> LOGGER.atError()
                        .setMessage("Gateway account not found for charge")
                        .addKeyValue(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())
                        .log());
    }
}
