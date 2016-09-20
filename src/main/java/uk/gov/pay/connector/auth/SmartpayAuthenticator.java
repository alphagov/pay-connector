package uk.gov.pay.connector.auth;

import com.google.common.base.Optional;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.NotificationCredentials;

import static java.lang.String.format;

public class SmartpayAuthenticator implements Authenticator<BasicCredentials, BasicAuthUser> {
    private NotificationCredentials creds;
    private static final Logger logger = LoggerFactory.getLogger(SmartpayAuthenticator.class);

    public SmartpayAuthenticator(NotificationCredentials creds) {
        this.creds = creds;
    }

    @Override
    public Optional<BasicAuthUser> authenticate(BasicCredentials basicCredentials) throws AuthenticationException {
        if (creds.asBasicCredentials().equals(basicCredentials)) {
            return Optional.of(new BasicAuthUser(creds.getUsername()));
        }

        logger.error(format("Authentication failure: failed for smartpay username %s", basicCredentials));
        return Optional.absent();
    }
}
