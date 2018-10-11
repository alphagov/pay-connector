package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.model.domain.EmailNotificationType;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.service.notify.NotifyClientFactory;
import uk.gov.pay.connector.service.notify.NotifyClientFactoryProvider;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.lang.Runtime.getRuntime;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


public class UserNotificationService {

    private String confirmationEmailTemplateId;
    private String refundIssuedEmailTemplateId;
    private boolean emailNotifyGloballyEnabled;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private NotifyClientFactoryProvider notifyClientFactoryProvider;
    private ExecutorService executorService;
    private final MetricRegistry metricRegistry;

    @Inject
    public UserNotificationService(NotifyClientFactoryProvider notifyClientFactoryProvider, ConnectorConfiguration configuration, Environment environment) {
        readEmailConfig(configuration);
        if (emailNotifyGloballyEnabled) {
            this.notifyClientFactoryProvider = notifyClientFactoryProvider;
            int numberOfThreads = configuration.getExecutorServiceConfig().getThreadsPerCpu() * getRuntime().availableProcessors();
            executorService = Executors.newFixedThreadPool(numberOfThreads);
        }
        this.metricRegistry = environment.metrics();
    }

    public Future<Optional<String>> sendRefundIssuedEmail(RefundEntity refundEntity) {
        return sendEmail(EmailNotificationType.REFUND_ISSUED, refundEntity.getChargeEntity(), buildRefundEmailPersonalisationFrom(refundEntity));
    }

    public Future<Optional<String>> sendPaymentConfirmedEmail(ChargeEntity chargeEntity) {
        return sendEmail(EmailNotificationType.PAYMENT_CONFIRMED, chargeEntity, buildConfirmationEmailPersonalisationFrom(chargeEntity));
    }

    private Future<Optional<String>> sendEmail(EmailNotificationType emailNotificationType, ChargeEntity chargeEntity, HashMap<String, String> personalisation) {
        boolean isEmailEnabled = Optional.ofNullable(chargeEntity.getGatewayAccount().getEmailNotifications().get(emailNotificationType))
                .map(EmailNotificationEntity::isEnabled)
                .orElse(false);
        if (emailNotifyGloballyEnabled && isEmailEnabled) {
            String emailAddress = chargeEntity.getEmail();
            Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
            return executorService.submit(() -> {
                try {
                    NotifyClientSettings notifyClientSettings = getNotifyClientSettings(emailNotificationType, chargeEntity);
                    logger.info("Sending {} email, charge_external_id={}", emailNotificationType, chargeEntity.getExternalId());
                    SendEmailResponse response = notifyClientSettings.getClient()
                            .sendEmail(notifyClientSettings.getTemplateId(), emailAddress, personalisation, null);
                    return Optional.of(response.getNotificationId().toString());
                } catch (NotificationClientException e) {
                    logger.error("Failed to send " + emailNotificationType + " email - charge_external_id=" + chargeEntity.getExternalId(), e);
                    metricRegistry.counter("notify-operations.failures").inc();
                    return Optional.empty();
                } finally {
                    responseTimeStopwatch.stop();
                    metricRegistry.histogram("notify-operations.response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
                }
            });
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }


    private NotifyClientSettings getNotifyClientSettings(EmailNotificationType emailNotificationType, ChargeEntity chargeEntity) {
        // todo introduce type for notify settings instead of Map
        Map<String, String> notifySettings = chargeEntity.getGatewayAccount().getNotifySettings();
        NotifyClientFactory notifyClientFactory = notifyClientFactoryProvider.clientFactory();
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

        private NotifyClientSettings(NotificationClient client, String templateId) {
            this.client = client;
            this.templateId = templateId;
        }

        public NotificationClient getClient() {
            return client;
        }

        public String getTemplateId() {
            return templateId;
        }

        public static NotifyClientSettings of(Map<String, String> notifySettings, NotifyClientFactory notifyClientFactory, String customTemplateId, String payTemplateId) {
            if (hasCustomTemplateAndApiKey(notifySettings, customTemplateId)) {
                return new NotifyClientSettings(notifyClientFactory.getInstance(notifySettings.get("api_token")), notifySettings.get(customTemplateId));
            }
            return new NotifyClientSettings(notifyClientFactory.getInstance(), payTemplateId);
        }
    }
    private static boolean hasCustomTemplateAndApiKey(Map<String, String> notifySettings, String customTemplateId) {
        return notifySettings != null  && (notifySettings.containsKey(customTemplateId) && isNotBlank(notifySettings.get("api_token")));
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

    private HashMap<String, String> buildConfirmationEmailPersonalisationFrom(ChargeEntity charge) {
        GatewayAccountEntity gatewayAccount = charge.getGatewayAccount();
        EmailNotificationEntity emailNotification = gatewayAccount
                .getEmailNotifications().get(EmailNotificationType.PAYMENT_CONFIRMED);

        String customParagraph = emailNotification != null ? emailNotification.getTemplateBody() : "";
        HashMap<String, String> map = new HashMap<>();

        map.put("serviceReference", charge.getReference().toString());
        map.put("date", DateTimeUtils.toUserFriendlyDate(charge.getCreatedDate()));
        map.put("amount", formatToPounds(charge.getAmount()));
        map.put("description", charge.getDescription());
        map.put("customParagraph", isBlank(customParagraph) ? "" : "^ " + customParagraph);
        map.put("serviceName", StringUtils.defaultString(gatewayAccount.getServiceName()));

        return map;
    }

    private HashMap<String, String> buildRefundEmailPersonalisationFrom(RefundEntity refundEntity) {
        ChargeEntity chargeEntity = refundEntity.getChargeEntity();
        HashMap<String, String> map = new HashMap<>();

        map.put("serviceReference", chargeEntity.getReference().toString());
        map.put("date", DateTimeUtils.toUserFriendlyDate(refundEntity.getCreatedDate()));
        map.put("amount", formatToPounds(refundEntity.getAmount()));
        map.put("description", chargeEntity.getDescription());
        map.put("serviceName", StringUtils.defaultString(chargeEntity.getGatewayAccount().getServiceName()));

        return map;
    }

    private String formatToPounds(long amountInPence) {
        BigDecimal amountInPounds = BigDecimal.valueOf(amountInPence, 2);

        return amountInPounds.toString();
    }
}
