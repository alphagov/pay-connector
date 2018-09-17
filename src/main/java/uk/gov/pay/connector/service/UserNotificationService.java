package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
        Boolean isEmailEnabled = Optional.ofNullable(chargeEntity.getGatewayAccount().
                getEmailNotifications()
                .get(emailNotificationType))
                .map(EmailNotificationEntity::isEnabled)
                .orElse(false);
        if (emailNotifyGloballyEnabled && isEmailEnabled) {
            String emailAddress = chargeEntity.getEmail();
            Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
            return executorService.submit(() -> {
                try {
                    Pair<NotificationClient, String> notifyClientSettings = getNotifyClientSettings(emailNotificationType, chargeEntity);
                    SendEmailResponse response = notifyClientSettings.getLeft()
                            .sendEmail(notifyClientSettings.getRight(), emailAddress, personalisation, null);
                    return Optional.of(response.getNotificationId().toString());
                } catch (NotificationClientException e) {
                    logger.error("Failed to send confirmation email - charge_external_id=" + chargeEntity.getExternalId(), e);
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

    private String getCustomTemplate(EmailNotificationType type, Map<String, String > notifySettings){
        if (notifySettings == null) {
            return null;
        }
        switch (type) {
            case REFUND_ISSUED:
                return notifySettings.get("refund_issued_template_id");
            case PAYMENT_CONFIRMED:
                return notifySettings.get("template_id");
            default:
                return null;
        }
    }

    private Pair<NotificationClient, String> getNotifyClientSettings(EmailNotificationType emailNotificationType, ChargeEntity chargeEntity) {
        Map<String, String> notifySettings = chargeEntity.getGatewayAccount().getNotifySettings();
        NotifyClientFactory notifyClientFactory = notifyClientFactoryProvider.clientFactory();
        String customTemplateId = getCustomTemplate(emailNotificationType, notifySettings);
        if (isNotBlank(customTemplateId)) {
            return Pair.of(
                    notifyClientFactory.getInstance(notifySettings.get("api_token")),
                    customTemplateId);
        }
        return Pair.of(notifyClientFactory.getInstance(), getTemplateIdFor(emailNotificationType));
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

    private String getTemplateIdFor(EmailNotificationType type) {
        switch (type){
            case REFUND_ISSUED:
                return refundIssuedEmailTemplateId;
            case PAYMENT_CONFIRMED:
                return confirmationEmailTemplateId;
        }
        return null;
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

        return map;
    }

    private String formatToPounds(long amountInPence) {
        BigDecimal amountInPounds = BigDecimal.valueOf(amountInPence, 2);

        return amountInPounds.toString();
    }
}
