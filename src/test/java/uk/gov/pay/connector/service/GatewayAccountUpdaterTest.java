package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.PatchRequest;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountUpdater;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GatewayAccountUpdaterTest {

    private GatewayAccountDao gatewayAccountDao = mock(GatewayAccountDao.class);
    private GatewayAccountUpdater updater;

    @Before
    public void setUp() {
        updater = new GatewayAccountUpdater(gatewayAccountDao);
    }

    @Test
    public void shouldUpdateNotifySettingsWhenUpdate() {
        Long gatewayAccountId = 100L;
        Map<String, String> settings = ImmutableMap.of("api_token", "anapitoken",
                "template_id", "atemplateid");
        PatchRequest request = PatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "notify_settings",
                "value", settings)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(gatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));

        Optional<GatewayAccount> optionalGatewayAcc = updater.doPatch(gatewayAccountId, request);

        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setNotifySettings(settings);
        verify(gatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateNotifySettingsWhenRemove() {
        Long gatewayAccountId = 100L;
        PatchRequest request = PatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "notify_settings")));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(gatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));

        Optional<GatewayAccount> optionalGatewayAcc = updater.doPatch(gatewayAccountId, request);

        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setNotifySettings(null);
        verify(gatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateEmailCollectionMode() {
        Long gatewayAccountId = 100L;
        PatchRequest request = PatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "email_collection_mode",
                "value", "off")));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(gatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));

        Optional<GatewayAccount> optionalGatewayAcc = updater.doPatch(gatewayAccountId, request);

        assertThat(optionalGatewayAcc.isPresent(), is(true));
        InOrder inOrder = Mockito.inOrder(entity, gatewayAccountDao);
        inOrder.verify(entity).setEmailCollectionMode(EmailCollectionMode.OFF);
        inOrder.verify(gatewayAccountDao).merge(entity);
    }
}
