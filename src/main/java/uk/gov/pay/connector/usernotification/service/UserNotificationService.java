package uk.gov.pay.connector.usernotification.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import io.dropwizard.core.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode.OFF;
import static uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode.OPTIONAL;
import static uk.gov.pay.connector.queue.tasks.model.RetryPaymentOrRefundEmailTaskData.of;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.REFUND_ISSUED;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.RESOURCE_EXTERNAL_ID;

public class UserNotificationService {

    private static final Pattern LITERAL_DOLLAR_REFERENCE = Pattern.compile(Pattern.quote("$reference"));
    private final TaskQueueService taskQueueService;
    private final InstantSource instantSource;

    private String confirmationEmailTemplateId;
    private String refundIssuedEmailTemplateId;
    private boolean emailNotifyGloballyEnabled;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private NotifyClientFactory notifyClientFactory;
    private ExecutorService executorService;
    private final MetricRegistry metricRegistry;

    @Inject
    public UserNotificationService(NotifyClientFactory notifyClientFactory, ConnectorConfiguration configuration,
                                   Environment environment, TaskQueueService taskQueueService, InstantSource instantSource) {
        readEmailConfig(configuration);
        if (emailNotifyGloballyEnabled) {
            this.notifyClientFactory = notifyClientFactory;
            int numberOfThreads = configuration.getExecutorServiceConfig().getThreadsPerCpu() * getRuntime().availableProcessors();
            executorService = Executors.newFixedThreadPool(numberOfThreads);
        }
        this.metricRegistry = environment.metrics();
        this.taskQueueService = taskQueueService;
        this.instantSource = instantSource;
    }

    public Future<Optional<String>> sendRefundIssuedEmail(RefundEntity refundEntity, Charge charge, GatewayAccountEntity gatewayAccountEntity) {
        return sendEmailAsync(REFUND_ISSUED, charge, gatewayAccountEntity,
                buildRefundEmailPersonalisationFrom(charge, refundEntity, gatewayAccountEntity),
                refundEntity.getExternalId()
        );
    }

    public Future<Optional<String>> sendPaymentConfirmedEmail(ChargeEntity chargeEntity, GatewayAccountEntity gatewayAccountEntity) {
        var charge = Charge.from(chargeEntity);
        return sendEmailAsync(EmailNotificationType.PAYMENT_CONFIRMED, charge, gatewayAccountEntity,
                buildConfirmationEmailPersonalisationFrom(charge, gatewayAccountEntity), charge.getExternalId());
    }

    public Optional<String> sendPaymentConfirmedEmailSynchronously(Charge charge, GatewayAccountEntity gatewayAccountEntity, boolean retryOnFailure) {
        return sendEmailSynchronously(EmailNotificationType.PAYMENT_CONFIRMED, charge, gatewayAccountEntity,
                buildConfirmationEmailPersonalisationFrom(charge, gatewayAccountEntity), charge.getExternalId(), retryOnFailure);
    }

    public Optional<String> sendRefundIssuedEmailSynchronously(Charge charge, GatewayAccountEntity gatewayAccountEntity, RefundEntity refundEntity, boolean retryOnFailure) {
        return sendEmailSynchronously(EmailNotificationType.REFUND_ISSUED, charge, gatewayAccountEntity,
                buildRefundEmailPersonalisationFrom(charge, refundEntity, gatewayAccountEntity),
                refundEntity.getExternalId(), retryOnFailure);
    }

    private Future<Optional<String>> sendEmailAsync(EmailNotificationType emailNotificationType, Charge charge, GatewayAccountEntity gatewayAccountEntity,
                                                    HashMap<String, String> personalisation, String paymentOrRefundExternalId) {
        if (shouldSendEmail(emailNotificationType, charge, gatewayAccountEntity)) {
            Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
            return executorService.submit(() -> sendEmail(emailNotificationType, charge, gatewayAccountEntity,
                    personalisation, responseTimeStopwatch, paymentOrRefundExternalId, true));
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private Optional<String> sendEmailSynchronously(EmailNotificationType emailNotificationType, Charge charge,
                                                    GatewayAccountEntity gatewayAccountEntity,
                                                    HashMap<String, String> personalisation,
                                                    String paymentOrRefundExternalId, boolean retryOnFailure) {
        if (shouldSendEmail(emailNotificationType, charge, gatewayAccountEntity)) {
            Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
            return sendEmail(emailNotificationType, charge, gatewayAccountEntity, personalisation, responseTimeStopwatch, paymentOrRefundExternalId, retryOnFailure);
        } else {
            return Optional.empty();
        }
    }

    private boolean shouldSendEmail(EmailNotificationType emailNotificationType, Charge
            charge, GatewayAccountEntity gatewayAccountEntity) {
        boolean isEmailEnabled = ofNullable(gatewayAccountEntity.getEmailNotifications().get(emailNotificationType))
                .map(EmailNotificationEntity::isEnabled)
                .orElse(false);

        boolean doNotTrySending = !emailNotifyGloballyEnabled || !isEmailEnabled || gatewayAccountEntity.getEmailCollectionMode().equals(OFF) ||
                gatewayAccountEntity.getEmailCollectionMode().equals(OPTIONAL) && ofNullable(charge.getEmail()).isEmpty();

        boolean noEmailAddress = charge.getEmail() == null;

        if (doNotTrySending) {
            return false;
        } else if (noEmailAddress) {
            logger.warn("Cannot send email for charge_external_id = {} because the charge does not have an email address", charge.getExternalId());
            return false;
        } else {
            return true;
        }
    }

    private Optional<String> sendEmail(EmailNotificationType emailNotificationType, Charge charge,
                                       GatewayAccountEntity gatewayAccountEntity, HashMap<String, String> personalisation,
                                       Stopwatch responseTimeStopwatch, String paymentOrRefundExternalId, boolean retryOnFailure) {
        try {
            MDC.put(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId().toString());
            MDC.put(PAYMENT_EXTERNAL_ID, charge.getExternalId());
            MDC.put(RESOURCE_EXTERNAL_ID, paymentOrRefundExternalId);
            MDC.put("email_notification_type", emailNotificationType.name());
            NotifyClientSettings notifyClientSettings = getNotifyClientSettings(emailNotificationType, gatewayAccountEntity);
            logger.info(format("Sending %s email.", emailNotificationType));
            SendEmailResponse response = notifyClientSettings.getClient()
                    .sendEmail(notifyClientSettings.getTemplateId(), charge.getEmail(), personalisation, null, notifyClientSettings.getEmailReplyToId());
            return Optional.of(response.getNotificationId().toString());
        } catch (NotificationClientException e) {
            if (retryOnFailure) {
                taskQueueService.addRetryFailedPaymentOrRefundEmailTask(of(paymentOrRefundExternalId, emailNotificationType, instantSource.instant()));
                logger.info("Failed to send email. Added to task queue for retrying",
                        kv("error", e.getMessage()), e);
            } else {
                logger.error("Failed to send email permanently",
                        kv("error", e.getMessage()), e);
                metricRegistry.counter("notify-operations.failures").inc();
            }
            return Optional.empty();
        } finally {
            MDC.remove(GATEWAY_ACCOUNT_ID);
            MDC.remove(PAYMENT_EXTERNAL_ID);
            MDC.remove(RESOURCE_EXTERNAL_ID);
            MDC.remove("email_notification_type");
            responseTimeStopwatch.stop();
            metricRegistry.histogram("notify-operations.response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    private NotifyClientSettings getNotifyClientSettings(EmailNotificationType emailNotificationType, GatewayAccountEntity gatewayAccountEntity) {
        // todo introduce type for notify settings instead of Map
        Map<String, String> notifySettings = gatewayAccountEntity.getNotifySettings();
        switch (emailNotificationType) {
            case REFUND_ISSUED:
                return NotifyClientSettings.of(notifySettings, notifyClientFactory, "refund_issued_template_id", refundIssuedEmailTemplateId);
            case PAYMENT_CONFIRMED:
                return NotifyClientSettings.of(notifySettings, notifyClientFactory, "template_id", confirmationEmailTemplateId);
        }
        return null;
    }

    private static class NotifyClientSettings {
        private NotificationClient client;
        private String templateId;
        private String emailReplyToId;

        private NotifyClientSettings(NotificationClient client, String templateId, String emailReplyToId) {
            this.client = client;
            this.templateId = templateId;
            this.emailReplyToId = emailReplyToId;
        }

        public NotificationClient getClient() {
            return client;
        }

        String getTemplateId() {
            return templateId;
        }

        public String getEmailReplyToId() {
            return emailReplyToId;
        }

        public static NotifyClientSettings of(Map<String, String> notifySettings, NotifyClientFactory notifyClientFactory, String customTemplateId, String payTemplateId) {
            if (hasCustomTemplateAndApiKey(notifySettings, customTemplateId)) {
                String emailReplyToId = notifySettings.get("email_reply_to_id");
                return new NotifyClientSettings(notifyClientFactory.getInstance(notifySettings.get("api_token")), notifySettings.get(customTemplateId), emailReplyToId);
            }
            return new NotifyClientSettings(notifyClientFactory.getInstance(), payTemplateId, null);
        }
    }

    private static boolean hasCustomTemplateAndApiKey(Map<String, String> notifySettings, String customTemplateId) {
        return notifySettings != null && (notifySettings.containsKey(customTemplateId) && isNotBlank(notifySettings.get("api_token")));
    }

    private void readEmailConfig(ConnectorConfiguration configuration) {
        emailNotifyGloballyEnabled = configuration.getNotifyConfiguration().isEmailNotifyEnabled();
        confirmationEmailTemplateId = configuration.getNotifyConfiguration().getEmailTemplateId();
        refundIssuedEmailTemplateId = configuration.getNotifyConfiguration().getRefundIssuedEmailTemplateId();

        if (!emailNotifyGloballyEnabled) {
            logger.warn("Email notifications is disabled by configuration");
        }
        if (emailNotifyGloballyEnabled && (isBlank(confirmationEmailTemplateId) || isBlank(refundIssuedEmailTemplateId))) {
            throw new RuntimeException("Check notify config, need to set 'emailTemplateId' (payment confirmation email) and 'refundIssuedEmailTemplateId' properties");
        }
    }

    private HashMap<String, String> buildConfirmationEmailPersonalisationFrom(Charge charge, GatewayAccountEntity gatewayAccount) {
        EmailNotificationEntity emailNotification = gatewayAccount
                .getEmailNotifications().get(EmailNotificationType.PAYMENT_CONFIRMED);

        String customParagraph = emailNotification != null ? emailNotification.getTemplateBody() : "";
        HashMap<String, String> map = new HashMap<>();

        map.put("serviceReference", charge.getReference());
        map.put("date", DateTimeUtils.toUserFriendlyDate(charge.getCreatedDate()));
        map.put("amount", formatToPounds(CorporateCardSurchargeCalculator.getTotalAmountFor(charge)));
        map.put("description", charge.getDescription());
        map.put("customParagraph", isBlank(customParagraph) ? "" : "^ " + LITERAL_DOLLAR_REFERENCE.matcher(customParagraph)
                .replaceAll(Matcher.quoteReplacement(charge.getReference())));
        map.put("serviceName", StringUtils.defaultString(gatewayAccount.getServiceName()));

        String corporateSurchargeMsg = charge.getCorporateSurcharge()
                .map(corporateSurcharge ->
                        format("Your payment includes a fee of £%s for using a corporate credit or debit card.",
                                formatToPounds(corporateSurcharge)))
                .orElse("");
        map.put("corporateCardSurcharge", corporateSurchargeMsg);

        return map;
    }

    private HashMap<String, String> buildRefundEmailPersonalisationFrom(Charge charge, RefundEntity refundEntity, GatewayAccountEntity gatewayAccountEntity) {
        HashMap<String, String> map = new HashMap<>();

        map.put("serviceReference", charge.getReference());
        map.put("date", DateTimeUtils.toUserFriendlyDate(charge.getCreatedDate()));
        map.put("amount", formatToPounds(refundEntity.getAmount()));
        map.put("description", charge.getDescription());
        map.put("serviceName", StringUtils.defaultString(gatewayAccountEntity.getServiceName()));

        return map;
    }

    private String formatToPounds(long amountInPence) {
        BigDecimal amountInPounds = BigDecimal.valueOf(amountInPence, 2);

        return amountInPounds.toString();
    }
}
