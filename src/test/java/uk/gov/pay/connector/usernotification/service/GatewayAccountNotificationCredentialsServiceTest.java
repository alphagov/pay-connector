package uk.gov.pay.connector.usernotification.service;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.exception.CredentialsException;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;
import uk.gov.pay.connector.util.HashUtil;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class GatewayAccountNotificationCredentialsServiceTest {

    private GatewayAccountNotificationCredentialsService gatewayAccountNotificationCredentialsService;

    @Mock
    GatewayAccountDao gatewayDao;

    @Mock
    HashUtil hashUtil;
    
    @Mock
    GatewayAccountEntity gatewayAccount;

    @BeforeEach
    void setup() {
        gatewayAccountNotificationCredentialsService = new GatewayAccountNotificationCredentialsService(gatewayDao, hashUtil);
    }

    @Test
    void shouldCreateNotificationCredentialsIfNotPresentAndEncryptPassword() throws CredentialsException {
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
    void shouldUpdateExistingNotificationCredentialIfPresent() throws CredentialsException {
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
    void shouldValidateThatPasswordisAtLeast10Characters() {
        Map<String, String> credentials = ImmutableMap.of("username", "bob", "password", "bobsecret");
        Exception exception = assertThrows(CredentialsException.class, () -> {
            gatewayAccountNotificationCredentialsService.setCredentialsForAccount(credentials, gatewayAccount);
        });

        String expectedMessage = "Invalid password length";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));

        verifyNoInteractions(hashUtil);
    }

}
