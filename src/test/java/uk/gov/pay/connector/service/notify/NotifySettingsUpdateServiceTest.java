package uk.gov.pay.connector.service.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.NotifySettingsUpdateRequest;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NotifySettingsUpdateServiceTest {

    @Mock
    private GatewayAccountDao gatewayAccountDao;

    private NotifySettingsUpdateService notifySettingsUpdateService;

    @Before
    public void setUp() throws Exception {
        notifySettingsUpdateService = new NotifySettingsUpdateService(gatewayAccountDao);
    }

    @Test
    public void shouldReturnOptionalOfGatewayEntity_whenSuccessfullReplace() throws Exception{
        Long gatewayAccountId = 1l;
        JsonNode payload = new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "notify_settings",
                "value", ImmutableMap.of("api_token", "anapitoken",
                        "template_id", "atemplateid")));
        NotifySettingsUpdateRequest request = NotifySettingsUpdateRequest.from(payload);

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountEntity.setId(gatewayAccountId);
        when(gatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));
        Optional<GatewayAccountEntity> optionalEntity = notifySettingsUpdateService.update(gatewayAccountId, request);

        assertThat(optionalEntity.isPresent(), is(true));
        assertThat(optionalEntity.get().getNotifySettings().values(), hasItems("anapitoken", "atemplateid"));
    }

    @Test
    public void shouldReturnOptionalOfGatewayEntity_whenSuccessfullRemove() {
        Long gatewayAccountId = 1l;
        Map<String, String> value = ImmutableMap.of();
        JsonNode payload = new ObjectMapper().valueToTree(ImmutableMap.of("op", "remove",
                "path", "notify_settings"));
        NotifySettingsUpdateRequest request = NotifySettingsUpdateRequest.from(payload);

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountEntity.setId(gatewayAccountId);
        when(gatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(gatewayAccountEntity));
        Optional<GatewayAccountEntity> optionalEntity = notifySettingsUpdateService.update(gatewayAccountId, request);

        assertThat(optionalEntity.isPresent(), is(true));
        assertThat(optionalEntity.get().getNotifySettings(), is(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowAnException_whenNotAllowedOperation() {
        Long gatewayAccountId = 1l;
        Map<String, String> value = ImmutableMap.of("api_token", "anapitoken",
                "template_id", "atemplateid");
        NotifySettingsUpdateRequest request = new NotifySettingsUpdateRequest("insert", "notify_settings", value);
        notifySettingsUpdateService.update(gatewayAccountId, request);
    }
}
