package uk.gov.pay.connector.app;

import io.dropwizard.core.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotifyConfiguration extends Configuration {

    private String emailTemplateId;
    private String refundIssuedEmailTemplateId;
    private String apiKey;
    private String notificationBaseURL;
    private boolean emailNotifyEnabled;

    private long retryFailedEmailAfterSeconds;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public String getEmailTemplateId() {
        return emailTemplateId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getNotificationBaseURL() {
        return notificationBaseURL;
    }

    public boolean isEmailNotifyEnabled() {
        return emailNotifyEnabled;
    }

    public String getRefundIssuedEmailTemplateId() {
        return refundIssuedEmailTemplateId;
    }

    public long getRetryFailedEmailAfterSeconds() {
        return retryFailedEmailAfterSeconds;
    }
}
