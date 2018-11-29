package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.PatchRequest;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GatewayAccountServiceTest {

    private GatewayAccountDao gatewayAccountDao = mock(GatewayAccountDao.class);
    private CardTypeDao cardTypeDao = mock(CardTypeDao.class);
    private GatewayAccountService updater;

    @Before
    public void setUp() {
        updater = new GatewayAccountService(gatewayAccountDao, cardTypeDao);
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
    
    @Test
    public void shouldUpdateCorporateCreditCardSurchargeAmount() {
        Long gatewayAccountId = 100L;
        PatchRequest request = PatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "corporate_credit_card_surcharge_amount",
                "value", 100)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(gatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));
        Optional<GatewayAccount> optionalGatewayAcc = updater.doPatch(gatewayAccountId, request);
        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setCorporateCreditCardSurchargeAmount(100L);
        verify(gatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateCorporateDebitCardSurchargeAmount() {
        Long gatewayAccountId = 100L;
        PatchRequest request = PatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "corporate_debit_card_surcharge_amount",
                "value", 100)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(gatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));
        Optional<GatewayAccount> optionalGatewayAcc = updater.doPatch(gatewayAccountId, request);
        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setCorporateDebitCardSurchargeAmount(100L);
        verify(gatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateCorporatePrepaidDebitCardSurchargeAmount() {
        Long gatewayAccountId = 100L;
        PatchRequest request = PatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "corporate_prepaid_debit_card_surcharge_amount",
                "value", 100)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(gatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));
        Optional<GatewayAccount> optionalGatewayAcc = updater.doPatch(gatewayAccountId, request);
        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setCorporatePrepaidDebitCardSurchargeAmount(100L);
        verify(gatewayAccountDao).merge(entity);
    }

    @Test
    public void shouldUpdateCorporatePrepaidCreditCardSurchargeAmount() {
        Long gatewayAccountId = 100L;
        PatchRequest request = PatchRequest.from(new ObjectMapper().valueToTree(ImmutableMap.of("op", "replace",
                "path", "corporate_prepaid_credit_card_surcharge_amount",
                "value", 100)));
        GatewayAccountEntity entity = mock(GatewayAccountEntity.class);

        when(gatewayAccountDao.findById(gatewayAccountId)).thenReturn(Optional.of(entity));
        Optional<GatewayAccount> optionalGatewayAcc = updater.doPatch(gatewayAccountId, request);
        assertThat(optionalGatewayAcc.isPresent(), is(true));
        verify(entity).setCorporatePrepaidCreditCardSurchargeAmount(100L);
        verify(gatewayAccountDao).merge(entity);
    }
}
