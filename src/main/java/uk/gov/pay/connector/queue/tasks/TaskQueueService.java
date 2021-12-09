package uk.gov.pay.connector.queue.tasks;

import org.apache.commons.lang3.ArrayUtils;
import org.jooq.tools.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.TaskQueueConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import javax.inject.Inject;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class TaskQueueService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME = "collect_fee_for_stripe_failed_payment";
    
    private final TaskQueue taskQueue;
    private final ConnectorConfiguration connectorConfiguration;

    @Inject
    public TaskQueueService(TaskQueue taskQueue,
                            ConnectorConfiguration connectorConfiguration) {
        this.taskQueue = taskQueue;
        this.connectorConfiguration = connectorConfiguration;
    }

    public void offerTasksOnStateTransition(ChargeEntity chargeEntity) {
        if (!connectorConfiguration.getTaskQueueConfig().getCollectFeeForStripeFailedPayments())
            return;
        
        boolean isTerminallyFailed = chargeEntity.getChargeStatus().isExpungeable() &&
                chargeEntity.getChargeStatus().toExternal() != ExternalChargeState.EXTERNAL_SUCCESS;

        if (isTerminallyFailed &&
                chargeEntity.getPaymentGatewayName() == PaymentGatewayName.STRIPE &&
                !StringUtils.isEmpty(chargeEntity.getGatewayTransactionId())) {
            addCollectStripeFeeForFailedPaymentTask(chargeEntity);
        }
    }

    private void addCollectStripeFeeForFailedPaymentTask(ChargeEntity chargeEntity) {
        PaymentTask paymentTask = new PaymentTask(chargeEntity.getExternalId(), COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME);
        try {
            taskQueue.addTaskToQueue(paymentTask);
            logger.info("Added payment task message to queue", ArrayUtils.addAll(
                    chargeEntity.getStructuredLoggingArgs(),
                    kv("task_name", COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME)
            ));
        } catch (Exception e) {
            logger.error("Error adding payment task message to queue", ArrayUtils.addAll(
                    chargeEntity.getStructuredLoggingArgs(),
                    kv("task_name", COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME),
                    kv("error", e.getMessage()),
                    kv("stack_trace", e.getStackTrace())
            ));
        }
    }
}
