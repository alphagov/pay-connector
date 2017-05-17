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
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.*;

import static java.lang.Runtime.getRuntime;


public class UserNotificationService {

    private String emailTemplateId;
    private boolean emailNotifyGloballyEnabled;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private NotificationClient notificationClient;
    private ExecutorService executorService;
    private final MetricRegistry metricRegistry;

    @Inject
    public UserNotificationService(NotifyClientProvider notifyClientProvider, ConnectorConfiguration configuration, Environment environment) {
        readEmailConfig(configuration);
        if (emailNotifyGloballyEnabled) {
            this.notificationClient = notifyClientProvider.get();
            int numberOfThreads = configuration.getExecutorServiceConfig().getThreadsPerCpu() * getRuntime().availableProcessors();
            executorService = Executors.newFixedThreadPool(numberOfThreads);
        }
        this.metricRegistry = environment.metrics();
    }

    public Future<Optional<String>> notifyPaymentSuccessEmail(ChargeEntity chargeEntity) {
        if (emailNotifyGloballyEnabled && chargeEntity.getGatewayAccount().hasEmailNotificationsEnabled()) {
            String emailAddress = chargeEntity.getEmail();
            Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
            return executorService.submit(() -> {
                try {
                    SendEmailResponse response = notificationClient.sendEmail(emailTemplateId, emailAddress, buildEmailPersonalisationFromCharge(chargeEntity), null);
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

    public String checkDeliveryStatus(String notificationId) throws NotificationClientException {
        Notification notification = notificationClient.getNotificationById(notificationId);
        return notification.getStatus();
    }

    private void readEmailConfig(ConnectorConfiguration configuration) {
        emailNotifyGloballyEnabled = configuration.getNotifyConfiguration().isEmailNotifyEnabled();
        emailTemplateId = configuration.getNotifyConfiguration().getEmailTemplateId();

        if (!emailNotifyGloballyEnabled) {
            logger.warn("Email notifications is disabled by configuration");
        }
        if (emailNotifyGloballyEnabled && StringUtils.isBlank(emailTemplateId)) {
            throw new RuntimeException("config property 'emailTemplateId' is missing or not set, which needs to point to the email template on the notify");
        }
    }

    private HashMap<String, String> buildEmailPersonalisationFromCharge(ChargeEntity charge) {
        GatewayAccountEntity gatewayAccount = charge.getGatewayAccount();
        EmailNotificationEntity emailNotification = gatewayAccount
                .getEmailNotification();

        String customParagraph = emailNotification != null ?
                emailNotification.getTemplateBody() :
                "";

        HashMap<String, String> map = new HashMap<>();

        map.put("serviceReference", charge.getReference());
        map.put("date", DateTimeUtils.toUserFriendlyDate(charge.getCreatedDate()));
        map.put("amount", formatToPounds(charge.getAmount()));
        map.put("description", charge.getDescription());
        map.put("customParagraph", StringUtils.defaultString(customParagraph));
        map.put("serviceName", StringUtils.defaultString(gatewayAccount.getServiceName()));

        return map;
    }

    private String formatToPounds(long amountInPence) {
        BigDecimal amountInPounds = BigDecimal.valueOf(amountInPence, 2);

        return amountInPounds.toString();
    }
}
