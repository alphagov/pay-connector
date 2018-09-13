package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.PatchRequest;
import uk.gov.pay.connector.model.domain.EmailCollectionMode;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        verify(entity, times(1)).setNotifySettings(settings);
        verify(gatewayAccountDao, times(1)).merge(entity);
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
        verify(entity, times(1)).setNotifySettings(null);
        verify(gatewayAccountDao, times(1)).merge(entity);
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
        verify(entity, times(1)).setEmailCollectionMode(EmailCollectionMode.OFF);
        verify(gatewayAccountDao, times(1)).merge(entity);
    }
}
