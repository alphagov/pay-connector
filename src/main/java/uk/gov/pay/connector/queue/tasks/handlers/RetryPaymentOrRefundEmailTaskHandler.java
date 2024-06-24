package uk.gov.pay.connector.queue.tasks.handlers;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.model.RetryPaymentOrRefundEmailTaskData;
import uk.gov.pay.connector.refund.exception.RefundNotFoundRuntimeException;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.time.InstantSource;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.PAYMENT_CONFIRMED;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.REFUND_ISSUED;
import static uk.gov.service.payments.logging.LoggingKeys.RESOURCE_EXTERNAL_ID;

public class RetryPaymentOrRefundEmailTaskHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryPaymentOrRefundEmailTaskHandler.class);
    private final ChargeService chargeService;
    private final RefundService refundService;
    private final GatewayAccountService gatewayAccountService;
    private final UserNotificationService userNotificationService;
    private final TaskQueueService taskQueueService;
    private final long retryFailedEmailAfterSeconds;
    private final InstantSource instantSource;

    @Inject
    public RetryPaymentOrRefundEmailTaskHandler(ChargeService chargeService,
                                                RefundService refundService,
                                                GatewayAccountService gatewayAccountService,
                                                UserNotificationService userNotificationService,
                                                TaskQueueService taskQueueService,
                                                ConnectorConfiguration connectorConfiguration,
                                                InstantSource instantSource) {
        this.chargeService = chargeService;
        this.refundService = refundService;
        this.gatewayAccountService = gatewayAccountService;
        this.userNotificationService = userNotificationService;
        this.taskQueueService = taskQueueService;
        retryFailedEmailAfterSeconds = connectorConfiguration.getNotifyConfiguration().getRetryFailedEmailAfterSeconds();
        this.instantSource = instantSource;
    }

    public void process(RetryPaymentOrRefundEmailTaskData retryPaymentOrRefundEmailTaskData) {
        long timeElapsedInSeconds = instantSource.instant().getEpochSecond() -
                retryPaymentOrRefundEmailTaskData.getFailedAttemptTime().getEpochSecond();

        if (timeElapsedInSeconds >= retryFailedEmailAfterSeconds) {
            if (retryPaymentOrRefundEmailTaskData.getEmailNotificationType() == PAYMENT_CONFIRMED) {
                Charge charge = getCharge(retryPaymentOrRefundEmailTaskData.getResourceExternalId());
                GatewayAccountEntity gatewayAccountEntity = getGatewayAccountEntity(charge.getGatewayAccountId());

                userNotificationService.sendPaymentConfirmedEmailSynchronously(charge, gatewayAccountEntity, false);
            } else if (retryPaymentOrRefundEmailTaskData.getEmailNotificationType() == REFUND_ISSUED) {
                RefundEntity refundEntity = getRefund(retryPaymentOrRefundEmailTaskData.getResourceExternalId());
                Charge charge = getCharge(refundEntity.getChargeExternalId());
                GatewayAccountEntity gatewayAccountEntity = getGatewayAccountEntity(charge.getGatewayAccountId());

                userNotificationService.sendRefundIssuedEmailSynchronously(charge, gatewayAccountEntity, refundEntity, false);
            }
        } else {
            taskQueueService.addRetryFailedPaymentOrRefundEmailTask(retryPaymentOrRefundEmailTaskData);
            LOGGER.info("Added retry failed payment or refund email task message back to task queue",
                    kv("time_elapsed_since_failure", timeElapsedInSeconds),
                    kv(RESOURCE_EXTERNAL_ID, retryPaymentOrRefundEmailTaskData.getResourceExternalId()),
                    kv("email_notification_type", retryPaymentOrRefundEmailTaskData.getEmailNotificationType()));
        }
    }

    private GatewayAccountEntity getGatewayAccountEntity(Long gatewayAccountId) {
        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .orElseThrow(() -> new GatewayAccountNotFoundException(gatewayAccountId));
    }

    private RefundEntity getRefund(String refundExternalId) {
        return refundService.findRefundByExternalId(refundExternalId)
                .orElseThrow(() -> new RefundNotFoundRuntimeException(refundExternalId));
    }

    private Charge getCharge(String externalId) {
        return chargeService.findCharge(externalId)
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(externalId));
    }
}
