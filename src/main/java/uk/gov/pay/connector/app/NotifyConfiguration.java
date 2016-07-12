package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class NotifyConfiguration extends Configuration {

    private String emailTemplateId;
    private String serviceId;
    private String secret;
    private String notificationBaseURL;
    private boolean emailNotifyEnabled;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

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
