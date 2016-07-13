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

    private String emailTemplateId;
    private boolean emailNotifyEnabled;

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private GovNotifyApiClient govNotifyApiClient;

    @Inject
    public UserNotificationService(NotifyClientProvider notifyClientProvider, ConnectorConfiguration configuration) {
        readEmailConfig(configuration);
        if (emailNotifyEnabled) {
            govNotifyApiClient = notifyClientProvider.get();
        }
    }

    public Optional<String> notifyPaymentSuccessEmail(String emailAddress) {
        if (emailNotifyEnabled) {
            EmailRequest emailRequest = buildRequest(emailAddress, this.emailTemplateId, Collections.EMPTY_MAP);
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
        return govNotifyApiClient.checkStatus(notificationId).getStatus();
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
        emailNotifyEnabled = configuration.getNotifyConfiguration().isEmailNotifyEnabled();
        emailTemplateId = configuration.getNotifyConfiguration().getEmailTemplateId();

        if (!emailNotifyEnabled) {
            logger.warn("Email notifications is disabled by configuration");
        }
        if (emailNotifyEnabled && StringUtils.isBlank(emailTemplateId)) {
            throw new RuntimeException("config property 'emailTemplateId' is missing or not set, which needs to point to the email template on the notify");
        }
    }
}
