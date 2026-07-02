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

import static com.adyen.model.notification.NotificationRequestItem.EVENT_CODE_CANCELLATION;
import static com.adyen.model.notification.NotificationRequestItem.EVENT_CODE_CAPTURE;
import static com.adyen.model.notification.NotificationRequestItem.EVENT_CODE_REFUND;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class AdyenWebhookTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenWebhookTaskHandler.class);
    private final ChargeService chargeService;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final RefundNotificationProcessor refundNotificationProcessor;
    private final GatewayAccountService gatewayAccountService;
    private final AdyenNotificationService adyenNotificationService;
    private final AdyenCancellationNotificationHandler adyenCancellationNotificationHandler;

    @Inject
    public AdyenWebhookTaskHandler(ChargeService chargeService,
                                   ChargeNotificationProcessor chargeNotificationProcessor,
                                   RefundNotificationProcessor refundNotificationProcessor,
                                   GatewayAccountService gatewayAccountService,
                                   AdyenNotificationService adyenNotificationService,
                                   AdyenCancellationNotificationHandler adyenCancellationNotificationHandler) {
        this.chargeService = chargeService;
        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.refundNotificationProcessor = refundNotificationProcessor;
        this.gatewayAccountService = gatewayAccountService;
        this.adyenNotificationService = adyenNotificationService;
        this.adyenCancellationNotificationHandler = adyenCancellationNotificationHandler;
    }

    @Transactional
    public void processAdyenWebhookNotification(String payload) {
        NotificationRequest notificationRequest =
                adyenNotificationService.deserialisePayloadToNotificationRequest(payload);

        List<NotificationRequestItem> items = adyenNotificationService.extractNotificationItems(notificationRequest);

        for (NotificationRequestItem item : items) {
            var itemEventCode = item.getEventCode();
            String gatewayTransactionId = item.getOriginalReference();

            var charge = chargeService.findByProviderAndTransactionIdFromDbOrLedger(
                    ADYEN.getName(),
                    gatewayTransactionId);

            if (charge.isEmpty()) {
                LOGGER.atInfo()
                        .setMessage("Charge not found in Connector or Ledger for Adyen {} webhook")
                        .addArgument(itemEventCode.toLowerCase())
                        .addKeyValue("gateway_Transaction_id", gatewayTransactionId)
                        .log();
                return;
            }

            Charge foundCharge = charge.get();

            switch (itemEventCode) {
                case EVENT_CODE_CAPTURE -> processCapturedNotification(item, foundCharge);
                case EVENT_CODE_REFUND -> processRefundNotification(item, foundCharge);
                case EVENT_CODE_CANCELLATION -> adyenCancellationNotificationHandler.process(item, foundCharge);
            }
        }
    }

    private void processRefundNotification(NotificationRequestItem item, Charge foundCharge) {
        var gatewayAccount = gatewayAccountService.getGatewayAccount(
                foundCharge.getGatewayAccountId());
        refundNotificationProcessor.invoke(
                PaymentGatewayName.ADYEN,
                RefundStatus.REFUNDED,
                gatewayAccount.get(),
                item.getPspReference(),
                item.getOriginalReference(),
                foundCharge);
    }

    private void processCapturedNotification(NotificationRequestItem item, Charge foundCharge) {
        String gatewayTransactionId = item.getOriginalReference();

        ChargeStatus targetStatus = item.isSuccess() ? CAPTURED : CAPTURE_ERROR;

        if (foundCharge.isHistoric()) {
            Optional<GatewayAccountEntity> gatewayAccount = gatewayAccountService.getGatewayAccount(
                    foundCharge.getGatewayAccountId());

            gatewayAccount.ifPresentOrElse(gatewayAccountEntity ->
                            chargeNotificationProcessor.processCaptureNotificationForExpungedCharge(gatewayAccountEntity,
                                    gatewayTransactionId, foundCharge, targetStatus),
                    () -> LOGGER.error("GatewayAccount not found for foundCharge",
                            kv(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())));
        } else {
            chargeNotificationProcessor.invoke(gatewayTransactionId, foundCharge, targetStatus, ZonedDateTime.ofInstant(
                    item.getEventDate().toInstant(), ZoneId.of("UTC")));
        }

        if (!item.isSuccess()) {
            LOGGER.error("Capture failed",
                    kv("gateway_transaction_id", gatewayTransactionId),
                    kv("eventCode", item.getEventCode()));
        }
    }

}
