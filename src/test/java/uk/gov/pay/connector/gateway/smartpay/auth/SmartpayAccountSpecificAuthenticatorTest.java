package uk.gov.pay.connector.gateway.smartpay.auth;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.basic.BasicCredentials;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.NotificationCredentials;
import uk.gov.pay.connector.util.HashUtil;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SmartpayAccountSpecificAuthenticatorTest {
    private String username = "MyService";
    private String password = "password";
    private String hashedPassword = "hashed";

    @Mock
    GatewayAccountDao gatewayAccountDao;

    @Mock
    HashUtil hashUtil;

    @InjectMocks
    private SmartpayAccountSpecificAuthenticator smartpayAccountSpecificAuthenticator;

    @Before
    public void setup() {
        GatewayAccountEntity gatewayAccountEntity = mock(GatewayAccountEntity.class);
        NotificationCredentials notificationCredentials = mock(NotificationCredentials.class);
        Optional<GatewayAccountEntity> gatewayAccountEntityMayBe = Optional.of(gatewayAccountEntity);
        BasicAuthUser basicAuthUser = mock(BasicAuthUser.class);

        when(gatewayAccountDao.findByNotificationCredentialsUsername(username)).thenReturn(gatewayAccountEntityMayBe);
        when(gatewayAccountEntity.getNotificationCredentials()).thenReturn(notificationCredentials);
        when(notificationCredentials.getPassword()).thenReturn(hashedPassword);
        when(hashUtil.check(password, hashedPassword)).thenReturn(true);
        when(gatewayAccountEntity.getNotificationCredentials().toBasicAuthUser()).thenReturn(basicAuthUser);
    }

    @Test
    public void whenAccountMatchesCredentials_shouldReturnBasicAuthUser() throws AuthenticationException {

        when(hashUtil.check(password, hashedPassword)).thenReturn(true);

        Optional<BasicAuthUser> authenticate = smartpayAccountSpecificAuthenticator.authenticate(new BasicCredentials(username, password));

        assertTrue(authenticate.isPresent());
    }

    @Test
    public void whenAccountExistsButCredentialsDontMatch_shouldReturnEmpty() throws AuthenticationException {

        when(hashUtil.check(password, hashedPassword)).thenReturn(false);

        Optional<BasicAuthUser> authenticate = smartpayAccountSpecificAuthenticator.authenticate(new BasicCredentials(username, password));

        assertFalse(authenticate.isPresent());
    }

    @Test
    public void whenAccountDoesNotExist_shouldReturnEmpty() throws AuthenticationException {
        Optional<GatewayAccountEntity> gatewayAccountEntityMayBe = Optional.empty();

        when(gatewayAccountDao.findByNotificationCredentialsUsername(username)).thenReturn(gatewayAccountEntityMayBe);

        Optional<BasicAuthUser> authenticate = smartpayAccountSpecificAuthenticator.authenticate(new BasicCredentials(username, password));

        assertFalse(authenticate.isPresent());
    }
}
