package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.notifications.client.api.GovNotifyApiClient;
import uk.gov.notifications.client.api.GovNotifyClientException;
import uk.gov.notifications.client.model.EmailRequest;
import uk.gov.notifications.client.model.NotificationCreatedResponse;
import uk.gov.notifications.client.model.Personalisation;
import uk.gov.notifications.client.model.StatusResponse;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.util.DateTimeUtils;

import javax.inject.Inject;
import java.math.BigDecimal;

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

    public Optional<String> notifyPaymentSuccessEmail(ChargeEntity chargeEntity) {
        if (emailNotifyEnabled) {
            String emailAddress = chargeEntity.getEmail();
            EmailRequest emailRequest = buildRequest(emailAddress, this.emailTemplateId, buildEmailPersonalisationFromCharge(chargeEntity));
            try {
                NotificationCreatedResponse notificationCreatedResponse = govNotifyApiClient.sendEmail(emailRequest);
                return Optional.of(notificationCreatedResponse.getId());
            } catch (GovNotifyClientException e) {
                logger.error(String.format("failed to send confirmation email at %s", emailAddress), e);
            }
        }
        return Optional.empty();
    }

    public StatusResponse checkDeliveryStatus(String notificationId) throws GovNotifyClientException {
        return govNotifyApiClient.checkStatus(notificationId);
    }

    public EmailRequest buildRequest(String emailAddress, String emailTemplateId, Map<String, String> emailPersonalisation) {
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

    private Map<String, String> buildEmailPersonalisationFromCharge(ChargeEntity charge) {
        GatewayAccountEntity gatewayAccount = charge.getGatewayAccount();

        return new ImmutableMap.Builder<String, String>()
                .put("serviceReference", charge.getReference())
                .put("date", DateTimeUtils.toUserFriendlyDate(charge.getCreatedDate()))
                .put("amount", formatToPounds(charge.getAmount()))
                .put("description", charge.getDescription())
                .put("customParagraph", StringUtils.defaultString(gatewayAccount
                        .getEmailNotification()
                        .getTemplateBody()))
                .put("serviceName",  StringUtils.defaultString(gatewayAccount.getServiceName()))
                .build();
    }

    private String formatToPounds(long amountInPence) {
        BigDecimal amountInPounds = BigDecimal.valueOf(amountInPence, 2);

        return amountInPounds.toString();
    }
}
