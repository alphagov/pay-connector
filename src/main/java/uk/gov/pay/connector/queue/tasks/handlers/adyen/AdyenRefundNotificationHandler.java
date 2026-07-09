package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import com.adyen.model.notification.NotificationRequestItem;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class AdyenRefundNotificationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenRefundNotificationHandler.class);
    private static final String GATEWAY_TRANSACTION_ID = "gateway_transaction_id";

    private final RefundNotificationProcessor refundNotificationProcessor;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public AdyenRefundNotificationHandler(RefundNotificationProcessor refundNotificationProcessor,
                                          GatewayAccountService gatewayAccountService) {
        this.refundNotificationProcessor = refundNotificationProcessor;
        this.gatewayAccountService = gatewayAccountService;
    }

    public void process(NotificationRequestItem item, Charge charge) {
        RefundStatus targetStatus = item.isSuccess() ? RefundStatus.REFUNDED : RefundStatus.REFUND_ERROR;

        gatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())
                .ifPresentOrElse(gatewayAccount -> {
                            LOGGER.atInfo()
                                    .setMessage("Processing Adyen refund notification")
                                    .addKeyValue(PAYMENT_EXTERNAL_ID, charge.getExternalId())
                                    .addKeyValue(GATEWAY_TRANSACTION_ID, item.getOriginalReference())
                                    .addKeyValue("success", item.isSuccess())
                                    .log();

                            refundNotificationProcessor.invoke(
                                    PaymentGatewayName.ADYEN,
                                    targetStatus,
                                    gatewayAccount,
                                    item.getPspReference(),
                                    item.getOriginalReference(),
                                    charge);
                        },
                        () -> LOGGER.atWarn()
                                .setMessage("GatewayAccount not found for refund notification")
                                .addKeyValue(PAYMENT_EXTERNAL_ID, charge.getExternalId())
                                .addKeyValue(GATEWAY_TRANSACTION_ID, item.getOriginalReference())
                                .log());
    }
}
