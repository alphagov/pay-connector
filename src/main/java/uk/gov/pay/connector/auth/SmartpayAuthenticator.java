package uk.gov.pay.connector.auth;

import com.google.common.base.Optional;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import uk.gov.pay.connector.app.NotificationCredentials;

public class SmartpayAuthenticator implements Authenticator<BasicCredentials, BasicAuthUser> {
    private NotificationCredentials creds;

    public SmartpayAuthenticator(NotificationCredentials creds) {
        this.creds = creds;
    }

    @Override
    public Optional<BasicAuthUser> authenticate(BasicCredentials basicCredentials) throws AuthenticationException {
        return creds.asBasicCredentials().equals(basicCredentials)
               ? Optional.of( new BasicAuthUser(creds.getUsername()) )
               : Optional.absent();
    }
}
