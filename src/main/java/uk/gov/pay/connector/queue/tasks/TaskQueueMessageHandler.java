package uk.gov.pay.connector.queue.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.queue.QueueException;

import javax.inject.Inject;
import java.util.List;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.queue.tasks.TaskQueueService.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class TaskQueueMessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskQueueMessageHandler.class);
    private final TaskQueue taskQueue;
    private final ChargeService chargeService;
    private final GatewayAccountDao gatewayAccountDao;
    private final GatewayAccountCredentialsService gatewayAccountCredentialsService;
    private StripePaymentProvider stripePaymentProvider;

    @Inject
    public TaskQueueMessageHandler(TaskQueue taskQueue,
                                   ChargeService chargeService,
                                   GatewayAccountDao gatewayAccountDao,
                                   GatewayAccountCredentialsService gatewayAccountCredentialsService,
                                   StripePaymentProvider stripePaymentProvider) {
        this.taskQueue = taskQueue;
        this.chargeService = chargeService;
        this.gatewayAccountDao = gatewayAccountDao;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
        this.stripePaymentProvider = stripePaymentProvider;
    }

    public void processMessages() throws QueueException {
        List<PaymentTaskMessage> paymentTaskMessages = taskQueue.retrieveTaskQueueMessages();
        for (PaymentTaskMessage paymentTaskMessage : paymentTaskMessages) {
            try {
                LOGGER.info("Processing task from queue",
                        kv("queueMessageId", paymentTaskMessage.getQueueMessageId()),
                        kv("queueMessageReceiptHandle", paymentTaskMessage.getQueueMessageReceiptHandle()),
                        kv(PAYMENT_EXTERNAL_ID, paymentTaskMessage.getPaymentExternalId())
                );

                if (paymentTaskMessage.getTask().equals(COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME)) {
                    Charge charge = chargeService.findCharge(paymentTaskMessage.getPaymentExternalId())
                            .orElseThrow(() -> new ChargeNotFoundRuntimeException(paymentTaskMessage.getPaymentExternalId()));
                    GatewayAccountEntity gatewayAccount = gatewayAccountDao.findById(charge.getGatewayAccountId())
                            .orElseThrow(() -> new GatewayAccountNotFoundException(charge.getGatewayAccountId()));
                    GatewayAccountCredentialsEntity gatewayAccountCredentials = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccount)
                            .orElseThrow(() -> new GatewayAccountCredentialsNotFoundException("Unable to find credentials for charge " + charge.getExternalId()));
                    stripePaymentProvider.transferFeesForFailedPayments(charge, gatewayAccount, gatewayAccountCredentials);
                } else {
                    LOGGER.error("Task [{}] is not supported", paymentTaskMessage.getTask());
                }

                taskQueue.markMessageAsProcessed(paymentTaskMessage.getQueueMessage());
            } catch (Exception e) {
                LOGGER.error(format("Error processing payment task from SQS message [queueMessageId=%s] [errorMessage=%s]",
                                paymentTaskMessage.getQueueMessageId(),
                                e.getMessage()),
                        kv(PAYMENT_EXTERNAL_ID, paymentTaskMessage.getPaymentExternalId())
                );
            }
        }
    }

    
}
