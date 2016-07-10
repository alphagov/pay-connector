package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import java.util.Map;

public class NotifyConfiguration extends Configuration {

    private String emailTemplateId;
    private String serviceId;
    private String secret;
    private String notificationBaseURL;
    private boolean emailNotifyEnabled;

    public String getEmailTemplateId() {
        return emailTemplateId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getSecret() {
        return secret;
    }

    public String getNotificationBaseURL() {
        return notificationBaseURL;
    }

    public boolean isEmailNotifyEnabled() {
        return emailNotifyEnabled;
    }
}
