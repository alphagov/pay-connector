package uk.gov.pay.connector.resources;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.app.SmartpayCredentialsConfig;
import uk.gov.pay.connector.dao.CardTypeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.builder.EntityBuilder;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.NotificationCredentials;
import uk.gov.pay.connector.util.HashUtil;

import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class GatewayAccountResourceTest {

    private GatewayAccountResource gatewayAccountResource;

    @Mock
    GatewayAccountDao gatewayDao;

    @Mock
    CardTypeDao cardTypeDao;

    @Mock
    ConnectorConfiguration conf;

    @Mock
    EntityBuilder entityBuilder;

    @Mock
    HashUtil hashUtil;

    @Before
    public void setup() {
        GatewayCredentialsConfig worldpayCredentials = mock(GatewayCredentialsConfig.class);
        SmartpayCredentialsConfig smartpayCredentialsConfig = mock(SmartpayCredentialsConfig.class);

        when(conf.getWorldpayConfig()).thenReturn(worldpayCredentials);
        when(conf.getSmartpayConfig()).thenReturn(smartpayCredentialsConfig);
        when(worldpayCredentials.getCredentials()).thenReturn(newArrayList());
        when(smartpayCredentialsConfig.getCredentials()).thenReturn(newArrayList());

        gatewayAccountResource = new GatewayAccountResource(gatewayDao, cardTypeDao, entityBuilder, conf, hashUtil  );
    }

    @Test
    public void shouldCreateNotificationCredentialsIfNotPresent() {
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
        NotificationCredentials notificationCredentials = mock(NotificationCredentials.class);
        Map<String, String> credentials = ImmutableMap.of("username", "bob", "password", "bobssecret");


        when(gatewayDao.findById(123L)).thenReturn(Optional.of(gatewayAccount));
        when(gatewayAccount.getNotificationCredentials()).thenReturn(null);
        when(entityBuilder.newNotificationCredentials(gatewayAccount)).thenReturn(notificationCredentials);

        gatewayAccountResource.createOrUpdateGatewayAccountNotificationCredentials(123L, credentials);

        verify(gatewayAccount).setNotificationCredentials(notificationCredentials);
        verify(gatewayDao).merge(gatewayAccount);
    }


    @Test
    public void shouldEncryptPasswordWhenCreatingNotificationCredentials() {
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
        NotificationCredentials notificationCredentials = mock(NotificationCredentials.class);
        Map<String, String> credentials = ImmutableMap.of("username", "bob", "password", "bobssecret");


        when(gatewayDao.findById(123L)).thenReturn(Optional.of(gatewayAccount));
        when(gatewayAccount.getNotificationCredentials()).thenReturn(null);
        when(entityBuilder.newNotificationCredentials(gatewayAccount)).thenReturn(notificationCredentials);
        when(hashUtil.hash("bobssecret")).thenReturn("bobshashedsecret");

        gatewayAccountResource.createOrUpdateGatewayAccountNotificationCredentials(123L, credentials);

        InOrder inOrder = Mockito.inOrder(hashUtil, notificationCredentials, gatewayAccount);
        inOrder.verify(hashUtil).hash("bobssecret");
        inOrder.verify(notificationCredentials).setPassword("bobshashedsecret");
        inOrder.verify(gatewayAccount).setNotificationCredentials(notificationCredentials);
    }

    @Test
    public void shouldUpdateExistingNotificationCredentialIfPresent() {
        GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
        NotificationCredentials notificationCredentials = mock(NotificationCredentials.class);
        Map<String, String> credentials = ImmutableMap.of("username", "bob", "password", "bobssecret");

        when(gatewayDao.findById(123L)).thenReturn(Optional.of(gatewayAccount));
        when(gatewayAccount.getNotificationCredentials()).thenReturn(notificationCredentials);
        when(hashUtil.hash("bobssecret")).thenReturn("bobshashedsecret");

        gatewayAccountResource.createOrUpdateGatewayAccountNotificationCredentials(123L, credentials);

        InOrder inOrder = Mockito.inOrder(hashUtil, notificationCredentials, gatewayAccount);
        inOrder.verify(notificationCredentials).setUserName("bob");
        inOrder.verify(hashUtil).hash("bobssecret");
        inOrder.verify(notificationCredentials).setPassword("bobshashedsecret");
        inOrder.verify(gatewayAccount).setNotificationCredentials(notificationCredentials);

        verifyZeroInteractions(entityBuilder);
    }
}
