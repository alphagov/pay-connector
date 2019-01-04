package uk.gov.pay.connector.usernotification.service;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.common.exception.CredentialsException;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;
import uk.gov.pay.connector.util.HashUtil;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class GatewayAccountNotificationCredentialsServiceTest {

    private GatewayAccountNotificationCredentialsService gatewayAccountNotificationCredentialsService;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    GatewayAccountDao gatewayDao;

    @Mock
    HashUtil hashUtil;

    @Before
    public void setup() {
        gatewayAccountNotificationCredentialsService = new GatewayAccountNotificationCredentialsService(gatewayDao, hashUtil);
    }

    @Test
    public void shouldCreateNotificationCredentialsIfNotPresentAndEncryptPassword() throws CredentialsException {
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
        Map<String, String> credentials = ImmutableMap.of("username", "bob", "password", "bobssecret");

        when(gatewayAccount.getNotificationCredentials()).thenReturn(null);
        when(hashUtil.hash("bobssecret")).thenReturn("bobshashedsecret");

        gatewayAccountNotificationCredentialsService.setCredentialsForAccount(credentials, gatewayAccount);

        ArgumentCaptor<NotificationCredentials> argumentCaptor = ArgumentCaptor.forClass(NotificationCredentials.class);
        verify(gatewayAccount).setNotificationCredentials(argumentCaptor.capture());
        final NotificationCredentials notificationCredentials = argumentCaptor.getValue();

        assertNotNull(notificationCredentials);
        assertEquals("bob", notificationCredentials.getUserName());
        assertEquals("bobshashedsecret", notificationCredentials.getPassword());
        InOrder inOrder = Mockito.inOrder(hashUtil, gatewayAccount, gatewayDao);
        inOrder.verify(hashUtil).hash("bobssecret");
        inOrder.verify(gatewayAccount).setNotificationCredentials(notificationCredentials);
        inOrder.verify(gatewayDao).merge(gatewayAccount);
    }

    @Test
    public void shouldUpdateExistingNotificationCredentialIfPresent() throws CredentialsException {
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
        NotificationCredentials notificationCredentials = mock(NotificationCredentials.class);
        Map<String, String> credentials = ImmutableMap.of("username", "bob", "password", "bobssecret");

        when(gatewayAccount.getNotificationCredentials()).thenReturn(notificationCredentials);
        when(hashUtil.hash("bobssecret")).thenReturn("bobshashedsecret");

        gatewayAccountNotificationCredentialsService.setCredentialsForAccount(credentials, gatewayAccount);

        InOrder inOrder = Mockito.inOrder(hashUtil, notificationCredentials, gatewayAccount);
        inOrder.verify(notificationCredentials).setUserName("bob");
        inOrder.verify(hashUtil).hash("bobssecret");
        inOrder.verify(notificationCredentials).setPassword("bobshashedsecret");
        inOrder.verify(gatewayAccount).setNotificationCredentials(notificationCredentials);
    }

    @Test
    public void shouldValidateThatPasswordisAtLeast10Characters() throws CredentialsException {
        expectedException.expect(CredentialsException.class);
        expectedException.expectMessage("Invalid password length");

        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
        Map<String, String> credentials = ImmutableMap.of("username", "bob", "password", "bobsecret");

        gatewayAccountNotificationCredentialsService.setCredentialsForAccount(credentials, gatewayAccount);

        verifyZeroInteractions(hashUtil);
    }
}
