package uk.gov.pay.connector.queue.tasks.handlers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;


public class AdyenWebhookTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenWebhookTaskHandler.class);
    private final ChargeService chargeService;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final GatewayAccountService gatewayAccountService;
    private final AdyenNotificationService adyenNotificationService;

    @Inject
    public AdyenWebhookTaskHandler(ChargeService chargeService, 
                                   ChargeNotificationProcessor chargeNotificationProcessor,
                                   GatewayAccountService gatewayAccountService,
                                   AdyenNotificationService adyenNotificationService) {
        this.chargeService = chargeService;
        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.gatewayAccountService = gatewayAccountService;
        this.adyenNotificationService = adyenNotificationService;
    }

    public void processAdyenWebhookNotification(String payload) {
        NotificationRequest notificationRequest =
                adyenNotificationService.deserialisePayloadToNotificationRequest(payload);

        List<NotificationRequestItem> items = adyenNotificationService.extractNotificationItem(notificationRequest);

        for (NotificationRequestItem item : items) {
            processCapturedNotification(item);
        }
    }

    private void processCapturedNotification(NotificationRequestItem item) {
        String gatewayTransactionId = item.getOriginalReference();

        Optional<Charge> charge = chargeService.findByProviderAndTransactionIdFromDbOrLedger(
                ADYEN.getName(), gatewayTransactionId);

        if (charge.isPresent()) {
            Charge foundCharge = charge.get();
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

        } else {
            LOGGER.warn("Charge not found in Connector or Ledger for Adyen capture webhook",
                    kv("gatewayTransactionId", gatewayTransactionId));
        }

    }
    
}
