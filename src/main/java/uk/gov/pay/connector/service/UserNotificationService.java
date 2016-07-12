package uk.gov.pay.connector.service;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.notifications.client.api.GovNotifyApiClient;
import uk.gov.notifications.client.api.GovNotifyClientException;
import uk.gov.notifications.client.model.EmailRequest;
import uk.gov.notifications.client.model.NotificationCreatedResponse;
import uk.gov.notifications.client.model.Personalisation;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;


public class UserNotificationService {

    private final NotifyClientProvider notifyClientProvider;
    private String emailTemplateId;
    private boolean emailNotifyEnabled;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public UserNotificationService(NotifyClientProvider notifyClientProvider, ConnectorConfiguration configuration) {
        this.notifyClientProvider = notifyClientProvider;
        readEmailConfig(configuration);
    }

    public Optional<String> notifyPaymentSuccessEmail(String emailAddress) {
        if (emailNotifyEnabled) {
            EmailRequest emailRequest = buildRequest(emailAddress, this.emailTemplateId, Collections.EMPTY_MAP);
            GovNotifyApiClient govNotifyApiClient = notifyClientProvider.get();
            try {
                NotificationCreatedResponse notificationCreatedResponse = govNotifyApiClient.sendEmail(emailRequest);
                return Optional.of(notificationCreatedResponse.getId());
            } catch (GovNotifyClientException e) {
                logger.error(String.format("failed to send confirmation email at %s", emailAddress), e);
            }
        }
        return Optional.empty();
    }

    public String checkDeliveryStatus(String notificationId) throws GovNotifyClientException {
        return notifyClientProvider.get().checkStatus(notificationId).getStatus();
    }

    public EmailRequest buildRequest(String emailAddress, String emailTemplateId, Map<String, Object> emailPersonalisation) {
        EmailRequest.Builder emailRequestBuilder = EmailRequest
                .builder()
                .email(emailAddress)
                .templateId(emailTemplateId);

        if (!emailPersonalisation.isEmpty()) {
            emailRequestBuilder = emailRequestBuilder
                    .personalisation(Personalisation.fromJsonString(new Gson().toJson(emailPersonalisation)));
        }
        return emailRequestBuilder.build();
    }

    private void readEmailConfig(ConnectorConfiguration configuration) {
        this.emailNotifyEnabled = configuration.getNotifyConfiguration().isEmailNotifyEnabled();
        if (!emailNotifyEnabled) {
            logger.warn("Email notifications is disabled by configuration");
        }
        if (StringUtils.isBlank(configuration.getNotifyConfiguration().getEmailTemplateId())) {
            throw new RuntimeException("config property 'emailTemplateId' is missing or not set, which needs to point to the email template on the notify");
        }
        this.emailTemplateId = configuration.getNotifyConfiguration().getEmailTemplateId();
    }
}
